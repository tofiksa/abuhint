package no.josefus.abuhint.configuration

import dev.langchain4j.http.client.HttpClientBuilder
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilderFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class HttpClientConfiguration {

    @Bean
    @Primary
    fun httpClientBuilder(): HttpClientBuilder {
        // Use Spring RestClient as the default HTTP client
        return SpringRestClientBuilderFactory().create()
    }
}

