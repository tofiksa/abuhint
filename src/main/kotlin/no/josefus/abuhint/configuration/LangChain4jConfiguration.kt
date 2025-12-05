package no.josefus.abuhint.configuration

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel
import no.josefus.abuhint.service.SimpleTokenizer
import no.josefus.abuhint.service.Tokenizer
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore
import no.josefus.abuhint.repository.ConcretePineconeChatMemoryStore
import no.josefus.abuhint.tools.EmailService
import no.josefus.abuhint.tools.PowerPointGeneratorTool
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
            .httpClientBuilder(dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilderFactory().create())
            .build()
    }

    fun embeddingStore(embeddingModel: EmbeddingModel, id: String): EmbeddingStore<TextSegment> {
        val effectiveNamespace = id.ifEmpty { "startup" }
        
        if (id.isEmpty()) {
            val logger = org.slf4j.LoggerFactory.getLogger(LangChain4jConfiguration::class.java)
            logger.warn("Empty chatId provided - using default namespace 'startup'. This may cause conversations to be mixed. Consider providing a unique chatId.")
        }

        return embeddingStoreCache.computeIfAbsent(effectiveNamespace) {
            // Note: createIndex() is removed to avoid missing dependency issue with org.openapitools.db_control.client
            // The index should already exist in Pinecone. If not, create it manually via Pinecone console or API.
            val logger = org.slf4j.LoggerFactory.getLogger(LangChain4jConfiguration::class.java)
            logger.info("Creating Pinecone embedding store for namespace: $effectiveNamespace")
            PineconeEmbeddingStore.builder()
                .apiKey(pinecone_api)
                .index("paaskeeggjakt")
                .nameSpace(effectiveNamespace)
                .build()
        }
    }

    @Bean
    fun chatMemoryProvider(
        chatMemoryStore: ConcretePineconeChatMemoryStore,
    ): ChatMemoryProvider {
        val maxMessages = 100

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

    @Bean
    fun emailService(
        @Value("\${resend.api-key}") apiKey: String,
        @Value("\${resend.from}") from: String,
        @Value("\${resend.subject}") subject: String = "Abuhint Notification"
    ): EmailService {
        return EmailService(apiKey, from, subject)
    }

    @Bean
    fun powerPointTool(): PowerPointGeneratorTool {
        return PowerPointGeneratorTool()
    }

    @Bean
    fun tokenizer(): Tokenizer {
        // In LangChain4j 1.9+, Tokenizer is not available as a separate class
        // Use a simple character-based approximation
        return SimpleTokenizer()
    }
}
