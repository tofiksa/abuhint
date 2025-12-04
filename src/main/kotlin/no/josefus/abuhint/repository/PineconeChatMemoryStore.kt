package no.josefus.abuhint.repository

import dev.langchain4j.data.embedding.Embedding
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
        // Return from cache - Pinecone retrieval is handled separately via semantic search
        return memoryCache[id]?.toMutableList() ?: mutableListOf()
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
            if (currentCount > previousCount) {
                val newMessages = messages.subList(previousCount, currentCount)
                storeMessagesToPinecone(id, newMessages, embeddingStore, embeddingModel, previousCount)
                lastStoredCount[id] = currentCount
                
                logger.info("Stored ${newMessages.size} new messages to Pinecone for ID: $id (total: $currentCount)")
            } else {
                // If count decreased or stayed same, update cache only
                logger.debug("No new messages to store for ID: $id (count: $currentCount)")
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
                val messageId = "${memoryId}_msg_${counter.getAndIncrement()}_${System.currentTimeMillis()}"
                
                // Format message text with type prefix for parsing later
                val messageText = "${message.type()}: ${message.text()}"
                val textSegment = TextSegment.from(messageText)
                
                // Generate embedding for this individual message
                val embeddingResponse = embeddingModel.embed(textSegment)
                val embedding = embeddingResponse.content()
                
                // Store in Pinecone - ID is auto-generated, messageId can be stored in metadata if needed
                embeddingStore.add(embedding, textSegment)
                
                logger.debug("Stored message $messageId to Pinecone: ${message.type()}")
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
}