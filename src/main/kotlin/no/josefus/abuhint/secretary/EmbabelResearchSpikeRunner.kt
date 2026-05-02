package no.josefus.abuhint.secretary

import no.josefus.abuhint.agent.AgentRegistry
import no.josefus.abuhint.service.TokenUsageContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

/**
 * Spike hook for swapping the LangChain4j research worker with an Embabel `@Agent` / `@Action`
 * implementation behind [DelegatedAgentRunner]. Enable via `abuhint.delegation.embabel-research-spike.enabled=true`
 * to verify runner ordering without Embabel on the classpath; replace the return body with a real Embabel invocation when integrated.
 */
@Configuration
class EmbabelResearchSpikeConfiguration {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(
        name = ["abuhint.delegation.embabel-research-spike.enabled"],
        havingValue = "true",
    )
    fun embabelResearchSpikeRunner(): DelegatedAgentRunner = EmbabelResearchSpikeRunner()
}

class EmbabelResearchSpikeRunner : DelegatedAgentRunner {
    override fun tryRun(agentId: String, taskId: String, brief: String, context: TokenUsageContext): String? {
        if (!agentId.equals(AgentRegistry.IDs.RESEARCH, ignoreCase = true)) return null
        return buildString {
            append("[Embabel spike stub] Research worker for task $taskId. ")
            append("Brief preview: ")
            append(brief.trim().take(120))
            if (brief.length > 120) append("…")
        }
    }
}
