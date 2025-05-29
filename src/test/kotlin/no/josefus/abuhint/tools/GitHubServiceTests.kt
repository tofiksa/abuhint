package no.josefus.abuhint.tools

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.kohsuke.github.*
import java.net.URL
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class GitHubServiceTest {

    @Mock
    private lateinit var mockGitHub: GitHub

    @Mock
    private lateinit var mockRepository: GHRepository

    @Mock
    private lateinit var mockPullRequest: GHPullRequest

    @Mock
    private lateinit var mockBranch: GHBranch

    @Mock
    private lateinit var mockRef: GHRef

    @Mock
    private lateinit var mockFileContent: GHContent

    @Mock
    private lateinit var mockContentBuilder: GHContentBuilder

    private lateinit var gitHubService: GitHubService
    private val testRepoUrl = "https://github.com/test-user/test-repo"
    private val testRepoName = "test-user/test-repo"

    @BeforeEach
    fun setup() {
        gitHubService = GitHubService(mockGitHub, testRepoUrl)
        
        // Common mock setup
        `when`(mockGitHub.getRepository(testRepoName)).thenReturn(mockRepository)
    }

    @Test
    fun `test createPullRequest success`() {
        // Setup
        val title = "Test PR"
        val description = "Test Description"
        val sourceBranch = "feature/test"
        val targetBranch = "develop"
        
        `when`(mockRepository.createPullRequest(
            eq(title), 
            eq(sourceBranch), 
            eq(targetBranch), 
            eq(description)
        )).thenReturn(mockPullRequest)
        
        `when`(mockPullRequest.number).thenReturn(123)
        `when`(mockPullRequest.htmlUrl).thenReturn(URL("https://github.com/test-user/test-repo/pull/123"))
        
        // Execute
        val result = gitHubService.createPullRequest(title, description, sourceBranch, targetBranch)
        
        // Verify
        verify(mockGitHub).getRepository(testRepoName)
        verify(mockRepository).createPullRequest(title, sourceBranch, targetBranch, description)
        assertTrue(result.contains("Successfully created pull request #123"))
        assertTrue(result.contains("https://github.com/test-user/test-repo/pull/123"))
    }

    @Test
    fun `test createPullRequest with default values`() {
        // Setup
        val title = "Test PR"
        
        `when`(mockRepository.createPullRequest(
            eq(title), 
            eq("feature/abuhint"), 
            eq("main"), 
            eq("dette er en pullrequest fra Abu-Hint")
        )).thenReturn(mockPullRequest)
        
        `when`(mockPullRequest.number).thenReturn(123)
        `when`(mockPullRequest.htmlUrl).thenReturn(URL("https://github.com/test-user/test-repo/pull/123"))
        
        // Execute
        val result = gitHubService.createPullRequest(title)
        
        // Verify
        verify(mockGitHub).getRepository(testRepoName)
        verify(mockRepository).createPullRequest(title, "feature/abuhint", "main", "dette er en pullrequest fra Abu-Hint")
        assertTrue(result.contains("Successfully created pull request #123"))
    }

    @Test
    fun `test createPullRequest failure`() {
        // Setup
        val title = "Test PR"
        val errorMsg = "API rate limit exceeded"
        
        `when`(mockRepository.createPullRequest(
            any(), any(), any(), any()
        )).thenThrow(GHIOException(errorMsg))
        
        // Execute
        val result = gitHubService.createPullRequest(title)
        
        // Verify
        assertTrue(result.contains("Failed to create pull request"))
        assertTrue(result.contains(errorMsg))
    }

    @Test
    fun `test createBranchAndCommit success`() {
        // Setup
        val branchName = "feature/test"
        val commitMessage = "Test commit"
        val fileContent = "# Updated README"
        
        `when`(mockRepository.getBranch("main")).thenReturn(mockBranch)
        `when`(mockBranch.shA1).thenReturn("abc123")
        `when`(mockRepository.createRef(eq("refs/heads/$branchName"), eq("abc123"))).thenReturn(mockRef)
        `when`(mockRepository.getFileContent("README.md", branchName)).thenReturn(mockFileContent)
        `when`(mockFileContent.sha).thenReturn("def456")
        
        `when`(mockRepository.createContent()).thenReturn(mockContentBuilder)
        `when`(mockContentBuilder.path(any())).thenReturn(mockContentBuilder)
        `when`(mockContentBuilder.content(any<String>())).thenReturn(mockContentBuilder)
        `when`(mockContentBuilder.sha(any())).thenReturn(mockContentBuilder)
        `when`(mockContentBuilder.message(any())).thenReturn(mockContentBuilder)
        `when`(mockContentBuilder.branch(any())).thenReturn(mockContentBuilder)
        
        // Execute
        val result = gitHubService.createBranchAndCommit(branchName, commitMessage, fileContent)
        
        // Verify
        verify(mockRepository).getBranch("main")
        verify(mockRepository).createRef("refs/heads/$branchName", "abc123")
        verify(mockRepository).getFileContent("README.md", branchName)
        verify(mockContentBuilder).path("README.md")
        verify(mockContentBuilder).content(fileContent)
        verify(mockContentBuilder).sha("def456")
        verify(mockContentBuilder).message(commitMessage)
        verify(mockContentBuilder).branch(branchName)
        verify(mockContentBuilder).commit()
        
        assertTrue(result.contains("Successfully created branch"))
        assertTrue(result.contains(branchName))
        assertTrue(result.contains(commitMessage))
    }

    @Test
    fun `test createBranchAndCommit failure`() {
        // Setup
        val branchName = "feature/test"
        val commitMessage = "Test commit"
        val fileContent = "# Updated README"
        val errorMsg = "Branch already exists"
        
        `when`(mockRepository.getBranch("main")).thenThrow(GHFileNotFoundException(errorMsg))
        
        // Execute
        val result = gitHubService.createBranchAndCommit(branchName, commitMessage, fileContent)
        
        // Verify
        assertTrue(result.contains("Failed to create branch and commit"))
        assertTrue(result.contains(errorMsg))
    }

    @Test
    fun `test getBranch success`() {
        // Setup
        val branchName = "feature/test"
        val content = "# Test README"
        
        `when`(mockRepository.getFileContent("README.md", branchName)).thenReturn(mockFileContent)
        `when`(mockFileContent.content).thenReturn(content)
        
        // Execute
        val result = gitHubService.getBranch(branchName)
        
        // Verify
        verify(mockRepository).getFileContent("README.md", branchName)
        assertTrue(result.contains("Successfully retrieved content"))
        assertTrue(result.contains(content))
    }

    @Test
    fun `test getBranch failure`() {
        // Setup
        val branchName = "feature/test"
        val errorMsg = "Branch not found"
        
        `when`(mockRepository.getFileContent("README.md", branchName)).thenThrow(GHFileNotFoundException(errorMsg))
        
        // Execute
        val result = gitHubService.getBranch(branchName)
        
        // Verify
        assertTrue(result.contains("Failed to retrieve branch content"))
        assertTrue(result.contains(errorMsg))
    }

    @Test
    fun `test pushToMain success`() {
        // Setup
        val branchName = "feature/test"
        
        `when`(mockRepository.createPullRequest(
            eq("Merging changes from $branchName to main"),
            eq(branchName),
            eq("main"),
            eq("This is a pull request to merge changes from $branchName to main.")
        )).thenReturn(mockPullRequest)
        
        `when`(mockPullRequest.htmlUrl).thenReturn(URL("https://github.com/test-user/test-repo/pull/123"))
        
        // Execute
        val result = gitHubService.pushToMain(branchName)
        
        // Verify
        verify(mockRepository).createPullRequest(
            "Merging changes from $branchName to main",
            branchName,
            "main",
            "This is a pull request to merge changes from $branchName to main."
        )
        assertTrue(result.contains("Successfully pushed branch"))
        assertTrue(result.contains("https://github.com/test-user/test-repo/pull/123"))
    }

    @Test
    fun `test pushToMain failure`() {
        // Setup
        val branchName = "feature/test"
        val errorMsg = "Branch cannot be merged to main"
        
        `when`(mockRepository.createPullRequest(
            any(), any(), any(), any()
        )).thenThrow(GHIOException(errorMsg))
        
        // Execute
        val result = gitHubService.pushToMain(branchName)
        
        // Verify
        assertTrue(result.contains("Failed to push branch to main"))
        assertTrue(result.contains(errorMsg))
    }
}