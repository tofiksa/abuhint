package no.josefus.abuhint.familie

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoogleOAuthControllerTest {

    private val store: UserGoogleCredentialStore = mock()
    private val googleOAuthService: GoogleOAuthService = mock()
    private val props = FamilieplanleggernProperties(
        googleClientId = "cid",
        googleClientSecret = "secret",
        googleRedirectUri = "https://app.example/api/google/oauth/callback",
        defaultTimezone = "Europe/Oslo",
        tokenEncryptionKeyBase64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        deepLinkSuccessUri = "familieplanleggern://oauth/done",
        webOAuthSuccessUri = "https://web.example/dashboard/familie/oauth/callback",
    )
    private val stateStore = InMemoryOAuthStateStore()

    private val controller = GoogleOAuthController(store, googleOAuthService, stateStore, props)

    @BeforeEach
    fun setupAuth() {
        val auth = UsernamePasswordAuthenticationToken("user-42", null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }

    @Test
    fun `start returns authUrl and generates state`() {
        whenever(googleOAuthService.buildAuthUrl(any())).thenReturn("https://accounts.google.com/o/oauth2/v2/auth?state=abc")

        val response = controller.start(client = null)

        assertEquals(200, response.statusCode.value())
        val body = response.body!!
        assertEquals("https://accounts.google.com/o/oauth2/v2/auth?state=abc", body.authUrl)
        assertNotNull(body.state)
        val ctx = stateStore.consume(body.state)
        assertEquals("user-42", ctx?.userId)
        assertEquals(OAuthReturnChannel.MOBILE, ctx?.returnChannel)
    }

    @Test
    fun `start with client web uses WEB return channel`() {
        whenever(googleOAuthService.buildAuthUrl(any())).thenReturn("https://accounts.google.com/o/oauth2/v2/auth?state=abc")

        val response = controller.start(client = "web")

        assertEquals(200, response.statusCode.value())
        val ctx = stateStore.consume(response.body!!.state)
        assertEquals("user-42", ctx?.userId)
        assertEquals(OAuthReturnChannel.WEB, ctx?.returnChannel)
    }

    @Test
    fun `start with client web returns 400 when web success URL is not configured`() {
        val propsNoWeb = props.copy(webOAuthSuccessUri = "")
        val freshStore = InMemoryOAuthStateStore()
        val ctl = GoogleOAuthController(store, googleOAuthService, freshStore, propsNoWeb)
        whenever(googleOAuthService.buildAuthUrl(any())).thenReturn("https://accounts.google.com/x")

        val ex = assertThrows(ResponseStatusException::class.java) { ctl.start(client = "web") }

        assertEquals(400, ex.statusCode.value())
    }

    @Test
    fun `callback with web channel redirects to configured web URL`() {
        val state = stateStore.issue(userId = "user-42", returnChannel = OAuthReturnChannel.WEB)
        whenever(googleOAuthService.exchangeCodeForTokens("auth-code", state)).thenReturn(
            GoogleTokenExchangeResult(
                refreshToken = "rt",
                accessToken = "at",
                accessTokenExpiresAt = Instant.parse("2026-05-01T00:00:00Z"),
                scope = "https://www.googleapis.com/auth/calendar",
                email = "user@example.com",
            )
        )

        val response = controller.callback(code = "auth-code", state = state, error = null)

        assertEquals(302, response.statusCode.value())
        assertEquals(
            "https://web.example/dashboard/familie/oauth/callback?status=ok",
            response.headers.location?.toString(),
        )
    }

    @Test
    fun `callback with valid state exchanges code and saves credentials`() {
        val state = stateStore.issue(userId = "user-42")
        whenever(googleOAuthService.exchangeCodeForTokens("auth-code", state)).thenReturn(
            GoogleTokenExchangeResult(
                refreshToken = "rt",
                accessToken = "at",
                accessTokenExpiresAt = Instant.parse("2026-05-01T00:00:00Z"),
                scope = "https://www.googleapis.com/auth/calendar",
                email = "user@example.com",
            )
        )

        val response = controller.callback(code = "auth-code", state = state, error = null)

        assertEquals(302, response.statusCode.value())
        assertEquals("familieplanleggern://oauth/done?status=ok", response.headers.location?.toString())
        verify(store).save(
            GoogleCredentials(
                userId = "user-42",
                refreshToken = "rt",
                accessToken = "at",
                accessTokenExpiresAt = Instant.parse("2026-05-01T00:00:00Z"),
                scope = "https://www.googleapis.com/auth/calendar",
                email = "user@example.com",
                timezone = null,
            )
        )
    }

    @Test
    fun `callback rejects unknown state`() {
        val response = controller.callback(code = "auth-code", state = "bogus", error = null)

        assertEquals(302, response.statusCode.value())
        val location = response.headers.location!!.toString()
        assertTrue(location.startsWith("familieplanleggern://oauth/done"))
        assertTrue(location.contains("status=invalid_state"))
    }

    @Test
    fun `callback forwards google error to deep-link for mobile channel`() {
        val state = stateStore.issue("user-42")

        val response = controller.callback(code = null, state = state, error = "access_denied")

        val location = response.headers.location!!.toString()
        assertTrue(location.startsWith("familieplanleggern://oauth/done"))
        assertTrue(location.contains("status=access_denied"))
    }

    @Test
    fun `callback forwards google error to post-auth URL preserving channel`() {
        val state = stateStore.issue("user-42", OAuthReturnChannel.WEB)

        val response = controller.callback(code = null, state = state, error = "access_denied")

        val location = response.headers.location!!.toString()
        assertTrue(location.startsWith("https://web.example/dashboard/familie/oauth/callback"))
        assertTrue(location.contains("status=access_denied"))
    }

    @Test
    fun `callback consumes state to prevent replay`() {
        val state = stateStore.issue("user-42")
        whenever(googleOAuthService.exchangeCodeForTokens(any(), any())).thenReturn(
            GoogleTokenExchangeResult("rt", "at", null, "scope", "e@x")
        )

        controller.callback(code = "code", state = state, error = null)
        assertNull(stateStore.consume(state), "State must be consumed after first use")
    }

    @Test
    fun `status returns connected false when no credentials exist`() {
        whenever(store.load("user-42")).thenReturn(null)

        val response = controller.status()

        assertEquals(200, response.statusCode.value())
        assertFalse(response.body!!.connected)
    }

    @Test
    fun `status returns connected true with email and timezone when credentials exist`() {
        whenever(store.load("user-42")).thenReturn(
            GoogleCredentials(
                userId = "user-42",
                refreshToken = "rt",
                accessToken = "at",
                accessTokenExpiresAt = null,
                scope = "scope",
                email = "user@example.com",
                timezone = "Europe/Oslo",
            )
        )

        val body = controller.status().body!!

        assertTrue(body.connected)
        assertEquals("user@example.com", body.email)
        assertEquals("Europe/Oslo", body.timezone)
    }

    @Test
    fun `disconnect deletes stored credentials`() {
        val response = controller.disconnect()

        assertEquals(204, response.statusCode.value())
        verify(store).delete("user-42")
    }
}
