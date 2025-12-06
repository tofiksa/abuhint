package no.josefus.abuhint.configuration

import no.josefus.abuhint.tools.WebSearchClient
import no.josefus.abuhint.tools.WebSearchProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WebSearchConfiguration {

    @Bean
    fun webSearchProperties(
        @Value("\${web-search.provider:tavily}") provider: String,
        @Value("\${web-search.api-key:}") apiKey: String,
        @Value("\${web-search.base-url:https://api.tavily.com}") baseUrl: String,
        @Value("\${web-search.timeout-ms:5000}") timeoutMs: Long,
        @Value("\${web-search.max-results:6}") maxResults: Int,
        @Value("\${web-search.locale:nb-NO}") locale: String,
        @Value("\${web-search.search-depth:basic}") searchDepth: String
    ): WebSearchProperties {
        return WebSearchProperties(
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            timeoutMs = timeoutMs,
            maxResults = maxResults,
            locale = locale,
            searchDepth = searchDepth
        )
    }

    @Bean
    fun webSearchClient(
        restTemplateBuilder: RestTemplateBuilder,
        webSearchProperties: WebSearchProperties
    ): WebSearchClient {
        return WebSearchClient(restTemplateBuilder, webSearchProperties)
    }
}

