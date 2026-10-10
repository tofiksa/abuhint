package no.josefus.abuhint.secretary

import no.josefus.abuhint.agent.AgentRegistry
import no.josefus.abuhint.service.TokenUsageContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Service
class SecretaryDelegationService(
    private val taskRepository: SecretaryTaskRepository,
    private val executionRepository: TaskExecutionRepository,
    private val workerExecutionService: WorkerExecutionService,
    private val agentRegistry: AgentRegistry,
    private val consentPolicyService: ConsentPolicyService,
    private val eventPublisher: ApplicationEventPublisher,
    platformTransactionManager: PlatformTransactionManager,
    @Autowired(required = false) delegatedAgentRunners: List<DelegatedAgentRunner>?,
) {

    private val log = LoggerFactory.getLogger(SecretaryDelegationService::class.java)
    private val optionalRunners: List<DelegatedAgentRunner> = delegatedAgentRunners.orEmpty()
    private val transactionTemplate = TransactionTemplate(platformTransactionManager)

    private fun publishTaskEvent(type: String, entity: SecretaryTaskEntity) {
        eventPublisher.publishEvent(SecretaryTaskEvent.from(type, entity))
    }

    fun delegate(
        task: SecretaryTaskEntity,
        userId: String,
        baseContext: TokenUsageContext,
    ): SecretaryTaskEntity {
        val agentId = task.assignedAgentId
            ?: throw IllegalStateException("Task ${task.id} has no assignedAgentId")
        agentRegistry.require(agentId)

        // Centralized consent-policy gate
        if (!consentPolicyService.mayExecute(task)) {
            task.status = SecretaryTaskStatus.waiting_for_confirmation
            task.updatedAt = Instant.now()
            val saved = taskRepository.save(task)
            publishTaskEvent("task.needs_confirmation", saved)
            return saved
        }

        val brief = task.delegatedBrief?.trim()?.takeIf { it.isNotBlank() }
            ?: task.description?.trim()?.takeIf { it.isNotBlank() }
            ?: task.title

        val taskCtx = baseContext.copy(
            taskId = task.id.toString(),
            workerAgent = agentId.uppercase(),
            parentAgent = "SECRETARY",
        )

        val workerMemoryId = when (agentId) {
            AgentRegistry.IDs.CALENDAR -> SecretaryChatIds.familieWorkerMemoryId(task.id.toString())
            else -> SecretaryChatIds.workerMemoryId(task.id.toString())
        }

        // Create execution record
        val execution = TaskExecutionEntity(
            taskId = task.id,
            agentId = agentId,
            userId = userId,
            chatId = task.chatId,
            brief = brief,
            status = TaskExecutionStatus.running,
        )
        persistRunningStart(task, execution)
        publishTaskEvent("task.running", task)
        val startTime = System.nanoTime()

        return try {
            for (runner in optionalRunners) {
                val out = runner.tryRun(agentId, task.id.toString(), brief, taskCtx)
                if (out != null) {
                    return persistCompletion(task, execution, startTime, out, null)
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
            persistCompletion(task, execution, startTime, result, null)
        } catch (e: Exception) {
            log.error("Delegation failed for task {}: {}", task.id, e.message, e)
            persistCompletion(
                task,
                execution,
                startTime,
                null,
                e.message ?: "Unknown error",
            )
        }
    }

    private fun persistRunningStart(task: SecretaryTaskEntity, execution: TaskExecutionEntity) {
        transactionTemplate.executeWithoutResult {
            task.status = SecretaryTaskStatus.running
            task.updatedAt = Instant.now()
            taskRepository.save(task)
            executionRepository.save(execution)
        }
    }

    private fun persistCompletion(
        task: SecretaryTaskEntity,
        execution: TaskExecutionEntity,
        startTime: Long,
        result: String?,
        error: String?,
    ): SecretaryTaskEntity {
        val saved = transactionTemplate.execute {
            if (error != null) {
                task.status = SecretaryTaskStatus.failed
                task.errorMessage = error
                task.updatedAt = Instant.now()
            } else {
                finishSuccess(task, result!!)
            }
            finishExecution(execution, result, error, startTime)
            taskRepository.save(task)
        }!!
        publishTaskEvent(if (error != null) "task.failed" else "task.done", saved)
        return saved
    }

    private fun finishSuccess(task: SecretaryTaskEntity, summary: String) {
        task.status = SecretaryTaskStatus.done
        task.resultSummary = summary.trim()
        task.errorMessage = null
        task.updatedAt = Instant.now()
    }

    private fun finishExecution(execution: TaskExecutionEntity, result: String?, error: String?, startTime: Long) {
        val durationMs = (System.nanoTime() - startTime) / 1_000_000
        execution.completedAt = Instant.now()
        execution.durationMs = durationMs
        if (error != null) {
            execution.status = TaskExecutionStatus.failed
            execution.errorMessage = error
        } else {
            execution.status = TaskExecutionStatus.done
            execution.resultSummary = result?.take(4000)
        }
        executionRepository.save(execution)
    }
}
