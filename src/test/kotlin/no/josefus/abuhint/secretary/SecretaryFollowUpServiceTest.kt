package no.josefus.abuhint.secretary

import no.josefus.abuhint.repository.SecretaryAssistant
import no.josefus.abuhint.service.ChatIdContextHolder
import no.josefus.abuhint.service.TokenUsageContext
import no.josefus.abuhint.service.TokenUsageContextHolder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretaryFollowUpServiceTest {

    private val assistant = mock<SecretaryAssistant>()
    private val repository = mock<SecretaryTaskRepository>()
    private val publisher = mock<ApplicationEventPublisher>()
    private val service = SecretaryFollowUpService(assistant, repository, publisher)
    private val context = TokenUsageContext("u1", "chat-a", "SECRETARY", "web")

    @Test
    fun `done task is answered by the secretary and pushed as assistant message`() {
        val task = task(SecretaryTaskStatus.done, resultSummary = "Sol og 12 grader")
        whenever(repository.findById(task.id)).thenReturn(Optional.of(task))
        whenever(assistant.chat(eq("chat-a"), any(), any(), any(), any())).thenReturn("Neste uke blir det sol.")

        service.on(SecretaryTaskBackgroundCompletedEvent(task.id, context))

        val note = argumentCaptor<String>()
        verify(assistant).chat(eq("chat-a"), note.capture(), any(), any(), any())
        assertTrue(note.firstValue.startsWith("[Oppgaveresultat]"))
        assertTrue(note.firstValue.contains("Sol og 12 grader"))

        val published = argumentCaptor<SecretaryAssistantMessageEvent>()
        verify(publisher).publishEvent(published.capture())
        assertEquals("Neste uke blir det sol.", published.firstValue.text)
        assertEquals("chat-a", published.firstValue.chatId)
        assertEquals("u1", published.firstValue.userId)
        assertEquals(task.id.toString(), published.firstValue.taskId)

        assertNull(ChatIdContextHolder.get())
        assertNull(TokenUsageContextHolder.get())
    }

    @Test
    fun `failed task note carries the error`() {
        val task = task(SecretaryTaskStatus.failed, errorMessage = "Worker brukte for lang tid")
        whenever(repository.findById(task.id)).thenReturn(Optional.of(task))
        whenever(assistant.chat(any(), any(), any(), any(), any())).thenReturn("Fikk ikke svar, prøve igjen?")

        service.on(SecretaryTaskBackgroundCompletedEvent(task.id, context))

        val note = argumentCaptor<String>()
        verify(assistant).chat(any(), note.capture(), any(), any(), any())
        assertTrue(note.firstValue.contains("Worker brukte for lang tid"))
    }

    @Test
    fun `secretary failure falls back to the raw result`() {
        val task = task(SecretaryTaskStatus.done, resultSummary = "Sol og 12 grader")
        whenever(repository.findById(task.id)).thenReturn(Optional.of(task))
        whenever(assistant.chat(any(), any(), any(), any(), any())).thenThrow(IllegalStateException("LLM down"))

        service.on(SecretaryTaskBackgroundCompletedEvent(task.id, context))

        val published = argumentCaptor<SecretaryAssistantMessageEvent>()
        verify(publisher).publishEvent(published.capture())
        assertEquals("Sol og 12 grader", published.firstValue.text)
    }

    @Test
    fun `task that is not finished is skipped`() {
        val task = task(SecretaryTaskStatus.running)
        whenever(repository.findById(task.id)).thenReturn(Optional.of(task))

        service.on(SecretaryTaskBackgroundCompletedEvent(task.id, context))

        verify(assistant, times(0)).chat(any(), any(), any(), any(), any())
        verify(publisher, times(0)).publishEvent(any<Any>())
    }

    private fun task(
        status: SecretaryTaskStatus,
        resultSummary: String? = null,
        errorMessage: String? = null,
    ) = SecretaryTaskEntity(
        id = UUID.randomUUID(),
        userId = "u1",
        chatId = "chat-a",
        title = "Været neste uke",
        description = null,
        status = status,
        assignedAgentId = "research",
        delegatedBrief = null,
        resultSummary = resultSummary,
        errorMessage = errorMessage,
        acceptanceCriteria = null,
        artifactsJson = null,
    )
}
