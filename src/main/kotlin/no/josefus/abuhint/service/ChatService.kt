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
import org.springframework.stereotype.Service

@Service
class ChatService(
    private val assistant: LangChain4jAssistant,
    private val geminiAssistant: TechAdvisorAssistant,
    private val concretePineconeChatMemoryStore: ConcretePineconeChatMemoryStore,
    private val tokenizer: Tokenizer
) {


    fun processChat(chatId: String, userMessage: String): String {
        val logger = org.slf4j.LoggerFactory.getLogger(ChatService::class.java)
        val uuid = UUID.randomUUID().toString()
        val maxContextTokens = 6000
        val tokenizer = this.tokenizer
        
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

        logger.info("Retrieved ${relevantMessages.size} relevant messages from chat memory in ${retrievalMs}ms")

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

            val response = assistant.chat(effectiveChatId, "$trimmedContext\nUser: $userMessage", uuid, dateTime)
            val modelMs = (System.nanoTime() - modelStart) / 1_000_000
            logger.info("Model call returned in ${modelMs}ms (trimmed context path)")
            return postProcessReply(response)
        }
        val modelStart = System.nanoTime()
        val response = assistant.chat(effectiveChatId, "$enhancedMessage\nUser: $userMessage", uuid, dateTime )
        val modelMs = (System.nanoTime() - modelStart) / 1_000_000
        logger.info("Model call returned in ${modelMs}ms")
        return postProcessReply(response)

    }

    fun processGeminiChat(chatId: String, userMessage: String): String {
        val logger = org.slf4j.LoggerFactory.getLogger(ChatService::class.java)
        val uuid = UUID.randomUUID().toString()
        val maxContextTokens = 6000
        val tokenizer = this.tokenizer
        
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

        logger.info("Retrieved ${relevantMessages.size} relevant messages from chat memory in ${retrievalMs}ms")

        // Combine context with current message
        val enhancedMessage =
            formatMessagesToContext(summarizedMessages)

        // Calculate token count
        val totalTokens = tokenizer.estimateTokenCount(enhancedMessage)
        val userTokens = tokenizer.estimateTokenCount(userMessage)
        val totalTokensWithUser = totalTokens + userTokens
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
            val response = geminiAssistant.chat(effectiveChatId, "$trimmedContext\nUser: $userMessage", uuid)
            val modelMs = (System.nanoTime() - modelStart) / 1_000_000
            logger.info("Gemini model call returned in ${modelMs}ms (trimmed context path)")
            return postProcessReply(response)
        }
        val modelStart = System.nanoTime()
        val response = geminiAssistant.chat(effectiveChatId, "$enhancedMessage\nUser: $userMessage", uuid)
        val modelMs = (System.nanoTime() - modelStart) / 1_000_000
        logger.info("Gemini model call returned in ${modelMs}ms")
        return postProcessReply(response)

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
        val contextBuilder = StringBuilder()
        if (messages.isNotEmpty()) {
            contextBuilder.append("Previous relevant conversation context (most recent first):\n")
            messages.forEach { message ->
                val text = getMessageText(message)
                when (message) {
                    is UserMessage -> contextBuilder.append("User: $text\n---\n")
                    is AiMessage -> contextBuilder.append("Assistant: $text\n---\n")
                    is SystemMessage -> contextBuilder.append("System: $text\n---\n")
                }
            }
            contextBuilder.append("\nEnd of recalled context.\nCurrent conversation:\n")
        }
        return contextBuilder.toString()
    }

    private fun postProcessReply(reply: String): String {
        val normalized = reply.trim().replace(Regex("\n{3,}"), "\n\n")
        val maxLen = 1200
        return if (normalized.length > maxLen) normalized.take(maxLen) + " ..." else normalized
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
