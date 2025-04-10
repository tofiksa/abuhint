package no.josefus.abuhint.configuration

import dev.langchain4j.data.document.parser.TextDocumentParser
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.memory.chat.TokenWindowChatMemory
import dev.langchain4j.model.Tokenizer
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument
import dev.langchain4j.data.document.splitter.DocumentSplitters.recursive

@Configuration
class LangChain4jConfiguration {

    @Bean
    fun embeddingModel(): EmbeddingModel {
        return AllMiniLmL6V2EmbeddingModel()
    }

    @Bean
    fun embeddingStore(): EmbeddingStore<TextSegment> {
        return InMemoryEmbeddingStore()
    }

    // In the real world, ingesting documents would often happen separately, on a CI server or similar
    @Bean
    fun ingestDocsForLangChain(
        embeddingModel: EmbeddingModel,
        embeddingStore: EmbeddingStore<TextSegment>,
        tokenizer: Tokenizer, // Tokenizer is provided by langchain4j-open-ai-spring-boot-starter
        resourceLoader: ResourceLoader
    ): CommandLineRunner {
        return CommandLineRunner { args ->
            val resource = resourceLoader.getResource("classpath:the-three-laws.txt")
            val termsOfUse = loadDocument(resource.file.toPath(), TextDocumentParser())
            val ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(recursive(50, 0, tokenizer))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
            ingestor.ingest(termsOfUse)
        }
    }

    @Bean
    fun contentRetriever(
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel
    ): ContentRetriever {
        return EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(2)
            .minScore(0.6)
            .build()
    }

    @Bean
    fun chatMemoryProvider(tokenizer: Tokenizer): ChatMemoryProvider {
        // Tokenizer is provided by langchain4j-open-ai-spring-boot-starter
        return ChatMemoryProvider { chatId -> TokenWindowChatMemory.withMaxTokens(1000, tokenizer) }
    }
}