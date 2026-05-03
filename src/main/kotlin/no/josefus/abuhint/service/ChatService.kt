package no.josefus.abuhint.service

import dev.langchain4j.service.TokenStream
import no.josefus.abuhint.repository.LangChain4jAssistant
import no.josefus.abuhint.repository.TechAdvisorAssistant
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Service
class ChatService(
    private val assistant: LangChain4jAssistant,
    private val geminiAssistant: TechAdvisorAssistant,
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

    fun processChat(chatId: String, userMessage: String, usageContext: TokenUsageContext? = null): String {
        val dateTime = java.time.LocalDateTime.now().toString()
        val effectiveChatId = memoryContextService.resolveChatId(chatId)
        val uuid = UUID.randomUUID().toString()
        val ctx = memoryContextService.buildFinalUserMessage(effectiveChatId, userMessage, openAiTokenizer)

        return invokeModel(effectiveChatId, usageContext) {
            retryLlmCall { assistant.chat(effectiveChatId, ctx.finalUserMessage, uuid, dateTime) }
        }
    }

    fun processGeminiChat(chatId: String, userMessage: String, usageContext: TokenUsageContext? = null): String {
        val dateTime = java.time.LocalDateTime.now().toString()
        val effectiveChatId = memoryContextService.resolveChatId(chatId)
        val uuid = UUID.randomUUID().toString()
        val ctx = memoryContextService.buildFinalUserMessage(effectiveChatId, userMessage, geminiTokenizer)

        return invokeModel(effectiveChatId, usageContext) {
            retryLlmCall { geminiAssistant.chat(effectiveChatId, ctx.finalUserMessage, uuid, dateTime) }
        }
    }

    private fun invokeModel(
        effectiveChatId: String,
        usageContext: TokenUsageContext?,
        block: () -> String,
    ): String {
        val effectiveUsageContext = usageContext?.copy(chatId = effectiveChatId)
        ChatIdContextHolder.set(effectiveChatId)
        if (effectiveUsageContext != null) {
            TokenUsageContextHolder.set(effectiveUsageContext)
        }
        val response = try {
            block()
        } finally {
            ChatIdContextHolder.clear()
            TokenUsageContextHolder.clear()
        }
        return postProcessReply(response)
    }

    fun getChatHistory(chatId: String) =
        memoryContextService.getChatHistory(chatId)

    fun processChatStream(chatId: String, userMessage: String, usageContext: TokenUsageContext? = null): SseEmitter {
        val effectiveChatId = memoryContextService.resolveChatId(chatId)
        val ctx = memoryContextService.buildFinalUserMessage(effectiveChatId, userMessage, openAiTokenizer)
        val uuid = UUID.randomUUID().toString()
        return streamFromAssistant(effectiveChatId, ctx.finalUserMessage, usageContext) {
            val dateTime = java.time.LocalDateTime.now().toString()
            assistant.chatStream(effectiveChatId, it, uuid)
        }
    }

    fun processGeminiChatStream(chatId: String, userMessage: String, usageContext: TokenUsageContext? = null): SseEmitter {
        val effectiveChatId = memoryContextService.resolveChatId(chatId)
        val ctx = memoryContextService.buildFinalUserMessage(effectiveChatId, userMessage, geminiTokenizer)
        val uuid = UUID.randomUUID().toString()
        return streamFromAssistant(effectiveChatId, ctx.finalUserMessage, usageContext) {
            val dateTime = java.time.LocalDateTime.now().toString()
            geminiAssistant.chatStream(effectiveChatId, it, uuid, dateTime)
        }
    }

    private fun streamFromAssistant(
        effectiveChatId: String,
        finalMessage: String,
        usageContext: TokenUsageContext?,
        streamFactory: (String) -> TokenStream,
    ): SseEmitter {
        val emitter = SseEmitter(120_000L)
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
            streamFactory(finalMessage)
        } catch (e: Exception) {
            cleanup()
            throw e
        }
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

        return emitter
    }

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
}
