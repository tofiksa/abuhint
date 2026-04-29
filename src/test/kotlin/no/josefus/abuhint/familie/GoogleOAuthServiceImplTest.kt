package no.josefus.abuhint.familie

import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals

class GoogleOAuthServiceImplTest {

    @Test
    fun `buildAuthUrl enables incremental authorization`() {
        val service = GoogleOAuthServiceImpl(
            FamilieplanleggernProperties(
                googleClientId = "cid",
                googleClientSecret = "secret",
                googleRedirectUri = "https://app.example/api/google/oauth/callback",
                defaultTimezone = "Europe/Oslo",
                tokenEncryptionKeyBase64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            )
        )

        val authUrl = service.buildAuthUrl("state-123")

        val query = URI(authUrl).rawQuery
            .split("&")
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0] to URLDecoder.decode(parts.getOrElse(1) { "" }, StandardCharsets.UTF_8)
            }
        assertEquals("true", query["include_granted_scopes"])
        assertEquals("offline", query["access_type"])
        assertEquals("consent", query["prompt"])
    }
}
