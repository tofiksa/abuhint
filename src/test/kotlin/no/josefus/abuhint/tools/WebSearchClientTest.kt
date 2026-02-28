package no.josefus.abuhint.tools

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebSearchClientTest {

    private lateinit var mockServer: MockRestServiceServer
    private lateinit var client: WebSearchClient

    @BeforeEach
    fun setup() {
        val restTemplate = RestTemplateBuilder()
            .build()
        val props = WebSearchProperties(
            apiKey = "test-key",
            baseUrl = "http://localhost:8089",
            timeoutMs = 2000,
            maxResults = 4,
            locale = "nb-NO",
            searchDepth = "basic"
        )
        client = WebSearchClient(restTemplate, props)
        mockServer = MockRestServiceServer.createServer(restTemplate)
    }

    @Test
    fun `search returns mapped results`() {
        val responseJson = """
            {
              "results": [
                {
                  "title": "Kotlin 2.0",
                  "url": "https://kotlinlang.org",
                  "content": "Kotlin 2.0 released with new features",
                  "published_date": "2024-12-05"
                }
              ],
              "answer": "Kotlin 2.0 summary"
            }
        """.trimIndent()

        mockServer.expect(requestTo("http://localhost:8089/search"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.query").value("kotlin nyheter"))
            .andExpect(jsonPath("$.max_results").value(4))
            .andExpect(jsonPath("$.api_key").value("test-key"))
            .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON))

        val response = client.search("kotlin nyheter", maxResults = 4, locale = "nb-NO")

        assertEquals(1, response.results.size)
        val result = response.results.first()
        assertEquals("Kotlin 2.0", result.title)
        assertEquals("https://kotlinlang.org", result.url)
        assertEquals("Kotlin 2.0 released with new features", result.snippet)
        assertEquals("tavily", response.provider)
        assertNull(response.error)

        mockServer.verify()
    }

    @Test
    fun `search handles api error`() {
        mockServer.expect(requestTo("http://localhost:8089/search"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        val response = client.search("kotlin nyheter")

        assertTrue(response.results.isEmpty())
        assertNotNull(response.error)

        mockServer.verify()
    }

    @Test
    fun `search short-circuits when api key missing`() {
        val props = WebSearchProperties(
            apiKey = "",
            baseUrl = "http://localhost:8089",
            timeoutMs = 2000,
            maxResults = 4,
            locale = "nb-NO",
            searchDepth = "basic"
        )
        val localClient = WebSearchClient(RestTemplateBuilder().build(), props)

        val response = localClient.search("whatever")

        assertTrue(response.results.isEmpty())
        assertEquals("Web search is not configured", response.error)
    }
}

