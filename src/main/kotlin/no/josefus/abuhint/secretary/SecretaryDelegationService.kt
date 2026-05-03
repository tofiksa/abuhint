package no.josefus.abuhint.secretary

import no.josefus.abuhint.agent.AgentRegistry
import no.josefus.abuhint.service.TokenUsageContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class SecretaryDelegationService(
    private val taskRepository: SecretaryTaskRepository,
    private val workerExecutionService: WorkerExecutionService,
    private val agentRegistry: AgentRegistry,
    @Autowired(required = false) delegatedAgentRunners: List<DelegatedAgentRunner>?,
) {

    private val log = LoggerFactory.getLogger(SecretaryDelegationService::class.java)
    private val optionalRunners: List<DelegatedAgentRunner> = delegatedAgentRunners.orEmpty()

    @Transactional
    fun delegate(
        task: SecretaryTaskEntity,
        userId: String,
        baseContext: TokenUsageContext,
    ): SecretaryTaskEntity {
        val agentId = task.assignedAgentId
            ?: throw IllegalStateException("Task ${task.id} has no assignedAgentId")
        agentRegistry.require(agentId)
        val brief = task.delegatedBrief?.trim()?.takeIf { it.isNotBlank() }
            ?: task.description?.trim()?.takeIf { it.isNotBlank() }
            ?: task.title

        task.status = SecretaryTaskStatus.running
        task.updatedAt = Instant.now()
        taskRepository.save(task)

        val taskCtx = baseContext.copy(
            taskId = task.id.toString(),
            workerAgent = agentId.uppercase(),
            parentAgent = "SECRETARY",
        )

        val workerMemoryId = when (agentId) {
            AgentRegistry.IDs.CALENDAR -> SecretaryChatIds.familieWorkerMemoryId(task.id.toString())
            else -> SecretaryChatIds.workerMemoryId(task.id.toString())
        }

        return try {
            for (runner in optionalRunners) {
                val out = runner.tryRun(agentId, task.id.toString(), brief, taskCtx)
                if (out != null) {
                    finishSuccess(task, out)
                    return taskRepository.save(task)
                }
            }

            val result = when (agentId) {
                AgentRegistry.IDs.TECH ->
                    workerExecutionService.runTechWorker(workerMemoryId, brief, taskCtx)
                AgentRegistry.IDs.CALENDAR ->
                    workerExecutionService.runCalendarWorker(workerMemoryId, brief, userId, taskCtx)
                AgentRegistry.IDs.RESEARCH,
                AgentRegistry.IDs.DELIVERY,
                AgentRegistry.IDs.GITHUB,
                AgentRegistry.IDs.COACH,
                -> workerExecutionService.runOpenAiWorker(agentId, workerMemoryId, brief, taskCtx)
                else -> throw IllegalArgumentException("Unhandled agent: $agentId")
            }
            finishSuccess(task, result)
            taskRepository.save(task)
        } catch (e: Exception) {
            log.error("Delegation failed for task {}: {}", task.id, e.message, e)
            task.status = SecretaryTaskStatus.failed
            task.errorMessage = e.message ?: "Unknown error"
            task.updatedAt = Instant.now()
            taskRepository.save(task)
        }
    }

    private fun finishSuccess(task: SecretaryTaskEntity, summary: String) {
        task.status = SecretaryTaskStatus.done
        task.resultSummary = summary.trim()
        task.errorMessage = null
        task.updatedAt = Instant.now()
    }
}
