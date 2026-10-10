package no.josefus.abuhint.secretary

import no.josefus.abuhint.agent.AgentRegistry
import no.josefus.abuhint.service.TokenUsageContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException

data class DelegationOutcome(
    val task: SecretaryTaskEntity,
    val backgrounded: Boolean = false,
)

@Service
class SecretaryDelegationService(
    private val taskRepository: SecretaryTaskRepository,
    private val executionRepository: TaskExecutionRepository,
    private val workerExecutionService: WorkerExecutionService,
    private val agentRegistry: AgentRegistry,
    private val consentPolicyService: ConsentPolicyService,
    private val eventPublisher: ApplicationEventPublisher,
    private val properties: SecretaryDelegationProperties,
    @param:Qualifier("secretaryWorkerExecutor") private val secretaryWorkerExecutor: Executor,
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
    ): DelegationOutcome {
        val agentId = task.assignedAgentId
            ?: throw IllegalStateException("Task ${task.id} has no assignedAgentId")
        agentRegistry.require(agentId)

        val prepared = prepare(task, userId, agentId)
        if (prepared.execution == null) {
            return DelegationOutcome(
                task = prepared.task,
                backgrounded = prepared.task.status == SecretaryTaskStatus.running,
            )
        }

        val future = launch(prepared, baseContext)
        return try {
            future.get(properties.syncWaitMs, MILLISECONDS)
            DelegationOutcome(reloadTask(task.id))
        } catch (_: TimeoutException) {
            DelegationOutcome(reloadTask(task.id), backgrounded = true)
        } catch (e: ExecutionException) {
            val fresh = reloadTask(task.id)
            if (fresh.status == SecretaryTaskStatus.running) {
                throw e
            }
            DelegationOutcome(fresh)
        }
    }

    private fun prepare(
        task: SecretaryTaskEntity,
        userId: String,
        agentId: String,
    ): PreparedDelegation = transactionTemplate.execute {
        // Centralized consent-policy gate
        if (!consentPolicyService.mayExecute(task)) {
            task.status = SecretaryTaskStatus.waiting_for_confirmation
            task.updatedAt = Instant.now()
            val saved = taskRepository.save(task)
            publishTaskEvent("task.needs_confirmation", saved)
            return@execute PreparedDelegation(saved, null, agentId, "", userId)
        }

        val now = Instant.now()
        if (taskRepository.markRunningIfNotRunning(task.id, now) == 0) {
            return@execute PreparedDelegation(reloadTask(task.id), null, agentId, "", userId)
        }

        val brief = task.delegatedBrief?.trim()?.takeIf { it.isNotBlank() }
            ?: task.description?.trim()?.takeIf { it.isNotBlank() }
            ?: task.title
        val execution = TaskExecutionEntity(
            taskId = task.id,
            agentId = agentId,
            userId = userId,
            chatId = task.chatId,
            brief = brief,
            status = TaskExecutionStatus.running,
        )
        executionRepository.save(execution)
        task.status = SecretaryTaskStatus.running
        task.updatedAt = now
        publishTaskEvent("task.running", task)
        PreparedDelegation(task, execution, agentId, brief, userId)
    }

    private fun launch(
        prepared: PreparedDelegation,
        baseContext: TokenUsageContext,
    ): CompletableFuture<String> {
        val execution = checkNotNull(prepared.execution)
        val future = CompletableFuture.supplyAsync(
            { runWorker(prepared, baseContext) },
            secretaryWorkerExecutor,
        ).orTimeout(properties.workerTimeoutMs, MILLISECONDS)

        return future.whenComplete { result, error ->
            complete(prepared.task.id, execution.id, result, error)
        }
    }

    private fun runWorker(prepared: PreparedDelegation, baseContext: TokenUsageContext): String {
        val task = prepared.task
        val agentId = prepared.agentId
        val brief = prepared.brief
        val taskCtx = baseContext.copy(
            taskId = task.id.toString(),
            workerAgent = agentId.uppercase(),
            parentAgent = "SECRETARY",
        )

        for (runner in optionalRunners) {
            val out = runner.tryRun(agentId, task.id.toString(), brief, taskCtx)
            if (out != null) {
                return out
            }
        }

        val workerMemoryId = when (agentId) {
            AgentRegistry.IDs.CALENDAR -> SecretaryChatIds.familieWorkerMemoryId(task.id.toString())
            else -> SecretaryChatIds.workerMemoryId(task.id.toString())
        }
        return when (agentId) {
            AgentRegistry.IDs.TECH ->
                workerExecutionService.runTechWorker(workerMemoryId, brief, taskCtx)
            AgentRegistry.IDs.CALENDAR ->
                workerExecutionService.runCalendarWorker(workerMemoryId, brief, prepared.userId, taskCtx)
            AgentRegistry.IDs.RESEARCH,
            AgentRegistry.IDs.DELIVERY,
            AgentRegistry.IDs.GITHUB,
            AgentRegistry.IDs.COACH,
            -> workerExecutionService.runOpenAiWorker(agentId, workerMemoryId, brief, taskCtx)
            else -> throw IllegalArgumentException("Unhandled agent: $agentId")
        }
    }

    private fun complete(
        taskId: UUID,
        executionId: UUID,
        result: String?,
        throwable: Throwable?,
    ) {
        try {
            val error = throwable?.rootCause()
            if (error != null) {
                log.error("Delegation failed for task {}", taskId, error)
            }
            val saved = transactionTemplate.execute {
                val task = reloadTask(taskId)
                if (task.status != SecretaryTaskStatus.running) {
                    return@execute null
                }
                val execution = executionRepository.findById(executionId)
                    .orElseThrow { IllegalStateException("Execution not found: $executionId") }
                val errorMessage = when (error) {
                    is TimeoutException -> "Worker brukte for lang tid"
                    null -> null
                    else -> error.message ?: "Unknown error"
                }
                if (errorMessage != null) {
                    task.status = SecretaryTaskStatus.failed
                    task.errorMessage = errorMessage
                } else {
                    finishSuccess(task, checkNotNull(result))
                }
                task.updatedAt = Instant.now()
                finishExecution(execution, result, errorMessage)
                taskRepository.save(task)
            }
            if (saved != null) {
                publishTaskEvent(if (error != null) "task.failed" else "task.done", saved)
            }
        } catch (e: Exception) {
            log.error("Could not complete delegation for task {}", taskId, e)
            throw e
        }
    }

    private fun finishSuccess(task: SecretaryTaskEntity, summary: String) {
        task.status = SecretaryTaskStatus.done
        task.resultSummary = summary.trim()
        task.errorMessage = null
        task.updatedAt = Instant.now()
    }

    private fun finishExecution(execution: TaskExecutionEntity, result: String?, error: String?) {
        val completedAt = Instant.now()
        execution.completedAt = completedAt
        execution.durationMs = completedAt.toEpochMilli() - execution.startedAt.toEpochMilli()
        if (error != null) {
            execution.status = TaskExecutionStatus.failed
            execution.errorMessage = error
        } else {
            execution.status = TaskExecutionStatus.done
            execution.resultSummary = result?.take(4000)
        }
        executionRepository.save(execution)
    }

    private fun reloadTask(taskId: UUID): SecretaryTaskEntity =
        taskRepository.findById(taskId)
            .orElseThrow { IllegalArgumentException("Task not found: $taskId") }

    private fun Throwable.rootCause(): Throwable {
        var current = this
        while ((current is ExecutionException || current is CompletionException) && current.cause != null) {
            current = current.cause!!
        }
        return current
    }

    private data class PreparedDelegation(
        val task: SecretaryTaskEntity,
        val execution: TaskExecutionEntity?,
        val agentId: String,
        val brief: String,
        val userId: String,
    )
}
