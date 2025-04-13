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
        description: String = "dette er en pullrequest fra Abu-Hint",
        sourceBranch: String = "feature/abuhint",
        targetBranch: String = "main"
    ): String {
        try {
            // Log the start of the pull request creation process
            println("INFO: Starting pull request creation process.")

            // Extract repository name from the URL
            println("INFO: Extracting repository name from URL: $repositoryUrl")
            val repoName = repositoryUrl.split("/").takeLast(2).joinToString("/")

            // Get repository object
            println("INFO: Fetching repository object for: $repoName")
            val repository = gitHubClient.getRepository(repoName)

            // Create a new pull request
            println("INFO: Preparing pull request details.")
            val pullRequestTitle = if (title.isNotBlank()) title else "Pull request from $sourceBranch to $targetBranch"
            val pullRequestDescription = if (description.isNotBlank()) description else "This is a pull request from $sourceBranch to $targetBranch"

            // Log the pull request details
            println("INFO: Pull request title: $pullRequestTitle")
            println("INFO: Pull request description: $pullRequestDescription")
            println("INFO: Source branch: $sourceBranch, Target branch: $targetBranch")

            // Create the pull request
            println("INFO: Creating pull request in repository.")
            val pullRequest = repository.createPullRequest(
                pullRequestTitle,
                sourceBranch,
                targetBranch,
                pullRequestDescription
            )

            // Log success
            println("INFO: Successfully created pull request #${pullRequest.number}: ${pullRequest.htmlUrl}")
            return "Successfully created pull request #${pullRequest.number}: ${pullRequest.htmlUrl}"
        } catch (e: Exception) {
            // Log failure
            println("INFO: Failed to create pull request: ${e.message}")
            return "Failed to create pull request: ${e.message}"
        }
    }
}