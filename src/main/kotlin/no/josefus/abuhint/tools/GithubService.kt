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

    // create a tool for creating a new branch and commit code
    @Tool(name = "createBranchAndCommit")
    fun createBranchAndCommit(
        branchName: String,
        commitMessage: String,
        fileContent: String
    ): String {
        try {
            // Log the start of the branch and commit creation process
            println("INFO: Starting branch and commit creation process.")

            // Extract repository name from the URL
            println("INFO: Extracting repository name from URL: $repositoryUrl")
            val repoName = repositoryUrl.split("/").takeLast(2).joinToString("/")

            // Get repository object
            println("INFO: Fetching repository object for: $repoName")
            val repository = gitHubClient.getRepository(repoName)

            // Create a new branch
            println("INFO: Creating new branch: $branchName")
            val baseBranch = repository.getBranch("main")
            val newBranch = repository.createRef("refs/heads/$branchName", baseBranch.shA1)

            // Update the README.md file in the new branch
            println("INFO: Updating README.md file.")

            // Retrieve the current file's SHA
            val existingFile = repository.getFileContent("README.md", branchName)
            val fileSha = existingFile.sha

            // Commit the updated content
            val commit = repository.createContent()
                .path("README.md") // Always target the README.md file
                .content(fileContent)
                .sha(fileSha) // Provide the SHA of the existing file
                .message(commitMessage)
                .branch(branchName)
                .commit()

            // Log success
            println("INFO: Successfully created branch '$branchName' and committed change to README. Commit message: $commitMessage")
            return "Successfully created branch '$branchName' and committed change to README. Commit message: $commitMessage"
        } catch (e: Exception) {
            // Log failure
            println("INFO: Failed to create branch and commit: ${e.message}")
            return "Failed to create branch and commit: ${e.message}"
        }
    }

    @Tool(name = "getBranch")
    fun getBranch(branchName: String): String {
        try {
            // Log the start of the branch retrieval process
            println("INFO: Starting branch retrieval process.")

            // Extract repository name from the URL
            println("INFO: Extracting repository name from URL: $repositoryUrl")
            val repoName = repositoryUrl.split("/").takeLast(2).joinToString("/")

            // Get repository object
            println("INFO: Fetching repository object for: $repoName")
            val repository = gitHubClient.getRepository(repoName)

            // Retrieve the branch content
            println("INFO: Retrieving content from branch: $branchName")
            val branchContent = repository.getFileContent("README.md", branchName)

            // Log success
            println("INFO: Successfully retrieved content from branch '$branchName'. Content: ${branchContent.content}")
            return "Successfully retrieved content from branch '$branchName'. Content: ${branchContent.content}"
        } catch (e: Exception) {
            // Log failure
            println("INFO: Failed to retrieve branch content: ${e.message}")
            return "Failed to retrieve branch content: ${e.message}"
        }
    }

    // push to the main branch
    @Tool(name = "pushToMain")
    fun pushToMain(branchName: String): String {
        try {
            // Log the start of the push process
            println("INFO: Starting push to main branch process.")

            // Extract repository name from the URL
            println("INFO: Extracting repository name from URL: $repositoryUrl")
            val repoName = repositoryUrl.split("/").takeLast(2).joinToString("/")

            // Get repository object
            println("INFO: Fetching repository object for: $repoName")
            val repository = gitHubClient.getRepository(repoName)

            // Push the branch to main
            println("INFO: Pushing branch '$branchName' to main.")
            val pullRequest = repository.createPullRequest(
                "Merging changes from $branchName to main",
                branchName,
                "main",
                "This is a pull request to merge changes from $branchName to main."
            )

            // Log success
            println("INFO: Successfully pushed branch '$branchName' to main. Pull request created: ${pullRequest.htmlUrl}")
            return "Successfully pushed branch '$branchName' to main. Pull request created: ${pullRequest.htmlUrl}"
        } catch (e: Exception) {
            // Log failure
            println("INFO: Failed to push branch to main: ${e.message}")
            return "Failed to push branch to main: ${e.message}"
        }
    }
}