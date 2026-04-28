package no.josefus.abuhint.familie

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI

@Tag(name = "Familieplanleggern – Google OAuth", description = "OAuth 2.0-flyt for å koble brukerens Google-konto til Familieplanleggern-agenten.")
@RestController
@RequestMapping("/api/google/oauth")
class GoogleOAuthController(
    private val credentialStore: UserGoogleCredentialStore,
    private val oauthService: GoogleOAuthService,
    private val stateStore: InMemoryOAuthStateStore,
    private val properties: FamilieplanleggernProperties,
) {

    private val log = LoggerFactory.getLogger(GoogleOAuthController::class.java)

    @Operation(
        summary = "Start OAuth-flyt",
        description = "Returnerer Google-authorisasjons-URL. Native: åpne i Custom Tab (302 etter callback går til deep link). Web: bruk `client=web` og sett `familie.web-oauth-success-uri` til SPA-ruten som skal motta `?status=…`.",
    )
    @PostMapping("/start", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun start(
        @Parameter(description = "Settes til `web` for Next.js/SPA; utelates for mobil (deep link).")
        @RequestParam(required = false) client: String?,
    ): ResponseEntity<StartResponse> {
        val userId = currentUserId()
        val returnChannel = when (client?.lowercase()) {
            "web" -> {
                if (properties.webOAuthSuccessUri.isBlank()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Web OAuth is not configured — set familie.web-oauth-success-uri (env FAMILIE_WEB_OAUTH_SUCCESS_URI), " +
                            "e.g. https://your-app/dashboard/familie/oauth/callback",
                    )
                }
                OAuthReturnChannel.WEB
            }
            else -> OAuthReturnChannel.MOBILE
        }
        val state = stateStore.issue(userId, returnChannel)
        val authUrl = oauthService.buildAuthUrl(state)
        return ResponseEntity.ok(StartResponse(authUrl = authUrl, state = state))
    }

    @Operation(
        summary = "OAuth callback (kalt av Googles redirect)",
        description = "Bytter `code` mot tokens, lagrer dem per bruker, og redirecter til deep-link (mobil) eller web-URL (når flyten ble startet med `client=web`).",
    )
    @GetMapping("/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> {
        val context = state?.takeIf { it.isNotBlank() }?.let { stateStore.consume(it) }

        fun redirectWithStatus(status: String): ResponseEntity<Void> {
            val base = postAuthBase(context?.returnChannel)
            return redirect(uriWithStatus(base, status))
        }

        if (error != null) {
            log.info("OAuth callback received error from Google: {}", error)
            return redirectWithStatus(error)
        }
        if (state.isNullOrBlank() || context == null) {
            log.warn("OAuth callback rejected — missing or unknown state")
            return redirectWithStatus("invalid_state")
        }
        val userId = context.userId
        if (code.isNullOrBlank()) {
            return redirectWithStatus("missing_code")
        }

        return try {
            val tokens = oauthService.exchangeCodeForTokens(code, state)
            credentialStore.save(
                GoogleCredentials(
                    userId = userId,
                    refreshToken = tokens.refreshToken,
                    accessToken = tokens.accessToken,
                    accessTokenExpiresAt = tokens.accessTokenExpiresAt,
                    scope = tokens.scope,
                    email = tokens.email,
                    timezone = null,
                )
            )
            redirectWithStatus("ok")
        } catch (e: Exception) {
            log.error("Token exchange failed for userId={}: {}", userId, e.message, e)
            redirectWithStatus("token_exchange_failed")
        }
    }

    @Operation(summary = "Hent tilkoblingsstatus", description = "Brukes av Android-appen for å vise om brukeren har koblet til Google.")
    @GetMapping("/status", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun status(): ResponseEntity<StatusResponse> {
        val userId = currentUserId()
        val credentials = credentialStore.load(userId)
        return ResponseEntity.ok(
            StatusResponse(
                connected = credentials != null,
                email = credentials?.email,
                timezone = credentials?.timezone,
            )
        )
    }

    @Operation(summary = "Koble fra Google", description = "Sletter lagrede OAuth-tokens for gjeldende bruker.")
    @DeleteMapping
    fun disconnect(): ResponseEntity<Void> {
        credentialStore.delete(currentUserId())
        return ResponseEntity.noContent().build()
    }

    private fun currentUserId(): String {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw IllegalStateException("No authenticated user in SecurityContext")
        return auth.name
    }

    private fun postAuthBase(channel: OAuthReturnChannel?): String =
        when (channel) {
            OAuthReturnChannel.WEB ->
                properties.webOAuthSuccessUri.ifBlank { properties.deepLinkSuccessUri }
            OAuthReturnChannel.MOBILE, null -> properties.deepLinkSuccessUri
        }

    private fun uriWithStatus(base: String, status: String): URI {
        val separator = if (base.contains('?')) '&' else '?'
        return URI.create("$base${separator}status=$status")
    }

    private fun redirect(location: URI): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location.toString()).build()

    data class StartResponse(val authUrl: String, val state: String)
    data class StatusResponse(val connected: Boolean, val email: String?, val timezone: String?)
}
