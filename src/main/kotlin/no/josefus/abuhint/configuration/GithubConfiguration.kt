package no.josefus.abuhint.configuration

import org.slf4j.LoggerFactory
import org.kohsuke.github.GitHubBuilder
import org.kohsuke.github.GitHub
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GitHubConfig {
    private val logger = LoggerFactory.getLogger(GitHubConfig::class.java)
    
    @Bean
    fun gitHubClient(@Value("\${github.repo-token}") token: String): GitHub {
        val maskedToken = if (token.length > 8) {
            "${token.take(4)}${"*".repeat(token.length - 8)}${token.takeLast(4)}"
        } else {
            "****"
        }
        logger.info("Initializing GitHub client with token: $maskedToken")
        val gitHub = GitHubBuilder()
            .withOAuthToken(token)
            .also { logger.info("GitHubBuilder configured with OAuth token.") }
            .build()
        logger.info("GitHub client successfully built.")
        return gitHub
    }
}