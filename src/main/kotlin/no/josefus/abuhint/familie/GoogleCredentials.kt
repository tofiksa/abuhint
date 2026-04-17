package no.josefus.abuhint.familie

import java.time.Instant

/**
 * Plaintext projection of a user's Google OAuth credentials. Only lives in memory —
 * persistence goes through [UserGoogleCredentialStore] which encrypts the sensitive
 * fields via [TokenCipher] before writing to the database.
 */
data class GoogleCredentials(
    val userId: String,
    val refreshToken: String,
    val accessToken: String?,
    val accessTokenExpiresAt: Instant?,
    val scope: String,
    val email: String?,
    val timezone: String?,
)
