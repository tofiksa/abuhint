package no.josefus.abuhint.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.service.TokenStream
import dev.langchain4j.store.embedding.EmbeddingMatch
import no.josefus.abuhint.service.Tokenizer
import java.util.UUID
import no.josefus.abuhint.repository.ConcretePineconeChatMemoryStore
import no.josefus.abuhint.repository.LangChain4jAssistant
import no.josefus.abuhint.repository.TechAdvisorAssistant
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class ChatService(
    private val assistant: LangChain4jAssistant,
    private val geminiAssistant: TechAdvisorAssistant,
    private val concretePineconeChatMemoryStore: ConcretePineconeChatMemoryStore,
    @Qualifier("openAiTokenizer")
    private val openAiTokenizer: Tokenizer,
    @Qualifier("geminiTokenizer")
    private val geminiTokenizer: Tokenizer
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

    fun processChat(chatId: String, userMessage: String): String {
        val dateTime = java.time.LocalDateTime.now().toString()
        return processChatInternal(chatId, userMessage, openAiTokenizer, "openai") { effectiveChatId, message, uuid ->
            retryLlmCall { assistant.chat(effectiveChatId, message, uuid, dateTime) }
        }
    }

    fun processGeminiChat(chatId: String, userMessage: String): String {
        val dateTime = java.time.LocalDateTime.now().toString()
        return processChatInternal(chatId, userMessage, geminiTokenizer, "gemini") { effectiveChatId, message, uuid ->
            retryLlmCall { geminiAssistant.chat(effectiveChatId, message, uuid, dateTime) }
        }
    }

    private fun processChatInternal(
        chatId: String,
        userMessage: String,
        tokenizer: Tokenizer,
        modelLabel: String,
        chatFn: (String, String, String) -> String
    ): String {
        val uuid = UUID.randomUUID().toString()
        val maxContextTokens = 5000

        val effectiveChatId = chatId.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also {
            log.warn("No chatId provided; generated session id $it to isolate conversation.")
        }

        val retrievalStart = System.nanoTime()
        val relevantEmbeddingMatches = retrieveRelevantContext(effectiveChatId, userMessage, maxContextTokens, tokenizer)
        val retrievalMs = (System.nanoTime() - retrievalStart) / 1_000_000

        val relevantMessages = concretePineconeChatMemoryStore.parseResultsToMessages(relevantEmbeddingMatches)
        val summarizedMessages = summarizeIfLong(relevantMessages, tokenizer, 20, 800)
        log.info("LatMeasure: retrievalMs={} matches={}", retrievalMs, relevantMessages.size)

        val enhancedMessage = formatMessagesToContext(summarizedMessages)
        val totalTokensWithUser = tokenizer.estimateTokenCount(enhancedMessage) + tokenizer.estimateTokenCount(userMessage)
        log.info("Total tokens in message (context + user): $totalTokensWithUser")

        val contextMessages: Int
        val contextTokens: Int
        val finalMessage: String

        if (totalTokensWithUser > maxContextTokens) {
            val trimmedMessages = trimToTokenLimit(relevantMessages, userMessage, maxContextTokens, tokenizer)
            val trimmedContext = formatMessagesToContext(trimmedMessages)
            finalMessage = "$trimmedContext\nUser: $userMessage"
            contextMessages = trimmedMessages.size
            contextTokens = tokenizer.estimateTokenCount(trimmedContext)
        } else {
            finalMessage = "$enhancedMessage\nUser: $userMessage"
            contextMessages = summarizedMessages.size
            contextTokens = tokenizer.estimateTokenCount(enhancedMessage)
        }

        val modelStart = System.nanoTime()
        val response = chatFn(effectiveChatId, finalMessage, uuid)
        val modelMs = (System.nanoTime() - modelStart) / 1_000_000
        log.info("LatMeasure: modelMs={} ({})", modelMs, modelLabel)

        val postProcessed = postProcessReply(response)
        logTelemetry(
            model = modelLabel,
            contextMessages = contextMessages,
            contextTokens = contextTokens,
            response = postProcessed,
            retrievalMatches = relevantMessages.size
        )
        return postProcessed
    }


    private fun getMessageText(message: ChatMessage): String = ChatMessageUtils.getMessageText(message)

    private fun formatMessagesToContext(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) return ""
        val contextBuilder = StringBuilder()
        contextBuilder.append("Context recap (most recent first). Use citation tags like [memory#1] when referring to these turns:\n")
        messages.forEachIndexed { index, message ->
            val text = getMessageText(message)
            val recencyTag = "[memory#${index + 1}]"
            when (message) {
                is UserMessage -> contextBuilder.append("$recencyTag User: $text\n")
                is AiMessage -> contextBuilder.append("$recencyTag Assistant: $text\n")
                is SystemMessage -> contextBuilder.append("$recencyTag System: $text\n")
            }
        }
        contextBuilder.append("End of recap.\nCurrent conversation:\n")
        return contextBuilder.toString()
    }

    private fun postProcessReply(reply: String): String {
        if (reply.isNullOrBlank()) {
            val logger = org.slf4j.LoggerFactory.getLogger(ChatService::class.java)
            logger.error("Received null or blank reply from assistant")
            return "I encountered an error processing your request. Please try again."
        }
        val normalized = reply.trim().replace(Regex("\n{3,}"), "\n\n")
        val softLimit = 3200
        val hardLimit = 3900
        val trimmed = if (normalized.length > hardLimit) {
            normalized.take(hardLimit).trimEnd() + " ..."
        } else {
            normalized
        }
        return if (trimmed.length > softLimit) {
            trimmed.take(softLimit).trimEnd() + " ..."
        } else {
            trimmed
        }
    }

    private fun logTelemetry(
        model: String,
        contextMessages: Int,
        contextTokens: Int,
        response: String,
        retrievalMatches: Int
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
            hasFollowUpQuestion
        )
    }

    /**
     * Summarize older messages if the list is long, keeping the most recent `keepRecent` messages
     * verbatim and prepending a brief summary of older content.
     */
    private fun summarizeIfLong(
        messages: List<ChatMessage>,
        tokenizer: Tokenizer,
        keepRecent: Int,
        summaryCharLimit: Int
    ): List<ChatMessage> {
        if (messages.size <= keepRecent) return messages

        val recent = messages.takeLast(keepRecent)
        val older = messages.dropLast(keepRecent)
        val summaryText = older.joinToString(" ") { getMessageText(it) }.take(summaryCharLimit)
        val summary = SystemMessage("Summary of earlier conversation: $summaryText")
        return listOf(summary) + recent
    }


    fun retrieveRelevantContext(
        memoryId: String,
        query: String,
        maxTokens: Int,
        tokenizer: Tokenizer
    ): List<EmbeddingMatch<TextSegment>> {
        val hasHistory = concretePineconeChatMemoryStore.hasStoredMessages(memoryId)
        if (!hasHistory) {
            val logger = org.slf4j.LoggerFactory.getLogger(ChatService::class.java)
            logger.info("No stored messages for chatId=$memoryId; skipping retrieval to avoid cold-start latency")
            return emptyList()
        }
        return concretePineconeChatMemoryStore.retrieveFromPineconeWithTokenLimit(memoryId, query, maxTokens, tokenizer)
    }

    fun processChatStream(chatId: String, userMessage: String): SseEmitter {
        return streamFromAssistant(chatId, userMessage, openAiTokenizer) { effectiveChatId, enhancedMessage, uuid ->
            val dateTime = java.time.LocalDateTime.now().toString()
            assistant.chatStream(effectiveChatId, enhancedMessage, uuid)
        }
    }

    fun processGeminiChatStream(chatId: String, userMessage: String): SseEmitter {
        return streamFromAssistant(chatId, userMessage, geminiTokenizer) { effectiveChatId, enhancedMessage, uuid ->
            val dateTime = java.time.LocalDateTime.now().toString()
            geminiAssistant.chatStream(effectiveChatId, enhancedMessage, uuid, dateTime)
        }
    }

    private fun streamFromAssistant(
        chatId: String,
        userMessage: String,
        tokenizer: Tokenizer,
        streamFactory: (String, String, String) -> TokenStream
    ): SseEmitter {
        val emitter = SseEmitter(120_000L)
        val uuid = UUID.randomUUID().toString()
        val maxContextTokens = 5000

        val effectiveChatId = chatId.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also {
            log.warn("No chatId provided; generated session id $it to isolate conversation.")
        }

        val relevantEmbeddingMatches = retrieveRelevantContext(effectiveChatId, userMessage, maxContextTokens, tokenizer)
        val relevantMessages = concretePineconeChatMemoryStore.parseResultsToMessages(relevantEmbeddingMatches)
        val summarizedMessages = summarizeIfLong(relevantMessages, tokenizer, 20, 800)
        val enhancedMessage = formatMessagesToContext(summarizedMessages)

        val totalTokens = tokenizer.estimateTokenCount(enhancedMessage) + tokenizer.estimateTokenCount(userMessage)
        val finalMessage = if (totalTokens > maxContextTokens) {
            val trimmedMessages = trimToTokenLimit(relevantMessages, userMessage, maxContextTokens, tokenizer)
            "${formatMessagesToContext(trimmedMessages)}\nUser: $userMessage"
        } else {
            "$enhancedMessage\nUser: $userMessage"
        }

        val tokenStream = streamFactory(effectiveChatId, finalMessage, uuid)
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
                }
            }
            .onError { error ->
                log.error("Streaming error: ${error.message}", error)
                try {
                    emitter.send(SseEmitter.event().data("{\"error\": \"${error.message}\"}"))
                    emitter.completeWithError(error)
                } catch (e: Exception) {
                    log.debug("Error sending error event: ${e.message}")
                }
            }
            .start()

        emitter.onTimeout { log.warn("SSE stream timed out for chatId=$effectiveChatId") }
        emitter.onError { log.debug("SSE emitter error for chatId=$effectiveChatId: ${it.message}") }

        return emitter
    }

    private fun trimToTokenLimit(
        messages: List<ChatMessage>,
        userMessage: String,
        maxTokens: Int,
        tokenizer: Tokenizer
    ): List<ChatMessage> {
        val trimmedMessages = mutableListOf<ChatMessage>()
        var currentTokenCount = tokenizer.estimateTokenCount(userMessage)
        for (message in messages) {
            val messageTokens = tokenizer.estimateTokenCount(getMessageText(message))
            if (currentTokenCount + messageTokens <= maxTokens) {
                trimmedMessages.add(message)
                currentTokenCount += messageTokens
            } else break
        }
        return trimmedMessages
    }

}
