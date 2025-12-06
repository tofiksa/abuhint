package no.josefus.abuhint.tools

import dev.langchain4j.agent.tool.Tool
import org.slf4j.LoggerFactory

class WebSearchTool(
    private val client: WebSearchClient,
    private val enabled: Boolean = false
) {

    private val log = LoggerFactory.getLogger(WebSearchTool::class.java)

    @Tool("Utfør et kort websøk for å hente oppdatert informasjon")
    fun searchWeb(query: String, maxResults: Int? = null, locale: String? = null): String {
        if (!enabled) {
            return "Web-søk er skrudd av (feature flag)."
        }
        if (query.isBlank()) {
            return "Ingen søketekst oppgitt."
        }
        val response = client.search(query, maxResults = maxResults, locale = locale)
        if (response.error != null) {
            return "Klarte ikke å søke på nettet: ${response.error}"
        }
        if (response.results.isEmpty()) {
            return "Fant ingen relevante treff for: \"$query\"."
        }
        val top = response.results.take(maxResults ?: client.properties.maxResults)
        val builder = StringBuilder()
        builder.append("Fant ${top.size} treff (via ${response.provider}, ${response.tookMs}ms):\n")
        top.forEachIndexed { idx, r ->
            builder.append("${idx + 1}. ${r.title} — ${r.url}\n")
            if (r.snippet.isNotBlank()) {
                builder.append("   ${r.snippet.take(280)}\n")
            }
            r.publishedAt?.let { builder.append("   Publisert: $it\n") }
        }
        return builder.toString().trimEnd()
    }
}

