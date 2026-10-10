package no.josefus.abuhint.secretary

import no.josefus.abuhint.agent.AgentRegistry
import no.josefus.abuhint.service.TokenUsageContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class SecretaryTaskServiceTest {

    @Mock
    lateinit var taskRepository: SecretaryTaskRepository

    @Mock
    lateinit var delegationService: SecretaryDelegationService

    @Mock
    lateinit var eventPublisher: ApplicationEventPublisher

    private val agentRegistry = AgentRegistry()

    private lateinit var service: SecretaryTaskService

    @BeforeEach
    fun init() {
        service = SecretaryTaskService(taskRepository, delegationService, agentRegistry, eventPublisher)
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
    lateinit var executionRepository: TaskExecutionRepository

    @Mock
    lateinit var workerExecutionService: WorkerExecutionService

    private val agentRegistry = AgentRegistry()
    private lateinit var consentPolicyService: ConsentPolicyService

    @Mock
    lateinit var eventPublisher: ApplicationEventPublisher

    private lateinit var delegationService: SecretaryDelegationService

    @BeforeEach
    fun init() {
        consentPolicyService = ConsentPolicyService(agentRegistry)
        delegationService = SecretaryDelegationService(
            taskRepository,
            executionRepository,
            workerExecutionService,
            agentRegistry,
            consentPolicyService,
            eventPublisher,
            platformTransactionManager = NoopPlatformTransactionManager,
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
        whenever(executionRepository.save(any())).thenAnswer { it.arguments[0] as TaskExecutionEntity }

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
        // task saved twice (running + done), execution saved twice (start + finish)
        verify(taskRepository, times(2)).save(any())
        verify(executionRepository, times(2)).save(any())
        assertEquals(SecretaryTaskStatus.done, task.status)
        assertEquals("result text", task.resultSummary)
    }

    @Test
    fun `delegation publishes running then done in order`() {
        val task = task()
        whenever(workerExecutionService.runOpenAiWorker(any(), any(), any(), any())).thenReturn("result text")
        whenever(taskRepository.save(any())).thenAnswer { it.arguments[0] as SecretaryTaskEntity }
        whenever(executionRepository.save(any())).thenAnswer { it.arguments[0] as TaskExecutionEntity }

        delegationService.delegate(task, "u1", baseContext())

        val events = argumentCaptor<SecretaryTaskEvent>()
        verify(eventPublisher, times(2)).publishEvent(events.capture())
        assertEquals(listOf("task.running", "task.done"), events.allValues.map { it.type })
    }

    @Test
    fun `delegation publishes failed event with error message on worker exception`() {
        val task = task()
        whenever(workerExecutionService.runOpenAiWorker(any(), any(), any(), any()))
            .thenThrow(IllegalStateException("worker exploded"))
        whenever(taskRepository.save(any())).thenAnswer { it.arguments[0] as SecretaryTaskEntity }
        whenever(executionRepository.save(any())).thenAnswer { it.arguments[0] as TaskExecutionEntity }

        delegationService.delegate(task, "u1", baseContext())

        val events = argumentCaptor<SecretaryTaskEvent>()
        verify(eventPublisher, times(2)).publishEvent(events.capture())
        val failed = events.allValues.last()
        assertEquals("task.failed", failed.type)
        assertEquals("worker exploded", failed.errorMessage)
    }

    @Test
    fun `delegation publishes needs confirmation at consent gate`() {
        val task = task(
            status = SecretaryTaskStatus.waiting_for_confirmation,
            assignedAgentId = AgentRegistry.IDs.DELIVERY,
            requiresConfirmation = true,
        )
        whenever(taskRepository.save(any())).thenAnswer { it.arguments[0] as SecretaryTaskEntity }

        delegationService.delegate(task, "u1", baseContext())

        val event = argumentCaptor<SecretaryTaskEvent>()
        verify(eventPublisher).publishEvent(event.capture())
        assertEquals("task.needs_confirmation", event.firstValue.type)
        assertEquals(SecretaryTaskStatus.waiting_for_confirmation.name, event.firstValue.status)
    }

    private fun task(
        status: SecretaryTaskStatus = SecretaryTaskStatus.ready,
        assignedAgentId: String = AgentRegistry.IDs.RESEARCH,
        requiresConfirmation: Boolean = false,
    ) = SecretaryTaskEntity(
        id = UUID.randomUUID(),
        userId = "u1",
        chatId = "c1",
        title = "Research",
        description = null,
        status = status,
        assignedAgentId = assignedAgentId,
        delegatedBrief = "Finn tre kilder",
        resultSummary = null,
        errorMessage = null,
        requiresConfirmation = requiresConfirmation,
        acceptanceCriteria = null,
        sortOrder = 0,
        listVersion = 0,
        artifactsJson = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    private fun baseContext() = TokenUsageContext(
        userId = "u1",
        chatId = "c1",
        assistant = "SECRETARY",
        clientPlatform = "test",
    )
}

private object NoopPlatformTransactionManager : PlatformTransactionManager {
    override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()

    override fun commit(status: TransactionStatus) {}

    override fun rollback(status: TransactionStatus) {}
}
