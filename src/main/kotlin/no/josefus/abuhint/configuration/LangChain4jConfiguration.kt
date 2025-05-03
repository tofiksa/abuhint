package no.josefus.abuhint.configuration

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.Tokenizer
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore
import dev.langchain4j.store.embedding.pinecone.PineconeServerlessIndexConfig
import no.josefus.abuhint.repository.ConcretePineconeChatMemoryStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ConcurrentHashMap

@Configuration
public class LangChain4jConfiguration {

    @Value("\${pinecone.api-key}")
    lateinit var pinecone_api: String

    @Value("\${langchain4j.open-ai.chat-model.api-key}")
    lateinit var openaiapikey: String
    private val embeddingStoreCache = ConcurrentHashMap<String, EmbeddingStore<TextSegment>>()


    @Bean
    fun embeddingModel(): EmbeddingModel {
        return OpenAiEmbeddingModel.builder()
            .apiKey(openaiapikey)
            .modelName("text-embedding-ada-002")
            .build()
    }


    fun embeddingStore(embeddingModel: EmbeddingModel, id: String): EmbeddingStore<TextSegment> {
        val effectiveNamespace = id.ifEmpty { "startup" }

        return embeddingStoreCache.computeIfAbsent(effectiveNamespace) {
            val indexConfig = PineconeServerlessIndexConfig.builder()
                .cloud("AWS")
                .region("us-east-1")
                .dimension(embeddingModel.dimension())
                .build()

            PineconeEmbeddingStore.builder()
                .apiKey(pinecone_api)
                .index("paaskeeggjakt")
                .nameSpace(effectiveNamespace)
                .createIndex(indexConfig)
                .build()
        }
    }


    @Bean
    fun chatMemoryProvider(
        tokenizer: Tokenizer,
        chatMemoryStore: ConcretePineconeChatMemoryStore
    ): ChatMemoryProvider {
        val maxMessages = 5

        return ChatMemoryProvider { chatId ->
            val logger = org.slf4j.LoggerFactory.getLogger(LangChain4jConfiguration::class.java)
            logger.info("Creating chat memory for chatId: $chatId with $maxMessages messages capacity")

            MessageWindowChatMemory.builder() // Try using MessageWindowChatMemory instead
                .id(chatId)
                .maxMessages(maxMessages) // Adjust as needed
                .chatMemoryStore(chatMemoryStore)
                .build()
        }
    }
}

