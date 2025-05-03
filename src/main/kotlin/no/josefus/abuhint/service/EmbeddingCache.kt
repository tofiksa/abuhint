package no.josefus.abuhint.service

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.model.embedding.EmbeddingModel
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class EmbeddingCache {
    private val cache = ConcurrentHashMap<String, Embedding>()

    fun getOrCompute(text: String, embeddingModel: EmbeddingModel): Embedding {
        return cache.computeIfAbsent(text) {
            embeddingModel.embed(it).content()
        }
    }
}