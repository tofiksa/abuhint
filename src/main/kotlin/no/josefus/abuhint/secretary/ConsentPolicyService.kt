package no.josefus.abuhint.secretary

import no.josefus.abuhint.agent.AgentRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Centralized consent-policy gate. Workers that mutate external systems
 * must pass through this check before execution.
 *
 * If a task requires confirmation and has not been explicitly confirmed,
 * delegation is blocked and the task is set to [SecretaryTaskStatus.waiting_for_confirmation].
 */
@Service
class ConsentPolicyService(
    private val agentRegistry: AgentRegistry,
) {

    private val log = LoggerFactory.getLogger(ConsentPolicyService::class.java)

    /**
     * Returns true if the task may proceed to execution.
     * Returns false if user confirmation is required but not yet given.
     */
    fun mayExecute(task: SecretaryTaskEntity): Boolean {
        val agentId = task.assignedAgentId ?: return true
        val descriptor = agentRegistry.get(agentId) ?: return true

        if (!descriptor.mutatesExternally) return true
        if (!task.requiresConfirmation) return true

        // If the task has already passed through waiting_for_confirmation and been moved to ready/delegated,
        // it means the user confirmed. Otherwise, block.
        if (task.status == SecretaryTaskStatus.waiting_for_confirmation) {
            log.info("Task {} blocked pending user confirmation (agent={}, risk={})", task.id, agentId, descriptor.riskLevel)
            return false
        }

        return true
    }

    /**
     * Determines whether a newly delegated task should require confirmation
     * based on the agent's risk level and mutating nature.
     */
    fun shouldRequireConfirmation(task: SecretaryTaskEntity): Boolean {
        val agentId = task.assignedAgentId ?: return false
        val descriptor = agentRegistry.get(agentId) ?: return false
        return descriptor.mutatesExternally && task.requiresConfirmation
    }
}
