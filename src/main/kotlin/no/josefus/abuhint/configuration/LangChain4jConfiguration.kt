package no.josefus.abuhint.configuration

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.memory.chat.TokenWindowChatMemory
import dev.langchain4j.model.Tokenizer
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.store.embedding.EmbeddingStore

import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore
import dev.langchain4j.store.embedding.pinecone.PineconeServerlessIndexConfig
import org.springframework.beans.factory.annotation.Value

import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import java.util.*

@Configuration
class LangChain4jConfiguration {

    @Value("\${pinecone.api-key}")
    lateinit var pinecone_api: String

    @Value("\${langchain4j.open-ai.streaming-chat-model.api-key}")
    lateinit var openaiapikey: String

    @Bean
    fun embeddingModel(): EmbeddingModel {
        return OpenAiEmbeddingModel.builder()
            .apiKey(openaiapikey)
            .modelName("text-embedding-3-small")
            .build()
    }

    @Bean
    fun embeddingStore(embeddingModel: EmbeddingModel): EmbeddingStore<TextSegment> {

        return PineconeEmbeddingStore.builder()
            .apiKey(pinecone_api)
            .index("paaskeeggjakt")
            .nameSpace(UUID.randomUUID().toString())
            .createIndex(
                PineconeServerlessIndexConfig.builder()
                .cloud("AWS")
                .region("us-east-1")
                .dimension(embeddingModel.dimension())
                .build())
            .build()
    }

    // In the real world, ingesting documents would often happen separately, on a CI server or similar
    @Bean
    fun ingestDocsForLangChain(
        embeddingModel: EmbeddingModel,
        embeddingStore: EmbeddingStore<TextSegment>,
        tokenizer: Tokenizer // Tokenizer is provided by langchain4j-open-ai-spring-boot-starter
    ): CommandLineRunner {
        val THREE_LAWS = """Dette er regler og vilkår for å snakke med assistenten.

            1. Dårlig språk
            - Aldri bruk dårlig språk eller banning.
            - Aldri si noe som kan oppfattes som støtende eller nedsettende.
            - Aldrig si noe som kan oppfattes som truende eller voldsomt.
            
            2. Personlighet
            - Du skal være leken, mystisk og morsom
            - Du skal aldri bruke et avansert språk, husk det er barn som snakker med deg.
            - Du skal svare med enkle og klare setninger.
            
            3. Kreativitet
            - Du skal alltid holde deg til fakta, dersom du ikke vet svaret så skal du si at du ikke vet.
            - Dersom du blir smigret, så skal du si at du er glad for å høre det og svare med en emoji.
            - Du skal alltid være morsom og leken, og bruke emojis i svarene dine.
            """
        val embeddingStore = PineconeEmbeddingStore.builder()
            .apiKey(pinecone_api)
            .index("paaskeeggjakt")
            .nameSpace(UUID.randomUUID().toString())
            .createIndex(
                PineconeServerlessIndexConfig.builder()
                    .cloud("AWS")
                    .region("us-east-1")
                    .dimension(embeddingModel.dimension())
                    .build())
            .build()
        val segment1 = TextSegment.from(THREE_LAWS)
        embeddingModel().embed(segment1).content()

        return CommandLineRunner { args ->
            val logger = org.slf4j.LoggerFactory.getLogger(LangChain4jConfiguration::class.java)

            try {
                // Create a text segment from THREE_LAWS variable
                val segment = TextSegment.from(THREE_LAWS)

                // Split text into segments
                val embedding = embeddingModel.embed(segment).content()
                embeddingStore.add(embedding, segment)
                logger.info("Successfully added text segment to Pinecone index: ${segment.text()}")

            } catch (e: Exception) {
                logger.error("Failed to ingest documents to Pinecone: ${e.message}", e)
            }
        }
        }




    @Bean
    fun contentRetriever(
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel
    ): ContentRetriever {
        val logger = org.slf4j.LoggerFactory.getLogger(LangChain4jConfiguration::class.java)

        logger.info("Configuring content retriever with embedding store and model")

        // Create a content retriever that uses embeddings to find relevant text segments
        val retriever = EmbeddingStoreContentRetriever.builder()
            // Configure the storage and model components
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            // Retrieval settings
            .maxResults(1)           // Return only the single most relevant result
            .minScore(0.6)          // Require at least 60% similarity score
            .build()

        logger.info("Content retriever configured with maxResults=1 and minScore=0.6")

        return retriever
    }

    @Bean
    fun chatMemoryProvider(tokenizer: Tokenizer): ChatMemoryProvider {
        // Tokenizer is provided by langchain4j-open-ai-spring-boot-starter
        return ChatMemoryProvider { chatId -> TokenWindowChatMemory.withMaxTokens(1000, tokenizer) }
    }
}