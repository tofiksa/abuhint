package no.josefus.abuhint.secretary

import no.josefus.abuhint.agent.AgentRegistry
import no.josefus.abuhint.service.TokenUsageContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SecretaryTaskService(
    private val taskRepository: SecretaryTaskRepository,
    private val delegationService: SecretaryDelegationService,
    private val agentRegistry: AgentRegistry,
    private val eventPublisher: ApplicationEventPublisher,
) {

    private fun publishTaskEvent(type: String, entity: SecretaryTaskEntity) {
        eventPublisher.publishEvent(SecretaryTaskEvent.from(type, entity))
    }

    @Transactional(readOnly = true)
    fun listTasks(clientChatId: String): List<SecretaryTaskEntity> =
        taskRepository.findAllByChatIdOrderBySortOrderAsc(clientChatId)

    @Transactional(readOnly = true)
    fun listTasks(clientChatId: String, userId: String): List<SecretaryTaskEntity> =
        taskRepository.findAllByChatIdAndUserIdOrderBySortOrderAsc(clientChatId, userId)

    @Transactional(readOnly = true)
    fun getTask(taskId: UUID, userId: String): SecretaryTaskEntity? =
        taskRepository.findByIdAndUserId(taskId, userId)

    @Transactional
    fun createTask(
        userId: String,
        clientChatId: String,
        title: String,
        description: String?,
        assignedAgentId: String?,
        requiresConfirmation: Boolean,
        acceptanceCriteria: String?,
    ): SecretaryTaskEntity {
        val nextOrder = taskRepository.findAllByChatIdOrderBySortOrderAsc(clientChatId).maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        val entity = SecretaryTaskEntity(
            userId = userId,
            chatId = clientChatId,
            title = title.trim(),
            description = description?.trim(),
            status = SecretaryTaskStatus.proposed,
            assignedAgentId = assignedAgentId?.trim()?.takeIf { it.isNotBlank() },
            delegatedBrief = null,
            resultSummary = null,
            errorMessage = null,
            requiresConfirmation = requiresConfirmation,
            acceptanceCriteria = acceptanceCriteria?.trim(),
            sortOrder = nextOrder,
            listVersion = 0,
            artifactsJson = null,
        )
        if (assignedAgentId != null) {
            agentRegistry.require(assignedAgentId)
        }
        val saved = taskRepository.save(entity)
        publishTaskEvent("task.created", saved)
        return saved
    }

    @Transactional
    fun updateTask(
        taskId: UUID,
        userId: String,
        status: SecretaryTaskStatus?,
        title: String?,
        description: String?,
        assignedAgentId: String?,
        delegatedBrief: String?,
        requiresConfirmation: Boolean?,
        acceptanceCriteria: String?,
    ): SecretaryTaskEntity {
        val task = taskRepository.findByIdAndUserId(taskId, userId)
            ?: throw IllegalArgumentException("Task not found or access denied")
        status?.let { task.status = it }
        title?.let { task.title = it.trim() }
        description?.let { task.description = it.trim().takeIf { s -> s.isNotBlank() } }
        assignedAgentId?.let {
            val id = it.trim()
            agentRegistry.require(id)
            task.assignedAgentId = id
        }
        delegatedBrief?.let { task.delegatedBrief = it.trim().takeIf { s -> s.isNotBlank() } }
        requiresConfirmation?.let { task.requiresConfirmation = it }
        acceptanceCriteria?.let { task.acceptanceCriteria = it.trim().takeIf { s -> s.isNotBlank() } }
        task.updatedAt = Instant.now()
        val saved = taskRepository.save(task)
        publishTaskEvent("task.updated", saved)
        return saved
    }

    @Transactional
    fun markDone(taskId: UUID, userId: String): SecretaryTaskEntity {
        val task = taskRepository.findByIdAndUserId(taskId, userId)
            ?: throw IllegalArgumentException("Task not found")
        task.status = SecretaryTaskStatus.done
        task.updatedAt = Instant.now()
        val saved = taskRepository.save(task)
        publishTaskEvent("task.done", saved)
        return saved
    }

    fun delegateTask(taskId: UUID, userId: String, baseContext: TokenUsageContext): DelegationOutcome {
        val task = taskRepository.findByIdAndUserId(taskId, userId)
            ?: throw IllegalArgumentException("Task not found")
        if (task.status == SecretaryTaskStatus.running) {
            return DelegationOutcome(task, backgrounded = true)
        }
        require(!task.assignedAgentId.isNullOrBlank()) { "assign agent before delegate" }
        task.status = SecretaryTaskStatus.delegated
        task.updatedAt = Instant.now()
        taskRepository.save(task)
        return delegationService.delegate(task, userId, baseContext)
    }

    fun summarizeList(clientChatId: String): String {
        val tasks = taskRepository.findAllByChatIdOrderBySortOrderAsc(clientChatId)
        if (tasks.isEmpty()) return "Ingen oppgaver i listen."
        return tasks.joinToString("\n") { t ->
            "- [${t.status}] ${t.title} (id=${t.id}, agent=${t.assignedAgentId ?: "-"})"
        }
    }
}
