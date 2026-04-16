package no.josefus.abuhint.familie

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FamilieplanleggernPropertiesTest {

    @Test
    fun `properties exposes oauth client config with sane defaults`() {
        val props = FamilieplanleggernProperties(
            googleClientId = "cid",
            googleClientSecret = "secret",
            googleRedirectUri = "https://app.example/api/google/oauth/callback",
            defaultTimezone = "Europe/Oslo",
            tokenEncryptionKeyBase64 = "AAAAAAAAAAAAAAAAAAAAAA==",
        )

        assertEquals("cid", props.googleClientId)
        assertEquals("Europe/Oslo", props.defaultTimezone)
        assertTrue(props.isConfigured())
    }

    @Test
    fun `isConfigured returns false when clientId missing`() {
        val props = FamilieplanleggernProperties(
            googleClientId = "",
            googleClientSecret = "secret",
            googleRedirectUri = "https://app.example/cb",
            defaultTimezone = "Europe/Oslo",
            tokenEncryptionKeyBase64 = "AAAAAAAAAAAAAAAAAAAAAA==",
        )
        assertTrue(!props.isConfigured())
    }

    @Test
    fun `oauth scopes include calendar and calendar_events`() {
        val props = FamilieplanleggernProperties(
            googleClientId = "cid",
            googleClientSecret = "secret",
            googleRedirectUri = "cb",
            defaultTimezone = "Europe/Oslo",
            tokenEncryptionKeyBase64 = "AAAAAAAAAAAAAAAAAAAAAA==",
        )
        val scopes = props.oauthScopes()
        assertTrue(scopes.any { it.endsWith("/auth/calendar") })
        assertTrue(scopes.any { it.endsWith("/auth/calendar.events") })
    }
}
