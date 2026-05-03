package no.josefus.abuhint.service

import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.service.TokenStream
import dev.langchain4j.store.embedding.EmbeddingMatch
import java.util.UUID
import no.josefus.abuhint.repository.ConcretePineconeChatMemoryStore
import no.josefus.abuhint.repository.LangChain4jAssistant
import no.josefus.abuhint.repository.SecretaryAssistant
import no.josefus.abuhint.repository.TechAdvisorAssistant
import no.josefus.abuhint.secretary.SecretaryChatIds
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.atomic.AtomicBoolean

@Service
class ChatService(
    private val assistant: LangChain4jAssistant,
    private val secretaryAssistant: SecretaryAssistant,
    private val geminiAssistant: TechAdvisorAssistant,
    private val concretePineconeChatMemoryStore: ConcretePineconeChatMemoryStore,
    private val memoryContextService: MemoryContextService,
    @Qualifier("openAiTokenizer")
    private val openAiTokenizer: Tokenizer,
    @Qualifier("geminiTokenizer")
    private val geminiTokenizer: Tokenizer,
) {

    private val log = org.slf4j.LoggerFactory.getLogger(ChatService::class.java)

    private fun <T> retryLlmCall(maxAttempts: Int = 3, block: () -> T): T {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                val isRetryable = e.message?.let { msg ->
                    msg.contains("429") || msg.contains("503") || msg.contains("rate limit", ignoreCase = true) ||
                        msg.contains("temporarily unavailable", ignoreCase = true)
                } ?: false
                if (!isRetryable || attempt == maxAttempts) {
                    log.error("LLM call failed after $attempt attempt(s): ${e.message}")
                    throw e
                }
                val backoffMs = (1000L * attempt).coerceAtMost(4000L)
                log.warn("LLM call failed (attempt $attempt/$maxAttempts), retrying in ${backoffMs}ms: ${e.message}")
                Thread.sleep(backoffMs)
            }
        }
        throw lastException!!
    }

    /** Default brukerreise: Sekretær orkestrering (oppgaver + delegering). LangChain-memory bruker `secretary-`-prefiks. */
    fun processChat(chatId: String, userMessage: String, usageContext: TokenUsageContext? = null): String =
        processOpenAiChatInternal(
            chatId,
            userMessage,
            openAiTokenizer,
            "openai-secretary",
            usageContext,
            assistantMemoryId = SecretaryChatIds::memoryId,
        ) { effId, msg, uuid, dt ->
            retryLlmCall { secretaryAssistant.chat(effId, msg, uuid, dt) }
        }

    /** Direkte coach (Abu-hint) utenom sekretær — brukes av `/api/coach`-endepunkter. */
    fun processCoachChat(chatId: String, userMessage: String, usageContext: TokenUsageContext? = null): String =
        processOpenAiChatInternal(
            chatId,
            userMessage,
            openAiTokenizer,
            "openai-coach",
            usageContext,
        ) { effId, msg, uuid, dt ->
            retryLlmCall { assistant.chat(effId, msg, uuid, dt) }
        }

    fun processGeminiChat(chatId: String, userMessage: String, usageContext: TokenUsageContext? = null): String {
        val dateTime = java.time.LocalDateTime.now().toString()
        return processGeminiChatInternal(chatId, userMessage, geminiTokenizer, "gemini", usageContext) { effectiveChatId, message, uuid, dt ->
            retryLlmCall { geminiAssistant.chat(effectiveChatId, message, uuid, dt) }
        }
    }

    private fun processOpenAiChatInternal(
        chatId: String,
        userMessage: String,
        tokenizer: Tokenizer,
        modelLabel: String,
        usageContext: TokenUsageContext?,
        assistantMemoryId: (String) -> String = { it },
        chatFn: (String, String, String, String) -> String,
    ): String {
        val uuid = UUID.randomUUID().toString()
        val dateTime = java.time.LocalDateTime.now().toString()
        val effectiveChatId = memoryContextService.resolveChatId(chatId)
        val langChainMemoryId = assistantMemoryId(effectiveChatId)
        val retrievalStart = System.nanoTime()
        val prepared = memoryContextService.buildFinalUserMessage(langChainMemoryId, userMessage, tokenizer)
        val retrievalMs = (System.nanoTime() - retrievalStart) / 1_000_000
        log.info("LatMeasure: retrievalMs={} matches={}", retrievalMs, prepared.retrievalMatches)

        val modelStart = System.nanoTime()
        val effectiveUsageContext = usageContext?.copy(chatId = effectiveChatId)
        ChatIdContextHolder.set(langChainMemoryId)
        if (effectiveUsageContext != null) {
            TokenUsageContextHolder.set(effectiveUsageContext)
        }
        val response = try {
            chatFn(langChainMemoryId, prepared.finalUserMessage, uuid, dateTime)
        } finally {
            ChatIdContextHolder.clear()
            TokenUsageContextHolder.clear()
        }
        val modelMs = (System.nanoTime() - modelStart) / 1_000_000
        log.info("LatMeasure: modelMs={} ({})", modelMs, modelLabel)

        val postProcessed = postProcessReply(response)
        logTelemetry(
            model = modelLabel,
            contextMessages = prepared.retrievalMatches,
            contextTokens = tokenizer.estimateTokenCount(prepared.finalUserMessage),
            response = postProcessed,
            retrievalMatches = prepared.retrievalMatches,
        )
        return postProcessed
    }

    private fun processGeminiChatInternal(
        chatId: String,
        userMessage: String,
        tokenizer: Tokenizer,
        modelLabel: String,
        usageContext: TokenUsageContext?,
        chatFn: (String, String, String, String) -> String,
    ): String {
        val uuid = UUID.randomUUID().toString()
        val dateTime = java.time.LocalDateTime.now().toString()
        val effectiveChatId = memoryContextService.resolveChatId(chatId)
        val retrievalStart = System.nanoTime()
        val prepared = memoryContextService.buildFinalUserMessage(effectiveChatId, userMessage, tokenizer)
        val retrievalMs = (System.nanoTime() - retrievalStart) / 1_000_000
        log.info("LatMeasure: retrievalMs={} matches={}", retrievalMs, prepared.retrievalMatches)

        val modelStart = System.nanoTime()
        val effectiveUsageContext = usageContext?.copy(chatId = effectiveChatId)
        ChatIdContextHolder.set(effectiveChatId)
        if (effectiveUsageContext != null) {
            TokenUsageContextHolder.set(effectiveUsageContext)
        }
        val response = try {
            chatFn(effectiveChatId, prepared.finalUserMessage, uuid, dateTime)
        } finally {
            ChatIdContextHolder.clear()
            TokenUsageContextHolder.clear()
        }
        val modelMs = (System.nanoTime() - modelStart) / 1_000_000
        log.info("LatMeasure: modelMs={} ({})", modelMs, modelLabel)

        val postProcessed = postProcessReply(response)
        logTelemetry(
            model = modelLabel,
            contextMessages = prepared.retrievalMatches,
            contextTokens = tokenizer.estimateTokenCount(prepared.finalUserMessage),
            response = postProcessed,
            retrievalMatches = prepared.retrievalMatches,
        )
        return postProcessed
    }

    fun retrieveRelevantContext(
        memoryId: String,
        query: String,
        maxTokens: Int,
        tokenizer: Tokenizer,
    ): List<EmbeddingMatch<TextSegment>> =
        memoryContextService.retrieveRelevantContext(memoryId, query, maxTokens, tokenizer)

    private fun postProcessReply(reply: String): String {
        if (reply.isNullOrBlank()) {
            log.error("Received null or blank reply from assistant")
            return "I encountered an error processing your request. Please try again."
        }
        val normalized = reply.trim().replace(Regex("\n{3,}"), "\n\n")
        val limit = 3900
        if (normalized.length <= limit) return normalized
        return truncateAtBoundary(normalized, limit)
    }

    private fun truncateAtBoundary(text: String, limit: Int): String {
        val lastCodeFence = text.lastIndexOf("```", limit)
        val codeBlocksBefore = text.substring(0, lastCodeFence.coerceAtLeast(0)).count("```")
        if (lastCodeFence > 0 && codeBlocksBefore % 2 != 0) {
            val openFence = text.lastIndexOf("```", lastCodeFence - 1)
            if (openFence > 0) {
                return text.substring(0, openFence).trimEnd() + "\n\n..."
            }
        }

        val searchRegion = text.substring(0, limit)
        val sentenceEnd = searchRegion.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
        return if (sentenceEnd > limit / 2) {
            text.substring(0, sentenceEnd + 1).trimEnd() + "\n\n..."
        } else {
            text.take(limit).trimEnd() + " ..."
        }
    }

    private fun String.count(sub: String): Int {
        var count = 0
        var idx = 0
        while (true) {
            idx = this.indexOf(sub, idx)
            if (idx < 0) break
            count++
            idx += sub.length
        }
        return count
    }

    private fun logTelemetry(
        model: String,
        contextMessages: Int,
        contextTokens: Int,
        response: String,
        retrievalMatches: Int,
    ) {
        val responseChars = response.length
        val responseTokens = (responseChars / 4).coerceAtLeast(1)
        val recallRate = if (retrievalMatches > 0) 1.0 else 0.0
        val hasFollowUpQuestion = response.contains('?')
        log.info(
            "Telemetry: model={} recallMatches={} recallRate={} contextMessages={} contextTokens={} responseChars={} responseTokens={} followUpQuestion={}",
            model,
            retrievalMatches,
            recallRate,
            contextMessages,
            contextTokens,
            responseChars,
            responseTokens,
            hasFollowUpQuestion,
        )
    }

    /**
     * Historikk for standard `/api/chat`-flyt (sekretær): samme Pinecone-minne som brukes med [SecretaryChatIds.memoryId].
     */
    fun getChatHistory(chatId: String): List<ChatMessage> {
        val effectiveChatId = memoryContextService.resolveChatId(chatId)
        val memoryId = SecretaryChatIds.memoryId(effectiveChatId)
        return concretePineconeChatMemoryStore.getMessages(memoryId)
    }

    fun processChatStream(chatId: String, userMessage: String, usageContext: TokenUsageContext? = null): SseEmitter =
        streamFromOpenAiAssistant(
            chatId,
            userMessage,
            openAiTokenizer,
            usageContext,
            assistantMemoryId = SecretaryChatIds::memoryId,
        ) { effId, finalMsg, uuid, dt ->
            secretaryAssistant.chatStream(effId, finalMsg, uuid, dt)
        }

    fun processCoachChatStream(chatId: String, userMessage: String, usageContext: TokenUsageContext? = null): SseEmitter =
        streamFromOpenAiAssistant(chatId, userMessage, openAiTokenizer, usageContext) { effId, finalMsg, uuid, _ ->
            assistant.chatStream(effId, finalMsg, uuid)
        }

    fun processGeminiChatStream(chatId: String, userMessage: String, usageContext: TokenUsageContext? = null): SseEmitter {
        return streamFromGeminiAssistant(chatId, userMessage, geminiTokenizer, usageContext) { effId, finalMsg, uuid, dt ->
            geminiAssistant.chatStream(effId, finalMsg, uuid, dt)
        }
    }

    private fun streamFromOpenAiAssistant(
        chatId: String,
        userMessage: String,
        tokenizer: Tokenizer,
        usageContext: TokenUsageContext?,
        assistantMemoryId: (String) -> String = { it },
        streamFactory: (String, String, String, String) -> TokenStream,
    ): SseEmitter {
        val emitter = SseEmitter(120_000L)
        val uuid = UUID.randomUUID().toString()
        val dateTime = java.time.LocalDateTime.now().toString()
        val effectiveChatId = memoryContextService.resolveChatId(chatId)
        val langChainMemoryId = assistantMemoryId(effectiveChatId)
        val prepared = memoryContextService.buildFinalUserMessage(langChainMemoryId, userMessage, tokenizer)
        val finalMessage = prepared.finalUserMessage

        val effectiveUsageContext = usageContext?.copy(chatId = effectiveChatId)
        val cleanedUp = AtomicBoolean(false)

        fun cleanup() {
            if (!cleanedUp.compareAndSet(false, true)) return
            ChatIdContextHolder.clear()
            TokenUsageContextHolder.clear()
        }

        ChatIdContextHolder.set(langChainMemoryId)
        if (effectiveUsageContext != null) {
            TokenUsageContextHolder.set(effectiveUsageContext)
        }
        val tokenStream = try {
            streamFactory(langChainMemoryId, finalMessage, uuid, dateTime)
        } catch (e: Exception) {
            cleanup()
            throw e
        }
        attachTokenStreamHandlers(emitter, tokenStream, { cleanup() }, effectiveChatId)
        return emitter
    }

    private fun streamFromGeminiAssistant(
        chatId: String,
        userMessage: String,
        tokenizer: Tokenizer,
        usageContext: TokenUsageContext?,
        streamFactory: (String, String, String, String) -> TokenStream,
    ): SseEmitter {
        val emitter = SseEmitter(120_000L)
        val uuid = UUID.randomUUID().toString()
        val dateTime = java.time.LocalDateTime.now().toString()
        val effectiveChatId = memoryContextService.resolveChatId(chatId)
        val prepared = memoryContextService.buildFinalUserMessage(effectiveChatId, userMessage, tokenizer)
        val finalMessage = prepared.finalUserMessage

        val effectiveUsageContext = usageContext?.copy(chatId = effectiveChatId)
        val cleanedUp = AtomicBoolean(false)

        fun cleanup() {
            if (!cleanedUp.compareAndSet(false, true)) return
            ChatIdContextHolder.clear()
            TokenUsageContextHolder.clear()
        }

        ChatIdContextHolder.set(effectiveChatId)
        if (effectiveUsageContext != null) {
            TokenUsageContextHolder.set(effectiveUsageContext)
        }
        val tokenStream = try {
            streamFactory(effectiveChatId, finalMessage, uuid, dateTime)
        } catch (e: Exception) {
            cleanup()
            throw e
        }
        attachTokenStreamHandlers(emitter, tokenStream, { cleanup() }, effectiveChatId)
        return emitter
    }

    private fun attachTokenStreamHandlers(
        emitter: SseEmitter,
        tokenStream: TokenStream,
        cleanup: () -> Unit,
        effectiveChatId: String,
    ) {
        val fullResponse = StringBuilder()
        tokenStream
            .onPartialResponse { token ->
                try {
                    fullResponse.append(token)
                    emitter.send(SseEmitter.event().data(token))
                } catch (e: Exception) {
                    log.debug("Client disconnected during streaming: ${e.message}")
                }
            }
            .onCompleteResponse { _ ->
                try {
                    emitter.send(SseEmitter.event().data("[DONE]"))
                    emitter.complete()
                } catch (e: Exception) {
                    log.debug("Error completing SSE stream: ${e.message}")
                } finally {
                    cleanup()
                }
            }
            .onError { error ->
                log.error("Streaming error: ${error.message}", error)
                try {
                    emitter.send(SseEmitter.event().data("{\"error\": \"${error.message}\"}"))
                    emitter.completeWithError(error)
                } catch (e: Exception) {
                    log.debug("Error sending error event: ${e.message}")
                } finally {
                    cleanup()
                }
            }
        try {
            tokenStream.start()
        } catch (e: Exception) {
            cleanup()
            throw e
        }

        emitter.onCompletion { cleanup() }
        emitter.onTimeout {
            log.warn("SSE stream timed out for chatId=$effectiveChatId")
            cleanup()
        }
        emitter.onError {
            log.debug("SSE emitter error for chatId=$effectiveChatId: ${it.message}")
            cleanup()
        }
    }
}
