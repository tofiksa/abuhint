package no.josefus.abuhint.secretary

import no.josefus.abuhint.service.TokenUsageContext
import no.josefus.abuhint.tools.WebSearchClient
import no.josefus.abuhint.tools.WebSearchResponse
import no.josefus.abuhint.tools.WebSearchResult
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EmbabelResearchSpikeRunnerTest {

    private val ctx = TokenUsageContext(
        userId = "u1",
        chatId = "chat-a",
        assistant = "SECRETARY",
        clientPlatform = "test",
    )

    private val mockClient = mock<WebSearchClient>()

    @Test
    fun `tryRun returns null for non-research agents`() {
        val out = EmbabelResearchSpikeRunner(mockClient).tryRun("delivery", "task-1", "brief", ctx)
        assertNull(out)
    }

    @Test
    fun `tryRun returns search results for research agent`() {
        whenever(mockClient.search(any(), anyOrNull(), anyOrNull())).thenReturn(
            WebSearchResponse(
                results = listOf(
                    WebSearchResult(
                        title = "Kotlin 2 released",
                        url = "https://example.com",
                        snippet = "Kotlin 2 is out",
                        publishedAt = null,
                    ),
                ),
                provider = "test",
                tookMs = 42,
                error = null,
            ),
        )
        val out = EmbabelResearchSpikeRunner(mockClient).tryRun("research", "task-99", "find Kotlin 2 news", ctx)
        assertNotNull(out)
        assertTrue(out!!.contains("Kotlin 2 released"))
        assertTrue(out.contains("find Kotlin 2 news"))
    }
}
