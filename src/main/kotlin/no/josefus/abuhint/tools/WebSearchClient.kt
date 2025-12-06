package no.josefus.abuhint.tools

import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestTemplate
import java.time.Duration

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val publishedAt: String? = null
)

data class WebSearchResponse(
    val results: List<WebSearchResult>,
    val provider: String,
    val tookMs: Long,
    val cacheHit: Boolean = false,
    val error: String? = null
)

@Component
class WebSearchClient(
    private val restTemplate: RestTemplate,
    private val properties: WebSearchProperties
) {
    companion object {
        private val log = LoggerFactory.getLogger(WebSearchClient::class.java)
    }

    constructor(
        restTemplateBuilder: RestTemplateBuilder,
        properties: WebSearchProperties
    ) : this(
        restTemplateBuilder
            .setConnectTimeout(Duration.ofMillis(properties.timeoutMs))
            .setReadTimeout(Duration.ofMillis(properties.timeoutMs))
            .build(),
        properties
    )

    fun search(query: String, maxResults: Int? = null, locale: String? = null): WebSearchResponse {
        if (properties.apiKey.isBlank()) {
            return WebSearchResponse(
                results = emptyList(),
                provider = properties.provider,
                tookMs = 0,
                cacheHit = false,
                error = "Web search API key is not configured"
            )
        }

        val effectiveMaxResults = maxResults ?: properties.maxResults
        val requestBody = TavilyRequest(
            api_key = properties.apiKey,
            query = query,
            max_results = effectiveMaxResults,
            search_depth = properties.searchDepth,
            language = locale ?: properties.locale,
            include_answer = false,
            include_images = false
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(requestBody, headers)

        val start = System.nanoTime()
        return try {
            val tavilyResponse = restTemplate.postForObject(
                properties.searchUrl,
                entity,
                TavilyResponse::class.java
            )
            val tookMs = (System.nanoTime() - start) / 1_000_000
            val results = tavilyResponse?.results?.map {
                WebSearchResult(
                    title = it.title.orEmpty(),
                    url = it.url.orEmpty(),
                    snippet = it.content.orEmpty(),
                    publishedAt = it.published_date
                )
            } ?: emptyList()

            WebSearchResponse(
                results = results,
                provider = properties.provider,
                tookMs = tookMs,
                cacheHit = tavilyResponse?.cacheHit ?: false,
                error = null
            )
        } catch (ex: ResourceAccessException) {
            val tookMs = (System.nanoTime() - start) / 1_000_000
            log.warn("Web search request failed or timed out", ex)
            WebSearchResponse(
                results = emptyList(),
                provider = properties.provider,
                tookMs = tookMs,
                cacheHit = false,
                error = "Søket tok for lang tid eller feilet på nettverksnivå"
            )
        } catch (ex: RestClientResponseException) {
            val tookMs = (System.nanoTime() - start) / 1_000_000
            log.warn("Web search API returned error status {}", ex.rawStatusCode, ex)
            val message = if (ex.rawStatusCode == 429) {
                "Søke-API er rate-limited nå, prøv igjen"
            } else {
                "Søke-API svarte med en feil (${ex.rawStatusCode})"
            }
            WebSearchResponse(
                results = emptyList(),
                provider = properties.provider,
                tookMs = tookMs,
                cacheHit = false,
                error = message
            )
        } catch (ex: Exception) {
            val tookMs = (System.nanoTime() - start) / 1_000_000
            log.warn("Unexpected web search failure", ex)
            WebSearchResponse(
                results = emptyList(),
                provider = properties.provider,
                tookMs = tookMs,
                cacheHit = false,
                error = "Uventet feil ved søk"
            )
        }
    }
}

private data class TavilyRequest(
    val api_key: String,
    val query: String,
    val max_results: Int,
    val search_depth: String,
    val include_answer: Boolean = false,
    val include_images: Boolean = false,
    val include_domains: List<String>? = null,
    val exclude_domains: List<String>? = null,
    val language: String? = null
)

private data class TavilyResult(
    val title: String? = null,
    val url: String? = null,
    val content: String? = null,
    val published_date: String? = null
)

private data class TavilyResponse(
    val results: List<TavilyResult> = emptyList(),
    val answer: String? = null,
    val query: String? = null,
    val cacheHit: Boolean = false
)

