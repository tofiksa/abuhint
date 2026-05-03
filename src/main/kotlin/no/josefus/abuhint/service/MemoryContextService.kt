package no.josefus.abuhint.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.store.embedding.EmbeddingMatch
import no.josefus.abuhint.repository.ConcretePineconeChatMemoryStore
import org.springframework.stereotype.Service
import java.util.UUID

data class MemoryContextResult(
    /** Chat/memory id brukt for lagring og retrieval (kan være generert). */
    val effectiveChatId: String,
    /** Ferdig UserMessage-innhold sendt til LLM (inkl. kontekstprep). */
    val finalUserMessage: String,
    val retrievalMatches: Int = 0,
)

@Service
class MemoryContextService(
    private val concretePineconeChatMemoryStore: ConcretePineconeChatMemoryStore,
) {

    private val log = org.slf4j.LoggerFactory.getLogger(MemoryContextService::class.java)

    fun resolveChatId(chatId: String): String =
        chatId.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also {
            log.warn("No chatId provided; generated session id $it to isolate conversation.")
        }

    /**
     * Manual Pinecone retrieval + context prep (samme logikk som tidligere ChatService).
     */
    fun buildFinalUserMessage(
        memoryId: String,
        userMessage: String,
        tokenizer: Tokenizer,
        maxContextTokens: Int = 5000,
    ): MemoryContextResult {
        val relevantEmbeddingMatches = retrieveRelevantContext(memoryId, userMessage, maxContextTokens, tokenizer)
        val relevantMessages = concretePineconeChatMemoryStore.parseResultsToMessages(relevantEmbeddingMatches)
        val summarizedMessages = summarizeIfLong(relevantMessages, tokenizer, 20, 800)
        log.info("MemoryContext: retrieval matches={}", relevantMessages.size)

        val enhancedMessage = formatMessagesToContext(summarizedMessages)
        val totalTokensWithUser = tokenizer.estimateTokenCount(enhancedMessage) + tokenizer.estimateTokenCount(userMessage)

        val finalMessage: String =
            if (totalTokensWithUser > maxContextTokens) {
                val trimmedMessages = trimToTokenLimit(relevantMessages, userMessage, maxContextTokens, tokenizer)
                val trimmedContext = formatMessagesToContext(trimmedMessages)
                "$trimmedContext\nUser: $userMessage"
            } else {
                "$enhancedMessage\nUser: $userMessage"
            }

        return MemoryContextResult(
            effectiveChatId = memoryId,
            finalUserMessage = finalMessage,
            retrievalMatches = relevantMessages.size,
        )
    }

    fun retrieveRelevantContext(
        memoryId: String,
        query: String,
        maxTokens: Int,
        tokenizer: Tokenizer,
    ): List<EmbeddingMatch<TextSegment>> {
        val hasHistory = concretePineconeChatMemoryStore.hasStoredMessages(memoryId)
        if (!hasHistory) {
            log.info("No stored messages for chatId=$memoryId; skipping retrieval to avoid cold-start latency")
            return emptyList()
        }
        return concretePineconeChatMemoryStore.retrieveFromPineconeWithTokenLimit(memoryId, query, maxTokens, tokenizer)
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

    private fun summarizeIfLong(
        messages: List<ChatMessage>,
        tokenizer: Tokenizer,
        keepRecent: Int,
        summaryCharLimit: Int,
    ): List<ChatMessage> {
        if (messages.size <= keepRecent) return messages
        val recent = messages.takeLast(keepRecent)
        val older = messages.dropLast(keepRecent)
        val summaryText = older.joinToString(" ") { getMessageText(it) }.take(summaryCharLimit)
        val summary = SystemMessage("Summary of earlier conversation: $summaryText")
        return listOf(summary) + recent
    }

    private fun trimToTokenLimit(
        messages: List<ChatMessage>,
        userMessage: String,
        maxTokens: Int,
        tokenizer: Tokenizer,
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
