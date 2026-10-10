package no.josefus.abuhint.controller

import no.josefus.abuhint.secretary.SecretaryTaskEntity
import no.josefus.abuhint.secretary.SecretaryTaskEventHub
import no.josefus.abuhint.secretary.SecretaryTaskService
import no.josefus.abuhint.secretary.SecretaryTaskStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import kotlin.test.assertEquals

class SecretaryTaskControllerTest {

    private val taskService = mock<SecretaryTaskService>()
    private val eventHub = mock<SecretaryTaskEventHub>()
    private val controller = SecretaryTaskController(taskService, eventHub)

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `list endpoint filters tasks by authenticated user`() {
        authenticate("user-a")
        whenever(taskService.listTasks("chat-a", "user-a")).thenReturn(listOf(task()))

        val response = controller.listTasks("chat-a")

        assertEquals(listOf("Task"), response.map { it.title })
        verify(taskService).listTasks("chat-a", "user-a")
    }

    @Test
    fun `events endpoint subscribes for user and sends snapshot`() {
        authenticate("user-a")
        val emitter = mock<SseEmitter>()
        whenever(taskService.listTasks("chat-a", "user-a")).thenReturn(listOf(task()))
        whenever(eventHub.subscribe("chat-a", "user-a", 1_800_000L)).thenReturn(emitter)

        controller.taskEvents("chat-a")

        verify(eventHub).subscribe("chat-a", "user-a", 1_800_000L)
        verify(emitter).send(any<SseEmitter.SseEventBuilder>())
    }

    private fun authenticate(userId: String) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())
    }

    private fun task() = SecretaryTaskEntity(
        userId = "user-a",
        chatId = "chat-a",
        title = "Task",
        description = null,
        status = SecretaryTaskStatus.proposed,
        assignedAgentId = null,
        delegatedBrief = null,
        resultSummary = null,
        errorMessage = null,
        acceptanceCriteria = null,
        artifactsJson = null,
    )
}
