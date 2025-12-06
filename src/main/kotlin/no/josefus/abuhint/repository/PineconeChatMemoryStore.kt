package no.josefus.abuhint.repository

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.store.memory.chat.ChatMemoryStore
import no.josefus.abuhint.configuration.LangChain4jConfiguration
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
abstract class PineconeChatMemoryStore(val langChain4jConfiguration: LangChain4jConfiguration) : ChatMemoryStore {
    private val logger = LoggerFactory.getLogger(PineconeChatMemoryStore::class.java)

    // Cache for better performance and to reduce API calls
    private val memoryCache = ConcurrentHashMap<String, MutableList<ChatMessage>>()
    
    // Track the last stored message count per memory ID to avoid re-storing messages
    private val lastStoredCount = ConcurrentHashMap<String, Int>()
    
    // Message counter for unique IDs
    private val messageCounters = ConcurrentHashMap<String, AtomicLong>()

    override fun getMessages(memoryId: Any): MutableList<ChatMessage> {
        val id = memoryId.toString()
        
        // Return from cache if available
        val cachedMessages = memoryCache[id]
        if (cachedMessages != null && cachedMessages.isNotEmpty()) {
            return cachedMessages.toMutableList()
        }
        
        // If cache is empty, try to load recent messages from Pinecone
        // This ensures long-term memory is available even after restart
        // Note: This is best-effort - if it fails, cache will be populated as new messages arrive
        try {
            logger.debug("Cache is empty for ID: $id, attempting to load from Pinecone")
            val recentMessages = loadRecentMessagesFromPinecone(id)
            if (recentMessages.isNotEmpty()) {
                memoryCache[id] = recentMessages.toMutableList()
                logger.info("Loaded ${recentMessages.size} recent messages from Pinecone for ID: $id")
                return recentMessages.toMutableList()
            } else {
                logger.debug("No messages found in Pinecone for ID: $id (this is normal for new conversations)")
            }
        } catch (e: Exception) {
            // This is not critical - cache will be populated as new messages arrive
            logger.debug("Could not load recent messages from Pinecone for ID: $id: ${e.message}. " +
                    "This is normal and cache will be populated as new messages arrive.")
        }
        
        return mutableListOf()
    }
    
    /**
     * Loads the most recent messages from Pinecone for a given memory ID.
     * This is used to populate cache when it's empty (e.g., after restart).
     * 
     * Note: Pinecone doesn't support querying all messages in a namespace directly.
     * We use a generic query that should match conversation content to retrieve messages.
     */
    protected fun loadRecentMessagesFromPinecone(memoryId: String): List<ChatMessage> {
        try {
            val embeddingStore = langChain4jConfiguration.embeddingStore(
                langChain4jConfiguration.embeddingModel(),
                memoryId
            )
            
            val embeddingModel = langChain4jConfiguration.embeddingModel()
            
            // Use a generic query that should match conversation content
            // Since we can't query by namespace directly, we use a query that semantically
            // matches typical conversation patterns
            val queryText = "conversation chat message user assistant"
            val queryEmbedding = embeddingModel.embed(queryText).content()
            
            logger.debug("Loading recent messages from Pinecone for memoryId: $memoryId (namespace: ${memoryId.ifEmpty { "startup" }})")
            
            val searchResults: List<dev.langchain4j.store.embedding.EmbeddingMatch<TextSegment>> =
                ConcretePineconeChatMemoryStore.searchWithRequest(
                    embeddingStore = embeddingStore,
                    queryEmbedding = queryEmbedding,
                    maxResults = 100,
                    minScore = 0.0,
                    logger = logger
                )
            
            // Parse results to messages
            val messages = searchResults.mapNotNull { match ->
                val parsed = parseSegmentToChatMessage(match.embedded())
                if (parsed == null) {
                    logger.debug("Failed to parse message segment: ${match.embedded().text().take(100)}")
                }
                parsed
            }
            
            // Don't update lastStoredCount here - we don't know the actual count
            // It will be updated when updateMessages is called with the actual message list
            // This prevents issues where we load fewer messages than actually exist
            
            logger.info("Loaded ${messages.size} messages from Pinecone for ID: $memoryId (from ${searchResults.size} search results)")
            if (messages.isEmpty() && searchResults.isNotEmpty()) {
                logger.warn("Found ${searchResults.size} search results but parsed 0 messages. This might indicate a format mismatch.")
                // Log first few results for debugging
                searchResults.take(3).forEach { match ->
                    logger.debug("Sample search result: ${match.embedded().text().take(200)}")
                }
            }
            return messages
        } catch (e: Exception) {
            logger.error("Failed to load recent messages from Pinecone: ${e.message}", e)
            return emptyList()
        }
    }
    
    /**
     * Normalizes message type from LangChain4j format to our storage format.
     * Examples: "USER_MESSAGE" -> "USER", "AI_MESSAGE" -> "AI", "SYSTEM_MESSAGE" -> "SYSTEM"
     */
    private fun normalizeMessageType(type: String): String {
        return when {
            type.uppercase().contains("USER") -> "USER"
            type.uppercase().contains("AI") || type.uppercase().contains("ASSISTANT") -> "AI"
            type.uppercase().contains("SYSTEM") -> "SYSTEM"
            else -> type.uppercase()
        }
    }
    
    /**
     * Helper method to parse TextSegment to ChatMessage.
     */
    private fun parseSegmentToChatMessage(segment: TextSegment): ChatMessage? {
        val text = segment.text().trim()
        if (text.isBlank()) return null
        
        val colonIndex = text.indexOf(": ")
        if (colonIndex == -1) return null
        
        val messageType = text.substring(0, colonIndex).trim()
        val messageContent = text.substring(colonIndex + 2).trim()
        
        if (messageContent.isBlank()) return null
        
        return when (messageType.uppercase()) {
            "USER" -> UserMessage(messageContent)
            "AI" -> AiMessage(messageContent)
            "SYSTEM" -> SystemMessage(messageContent)
            else -> null
        }
    }

    override fun updateMessages(memoryId: Any, messages: List<ChatMessage>) {
        try {
            val id = memoryId.toString()
            val embeddingStore = langChain4jConfiguration.embeddingStore(
                langChain4jConfiguration.embeddingModel(), 
                id
            )
            val embeddingModel = langChain4jConfiguration.embeddingModel()
            
            // Get previous message count
            val previousCount = lastStoredCount[id] ?: 0
            val currentCount = messages.size
            
            // Update cache
            memoryCache[id] = messages.toMutableList()
            
            // Only store NEW messages to Pinecone (incremental storage)
            // Check if we have new messages compared to what's stored
            if (currentCount > previousCount) {
                val newMessages = messages.subList(previousCount, currentCount)
                storeMessagesToPinecone(id, newMessages, embeddingStore, embeddingModel, previousCount)
                lastStoredCount[id] = currentCount
                
                logger.info("Stored ${newMessages.size} new messages to Pinecone for ID: $id (total: $currentCount)")
            } else if (currentCount == previousCount && previousCount == 0 && messages.isNotEmpty()) {
                // Special case: if we loaded messages from Pinecone but lastStoredCount wasn't set,
                // we need to check if these messages are already in Pinecone
                // For now, we'll assume they are and just update the count
                lastStoredCount[id] = currentCount
                logger.debug("Updated lastStoredCount for ID: $id to $currentCount (messages were loaded from Pinecone)")
            } else {
                // If count decreased or stayed same, update cache only
                logger.debug("No new messages to store for ID: $id (count: $currentCount, previous: $previousCount)")
            }
        } catch (e: Exception) {
            logger.error("Failed to update chat memory in Pinecone: ${e.message}", e)
        }
    }
    
    /**
     * Stores individual messages to Pinecone for efficient semantic search.
     * Each message is stored separately with a unique ID.
     */
    protected fun storeMessagesToPinecone(
        memoryId: String,
        messages: List<ChatMessage>,
        embeddingStore: dev.langchain4j.store.embedding.EmbeddingStore<TextSegment>,
        embeddingModel: dev.langchain4j.model.embedding.EmbeddingModel,
        startIndex: Int
    ) {
        val counter = messageCounters.computeIfAbsent(memoryId) { AtomicLong(0) }
        
        messages.forEachIndexed { index, message ->
            try {
                // Create unique ID for this message
                val order = counter.getAndIncrement()
                val timestamp = System.currentTimeMillis()
                val messageId = "${memoryId}_msg_${order}_${timestamp}"
                
                // Get message text using reflection (same approach as ChatService)
                val messageText = getMessageText(message)
                
                // Format message text with type prefix for parsing later
                // Normalize message type: "USER_MESSAGE" -> "USER", "AI_MESSAGE" -> "AI", etc.
                val messageType = normalizeMessageType(message.type().toString())
                val formattedMessageText = "$messageType: $messageText"
                val metadata = Metadata.from(
                    mapOf(
                        "ts" to timestamp.toString(),
                        "order" to order.toString()
                    )
                )
                val textSegment = TextSegment.from(formattedMessageText, metadata)
                
                // Generate embedding for this individual message
                val embeddingResponse = embeddingModel.embed(textSegment)
                val embedding = embeddingResponse.content()
                
                // Store in Pinecone - ID is auto-generated, messageId can be stored in metadata if needed
                embeddingStore.add(embedding, textSegment)
                
                logger.debug("Stored message $messageId to Pinecone: $messageType (namespace: $memoryId)")
            } catch (e: Exception) {
                logger.error("Failed to store individual message to Pinecone: ${e.message}", e)
            }
        }
    }

    override fun deleteMessages(memoryId: Any) {
        val id = memoryId.toString()
        memoryCache.remove(id)
        lastStoredCount.remove(id)
        messageCounters.remove(id)
        
        logger.info("Deleted chat memory cache for ID: $id (Pinecone data remains)")
    }
    
    /**
     * Helper method to extract text from ChatMessage using reflection.
     * Same approach as used in ChatService.
     */
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
}