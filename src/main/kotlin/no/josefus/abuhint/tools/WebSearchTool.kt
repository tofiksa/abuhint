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
            log.info("webSearchTool disabled (feature flag) for query=\"{}\"", query.take(200))
            return "Web-søk er skrudd av (feature flag)."
        }
        log.info("søker på nettet: query=\"{}\" maxResults={} locale={}", query.take(200), maxResults, locale)
        if (query.isBlank()) {
            return "Ingen søketekst oppgitt."
        }
        val response = client.search(query, maxResults = maxResults, locale = locale)
        if (response.error != null) {
            return when {
                response.error.contains("not configured", ignoreCase = true) ->
                    "Web-søk ikke konfigurert."
                response.error.contains("rate", ignoreCase = true) ->
                    "Søk er midlertidig begrenset, forsøker senere."
                else ->
                    "Klarte ikke hente resultater nå (${response.error}); svarer uten eksterne data."
            }
        }
        if (response.results.isEmpty()) {
            return "Fant ingen oppdaterte kilder; sier ifra og svarer uten eksterne data."
        }
        val top = response.results.take(maxResults ?: client.properties.maxResults)
        log.info(
            "webSearchTool invoked provider={} query=\"{}\" maxResults={} tookMs={}",
            response.provider,
            query.take(200),
            top.size,
            response.tookMs
        )
        val builder = StringBuilder()
        builder.append("Fant ${top.size} treff (via ${response.provider}, ${response.tookMs}ms):\n")
        top.forEachIndexed { idx, r ->
            builder.append("${idx + 1}. ${r.title} — ${r.url}\n")
            if (r.snippet.isNotBlank()) {
                builder.append("   ${r.snippet.take(280)}\n")
            }
            r.publishedAt?.let { builder.append("   Publisert: $it\n") }
        }
        log.info("webSearchTool results: \n{}", builder.toString())
        return builder.toString().trimEnd()
    }
}
