package no.josefus.abuhint.familie

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import java.time.Instant
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest
@Import(JpaUserGoogleCredentialStoreTest.Config::class)
class JpaUserGoogleCredentialStoreTest {

    @Autowired
    lateinit var repository: UserGoogleCredentialRepository

    private val cipher = TokenCipher(Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }))

    private fun store() = JpaUserGoogleCredentialStore(repository, cipher)

    @Test
    fun `load returns null when user has no stored credentials`() {
        assertNull(store().load("unknown-user"))
    }

    @Test
    fun `save then load returns decrypted tokens for same user`() {
        val userId = "user-123"
        val saved = GoogleCredentials(
            userId = userId,
            refreshToken = "refresh-abc",
            accessToken = "access-xyz",
            accessTokenExpiresAt = Instant.parse("2026-05-01T10:00:00Z"),
            scope = "https://www.googleapis.com/auth/calendar",
            email = "user@example.com",
            timezone = "Europe/Oslo",
        )

        store().save(saved)
        val loaded = store().load(userId)

        assertNotNull(loaded)
        assertEquals("refresh-abc", loaded.refreshToken)
        assertEquals("access-xyz", loaded.accessToken)
        assertEquals("user@example.com", loaded.email)
        assertEquals("Europe/Oslo", loaded.timezone)
    }

    @Test
    fun `save twice for same user updates tokens rather than duplicating row`() {
        val userId = "user-456"
        store().save(GoogleCredentials(userId, "rt1", "at1", Instant.EPOCH, "scope", "a@b.c", "UTC"))
        store().save(GoogleCredentials(userId, "rt2", "at2", Instant.EPOCH, "scope", "a@b.c", "UTC"))

        val loaded = store().load(userId)
        assertEquals("rt2", loaded?.refreshToken)
        assertEquals(1, repository.count())
    }

    @Test
    fun `delete removes stored credentials`() {
        val userId = "user-789"
        store().save(GoogleCredentials(userId, "rt", "at", Instant.EPOCH, "scope", "x@y.z", "UTC"))
        store().delete(userId)
        assertNull(store().load(userId))
    }

    @Test
    fun `credentials from different users are isolated`() {
        store().save(GoogleCredentials("alice", "alice-rt", "alice-at", Instant.EPOCH, "scope", "a@x", "UTC"))
        store().save(GoogleCredentials("bob", "bob-rt", "bob-at", Instant.EPOCH, "scope", "b@x", "UTC"))

        assertEquals("alice-rt", store().load("alice")?.refreshToken)
        assertEquals("bob-rt", store().load("bob")?.refreshToken)
    }

    @Test
    fun `raw refresh token is never stored in plaintext in the database`() {
        val userId = "user-secret"
        val refresh = "SUPER-SECRET-REFRESH-TOKEN"
        store().save(GoogleCredentials(userId, refresh, "at", Instant.EPOCH, "scope", "e@x", "UTC"))

        val row = repository.findById(userId).orElseThrow()
        assertTrue(refresh !in row.encryptedRefreshToken, "Refresh token must be encrypted in DB")
        assertTrue(refresh !in (row.encryptedAccessToken ?: ""), "Access token field must not leak plaintext")
    }

    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = [UserGoogleCredentialRepository::class])
    @EntityScan(basePackageClasses = [UserGoogleCredentialEntity::class])
    class Config
}
