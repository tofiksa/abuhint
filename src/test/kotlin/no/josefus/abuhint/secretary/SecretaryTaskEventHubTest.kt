package no.josefus.abuhint.secretary

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import kotlin.test.assertEquals

class SecretaryTaskEventHubTest {

    @Test
    fun `event is delivered only to matching chat and user`() {
        val hub = SecretaryTaskEventHub()
        val matching = hub.subscribe("chat-a", "user-a", 1_000)
        val wrongUser = hub.subscribe("chat-a", "user-b", 1_000)
        val wrongChat = hub.subscribe("chat-b", "user-a", 1_000)

        hub.on(event(chatId = "chat-a", userId = "user-a"))

        assertEquals(3, queuedSendCount(matching))
        assertEquals(0, queuedSendCount(wrongUser))
        assertEquals(0, queuedSendCount(wrongChat))
    }

    @Test
    fun `dead emitter is removed when send throws`() {
        val hub = SecretaryTaskEventHub()
        val deadEmitter = hub.subscribe("chat-a", "user-a", 1_000)
        deadEmitter.complete()

        assertDoesNotThrow {
            hub.on(event(chatId = "chat-a", userId = "user-a"))
        }

        assertEquals(0, subscriberCount(hub, "chat-a"))
    }

    private fun event(chatId: String, userId: String) = SecretaryTaskEvent(
        type = "task.running",
        taskId = "task-1",
        chatId = chatId,
        userId = userId,
        title = "Task",
        status = SecretaryTaskStatus.running.name,
        assignedAgentId = "research",
        resultSummary = null,
        errorMessage = null,
        at = Instant.EPOCH,
    )

    private fun queuedSendCount(emitter: SseEmitter): Int {
        val field = ResponseBodyEmitter::class.java.getDeclaredField("earlySendAttempts")
        field.isAccessible = true
        return (field.get(emitter) as Collection<*>).size
    }

    private fun subscriberCount(hub: SecretaryTaskEventHub, chatId: String): Int {
        val field = SecretaryTaskEventHub::class.java.getDeclaredField("subscribersByChatId")
        field.isAccessible = true
        val subscribers = field.get(hub) as Map<*, *>
        return (subscribers[chatId] as? Collection<*>)?.size ?: 0
    }
}
