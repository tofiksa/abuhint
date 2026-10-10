package no.josefus.abuhint.controller

import no.josefus.abuhint.secretary.SecretaryTaskEntity
import no.josefus.abuhint.secretary.SecretaryTaskEventHub
import no.josefus.abuhint.secretary.SecretaryTaskService
import no.josefus.abuhint.secretary.SecretaryTaskSnapshot
import no.josefus.abuhint.secretary.SecretaryTaskStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

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
    fun `events endpoint subscribes for user with snapshot as initial event`() {
        authenticate("user-a")
        val emitter = mock<SseEmitter>()
        whenever(taskService.listTasks("chat-a", "user-a")).thenReturn(listOf(task()))
        val initial = argumentCaptor<() -> Any>()
        whenever(eventHub.subscribe(eq("chat-a"), eq("user-a"), eq(1_800_000L), initial.capture())).thenReturn(emitter)

        val result = controller.taskEvents("chat-a")

        assertSame(emitter, result)
        val snapshot = initial.firstValue()
        assertIs<SecretaryTaskSnapshot>(snapshot)
        assertEquals(listOf("Task"), snapshot.tasks.map { it.title })
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
