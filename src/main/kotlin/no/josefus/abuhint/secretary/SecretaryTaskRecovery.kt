package no.josefus.abuhint.secretary

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class SecretaryTaskRecovery(
    private val taskRepository: SecretaryTaskRepository,
    private val executionRepository: TaskExecutionRepository,
) {

    private val log = LoggerFactory.getLogger(SecretaryTaskRecovery::class.java)

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun recoverInterruptedWork() {
        val now = Instant.now()
        val errorMessage = RECOVERY_ERROR_MESSAGE

        val staleTasks = taskRepository.findAllByStatusIn(STALE_TASK_STATUSES)
        staleTasks.forEach { task ->
            task.status = SecretaryTaskStatus.failed
            task.errorMessage = errorMessage
            task.updatedAt = now
        }
        if (staleTasks.isNotEmpty()) {
            taskRepository.saveAll(staleTasks)
        }

        val staleExecutions = executionRepository.findAllByStatus(TaskExecutionStatus.running)
        staleExecutions.forEach { execution ->
            execution.status = TaskExecutionStatus.failed
            execution.errorMessage = errorMessage
            execution.completedAt = now
            execution.durationMs = now.toEpochMilli() - execution.startedAt.toEpochMilli()
        }
        if (staleExecutions.isNotEmpty()) {
            executionRepository.saveAll(staleExecutions)
        }

        if (staleTasks.isNotEmpty() || staleExecutions.isNotEmpty()) {
            log.info(
                "Marked {} secretary task(s) and {} task execution(s) as failed after service restart",
                staleTasks.size,
                staleExecutions.size,
            )
        }
    }

    companion object {
        const val RECOVERY_ERROR_MESSAGE = "Avbrutt pga. omstart av tjenesten"

        private val STALE_TASK_STATUSES = listOf(
            SecretaryTaskStatus.running,
            SecretaryTaskStatus.delegated,
        )
    }
}
