package no.josefus.abuhint.configuration

import okhttp3.internal.concurrent.TaskRunner.Companion.logger
import org.kohsuke.github.GitHubBuilder
import org.kohsuke.github.GitHub
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GitHubConfig {
    @Bean
    fun gitHubClient(@Value("\${github.repo-token}") token: String): GitHub {
        logger.info("Initializing GitHub client with provided token: $token")
        val gitHub = GitHubBuilder()
            .withOAuthToken(token)
            .also { logger.info("GitHubBuilder configured with OAuth token.") }
            .build()
        logger.info("GitHub client successfully built.")
        return gitHub
    }
}