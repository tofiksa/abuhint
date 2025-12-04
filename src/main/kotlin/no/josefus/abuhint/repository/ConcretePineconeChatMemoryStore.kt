package no.josefus.abuhint.repository

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.store.embedding.EmbeddingMatch
import no.josefus.abuhint.service.Tokenizer
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

            // In LangChain4j 1.9+, use search() method with SearchRequest
            val searchResults = try {
                // Try new API first
                val searchRequestClass = Class.forName("dev.langchain4j.store.embedding.SearchRequest")
                val builderMethod = searchRequestClass.getMethod("builder")
                val builder = builderMethod.invoke(null)
                val builderClass = builder.javaClass
                builderClass.getMethod("queryEmbedding", dev.langchain4j.data.embedding.Embedding::class.java).invoke(builder, queryEmbedding)
                builderClass.getMethod("maxResults", Int::class.java).invoke(builder, 5)
                builderClass.getMethod("minScore", Double::class.java).invoke(builder, 0.75)
                val request = builderClass.getMethod("build").invoke(builder)
                val searchMethod = embeddingStore.javaClass.getMethod("search", searchRequestClass)
                val searchResult = searchMethod.invoke(embeddingStore, request)
                // In LangChain4j 1.x, search() returns EmbeddingSearchResult, not List directly
                // Need to call .matches() to get the actual list
                val matchesMethod = searchResult.javaClass.getMethod("matches")
                @Suppress("UNCHECKED_CAST")
                matchesMethod.invoke(searchResult) as List<EmbeddingMatch<TextSegment>>
            } catch (e: Exception) {
                logger.debug("New API failed, trying fallback: ${e.message}")
                // Fallback to old API if available
                try {
                    val findRelevantMethod = embeddingStore.javaClass.getMethod("findRelevant", 
                        dev.langchain4j.data.embedding.Embedding::class.java, Int::class.java, Double::class.java)
                    @Suppress("UNCHECKED_CAST")
                    findRelevantMethod.invoke(embeddingStore, queryEmbedding, 5, 0.75) as List<EmbeddingMatch<TextSegment>>
                } catch (e2: Exception) {
                    logger.warn("Both search APIs failed, returning empty list: ${e2.message}")
                    emptyList<EmbeddingMatch<TextSegment>>()
                }
            }

            var tokenCount = 0
            val limitedResults = mutableListOf<EmbeddingMatch<TextSegment>>()

            for (result in searchResults) {
                // Calculate the token count for the current segment
                val segmentTokens = tokenizer.estimateTokenCount(result.embedded().text())
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
