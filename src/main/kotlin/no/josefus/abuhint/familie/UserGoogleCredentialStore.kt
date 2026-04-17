package no.josefus.abuhint.familie

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Per-user persistent store for Google OAuth credentials.
 * Implementations are responsible for encrypting sensitive fields at rest.
 */
interface UserGoogleCredentialStore {
    fun save(credentials: GoogleCredentials)
    fun load(userId: String): GoogleCredentials?
    fun delete(userId: String)
}

/**
 * JPA-backed [UserGoogleCredentialStore] that encrypts access and refresh tokens
 * via [TokenCipher] before writing. Rows are keyed on the JWT subject ([GoogleCredentials.userId])
 * so each user sees only their own credentials.
 */
@Service
class JpaUserGoogleCredentialStore(
    private val repository: UserGoogleCredentialRepository,
    private val tokenCipher: TokenCipher,
) : UserGoogleCredentialStore {

    private val log = LoggerFactory.getLogger(JpaUserGoogleCredentialStore::class.java)

    override fun save(credentials: GoogleCredentials) {
        val entity = repository.findById(credentials.userId).orElseGet {
            UserGoogleCredentialEntity(userId = credentials.userId)
        }
        entity.encryptedRefreshToken = tokenCipher.encrypt(credentials.refreshToken)
        entity.encryptedAccessToken = credentials.accessToken?.let(tokenCipher::encrypt)
        entity.accessTokenExpiresAt = credentials.accessTokenExpiresAt
        entity.scope = credentials.scope
        entity.email = credentials.email
        entity.timezone = credentials.timezone
        entity.updatedAt = Instant.now()
        repository.save(entity)
    }

    override fun load(userId: String): GoogleCredentials? {
        val entity = repository.findById(userId).orElse(null) ?: return null
        val refresh = tokenCipher.decrypt(entity.encryptedRefreshToken)
        if (refresh == null) {
            log.warn("Unable to decrypt refresh token for userId={} — key mismatch or corrupt data", userId)
            return null
        }
        val access = entity.encryptedAccessToken?.let(tokenCipher::decrypt)
        return GoogleCredentials(
            userId = entity.userId,
            refreshToken = refresh,
            accessToken = access,
            accessTokenExpiresAt = entity.accessTokenExpiresAt,
            scope = entity.scope,
            email = entity.email,
            timezone = entity.timezone,
        )
    }

    override fun delete(userId: String) {
        repository.deleteById(userId)
    }
}
