package no.josefus.abuhint.tools

import dev.langchain4j.agent.tool.Tool
import org.kohsuke.github.GitHub
import org.kohsuke.github.GHRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GitHubService(
    private val gitHubClient: GitHub,
    @Value("\${github.repository-url}") private val repositoryUrl: String
) {

    @Tool(name = "createPullRequest")
    fun createPullRequest(
        title: String,
        description: String,
        sourceBranch: String,
        targetBranch: String = "main"
    ): String {
        try {
            // Extract repository name from the URL
            val repoName = repositoryUrl.split("/").takeLast(2).joinToString("/")

            // Get repository object
            val repository = gitHubClient.getRepository(repoName)

            // Create the pull request
            val pullRequest = repository.createPullRequest(
                title,
                sourceBranch,
                targetBranch,
                description
            )

            return "Successfully created pull request #${pullRequest.number}: ${pullRequest.htmlUrl}"
        } catch (e: Exception) {
            return "Failed to create pull request: ${e.message}"
        }
    }
}