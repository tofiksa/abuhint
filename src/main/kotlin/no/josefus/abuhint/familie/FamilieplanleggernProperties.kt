package no.josefus.abuhint.familie

/**
 * Configuration for the Familieplanleggern agent and its Google Calendar integration.
 * Populated from application.yml / environment variables via [FamilieplanleggernConfiguration].
 */
data class FamilieplanleggernProperties(
    val googleClientId: String,
    val googleClientSecret: String,
    val googleRedirectUri: String,
    val defaultTimezone: String,
    val tokenEncryptionKeyBase64: String,
    val deepLinkSuccessUri: String = "familieplanleggern://oauth/done",
    /**
     * Final browser redirect after the backend `/api/google/oauth/callback` when the flow
     * was started with `client=web`. Must match a route in the SPA (e.g.
     * `https://app.example/dashboard/familie/oauth/callback`). Google still redirects
     * to [googleRedirectUri] only; this URI is used for the second hop back to the webapp.
     */
    val webOAuthSuccessUri: String = "",
) {
    fun isConfigured(): Boolean =
        googleClientId.isNotBlank() &&
            googleClientSecret.isNotBlank() &&
            googleRedirectUri.isNotBlank() &&
            tokenEncryptionKeyBase64.isNotBlank()

    fun oauthScopes(): List<String> = listOf(
        "https://www.googleapis.com/auth/calendar",
        "https://www.googleapis.com/auth/calendar.events",
        "openid",
        "email",
    )

    fun requiredCalendarScopes(): List<String> = listOf(
        "https://www.googleapis.com/auth/calendar",
        "https://www.googleapis.com/auth/calendar.events",
    )
}
