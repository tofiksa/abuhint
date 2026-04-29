package no.josefus.abuhint.familie

import java.time.Instant

/**
 * Abstraction around Google's OAuth 2.0 endpoints so the controller can be tested
 * without hitting the network. The real implementation uses `google-oauth-client`.
 */
interface GoogleOAuthService {

    /**
     * Builds a Google authorization URL with `access_type=offline`,
     * `include_granted_scopes=true`, and `prompt=consent` so we get a refresh
     * token back on the first consent and comply with Google's incremental
     * authorization guidance.
     */
    fun buildAuthUrl(state: String): String

    /**
     * Exchanges an authorization `code` for a [GoogleTokenExchangeResult].
     * The [state] is passed for logging/telemetry only; CSRF validation happens in the controller.
     */
    fun exchangeCodeForTokens(code: String, state: String): GoogleTokenExchangeResult
}

data class GoogleTokenExchangeResult(
    val refreshToken: String,
    val accessToken: String?,
    val accessTokenExpiresAt: Instant?,
    val scope: String,
    val email: String?,
)
