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

    /**
     * Retrieves relevant chat messages from Pinecone using semantic search.
     * Messages are filtered by token limit and relevance score.
     * 
     * @param memoryId The chat session ID
     * @param query The search query (typically the current user message)
     * @param maxTokens Maximum number of tokens to retrieve
     * @param tokenizer Tokenizer for counting tokens
     * @return List of relevant embedding matches sorted by relevance
     */
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

            // Perform semantic search - retrieve more results than needed to account for token filtering
            // Using a lower minScore (0.5) to get more relevant results, then filter by tokens
            val searchResults = embeddingStore.findRelevant(
                queryEmbedding,
                50,  // maxResults - Get more results initially
                0.5  // minScore - Lower threshold for better recall
            )

            // Filter results by token limit while maintaining relevance order
            var tokenCount = 0
            val limitedResults = mutableListOf<EmbeddingMatch<TextSegment>>()

            for (result in searchResults) {
                // Calculate the token count for the current segment
                val segmentText = result.embedded().text()
                val segmentTokens = tokenizer.estimateTokenCountInText(segmentText)
                
                // Stop if adding this segment would exceed the token limit
                if (tokenCount + segmentTokens > maxTokens) {
                    logger.debug("Token limit reached at ${limitedResults.size} messages (${tokenCount} tokens)")
                    break
                }
                
                tokenCount += segmentTokens
                limitedResults.add(result)
                
                logger.debug("Added message with ${segmentTokens} tokens (total: $tokenCount)")
            }

            logger.info("Retrieved ${limitedResults.size} relevant messages from Pinecone for ID: $memoryId " +
                    "(${tokenCount} tokens, query: '${query.take(50)}...')")
            
            return limitedResults
        } catch (e: Exception) {
            logger.error("Failed to retrieve chat memory from Pinecone for ID: $memoryId: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Converts Pinecone search results to ChatMessage objects.
     * Each TextSegment contains a single message formatted as "TYPE: content".
     * 
     * @param segments List of embedding matches from Pinecone
     * @return List of parsed ChatMessage objects
     */
    fun parseResultsToMessages(segments: List<EmbeddingMatch<TextSegment>>): List<ChatMessage> {
        return segments.mapNotNull { embeddingMatch ->
            parseSegmentToChatMessage(embeddingMatch.embedded())
        }
    }
    
    /**
     * Parses a single TextSegment to a ChatMessage.
     * Expected format: "MESSAGE_TYPE: message content"
     */
    private fun parseSegmentToChatMessage(segment: TextSegment): ChatMessage? {
        val text = segment.text().trim()
        if (text.isBlank()) return null
        
        // Split on first ": " to separate type from content
        val colonIndex = text.indexOf(": ")
        if (colonIndex == -1) {
            logger.warn("Invalid message format (missing ': '): ${text.take(50)}")
            return null
        }
        
        val messageType = text.substring(0, colonIndex).trim()
        val messageContent = text.substring(colonIndex + 2).trim()
        
        if (messageContent.isBlank()) {
            logger.warn("Empty message content for type: $messageType")
            return null
        }
        
        return when (messageType.uppercase()) {
            "USER" -> UserMessage(messageContent)
            "AI" -> AiMessage(messageContent)
            "SYSTEM" -> SystemMessage(messageContent)
            else -> {
                logger.warn("Unknown message type: $messageType")
                null
            }
        }
    }
}
