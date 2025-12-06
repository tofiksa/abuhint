package no.josefus.abuhint.tools

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import kotlin.test.assertTrue

class WebSearchToolTest {

    private val client: WebSearchClient = mock(WebSearchClient::class.java)

    @Test
    fun `searchWeb returns formatted results when enabled`() {
        val response = WebSearchResponse(
            results = listOf(
                WebSearchResult(
                    title = "Nyhet",
                    url = "https://example.com",
                    snippet = "Oppdatert info",
                    publishedAt = "2025-12-06"
                )
            ),
            provider = "tavily",
            tookMs = 123,
            cacheHit = false,
            error = null
        )
        `when`(client.search("hei", 2, "nb-NO")).thenReturn(response)

        val tool = WebSearchTool(client, enabled = true)
        val output = tool.searchWeb("hei", maxResults = 2, locale = "nb-NO")

        assertTrue(output.contains("Fant 1 treff"))
        assertTrue(output.contains("https://example.com"))
        verify(client, times(1)).search("hei", 2, "nb-NO")
    }

    @Test
    fun `searchWeb short-circuits when disabled`() {
        val tool = WebSearchTool(client, enabled = false)
        val output = tool.searchWeb("hei", maxResults = 1, locale = "nb-NO")
        assertTrue(output.contains("Web-søk er skrudd av"))
    }
}

