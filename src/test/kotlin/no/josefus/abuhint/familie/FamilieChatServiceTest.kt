package no.josefus.abuhint.familie

import no.josefus.abuhint.repository.ConcretePineconeChatMemoryStore
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertTrue

class FamilieChatServiceTest {

    private val assistant: FamilieplanleggernAssistant = mock()
    private val chatMemoryStore: ConcretePineconeChatMemoryStore = mock()
    private val props = FamilieplanleggernProperties(
        googleClientId = "c",
        googleClientSecret = "s",
        googleRedirectUri = "r",
        defaultTimezone = "Europe/Oslo",
        tokenEncryptionKeyBase64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    )

    private val service = FamilieChatService(assistant, chatMemoryStore, props)

    @Test
    fun `buildDateTimeContext prefers client timezone and sentAt`() {
        val meta = FamilieClientMetadata(
            timezone = "UTC",
            sentAt = "2026-04-18T12:00:00Z",
        )
        val ctx = service.buildDateTimeContext(meta)
        assertTrue(ctx.contains("UTC"), "expected UTC in: $ctx")
        assertTrue(ctx.contains("2026-04-18"), "expected date in: $ctx")
    }

    @Test
    fun `buildDateTimeContext falls back to server defaults when metadata absent`() {
        val ctx = service.buildDateTimeContext(null)
        assertTrue(ctx.contains("Europe/Oslo"), "expected default zone in: $ctx")
    }
}
