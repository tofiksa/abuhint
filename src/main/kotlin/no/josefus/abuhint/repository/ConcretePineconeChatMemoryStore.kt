package no.josefus.abuhint.repository

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.Tokenizer
import dev.langchain4j.store.embedding.EmbeddingMatch
import no.josefus.abuhint.configuration.LangChain4jConfiguration

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ConcretePineconeChatMemoryStore(langChain4jConfiguration: LangChain4jConfiguration) :
        PineconeChatMemoryStore(langChain4jConfiguration) {

    private val logger = LoggerFactory.getLogger(ConcretePineconeChatMemoryStore::class.java)

    fun retrieveFromPineconeWithTokenLimit(
        memoryId: String,
        query: String,
        maxTokens: Int,
        tokenizer: Tokenizer
    ): List<EmbeddingMatch<TextSegment>> {
        try {
            val embeddingStore = langChain4jConfiguration.embeddingStore(
                langChain4jConfiguration.embeddingModel(),
                memoryId
            )

            val embeddingModel = langChain4jConfiguration.embeddingModel()
            val queryEmbedding = embeddingModel.embed(query).content()

            val searchResults = embeddingStore.findRelevant(queryEmbedding, 100)

            var tokenCount = 0
            val limitedResults = mutableListOf<EmbeddingMatch<TextSegment>>()

            for (result in searchResults) {
                // Calculate the token count for the current segment
                val segmentTokens = tokenizer.estimateTokenCountInText(result.embedded().text())
                if (tokenCount + segmentTokens > maxTokens) break
                tokenCount += segmentTokens
                limitedResults.add(result)
            }

            logger.info("Retrieved ${limitedResults.size} messages within token limit for ID: $memoryId")
            return limitedResults
        } catch (e: Exception) {
            logger.error("Failed to retrieve chat memory from Pinecone: ${e.message}", e)
            return emptyList()
        }
    }

    /** Converts search results to chat messages */
    fun parseResultsToMessages(segments: List<EmbeddingMatch<TextSegment>>): List<ChatMessage> {

        fun parseLineToChatMessage(line: String): ChatMessage? {
            val parts = line.split(": ", limit = 10)
            if (parts.size == 2 && parts[1].isNotBlank()) {
                return when (parts[0]) {
                    "USER" -> UserMessage(parts[1])
                    "AI" -> AiMessage(parts[1])
                    "SYSTEM" -> SystemMessage(parts[1])
                    else -> null
                }
            }
            return null
        }

        fun convertSegmentToChatMessages(segment: TextSegment): List<ChatMessage> {
            return segment.text().split("\n")
                .filter { it.isNotBlank() }
                .mapNotNull { line -> parseLineToChatMessage(line) }
        }

        return segments.flatMap { embeddingMatch ->
            convertSegmentToChatMessages(embeddingMatch.embedded())
        }
    }
}
