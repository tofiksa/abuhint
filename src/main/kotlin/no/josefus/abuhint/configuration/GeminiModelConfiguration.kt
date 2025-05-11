package no.josefus.abuhint.configuration

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import dev.langchain4j.store.embedding.EmbeddingStore
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ConcurrentHashMap

@Configuration
class GeminiModelConfiguration {

    private val embeddingStoreCache = ConcurrentHashMap<String, EmbeddingStore<TextSegment>>()

    @Value("\${langchain4j.gemini.api-key}")
    @Qualifier lateinit var geminiaikey: String

    @Value("\${langchain4j.gemini.model-name}")
    @Qualifier lateinit var geminiModelName: String

    @Bean
    fun geminiChatModel(): ChatLanguageModel {
        if (geminiaikey.isEmpty()) {
            throw IllegalArgumentException("Gemini API key is not configured. Please set the 'gemini.api-key' property.")
        }

        return GoogleAiGeminiChatModel.builder() // Bruk korrekt builder
            .apiKey(geminiaikey)
            .modelName(geminiModelName)
            .logRequestsAndResponses(true)
            .build()
    }
}