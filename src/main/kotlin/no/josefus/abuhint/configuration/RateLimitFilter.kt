package no.josefus.abuhint.configuration

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimitFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)

    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        if (!path.startsWith("/api/") && !path.startsWith("/v1/chat/")) {
            filterChain.doFilter(request, response)
            return
        }

        val key = resolveKey(request)
        val bucket = buckets.computeIfAbsent(key) { createBucket() }

        val probe = bucket.tryConsumeAndReturnRemaining(1)
        if (probe.isConsumed) {
            response.setHeader("X-Rate-Limit-Remaining", probe.remainingTokens.toString())
            filterChain.doFilter(request, response)
        } else {
            val retryAfterSeconds = (probe.nanosToWaitForRefill / 1_000_000_000) + 1
            log.warn("Rate limit exceeded for key=$key on $path")
            response.status = 429
            response.setHeader("Retry-After", retryAfterSeconds.toString())
            response.contentType = "application/json"
            response.writer.write("""{"error":"For mange forespørsler. Prøv igjen om ${retryAfterSeconds}s.","code":"RATE_LIMITED"}""")
        }
    }

    private fun resolveKey(request: HttpServletRequest): String {
        val chatId = request.getParameter("chatId")
        if (!chatId.isNullOrBlank()) return "chat:$chatId"
        return "ip:${request.remoteAddr}"
    }

    private fun createBucket(): Bucket {
        return Bucket.builder()
            .addLimit(Bandwidth.simple(10, Duration.ofMinutes(1)))
            .addLimit(Bandwidth.simple(100, Duration.ofHours(1)))
            .build()
    }
}
