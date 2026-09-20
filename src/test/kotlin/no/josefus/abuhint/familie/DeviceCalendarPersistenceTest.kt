package no.josefus.abuhint.familie

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.TestPropertySource
import jakarta.persistence.EntityManager

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:device_calendar_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
])
class DeviceCalendarPersistenceTest {
    @Autowired lateinit var repository: DeviceCalendarSessionRepository
    @Autowired lateinit var entityManager: EntityManager

    @Test fun `migration persists operation and replay after reloading from database`() {
        val assistant = mock<DeviceCalendarAssistant>()
        whenever(assistant.respond(any(), any())).thenReturn(DeviceAgentDecision("Forslag",
            DeviceCalendarAction("create_event", "2026-09-24T16:00:00Z", "2026-09-24T17:00:00Z", "Europe/Oslo", "Tannlege")))
        val request = DeviceTurnRequest("req-1", "Lag en avtale")
        val first = DeviceCalendarChatService(assistant, repository).turn("alice", "chat", request)
        entityManager.flush()
        entityManager.clear()
        val restarted = DeviceCalendarChatService(assistant, repository)
        assertEquals(first, restarted.state("alice", "chat"))
        assertEquals(first, restarted.turn("alice", "chat", request))
        assertTrue(restarted.state("bob", "chat").messages.isEmpty())
        verify(assistant, times(1)).respond(any(), any())
    }
}
