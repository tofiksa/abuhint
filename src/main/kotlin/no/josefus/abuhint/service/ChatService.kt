package no.josefus.abuhint.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.store.embedding.EmbeddingMatch
import no.josefus.abuhint.service.Tokenizer
import java.util.UUID
import no.josefus.abuhint.repository.ConcretePineconeChatMemoryStore
import no.josefus.abuhint.repository.LangChain4jAssistant
import no.josefus.abuhint.repository.TechAdvisorAssistant
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

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
    fun processChat(chatId: String, userMessage: String): String {
        val logger = org.slf4j.LoggerFactory.getLogger(ChatService::class.java)
        val uuid = UUID.randomUUID().toString()
        val maxContextTokens = 5000
        val tokenizer = openAiTokenizer
        
        // Require a unique chatId per session to avoid cross-talk
        val effectiveChatId = chatId.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also {
            logger.warn("No chatId provided; generated session id $it to isolate conversation.")
        }

        val retrievalStart = System.nanoTime()
        val relevantEmbeddingMatches =
            retrieveRelevantContext(
                effectiveChatId, userMessage, maxContextTokens, tokenizer
            )
        val retrievalMs = (System.nanoTime() - retrievalStart) / 1_000_000

        val relevantMessages =
            concretePineconeChatMemoryStore.parseResultsToMessages(relevantEmbeddingMatches)
        val summarizedMessages = summarizeIfLong(relevantMessages, tokenizer, 20, 800)

        logger.info("LatMeasure: retrievalMs={} matches={}", retrievalMs, relevantMessages.size)

        // Combine context with current message
        val enhancedMessage =
            formatMessagesToContext(summarizedMessages)

        logger.info("Enhanced message {}", enhancedMessage)

        // Calculate token count
        val totalTokens = tokenizer.estimateTokenCount(enhancedMessage)
        val userTokens = tokenizer.estimateTokenCount(userMessage)
        val totalTokensWithUser = totalTokens + userTokens
        val dateTime = java.time.LocalDateTime.now().toString()
        logger.info("Total tokens in message (context + user): $totalTokensWithUser")
        // Better context trimming
        if (totalTokensWithUser > maxContextTokens) {
            // Instead of just taking characters, prioritize keeping complete messages
            // Split into message units
            val messages = relevantMessages.toList()
            val trimmedMessages = mutableListOf<ChatMessage>()
            var currentTokenCount = tokenizer.estimateTokenCount(userMessage)


            for (message in messages) {
                val messageText = getMessageText(message)
                val messageTokens = tokenizer.estimateTokenCount(messageText)
                if (currentTokenCount + messageTokens <= maxContextTokens) {
                    trimmedMessages.add(message)
                    currentTokenCount += messageTokens
                } else {
                    break
                }
            }

            // Rebuild context with only the messages that fit
            val trimmedContext = formatMessagesToContext(trimmedMessages)
            val modelStart = System.nanoTime()

            val response = assistant.chat(effectiveChatId, "$trimmedContext\nUser: $userMessage", uuid, dateTime)
            val modelMs = (System.nanoTime() - modelStart) / 1_000_000
            logger.info("LatMeasure: modelMs={} (OpenAI/Abu-hint, trimmed path)", modelMs)
            val postModelStart = System.nanoTime()
            val postProcessed = postProcessReply(response)
            logTelemetry(
                model = "openai",
                contextMessages = trimmedMessages.size,
                contextTokens = tokenizer.estimateTokenCount(trimmedContext),
                response = postProcessed,
                retrievalMatches = relevantMessages.size
            )
            val postModelMs = (System.nanoTime() - postModelStart) / 1_000_000
            logger.info("LatMeasure: postProcessMs={} (OpenAI/Abu-hint, trimmed path)", postModelMs)
            return postProcessed
        }
        val modelStart = System.nanoTime()
        log.info("meldingen som sendes til langchain4jAssistant: chatId={} userMessage=\"{}\" uuid={} dateTime={}",
            chatId,
            userMessage.take(100),
            uuid,
            dateTime
        )
        val response = assistant.chat(effectiveChatId, "$enhancedMessage\nUser: $userMessage", uuid, dateTime )
        val modelMs = (System.nanoTime() - modelStart) / 1_000_000
        logger.info("LatMeasure: modelMs={} (OpenAI/Abu-hint)", modelMs)
        val postModelStart = System.nanoTime()
        val postProcessed = postProcessReply(response)
        logTelemetry(
            model = "openai",
            contextMessages = summarizedMessages.size,
            contextTokens = tokenizer.estimateTokenCount(enhancedMessage),
            response = postProcessed,
            retrievalMatches = relevantMessages.size
        )
        val postModelMs = (System.nanoTime() - postModelStart) / 1_000_000
        logger.info("LatMeasure: postProcessMs={} (OpenAI/Abu-hint)", postModelMs)
        return postProcessed

    }

    fun processGeminiChat(chatId: String, userMessage: String): String {
        val logger = org.slf4j.LoggerFactory.getLogger(ChatService::class.java)
        val uuid = UUID.randomUUID().toString()
        val maxContextTokens = 5000
        val tokenizer = geminiTokenizer
        
        val effectiveChatId = chatId.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also {
            logger.warn("No chatId provided; generated session id $it to isolate conversation.")
        }

        val retrievalStart = System.nanoTime()
        val relevantEmbeddingMatches =
            retrieveRelevantContext(
                effectiveChatId, userMessage, maxContextTokens, tokenizer
            )
        val retrievalMs = (System.nanoTime() - retrievalStart) / 1_000_000

        val relevantMessages =
            concretePineconeChatMemoryStore.parseResultsToMessages(relevantEmbeddingMatches)
        val summarizedMessages = summarizeIfLong(relevantMessages, tokenizer, 20, 800)

        logger.info("LatMeasure: retrievalMs={} matches={}", retrievalMs, relevantMessages.size)

        // Combine context with current message
        val enhancedMessage =
            formatMessagesToContext(summarizedMessages)

        // Calculate token count
        val totalTokens = tokenizer.estimateTokenCount(enhancedMessage)
        val userTokens = tokenizer.estimateTokenCount(userMessage)
        val totalTokensWithUser = totalTokens + userTokens
        val dateTime = java.time.LocalDateTime.now().toString()
        logger.info("Total tokens in message (context + user): $totalTokensWithUser")
        // Better context trimming
        if (totalTokensWithUser > maxContextTokens) {
            // Instead of just taking characters, prioritize keeping complete messages
            // Split into message units
            val messages = relevantMessages.toList()
            val trimmedMessages = mutableListOf<ChatMessage>()
            var currentTokenCount = tokenizer.estimateTokenCount(userMessage)

            for (message in messages) {
                val messageText = getMessageText(message)
                val messageTokens = tokenizer.estimateTokenCount(messageText)
                if (currentTokenCount + messageTokens <= maxContextTokens) {
                    trimmedMessages.add(message)
                    currentTokenCount += messageTokens
                } else {
                    break
                }
            }

            // Rebuild context with only the messages that fit
            val trimmedContext = formatMessagesToContext(trimmedMessages)
            val modelStart = System.nanoTime()
            val response = geminiAssistant.chat(effectiveChatId, "$trimmedContext\nUser: $userMessage", uuid, dateTime)
            val modelMs = (System.nanoTime() - modelStart) / 1_000_000
            logger.info("LatMeasure: modelMs={} (Gemini/Abdikverrulant, trimmed path)", modelMs)
            val postModelStart = System.nanoTime()
            val postProcessed = postProcessReply(response)
            logTelemetry(
                model = "gemini",
                contextMessages = trimmedMessages.size,
                contextTokens = tokenizer.estimateTokenCount(trimmedContext),
                response = postProcessed,
                retrievalMatches = relevantMessages.size
            )
            val postModelMs = (System.nanoTime() - postModelStart) / 1_000_000
            logger.info("LatMeasure: postProcessMs={} (Gemini/Abdikverrulant, trimmed path)", postModelMs)
            return postProcessed
        }
        val modelStart = System.nanoTime()
        val response = geminiAssistant.chat(effectiveChatId, "$enhancedMessage\nUser: $userMessage", uuid, dateTime)
        val modelMs = (System.nanoTime() - modelStart) / 1_000_000
        logger.info("LatMeasure: modelMs={} (Gemini/Abdikverrulant)", modelMs)
        val postModelStart = System.nanoTime()
        val postProcessed = postProcessReply(response)
        logTelemetry(
            model = "gemini",
            contextMessages = summarizedMessages.size,
            contextTokens = tokenizer.estimateTokenCount(enhancedMessage),
            response = postProcessed,
            retrievalMatches = relevantMessages.size
        )
        val postModelMs = (System.nanoTime() - postModelStart) / 1_000_000
        logger.info("LatMeasure: postProcessMs={} (Gemini/Abdikverrulant)", postModelMs)
        return postProcessed

    }


    private fun getMessageText(message: ChatMessage): String {
        return when (message) {
            is UserMessage -> {
                try {
                    message.javaClass.getMethod("singleText").invoke(message) as? String
                        ?: message.javaClass.getMethod("text").invoke(message) as? String
                        ?: ""
                } catch (e: Exception) {
                    message.toString().substringAfter("text=").substringBefore(",").substringBefore(")") ?: ""
                }
            }
            is AiMessage -> {
                try {
                    message.javaClass.getMethod("singleText").invoke(message) as? String
                        ?: message.javaClass.getMethod("text").invoke(message) as? String
                        ?: ""
                } catch (e: Exception) {
                    message.toString().substringAfter("text=").substringBefore(",").substringBefore(")") ?: ""
                }
            }
            is SystemMessage -> {
                try {
                    message.javaClass.getMethod("singleText").invoke(message) as? String
                        ?: message.javaClass.getMethod("text").invoke(message) as? String
                        ?: ""
                } catch (e: Exception) {
                    message.toString().substringAfter("text=").substringBefore(",").substringBefore(")") ?: ""
                }
            }
            else -> ""
        }
    }

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
        val acknowledged = ensureAcknowledgement(normalized)
        val softLimit = 700
        val hardLimit = 1200
        val trimmed = if (acknowledged.length > hardLimit) {
            acknowledged.take(hardLimit).trimEnd() + " ..."
        } else {
            acknowledged
        }
        return if (trimmed.length > softLimit) {
            trimmed.take(softLimit).trimEnd() + " ..."
        } else {
            trimmed
        }
    }

    private fun ensureAcknowledgement(text: String): String {
        val trimmed = text.trimStart()
        val firstSentence = trimmed.substringBefore("\n").substringBefore(".")
        val alreadyAcknowledged = Regex("^(got it|ok|okay|sure|understood|thanks)", RegexOption.IGNORE_CASE)
            .containsMatchIn(firstSentence)
        if (alreadyAcknowledged) return text
        return "Got it. $trimmed"
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



}
