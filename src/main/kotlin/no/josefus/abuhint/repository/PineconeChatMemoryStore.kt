package no.josefus.abuhint.repository

import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.memory.chat.ChatMemoryStore
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class PineconeChatMemoryStore(private val embeddingStore: EmbeddingStore<*>) : ChatMemoryStore {
    private val logger = LoggerFactory.getLogger(PineconeChatMemoryStore::class.java)

    // Cache for better performance and to reduce API calls
    private val memoryCache = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    override fun getMessages(memoryId: Any): MutableList<ChatMessage> {
        val id = memoryId.toString()
        return memoryCache[id]?.toMutableList() ?: mutableListOf()
    }

    override fun updateMessages(memoryId: Any, messages: List<ChatMessage>) {
        try {
            val id = memoryId.toString()
            // Update the cache
            memoryCache[id] = messages.toMutableList()

            // Create a text representation of the chat history
            val chatText = messages.joinToString("\n") { "${it.type()}: ${it.text()}" }

            // Store in Pinecone with the chatId as part of the metadata
            // This would require some modifications to your embedding storage approach
            // For example, you could use metadata to store the chatId

            logger.info("Updated chat memory for ID: $id with ${messages.size} messages")
        } catch (e: Exception) {
            logger.error("Failed to update chat memory in Pinecone: ${e.message}", e)
        }
    }

    override fun deleteMessages(memoryId: Any) {
        val id = memoryId.toString()
        memoryCache.remove(id)
        // You would need to implement deletion from Pinecone as well
        logger.info("Deleted chat memory for ID: $id")
    }
}