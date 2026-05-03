package no.josefus.abuhint.secretary

import no.josefus.abuhint.agent.AgentRegistry
import no.josefus.abuhint.service.TokenUsageContext
import no.josefus.abuhint.tools.WebSearchClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

/**
 * Embabel-style spike: a standalone research worker that bypasses the LangChain4j assistant
 * and calls the WebSearchClient tool directly. This demonstrates the [DelegatedAgentRunner]
 * pattern with real tool execution — proving that workers can be swapped to alternative
 * runtimes (Embabel @Agent/@Action, Spring AI, or direct tool calls) without changing
 * the controller/API layer.
 *
 * Enable via `abuhint.delegation.embabel-research-spike.enabled=true`.
 *
 * Architecture intent: This mirrors what an Embabel @Agent with a @Action("research")
 * would do — receive a brief, plan tool calls, execute them, and return a result.
 * Once Embabel matures past SNAPSHOT, replace the body with:
 *   embabelRuntime.run(ResearchAgent::class, brief)
 */
@Configuration
class EmbabelResearchSpikeConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(
        name = ["abuhint.delegation.embabel-research-spike.enabled"],
        havingValue = "true",
    )
    fun embabelResearchSpikeRunner(webSearchClient: WebSearchClient): DelegatedAgentRunner =
        EmbabelResearchSpikeRunner(webSearchClient)
}

/**
 * Real research worker spike — executes web search and returns formatted results.
 * This is the functional equivalent of an Embabel @Agent with @Action that uses
 * a web search tool to fulfill research briefs.
 */
class EmbabelResearchSpikeRunner(
    private val webSearchClient: WebSearchClient,
) : DelegatedAgentRunner {

    private val log = LoggerFactory.getLogger(EmbabelResearchSpikeRunner::class.java)

    override fun tryRun(agentId: String, taskId: String, brief: String, context: TokenUsageContext): String? {
        if (!agentId.equals(AgentRegistry.IDs.RESEARCH, ignoreCase = true)) return null

        log.info("[EmbabelSpike] Research worker executing for task={} brief=\"{}\"", taskId, brief.take(100))

        // Extract search query from brief (use first 200 chars as query)
        val query = brief.trim().take(200)
        val searchResponse = webSearchClient.search(query, maxResults = 5)

        if (searchResponse.error != null) {
            log.warn("[EmbabelSpike] Search failed: {}", searchResponse.error)
            return "[EmbabelSpike Research] Søk feilet: ${searchResponse.error}. Brief: ${brief.take(200)}"
        }

        if (searchResponse.results.isEmpty()) {
            return "[EmbabelSpike Research] Ingen resultater funnet for: ${query.take(100)}"
        }

        val result = buildString {
            append("Research-resultat (${searchResponse.results.size} treff via ${searchResponse.provider}):\n\n")
            searchResponse.results.forEachIndexed { idx, r ->
                append("${idx + 1}. **${r.title}**\n")
                append("   ${r.url}\n")
                if (r.snippet.isNotBlank()) {
                    append("   ${r.snippet.take(300)}\n")
                }
                append("\n")
            }
            append("---\nOpprinnelig brief: ${brief.take(300)}")
        }

        log.info("[EmbabelSpike] Research completed for task={}, results={}", taskId, searchResponse.results.size)
        return result
    }
}
