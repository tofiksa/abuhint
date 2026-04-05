package no.josefus.abuhint.configuration

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import javax.crypto.SecretKey
import java.util.Date
import java.util.function.Function

/**
 * Read-only JWT service that validates tokens issued by josefus-highscore.
 * Uses the same HMAC secret so signatures match.
 */
@Service
class JwtService(
    @Value("\${spring.jwt.secret}") private val secretKey: String
) {

    fun extractUsername(token: String): String =
        extractClaim(token, Claims::getSubject)

    fun <T> extractClaim(token: String, resolver: Function<Claims, T>): T {
        val claims = extractAllClaims(token)
        return resolver.apply(claims)
    }

    fun extractRoles(token: String): List<String> {
        val claims = extractAllClaims(token)
        @Suppress("UNCHECKED_CAST")
        return claims["roles"] as? List<String> ?: emptyList()
    }

    fun isTokenValid(token: String): Boolean =
        try {
            extractAllClaims(token)
            !isTokenExpired(token)
        } catch (_: Exception) {
            false
        }

    private fun isTokenExpired(token: String): Boolean =
        extractClaim(token, Claims::getExpiration).before(Date())

    private fun extractAllClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(getSignInKey())
            .build()
            .parseSignedClaims(token)
            .payload

    private fun getSignInKey(): SecretKey {
        val keyBytes = Decoders.BASE64.decode(secretKey)
        return Keys.hmacShaKeyFor(keyBytes)
    }
}
