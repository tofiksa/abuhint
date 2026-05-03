package no.josefus.abuhint.secretary

import no.josefus.abuhint.agent.AgentRegistry
import no.josefus.abuhint.service.TokenUsageContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class SecretaryTaskServiceTest {

    @Mock
    lateinit var taskRepository: SecretaryTaskRepository

    @Mock
    lateinit var delegationService: SecretaryDelegationService

    private val agentRegistry = AgentRegistry()

    private lateinit var service: SecretaryTaskService

    @BeforeEach
    fun init() {
        service = SecretaryTaskService(taskRepository, delegationService, agentRegistry)
    }

    @Test
    fun `createTask persists with proposed status`() {
        whenever(taskRepository.findAllByChatIdOrderBySortOrderAsc("chat-a")).thenReturn(emptyList())
        whenever(taskRepository.save(any())).thenAnswer { it.arguments[0] as SecretaryTaskEntity }

        val saved = service.createTask(
            userId = "u1",
            clientChatId = "chat-a",
            title = "Hello",
            description = "d",
            assignedAgentId = AgentRegistry.IDs.RESEARCH,
            requiresConfirmation = false,
            acceptanceCriteria = null,
        )

        verify(taskRepository).save(any())
        assertEquals(SecretaryTaskStatus.proposed, saved.status)
        assertEquals(AgentRegistry.IDs.RESEARCH, saved.assignedAgentId)
        assertEquals("chat-a", saved.chatId)
    }
}

@ExtendWith(MockitoExtension::class)
class SecretaryDelegationServiceTest {

    @Mock
    lateinit var taskRepository: SecretaryTaskRepository

    @Mock
    lateinit var workerExecutionService: WorkerExecutionService

    private val agentRegistry = AgentRegistry()

    private lateinit var delegationService: SecretaryDelegationService

    @BeforeEach
    fun init() {
        delegationService = SecretaryDelegationService(
            taskRepository,
            workerExecutionService,
            agentRegistry,
            delegatedAgentRunners = null,
        )
    }

    @Test
    fun `delegation calls research worker and marks done`() {
        val id = UUID.randomUUID()
        val task = SecretaryTaskEntity(
            id = id,
            userId = "u1",
            chatId = "c1",
            title = "R",
            description = null,
            status = SecretaryTaskStatus.ready,
            assignedAgentId = AgentRegistry.IDs.RESEARCH,
            delegatedBrief = "Finn tre kilder",
            resultSummary = null,
            errorMessage = null,
            requiresConfirmation = false,
            acceptanceCriteria = null,
            sortOrder = 0,
            listVersion = 0,
            artifactsJson = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        whenever(workerExecutionService.runOpenAiWorker(any(), any(), any(), any())).thenReturn("result text")
        whenever(taskRepository.save(any())).thenAnswer { it.arguments[0] as SecretaryTaskEntity }

        val base = TokenUsageContext(
            userId = "u1",
            chatId = "c1",
            assistant = "SECRETARY",
            clientPlatform = "test",
        )
        delegationService.delegate(task, "u1", base)

        verify(workerExecutionService).runOpenAiWorker(
            AgentRegistry.IDs.RESEARCH,
            SecretaryChatIds.workerMemoryId(id.toString()),
            "Finn tre kilder",
            base.copy(taskId = id.toString(), workerAgent = "RESEARCH", parentAgent = "SECRETARY"),
        )
        verify(taskRepository, times(2)).save(any())
        assertEquals(SecretaryTaskStatus.done, task.status)
        assertEquals("result text", task.resultSummary)
    }
}
