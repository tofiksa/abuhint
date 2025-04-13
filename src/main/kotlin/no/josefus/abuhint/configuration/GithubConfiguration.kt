package no.josefus.abuhint.configuration

import org.kohsuke.github.GitHubBuilder
import org.kohsuke.github.GitHub
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GitHubConfig {
    @Bean
    fun gitHubClient(@Value("\${github.token}") token: String): GitHub {
        return GitHubBuilder().withOAuthToken(token).build()
    }
}