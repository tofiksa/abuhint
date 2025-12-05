package no.josefus.abuhint.repository

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.store.embedding.EmbeddingMatch
import dev.langchain4j.store.embedding.EmbeddingSearchRequest
import dev.langchain4j.store.embedding.EmbeddingSearchResult
import dev.langchain4j.store.embedding.EmbeddingStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConcretePineconeChatMemoryStoreTest {

    @Test
    fun `searchWithRequest uses EmbeddingSearchRequest signature`() {
        val store = RecordingEmbeddingStore()
        val queryEmbedding = Embedding.from(floatArrayOf(1f, 2f))

        val results = ConcretePineconeChatMemoryStore.searchWithRequest(
            embeddingStore = store,
            queryEmbedding = queryEmbedding,
            maxResults = 5,
            minScore = 0.42
        )

        assertEquals(1, results.size)
        assertEquals(queryEmbedding, store.lastRequest?.queryEmbedding())
        assertEquals(5, store.lastRequest?.maxResults())
        assertEquals(0.42, store.lastRequest?.minScore())
    }

    private class RecordingEmbeddingStore : EmbeddingStore<TextSegment> {
        var lastRequest: EmbeddingSearchRequest? = null

        override fun search(request: EmbeddingSearchRequest): EmbeddingSearchResult<TextSegment> {
            lastRequest = request
            val match = EmbeddingMatch(
                0.9,
                "test-id",
                request.queryEmbedding(),
                TextSegment.from("USER: hi")
            )
            return EmbeddingSearchResult(listOf(match))
        }

        override fun add(embedding: Embedding): String = "id"

        override fun add(id: String, embedding: Embedding) {
            // no-op for test double
        }

        override fun add(embedding: Embedding, embedded: TextSegment): String = "id"

        override fun addAll(embeddings: List<Embedding>): List<String> = emptyList()
    }
}

