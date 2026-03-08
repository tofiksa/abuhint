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
        assertTrue(output.contains("[kilde:example](https://example.com)"))
        verify(client, times(1)).search("hei", 2, "nb-NO")
    }

    @Test
    fun `searchWeb short-circuits when disabled`() {
        val tool = WebSearchTool(client, enabled = false)
        val output = tool.searchWeb("hei", maxResults = 1, locale = "nb-NO")
        assertTrue(output.contains("Web-søk er skrudd av"))
    }

    @Test
    fun `searchWeb formats source link with correct site name`() {
        fun makeResponse(url: String) = WebSearchResponse(
            results = listOf(WebSearchResult(title = "T", url = url, snippet = "s")),
            provider = "tavily", tookMs = 1, cacheHit = false, error = null
        )
        val tool = WebSearchTool(client, enabled = true)

        `when`(client.search("q", 1, null)).thenReturn(makeResponse("https://www.bbc.co.uk/news/article"))
        val bbcOutput = tool.searchWeb("q", maxResults = 1)
        assertTrue(
            bbcOutput.contains("[kilde:bbc.co](https://www.bbc.co.uk/news/article)") ||
            bbcOutput.contains("[kilde:bbc](https://www.bbc.co.uk/news/article)"),
            "Expected kilde:bbc in output but got: $bbcOutput"
        )

        `when`(client.search("q", 1, null)).thenReturn(makeResponse("https://www.vg.no/artikkel"))
        val vgOutput = tool.searchWeb("q", maxResults = 1)
        assertTrue(vgOutput.contains("[kilde:vg](https://www.vg.no/artikkel)"),
            "Expected kilde:vg in output but got: $vgOutput")

        `when`(client.search("q", 1, null)).thenReturn(makeResponse("https://nrk.no/nyheter"))
        val nrkOutput = tool.searchWeb("q", maxResults = 1)
        assertTrue(nrkOutput.contains("[kilde:nrk](https://nrk.no/nyheter)"),
            "Expected kilde:nrk in output but got: $nrkOutput")
    }
}

