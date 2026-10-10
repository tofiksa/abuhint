package no.josefus.abuhint.secretary

import no.josefus.abuhint.agent.AgentRegistry
import no.josefus.abuhint.service.TokenUsageContext
import org.junit.jupiter.api.AfterEach
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
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    private var execution: TaskExecutionEntity? = null
    private val executors = mutableListOf<ExecutorService>()

    @BeforeEach
    fun init() {
        consentPolicyService = ConsentPolicyService(agentRegistry)
        delegationService = service(
            properties = SecretaryDelegationProperties(),
            executor = Executor { command -> command.run() },
        )
    }

    @AfterEach
    fun shutDownExecutors() {
        executors.forEach { it.shutdownNow() }
    }

    @Test
    fun `fast worker returns done with result without backgrounding`() {
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
        stubRunningTransition(task)

        val base = TokenUsageContext(
            userId = "u1",
            chatId = "c1",
            assistant = "SECRETARY",
            clientPlatform = "test",
        )
        val outcome = delegationService.delegate(task, "u1", base)

        verify(workerExecutionService).runOpenAiWorker(
            AgentRegistry.IDs.RESEARCH,
            SecretaryChatIds.workerMemoryId(id.toString()),
            "Finn tre kilder",
            base.copy(taskId = id.toString(), workerAgent = "RESEARCH", parentAgent = "SECRETARY"),
        )
        // The running transition is an atomic update; completion saves the task entity.
        verify(taskRepository).save(any())
        verify(executionRepository, times(2)).save(any())
        assertFalse(outcome.backgrounded)
        assertEquals(SecretaryTaskStatus.done, outcome.task.status)
        assertEquals("result text", outcome.task.resultSummary)
    }

    @Test
    fun `slow worker returns running then publishes done when completed`() {
        val task = task()
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        val donePublished = CountDownLatch(1)
        whenever(workerExecutionService.runOpenAiWorker(any(), any(), any(), any())).thenAnswer {
            workerStarted.countDown()
            assertTrue(releaseWorker.await(1, TimeUnit.SECONDS))
            "slow result"
        }
        whenever(taskRepository.save(any())).thenAnswer { it.arguments[0] as SecretaryTaskEntity }
        whenever(eventPublisher.publishEvent(any<SecretaryTaskEvent>())).thenAnswer {
            if ((it.arguments[0] as SecretaryTaskEvent).type == "task.done") {
                donePublished.countDown()
            }
            Unit
        }
        stubRunningTransition(task)
        delegationService = service(
            properties = SecretaryDelegationProperties(syncWaitMs = 10, workerTimeoutMs = 1_000),
            executor = asyncExecutor(),
        )

        val outcome = delegationService.delegate(task, "u1", baseContext())

        assertTrue(workerStarted.await(1, TimeUnit.SECONDS))
        assertTrue(outcome.backgrounded)
        assertEquals(SecretaryTaskStatus.running, outcome.task.status)

        releaseWorker.countDown()
        assertTrue(donePublished.await(1, TimeUnit.SECONDS))
        assertEquals(SecretaryTaskStatus.done, task.status)
        assertEquals("slow result", task.resultSummary)

        val events = argumentCaptor<SecretaryTaskEvent>()
        verify(eventPublisher, times(2)).publishEvent(events.capture())
        assertEquals(listOf("task.running", "task.done"), events.allValues.map { it.type })
    }

    @Test
    fun `worker timeout fails task and late result does not overwrite failure`() {
        val task = task()
        val releaseWorker = CountDownLatch(1)
        val failedPublished = CountDownLatch(1)
        whenever(workerExecutionService.runOpenAiWorker(any(), any(), any(), any())).thenAnswer {
            releaseWorker.await(1, TimeUnit.SECONDS)
            "late result"
        }
        whenever(taskRepository.save(any())).thenAnswer { it.arguments[0] as SecretaryTaskEntity }
        whenever(eventPublisher.publishEvent(any<SecretaryTaskEvent>())).thenAnswer {
            if ((it.arguments[0] as SecretaryTaskEvent).type == "task.failed") {
                failedPublished.countDown()
            }
            Unit
        }
        stubRunningTransition(task)
        delegationService = service(
            properties = SecretaryDelegationProperties(syncWaitMs = 5, workerTimeoutMs = 30),
            executor = asyncExecutor(),
        )

        val outcome = delegationService.delegate(task, "u1", baseContext())

        assertTrue(outcome.backgrounded)
        assertTrue(failedPublished.await(1, TimeUnit.SECONDS))
        assertEquals(SecretaryTaskStatus.failed, task.status)
        assertEquals("Worker brukte for lang tid", task.errorMessage)
        assertNull(task.resultSummary)

        releaseWorker.countDown()
        Thread.sleep(50)
        assertEquals(SecretaryTaskStatus.failed, task.status)
        assertNull(task.resultSummary)
        val events = argumentCaptor<SecretaryTaskEvent>()
        verify(eventPublisher, times(2)).publishEvent(events.capture())
        assertEquals(listOf("task.running", "task.failed"), events.allValues.map { it.type })
    }

    @Test
    fun `concurrent double delegation starts only one worker`() {
        val task = task()
        val calls = AtomicInteger()
        val releaseWorker = CountDownLatch(1)
        val donePublished = CountDownLatch(1)
        whenever(workerExecutionService.runOpenAiWorker(any(), any(), any(), any())).thenAnswer {
            calls.incrementAndGet()
            releaseWorker.await(1, TimeUnit.SECONDS)
            "result"
        }
        whenever(taskRepository.save(any())).thenAnswer { it.arguments[0] as SecretaryTaskEntity }
        whenever(eventPublisher.publishEvent(any<SecretaryTaskEvent>())).thenAnswer {
            if ((it.arguments[0] as SecretaryTaskEvent).type == "task.done") {
                donePublished.countDown()
            }
            Unit
        }
        val transitions = AtomicInteger()
        whenever(taskRepository.markRunningIfNotRunning(any(), any())).thenAnswer {
            if (transitions.getAndIncrement() == 0) 1 else 0
        }
        whenever(taskRepository.findById(task.id)).thenReturn(Optional.of(task))
        whenever(executionRepository.save(any())).thenAnswer {
            (it.arguments[0] as TaskExecutionEntity).also { saved -> execution = saved }
        }
        whenever(executionRepository.findById(any())).thenAnswer { Optional.ofNullable(execution) }
        delegationService = service(
            properties = SecretaryDelegationProperties(syncWaitMs = 5, workerTimeoutMs = 1_000),
            executor = asyncExecutor(),
        )
        val callers = asyncExecutor(2)
        val start = CountDownLatch(1)

        val first = callers.submit<DelegationOutcome> {
            start.await()
            delegationService.delegate(task, "u1", baseContext())
        }
        val second = callers.submit<DelegationOutcome> {
            start.await()
            delegationService.delegate(task, "u1", baseContext())
        }
        start.countDown()
        first.get(1, TimeUnit.SECONDS)
        second.get(1, TimeUnit.SECONDS)

        assertEquals(1, calls.get())
        verify(workerExecutionService, times(1)).runOpenAiWorker(any(), any(), any(), any())
        releaseWorker.countDown()
        assertTrue(donePublished.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun `delegation publishes running then done in order`() {
        val task = task()
        whenever(workerExecutionService.runOpenAiWorker(any(), any(), any(), any())).thenReturn("result text")
        whenever(taskRepository.save(any())).thenAnswer { it.arguments[0] as SecretaryTaskEntity }
        stubRunningTransition(task)

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
        stubRunningTransition(task)

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

    private fun stubRunningTransition(task: SecretaryTaskEntity) {
        whenever(taskRepository.markRunningIfNotRunning(any(), any())).thenReturn(1)
        whenever(taskRepository.findById(task.id)).thenReturn(Optional.of(task))
        whenever(executionRepository.save(any())).thenAnswer {
            (it.arguments[0] as TaskExecutionEntity).also { saved -> execution = saved }
        }
        whenever(executionRepository.findById(any())).thenAnswer { Optional.ofNullable(execution) }
    }

    private fun service(properties: SecretaryDelegationProperties, executor: Executor) =
        SecretaryDelegationService(
            taskRepository,
            executionRepository,
            workerExecutionService,
            agentRegistry,
            consentPolicyService,
            eventPublisher,
            properties,
            executor,
            NoopPlatformTransactionManager,
            null,
        )

    private fun asyncExecutor(threads: Int = 1): ExecutorService =
        Executors.newFixedThreadPool(threads).also(executors::add)
}

private object NoopPlatformTransactionManager : PlatformTransactionManager {
    override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()

    override fun commit(status: TransactionStatus) {}

    override fun rollback(status: TransactionStatus) {}
}
