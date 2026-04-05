package no.josefus.abuhint.configuration

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Extracts and validates a Bearer JWT from the Authorization header.
 * On success, populates the SecurityContext with the token's subject and roles.
 * No local user database is needed – we trust claims from josefus-highscore.
 */
@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val jwt = authHeader.substring(7)

        try {
            if (jwtService.isTokenValid(jwt) && SecurityContextHolder.getContext().authentication == null) {
                val username = jwtService.extractUsername(jwt)
                val roles = jwtService.extractRoles(jwt)
                val authorities = roles.map { SimpleGrantedAuthority(it) }

                val authToken = UsernamePasswordAuthenticationToken(username, null, authorities)
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authToken
            }
        } catch (ex: Exception) {
            log.warn("JWT authentication failed: {}", ex.message)
        }

        filterChain.doFilter(request, response)
    }
}
