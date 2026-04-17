package no.josefus.abuhint.familie

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Short-lived mapping between a random OAuth state parameter and the authenticated
 * user that initiated the flow. Used to defend the `/callback` endpoint against CSRF
 * and to recover the caller's identity after the browser-hop to Google (where the
 * JWT is not present).
 */
@Component
class InMemoryOAuthStateStore {

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .maximumSize(10_000)
        .build<String, String>()

    private val random = SecureRandom()

    fun issue(userId: String): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        cache.put(state, userId)
        return state
    }

    /**
     * Returns the `userId` associated with [state] and atomically invalidates it,
     * preventing replay. Returns `null` if the state is unknown or expired.
     */
    fun consume(state: String): String? {
        val userId = cache.getIfPresent(state) ?: return null
        cache.invalidate(state)
        return userId
    }
}
