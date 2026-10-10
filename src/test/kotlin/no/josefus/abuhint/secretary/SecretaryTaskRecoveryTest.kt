package no.josefus.abuhint.secretary

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class SecretaryTaskRecoveryTest {

    @Mock
    lateinit var taskRepository: SecretaryTaskRepository

    @Mock
    lateinit var executionRepository: TaskExecutionRepository

    @Test
    fun `application ready recovery marks running task and execution failed`() {
        val task = SecretaryTaskEntity(
            userId = "u1",
            chatId = "c1",
            title = "Interrupted task",
            description = null,
            status = SecretaryTaskStatus.running,
            assignedAgentId = "research",
            delegatedBrief = "Research",
            resultSummary = null,
            errorMessage = null,
            acceptanceCriteria = null,
            artifactsJson = null,
        )
        val execution = TaskExecutionEntity(
            taskId = task.id,
            agentId = "research",
            userId = task.userId,
            chatId = task.chatId,
            brief = "Research",
            startedAt = Instant.now().minusSeconds(1),
        )
        whenever(taskRepository.findAllByStatusIn(listOf(SecretaryTaskStatus.running, SecretaryTaskStatus.delegated)))
            .thenReturn(listOf(task))
        whenever(executionRepository.findAllByStatus(TaskExecutionStatus.running))
            .thenReturn(listOf(execution))

        SecretaryTaskRecovery(taskRepository, executionRepository).recoverInterruptedWork()

        val savedTasks = argumentCaptor<Iterable<SecretaryTaskEntity>>()
        verify(taskRepository).saveAll(savedTasks.capture())
        val recoveredTask = savedTasks.firstValue.single()
        assertEquals(SecretaryTaskStatus.failed, recoveredTask.status)
        assertEquals(SecretaryTaskRecovery.RECOVERY_ERROR_MESSAGE, recoveredTask.errorMessage)

        val savedExecutions = argumentCaptor<Iterable<TaskExecutionEntity>>()
        verify(executionRepository).saveAll(savedExecutions.capture())
        val recoveredExecution = savedExecutions.firstValue.single()
        assertEquals(TaskExecutionStatus.failed, recoveredExecution.status)
        assertEquals(SecretaryTaskRecovery.RECOVERY_ERROR_MESSAGE, recoveredExecution.errorMessage)
        assertNotNull(recoveredExecution.completedAt)
        assertNotNull(recoveredExecution.durationMs)
    }
}
