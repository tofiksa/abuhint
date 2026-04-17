package no.josefus.abuhint.familie

import io.swagger.v3.oas.annotations.Operation
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

    @Operation(summary = "Start OAuth-flyt", description = "Returnerer Google-authorisasjons-URL som Android-klienten åpner i Custom Tab.")
    @PostMapping("/start", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun start(): ResponseEntity<StartResponse> {
        val userId = currentUserId()
        val state = stateStore.issue(userId)
        val authUrl = oauthService.buildAuthUrl(state)
        return ResponseEntity.ok(StartResponse(authUrl = authUrl, state = state))
    }

    @Operation(
        summary = "OAuth callback (kalt av Googles redirect)",
        description = "Bytter `code` mot tokens, lagrer dem per bruker, og redirecter til deep-link slik at Android-appen vet at oppsettet er ferdig.",
    )
    @GetMapping("/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> {
        if (error != null) {
            log.info("OAuth callback received error from Google: {}", error)
            return redirect(deepLinkWithStatus(error))
        }
        if (state.isNullOrBlank()) {
            return redirect(deepLinkWithStatus("invalid_state"))
        }
        val userId = stateStore.consume(state)
        if (userId == null) {
            log.warn("OAuth callback rejected — unknown or replayed state")
            return redirect(deepLinkWithStatus("invalid_state"))
        }
        if (code.isNullOrBlank()) {
            return redirect(deepLinkWithStatus("missing_code"))
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
            redirect(deepLinkWithStatus("ok"))
        } catch (e: Exception) {
            log.error("Token exchange failed for userId={}: {}", userId, e.message, e)
            redirect(deepLinkWithStatus("token_exchange_failed"))
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

    private fun deepLinkWithStatus(status: String): URI {
        val separator = if (properties.deepLinkSuccessUri.contains('?')) '&' else '?'
        return URI.create("${properties.deepLinkSuccessUri}${separator}status=$status")
    }

    private fun redirect(location: URI): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location.toString()).build()

    data class StartResponse(val authUrl: String, val state: String)
    data class StatusResponse(val connected: Boolean, val email: String?, val timezone: String?)
}
