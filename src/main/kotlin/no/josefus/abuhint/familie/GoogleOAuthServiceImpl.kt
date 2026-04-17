package no.josefus.abuhint.familie

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Production [GoogleOAuthService] that delegates to Google's OAuth 2.0 endpoints
 * via the `google-oauth-client` library. Requires `familie.google.*` properties.
 */
@Service
class GoogleOAuthServiceImpl(
    private val properties: FamilieplanleggernProperties,
) : GoogleOAuthService {

    private val log = LoggerFactory.getLogger(GoogleOAuthServiceImpl::class.java)
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport: NetHttpTransport by lazy { GoogleNetHttpTransport.newTrustedTransport() }

    private val flow: GoogleAuthorizationCodeFlow by lazy {
        require(properties.isConfigured()) {
            "Google OAuth is not configured — set GOOGLE_CLIENT_ID/SECRET/REDIRECT_URI env vars"
        }
        GoogleAuthorizationCodeFlow.Builder(
            httpTransport,
            jsonFactory,
            properties.googleClientId,
            properties.googleClientSecret,
            properties.oauthScopes(),
        )
            .setAccessType("offline")
            .setApprovalPrompt("force")
            .build()
    }

    override fun buildAuthUrl(state: String): String =
        flow.newAuthorizationUrl()
            .setRedirectUri(properties.googleRedirectUri)
            .setState(state)
            .set("prompt", "consent")
            .build()

    override fun exchangeCodeForTokens(code: String, state: String): GoogleTokenExchangeResult {
        val tokenResponse = GoogleAuthorizationCodeTokenRequest(
            httpTransport,
            jsonFactory,
            properties.googleClientId,
            properties.googleClientSecret,
            code,
            properties.googleRedirectUri,
        ).execute()

        val refresh = tokenResponse.refreshToken
            ?: throw IllegalStateException("Google did not return a refresh_token — ensure prompt=consent and access_type=offline")

        val email = tokenResponse.parseIdToken()?.let(::extractEmail)
        val expiresAt = tokenResponse.expiresInSeconds?.let { Instant.now().plusSeconds(it) }

        return GoogleTokenExchangeResult(
            refreshToken = refresh,
            accessToken = tokenResponse.accessToken,
            accessTokenExpiresAt = expiresAt,
            scope = tokenResponse.scope ?: properties.oauthScopes().joinToString(" "),
            email = email,
        )
    }

    private fun extractEmail(idToken: GoogleIdToken): String? =
        try {
            idToken.payload.email
        } catch (e: Exception) {
            log.warn("Failed to read email from Google id_token: {}", e.message)
            null
        }
}
