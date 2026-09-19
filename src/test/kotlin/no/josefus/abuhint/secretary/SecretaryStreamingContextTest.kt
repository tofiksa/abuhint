package no.josefus.abuhint.secretary

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.invocation.InvocationContext
import dev.langchain4j.invocation.InvocationParameters
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.tool.DefaultToolExecutor
import no.josefus.abuhint.agent.AgentRegistry
import no.josefus.abuhint.controller.SecretaryController
import no.josefus.abuhint.repository.SecretaryAssistant
import no.josefus.abuhint.service.ChatIdContextHolder
import no.josefus.abuhint.service.TokenUsageContext
import no.josefus.abuhint.service.TokenUsageContextHolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import no.josefus.abuhint.dto.ChatRequest as UserChatRequest

class SecretaryStreamingContextTest {

    private val repository = mock<SecretaryTaskRepository>()
    private val delegation = mock<SecretaryDelegationService>()
    private val service = SecretaryTaskService(repository, delegation, AgentRegistry())
    private val pending = LinkedBlockingQueue<Pair<ChatRequest, StreamingChatResponseHandler>>()
    private val model = object : StreamingChatModel {
        override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
            pending.add(request to handler)
        }
    }
    private val assistant = AiServices.builder(SecretaryAssistant::class.java)
        .streamingChatModel(model)
        .chatMemoryProvider { MessageWindowChatMemory.withMaxMessages(30) }
        .tools(SecretaryTaskTool(service))
        .build()
    private val controller = SecretaryController(assistant)

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
        ChatIdContextHolder.clear()
        TokenUsageContextHolder.clear()
    }

    @Test
    fun `streamed tools retain authenticated identity and delegation context across callbacks`() {
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as SecretaryTaskEntity }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("user-a", null, emptyList())

        controller.chatStream("chat-a", "android", UserChatRequest("Opprett og utfør oppgaven"))
        cleanup() // The servlet request has returned before the model responds.

        Executors.newSingleThreadExecutor().use { executor ->
            executor.submit {
                assertNull(SecurityContextHolder.getContext().authentication)
                assertNull(TokenUsageContextHolder.get())
                respondWithTool(
                    "createSecretaryTask",
                    """{"title":"Research","description":"Find sources","assignedAgentId":"research","requiresConfirmation":false,"acceptanceCriteria":null}""",
                )
                val created = argumentCaptor<SecretaryTaskEntity>()
                verify(repository).save(created.capture())
                val task = created.firstValue
                assertEquals("user-a", task.userId)
                assertEquals("chat-a", task.chatId)
                whenever(repository.findByIdAndUserId(task.id, "user-a")).thenReturn(task)

                respondWithTool(
                    "updateSecretaryTask",
                    """{"taskId":"${task.id}","status":"ready","title":null,"description":null,"assignedAgentId":null,"delegatedBrief":"Find three sources","requiresConfirmation":null,"acceptanceCriteria":null}""",
                )
                assertEquals("Find three sources", task.delegatedBrief)

                respondWithTool("markSecretaryTaskBlocked", """{"taskId":"${task.id}","reason":"Need details"}""")
                assertEquals(SecretaryTaskStatus.blocked, task.status)
                respondWithTool("markSecretaryTaskReady", """{"taskId":"${task.id}"}""")
                assertEquals(SecretaryTaskStatus.ready, task.status)

                whenever(delegation.delegate(any(), any(), any())).thenReturn(task)
                respondWithTool("delegateSecretaryTask", """{"taskId":"${task.id}"}""")
                verify(delegation).delegate(
                    task,
                    "user-a",
                    TokenUsageContext("user-a", "chat-a", "SECRETARY", "android"),
                )

                respondWithTool("getSecretaryTask", """{"taskId":"${task.id}"}""")
                respondWithTool("markSecretaryTaskDone", """{"taskId":"${task.id}"}""")
                assertEquals(SecretaryTaskStatus.done, task.status)
                nextRequest().second.onCompleteResponse(response(AiMessage.from("Ferdig")))
                assertNull(SecurityContextHolder.getContext().authentication)
                assertNull(TokenUsageContextHolder.get())
            }.get(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `stream startup clears request thread context before returning`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("user-a", null, emptyList())

        controller.chatStream("chat-a", "android", UserChatRequest("Hei"))

        assertNull(ChatIdContextHolder.get())
        assertNull(TokenUsageContextHolder.get())
    }

    @Test
    fun `non-streaming chat passes authenticated context to tools`() {
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as SecretaryTaskEntity }
        val syncModel = object : ChatModel {
            override fun doChat(request: ChatRequest): ChatResponse {
                return if (request.messages().last() is ToolExecutionResultMessage) {
                    response(AiMessage.from((request.messages().last() as ToolExecutionResultMessage).text()))
                } else {
                    response(AiMessage.from(createRequest()))
                }
            }
        }
        val syncAssistant = AiServices.builder(SecretaryAssistant::class.java)
            .chatModel(syncModel)
            .streamingChatModel(model)
            .chatMemoryProvider { MessageWindowChatMemory.withMaxMessages(10) }
            .tools(SecretaryTaskTool(service))
            .build()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("user-sync", null, emptyList())

        SecretaryController(syncAssistant).chat("chat-sync", "web", UserChatRequest("Opprett oppgaven"))

        val created = argumentCaptor<SecretaryTaskEntity>()
        verify(repository).save(created.capture())
        assertEquals("user-sync", created.firstValue.userId)
        assertEquals("chat-sync", created.firstValue.chatId)
        assertNull(TokenUsageContextHolder.get())
        assertNull(ChatIdContextHolder.get())
    }

    @Test
    fun `tool rejects missing invocation identity even when thread has another user`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("unrelated-user", null, emptyList())
        val request = createRequest()
        val tool = SecretaryTaskTool(service)
        val executor = DefaultToolExecutor(tool, request)
        val context: InvocationContext = InvocationContext.builder()
            .chatMemoryId("chat-a")
            .invocationParameters(InvocationParameters())
            .build()

        val result = executor.executeWithContext(request, context)

        assertTrue(result.isError)
        assertTrue(result.resultText().contains("Mangler autentisert sekretærkontekst"))
        verifyNoInteractions(repository, delegation)
    }

    @Test
    fun `interleaved invocations keep their own identity even with the same chat id`() {
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as SecretaryTaskEntity }
        val first = SecretaryInvocationContext.from(TokenUsageContext("user-a", "chat-a", "SECRETARY", "android"))
        val second = SecretaryInvocationContext.from(TokenUsageContext("user-b", "chat-a", "SECRETARY", "web"))
        val request = createRequest()
        val executor = DefaultToolExecutor(SecretaryTaskTool(service), request)

        for (parameters in listOf(second, first)) {
            val context: InvocationContext = InvocationContext.builder()
                .chatMemoryId("chat-a")
                .invocationParameters(parameters)
                .build()
            assertFalse(executor.executeWithContext(request, context).isError)
        }

        val created = argumentCaptor<SecretaryTaskEntity>()
        verify(repository, times(2)).save(created.capture())
        assertEquals(listOf("user-b", "user-a"), created.allValues.map { it.userId })
    }

    private fun createRequest(): ToolExecutionRequest = ToolExecutionRequest.builder()
        .id("call-create")
        .name("createSecretaryTask")
        .arguments("""{"title":"Task","requiresConfirmation":false}""")
        .build()

    private fun respondWithTool(name: String, arguments: String) {
        val (request, handler) = nextRequest()
        val specification = request.toolSpecifications().first { it.name() == name }
        assertTrue(specification.parameters().properties().keys.none {
            it in setOf("parameters", "context", "userId", "memoryId")
        }, "Internal invocation context must not be part of the model's tool schema")
        val toolRequest = ToolExecutionRequest.builder()
            .id("call-$name")
            .name(name)
            .arguments(arguments)
            .build()
        handler.onCompleteResponse(response(AiMessage.from(toolRequest)))
        val result = pending.peek()?.first?.messages()?.last() as? ToolExecutionResultMessage
        assertNotNull(result, "Expected the tool result to be sent back to the model")
        assertTrue(result.text().startsWith("{"), "Tool failed: ${result.text()}")
        assertTrue(!result.text().contains("not found"), "Task must belong to the authenticated user")
    }

    private fun nextRequest(): Pair<ChatRequest, StreamingChatResponseHandler> =
        assertNotNull(pending.poll(2, TimeUnit.SECONDS), "Expected a model request")

    private fun response(message: AiMessage): ChatResponse = ChatResponse.builder().aiMessage(message).build()
}
