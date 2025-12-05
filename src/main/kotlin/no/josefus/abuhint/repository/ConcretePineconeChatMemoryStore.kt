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

            // Perform semantic search using the search method
            // Use getMethods() instead of getDeclaredMethod to avoid ClassNotFoundException
            // when optional dependencies aren't available
            val searchResults: List<EmbeddingMatch<TextSegment>> = try {
                val allMethods = embeddingStore.javaClass.methods
                val searchMethods = allMethods.filter { it.name == "search" }
                
                if (searchMethods.isEmpty()) {
                    logger.error("No 'search' method found in EmbeddingStore. Available methods: ${allMethods.map { "${it.name}(${it.parameterTypes.joinToString { it.simpleName }})" }}")
                    emptyList()
                } else {
                    logger.debug("Found ${searchMethods.size} search method(s). Signatures: ${searchMethods.map { "${it.name}(${it.parameterTypes.joinToString { it.simpleName }})" }}")
                    
                    var results: List<EmbeddingMatch<TextSegment>>? = null
                    var lastError: Exception? = null
                    
                    // Try all search methods with 3 parameters first (most common: Embedding, int, double)
                    for (method in searchMethods.filter { it.parameterTypes.size == 3 }) {
                        try {
                            // Check if first parameter is Embedding type
                            val firstParam = method.parameterTypes[0]
                            if (dev.langchain4j.data.embedding.Embedding::class.java.isAssignableFrom(firstParam)) {
                                @Suppress("UNCHECKED_CAST")
                                val invoked = method.invoke(embeddingStore, queryEmbedding, 50, 0.3)
                                results = invoked as? List<EmbeddingMatch<TextSegment>>
                                if (results != null) {
                                    logger.debug("Successfully invoked search method: ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            lastError = e
                            logger.debug("Failed to invoke search method with 3 params: ${e.message}")
                        }
                    }
                    
                    // Try all search methods with 2 parameters
                    if (results == null) {
                        for (method in searchMethods.filter { it.parameterTypes.size == 2 }) {
                            try {
                                // Check if first parameter is Embedding type
                                val firstParam = method.parameterTypes[0]
                                if (dev.langchain4j.data.embedding.Embedding::class.java.isAssignableFrom(firstParam)) {
                                    @Suppress("UNCHECKED_CAST")
                                    val invoked = method.invoke(embeddingStore, queryEmbedding, 50)
                                    results = invoked as? List<EmbeddingMatch<TextSegment>>
                                    if (results != null) {
                                        logger.debug("Successfully invoked search method: ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                lastError = e
                                logger.debug("Failed to invoke search method with 2 params: ${e.message}")
                            }
                        }
                    }
                    
                    if (results == null) {
                        logger.error("Could not invoke any search method. Last error: ${lastError?.message}. Available search methods: ${searchMethods.map { "${it.name}(${it.parameterTypes.joinToString { it.simpleName }})" }}")
                        lastError?.printStackTrace()
                        emptyList()
                    } else {
                        results
                    }
                }
            } catch (e: ClassNotFoundException) {
                logger.error("ClassNotFoundException while searching methods (missing optional dependency): ${e.message}. Trying alternative approach.", e)
                emptyList()
            } catch (e: NoClassDefFoundError) {
                logger.error("NoClassDefFoundError while searching methods (missing optional dependency): ${e.message}. Trying alternative approach.", e)
                emptyList()
            } catch (e: Exception) {
                logger.error("Error calling search method: ${e.message}", e)
                e.printStackTrace()
                emptyList()
            }
            
            if (searchResults.isNotEmpty()) {
                logger.info("Search returned ${searchResults.size} results for query: '${query.take(50)}...' (memoryId: $memoryId, namespace: ${memoryId.ifEmpty { "startup" }})")
            }

            // Filter results by token limit while maintaining relevance order
            var tokenCount = 0
            val limitedResults = mutableListOf<EmbeddingMatch<TextSegment>>()

            for (result in searchResults) {
                // Calculate the token count for the current segment
                val segmentText = result.embedded().text()
                val segmentTokens = tokenizer.estimateTokenCount(segmentText)
                
                // Stop if adding this segment would exceed the token limit
                if (tokenCount + segmentTokens <= maxTokens) {
                    tokenCount += segmentTokens
                    limitedResults.add(result)
                    logger.debug("Added message with $segmentTokens tokens (total: $tokenCount)")
                } else {
                    logger.debug("Token limit reached at ${limitedResults.size} messages (${tokenCount} tokens)")
                    break
                }
            }

            logger.info("Retrieved ${limitedResults.size} relevant messages from Pinecone for ID: $memoryId " +
                    "(${tokenCount} tokens, query: '${query.take(50)}...', namespace: ${memoryId.ifEmpty { "startup" }})")
            
            if (limitedResults.isEmpty() && searchResults.isNotEmpty()) {
                logger.warn("No messages passed token limit filter. Search returned ${searchResults.size} results but all exceeded token limit.")
            } else if (limitedResults.isEmpty()) {
                logger.warn("No messages found in Pinecone for memoryId: $memoryId. Check if messages were stored correctly.")
            }
            
            return limitedResults
        } catch (e: Exception) {
            logger.error("Failed to retrieve chat memory from Pinecone for ID: $memoryId (namespace: ${memoryId.ifEmpty { "startup" }}): ${e.message}", e)
            e.printStackTrace()
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
        
        return when {
            messageType.uppercase().contains("USER") -> UserMessage(messageContent)
            messageType.uppercase().contains("AI") || messageType.uppercase().contains("ASSISTANT") -> AiMessage(messageContent)
            messageType.uppercase().contains("SYSTEM") -> SystemMessage(messageContent)
            else -> {
                logger.warn("Unknown message type: $messageType (content preview: ${messageContent.take(50)}...)")
                null
            }
        }
    }
}
