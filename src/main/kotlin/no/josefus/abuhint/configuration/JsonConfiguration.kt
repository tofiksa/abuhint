package no.josefus.abuhint.configuration

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

@Configuration
class JsonConfig {

    @Bean("objectMapper")
    @Primary
    fun objectMapper(): ObjectMapper {

        return Jackson2ObjectMapperBuilder
            .json()
            .serializationInclusion(JsonInclude.Include.ALWAYS)
            .failOnEmptyBeans(false)
            .failOnUnknownProperties(false)
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .modulesToInstall(kotlinModule(), JavaTimeModule())
            .build()
    }
}
