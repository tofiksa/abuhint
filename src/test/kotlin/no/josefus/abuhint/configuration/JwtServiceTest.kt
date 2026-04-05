package no.josefus.abuhint.configuration

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwtServiceTest {

    // 64-byte Base64 secret (produces HS512)
    private val testSecret: String = Base64.getEncoder().encodeToString(
        "this-is-a-test-secret-key-that-is-at-least-32-bytes-long!!12345".toByteArray()
    )

    private val jwtService = JwtService(testSecret)

    private fun buildToken(
        subject: String = "testuser",
        roles: List<String> = listOf("ROLE_USER"),
        email: String = "test@example.com",
        name: String = "Test User",
        expiration: Long = 3_600_000,
        secret: String = testSecret
    ): String {
        val keyBytes = Decoders.BASE64.decode(secret)
        val key = Keys.hmacShaKeyFor(keyBytes)
        return Jwts.builder()
            .header().add("typ", "JWT").and()
            .subject(subject)
            .claim("roles", roles)
            .claim("email", email)
            .claim("name", name)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(key)
            .compact()
    }

    @Test
    fun `isTokenValid returns true for a valid token`() {
        val token = buildToken()
        assertTrue(jwtService.isTokenValid(token))
    }

    @Test
    fun `extractUsername returns the subject claim`() {
        val token = buildToken(subject = "johndoe")
        assertEquals("johndoe", jwtService.extractUsername(token))
    }

    @Test
    fun `extractRoles returns the roles claim`() {
        val token = buildToken(roles = listOf("ROLE_USER", "ROLE_ADMIN"))
        assertEquals(listOf("ROLE_USER", "ROLE_ADMIN"), jwtService.extractRoles(token))
    }

    @Test
    fun `isTokenValid returns false for an expired token`() {
        val token = buildToken(expiration = -1_000) // already expired
        assertFalse(jwtService.isTokenValid(token))
    }

    @Test
    fun `isTokenValid returns false for a token signed with a different secret`() {
        val otherSecret = Base64.getEncoder().encodeToString(
            "another-secret-key-that-is-also-at-least-32-bytes-long!!6789".toByteArray()
        )
        val token = buildToken(secret = otherSecret)
        assertFalse(jwtService.isTokenValid(token))
    }

    @Test
    fun `isTokenValid returns false for garbage input`() {
        assertFalse(jwtService.isTokenValid("not.a.jwt"))
    }

    @Test
    fun `extractUsername throws for an invalid token`() {
        assertThrows<Exception> {
            jwtService.extractUsername("invalid-token")
        }
    }
}
