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
}
