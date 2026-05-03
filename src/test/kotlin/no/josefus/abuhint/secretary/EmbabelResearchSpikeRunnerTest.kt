package no.josefus.abuhint.secretary

import no.josefus.abuhint.service.TokenUsageContext
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmbabelResearchSpikeRunnerTest {

    private val ctx = TokenUsageContext(
        userId = "u1",
        chatId = "chat-a",
        assistant = "SECRETARY",
        clientPlatform = "test",
    )

    @Test
    fun `tryRun returns null for non-research agents`() {
        val out = EmbabelResearchSpikeRunner().tryRun("delivery", "task-1", "brief", ctx)
        assertNull(out)
    }

    @Test
    fun `tryRun returns stub summary for research agent`() {
        val out = EmbabelResearchSpikeRunner().tryRun("research", "task-99", "find Kotlin 2 news", ctx)
        assertNotNull(out)
        assertTrue(out!!.contains("Embabel spike stub"))
        assertTrue(out.contains("task-99"))
        assertTrue(out.contains("find Kotlin 2 news"))
    }
}
