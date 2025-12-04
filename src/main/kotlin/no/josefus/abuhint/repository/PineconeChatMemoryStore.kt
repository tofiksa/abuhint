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

@Component
abstract class PineconeChatMemoryStore(val langChain4jConfiguration: LangChain4jConfiguration) : ChatMemoryStore {
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
            val chatText = TextSegment.from(messages.joinToString("\n") { message ->
                val text = when (message) {
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
                "${message.type()}: $text"
            })
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

        logger.info("Deleted chat memory for ID: $id")
    }


}