package no.josefus.abuhint.configuration

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RequestLoggingFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        if (path.startsWith("/actuator")) {
            filterChain.doFilter(request, response)
            return
        }

        val start = System.currentTimeMillis()
        val chatId = request.getParameter("chatId") ?: "-"
        val method = request.method

        try {
            filterChain.doFilter(request, response)
        } finally {
            val latencyMs = System.currentTimeMillis() - start
            val status = response.status
            log.info("HTTP {} {} status={} chatId={} latencyMs={}", method, path, status, chatId, latencyMs)
        }
    }
}
