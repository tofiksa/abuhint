package no.josefus.abuhint.repository

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.store.memory.chat.ChatMemoryStore
import no.josefus.abuhint.configuration.LangChain4jConfiguration
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class PineconeChatMemoryStore(private val langChain4jConfiguration: LangChain4jConfiguration) : ChatMemoryStore {
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
            val embeddingStore = langChain4jConfiguration.embeddingStore(langChain4jConfiguration.embeddingModel(), id)
            memoryCache[id] = messages.toMutableList()

            // Create a text representation of the chat history
            val chatText = TextSegment.from(messages.joinToString("\n") { "${it.type()}: ${it.text()}" })
            // Create a TextSegment for the embedding
            val embeddingModel = langChain4jConfiguration.embeddingModel()
            // Generate the embedding
            val embeddingResponse = embeddingModel.embed(chatText)
            // create an Embedding object
            val embeddingValues = embeddingResponse.content().vectorAsList().toFloatArray()
            val embedding = Embedding(embeddingValues)

            embeddingStore.add(embedding, chatText)

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