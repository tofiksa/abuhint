package no.josefus.abuhint.familie

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class DeviceCalendarChatServiceTest {
    private val assistant = mock<DeviceCalendarAssistant>()
    private val stored = mutableMapOf<String, DeviceCalendarSessionEntity>()
    private val sessions = mock<DeviceCalendarSessionRepository> {
        on { findById(any()) } doAnswer { java.util.Optional.ofNullable(stored[it.getArgument<String>(0)]) }
        on { saveAndFlush(any<DeviceCalendarSessionEntity>()) } doAnswer {
            it.getArgument<DeviceCalendarSessionEntity>(0).also { entity -> stored[entity.id] = entity }
        }
    }
    private val service = DeviceCalendarChatService(assistant, sessions)

    @Test
    fun `device chat attributes model usage and clears thread context`() {
        whenever(assistant.respond(any(), any())).thenAnswer {
            val usage = no.josefus.abuhint.service.TokenUsageContextHolder.get()
            assertEquals("user", usage?.userId)
            assertEquals("FAMILIE", usage?.assistant)
            DeviceAgentDecision("Hei!")
        }
        service.turn("user", "chat", DeviceTurnRequest("one", "Hei"))
        assertNull(no.josefus.abuhint.service.TokenUsageContextHolder.get())
    }

    @Test
    fun `pending operation and request replay survive service restart`() {
        whenever(assistant.respond(any(), any())).thenReturn(DeviceAgentDecision("Sjekker.", read()))
        val request = DeviceTurnRequest("one", "I morgen?")
        val first = service.turn("user", "chat", request)
        val restarted = DeviceCalendarChatService(assistant, sessions)
        assertEquals(first, restarted.state("user", "chat"))
        assertEquals(first, restarted.turn("user", "chat", request))
        verify(assistant, times(1)).respond(any(), any())
    }

    @Test
    fun `read request is returned as a pending operation without Google credentials`() {
        whenever(assistant.respond(any(), any())).thenReturn(DeviceAgentDecision("Jeg sjekker torsdag.", read()))
        val reply = service.turn("user", "chat", DeviceTurnRequest("request-1", "Hva skjer torsdag?"))
        assertEquals("read_events", reply.pendingOperation?.action?.type)
        assertFalse(reply.pendingOperation!!.id.isBlank())
        assertEquals(2, reply.messages.size)
    }

    @Test
    fun `result resumes agent and duplicate request does not execute model twice`() {
        whenever(assistant.respond(any(), any())).thenReturn(
            DeviceAgentDecision("Sjekker.", read()), DeviceAgentDecision("Du har ingen avtaler.")
        )
        val first = service.turn("user", "chat", DeviceTurnRequest("one", "I morgen?"))
        val request = DeviceTurnRequest("two", result = DeviceOperationResult(first.pendingOperation!!.id, "success"))
        val completed = service.turn("user", "chat", request)
        assertNull(completed.pendingOperation)
        assertEquals(completed, service.turn("user", "chat", request))
        verify(assistant, times(2)).respond(any(), any())
    }

    @Test
    fun `foreign operation results are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.turn("user", "chat", DeviceTurnRequest("one", result = DeviceOperationResult("unknown", "success")))
        }
        verifyNoInteractions(assistant)
    }

    @Test
    fun `same chat id is isolated by authenticated user`() {
        whenever(assistant.respond(any(), any())).thenReturn(DeviceAgentDecision("Sjekker.", read()))
        service.turn("alice", "chat", DeviceTurnRequest("one", "Mine avtaler"))
        assertTrue(service.state("bob", "chat").messages.isEmpty())
    }

    @Test
    fun `invalid model date range cannot become a device operation`() {
        whenever(assistant.respond(any(), any())).thenReturn(DeviceAgentDecision("Sjekker.", read().copy(end = "2026-01-01T00:00:00Z")))
        assertThrows(IllegalArgumentException::class.java) {
            service.turn("user", "chat", DeviceTurnRequest("one", "Avtaler?"))
        }
        assertTrue(service.state("user", "chat").messages.isEmpty())
    }

    @Test
    fun `offset and local model dates become pending operations`() {
        whenever(assistant.respond(any(), any())).thenReturn(
            DeviceAgentDecision(
                "Sjekker.",
                DeviceCalendarAction(
                    "read_events",
                    "2026-09-24T00:00:00+02:00",
                    "2026-09-25T00:00:00",
                    "Europe/Oslo",
                ),
            ),
        )
        val reply = service.turn("user", "chat", DeviceTurnRequest("one", "Torsdag?"))
        assertEquals("read_events", reply.pendingOperation?.action?.type)
        assertEquals("2026-09-24T00:00:00+02:00", reply.pendingOperation?.action?.start)
        assertEquals("2026-09-25T00:00:00", reply.pendingOperation?.action?.end)
    }

    @Test
    fun `unparseable model dates become IllegalArgumentException not DateTimeParseException`() {
        whenever(assistant.respond(any(), any())).thenReturn(
            DeviceAgentDecision("Sjekker.", read().copy(start = "torsdag", end = "fredag")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            service.turn("user", "chat", DeviceTurnRequest("one", "Avtaler?"))
        }
        assertTrue(service.state("user", "chat").messages.isEmpty())
    }

    private fun read() = DeviceCalendarAction("read_events", "2026-09-24T00:00:00Z", "2026-09-25T00:00:00Z", "Europe/Oslo")
}
