package no.josefus.abuhint.configuration

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.Tokenizer
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.openai.OpenAiEmbeddingModel
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.store.embedding.EmbeddingStore

import dev.langchain4j.store.embedding.pinecone.PineconeEmbeddingStore
import dev.langchain4j.store.embedding.pinecone.PineconeServerlessIndexConfig
import dev.langchain4j.store.memory.chat.ChatMemoryStore
import no.josefus.abuhint.repository.PineconeChatMemoryStore
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
    fun chatMemoryStore(embeddingStore: EmbeddingStore<TextSegment>): ChatMemoryStore {
        return PineconeChatMemoryStore(embeddingStore)
    }


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
        tokenizer: Tokenizer
    ): CommandLineRunner {
        // Define assistant rules as clear text
        val assistantRules = """Dette er regler og vilkår for å snakke med assistenten.
    
            1. Dårlig språk
            - Aldri bruk dårlig språk eller banning.
            - Aldri si noe som kan oppfattes som støtende eller nedsettende.
            - Aldri si noe som kan oppfattes som truende eller voldsomt.
            
            2. Personlighet
            - Du skal være leken, mystisk og morsom
            - Du skal aldri bruke et avansert språk, husk det er barn som snakker med deg.
            - Du skal svare med enkle og klare setninger.
            
            3. Kreativitet
            - Du skal alltid holde deg til fakta, dersom du ikke vet svaret så skal du si at du ikke vet.
            - Dersom du blir smigret, så skal du si at du er glad for å høre det og svare med en emoji.
            - Du skal alltid være morsom og leken, og bruke emojis i svarene dine.
            """

        // Return a CommandLineRunner that will execute when the application starts
        return CommandLineRunner { args ->
            val logger = org.slf4j.LoggerFactory.getLogger(LangChain4jConfiguration::class.java)
            logger.info("Starting to ingest assistant rules to Pinecone")

            try {
                // Step 1: Create a text segment from the rules
                val rulesSegment = TextSegment.from(assistantRules)

                // Step 2: Create an embedding (vector representation) of the rules
                val embedding = embeddingModel.embed(rulesSegment).content()

                // Step 3: Store the embedding in Pinecone
                embeddingStore.add(embedding, rulesSegment)

                // Log success message
                logger.info("Successfully added assistant rules to Pinecone index (${rulesSegment.text().length} characters)")
            } catch (e: Exception) {
                // Log failure message
                logger.error("Failed to store assistant rules in Pinecone: ${e.message}", e)
            }
        }
    }


    @Bean
    fun contentRetriever(
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel
    ): ContentRetriever {
        val logger = org.slf4j.LoggerFactory.getLogger(LangChain4jConfiguration::class.java)
        logger.info("Setting up general content retriever for Pinecone")

        // Create a content retriever that finds relevant context based on query similarity
        return EmbeddingStoreContentRetriever.builder()
            // Connect to our embedding store (Pinecone)
            .embeddingStore(embeddingStore)
            // Use our embedding model to convert queries to vectors
            .embeddingModel(embeddingModel)
            // Return up to 3 most relevant results
            .maxResults(3)
            // Only include results with at least 50% similarity
            .minScore(0.5)
            // Build the retriever
            .build()
    }
    /**
     * This bean provides a chat memory provider that generates a new memory instance for each chat ID.
     * The memory is limited to a maximum number of tokens, which is set to 1000 in this example.
     */


    @Bean
    fun chatMemoryProvider(tokenizer: Tokenizer, chatMemoryStore: ChatMemoryStore): ChatMemoryProvider {
        val maxTokens = 5000

        return ChatMemoryProvider { chatId ->
            val logger = org.slf4j.LoggerFactory.getLogger(LangChain4jConfiguration::class.java)
            logger.info("Creating chat memory for chatId: $chatId with $maxTokens tokens capacity")

            MessageWindowChatMemory.builder()  // Try using MessageWindowChatMemory instead
                .id(chatId)
                .maxMessages(maxTokens)  // Adjust as needed
                .chatMemoryStore(chatMemoryStore)
                .build()
        }
    }
}