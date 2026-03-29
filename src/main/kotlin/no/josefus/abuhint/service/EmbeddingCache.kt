package no.josefus.abuhint.service

import com.github.benmanes.caffeine.cache.Caffeine
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.model.embedding.EmbeddingModel
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class EmbeddingCache {
    private val cache = Caffeine.newBuilder()
        .maximumSize(5000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build<String, Embedding>()

    fun getOrCompute(text: String, embeddingModel: EmbeddingModel): Embedding {
        return cache.get(text) {
            embeddingModel.embed(it).content()
        }
    }
}
