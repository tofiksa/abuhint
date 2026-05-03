package no.josefus.abuhint.secretary

import no.josefus.abuhint.service.TokenUsageContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Extension point for Embabel (or other runtimes). Return non-null to skip built-in LangChain4j workers.
 */
fun interface DelegatedAgentRunner {
    fun tryRun(agentId: String, taskId: String, brief: String, context: TokenUsageContext): String?
}

/**
 * Placeholder bean — returns null so [SecretaryDelegationService] always uses [WorkerExecutionService].
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class EmbabelDelegatedAgentStub : DelegatedAgentRunner {
    override fun tryRun(agentId: String, taskId: String, brief: String, context: TokenUsageContext): String? = null
}
