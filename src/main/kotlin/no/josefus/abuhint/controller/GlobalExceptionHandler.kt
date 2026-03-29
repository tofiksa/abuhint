package no.josefus.abuhint.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import java.util.concurrent.TimeoutException

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    data class ErrorResponse(val error: String, val code: String)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.warn("Bad request: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(error = ex.message ?: "Invalid request", code = "BAD_REQUEST"))
    }

    @ExceptionHandler(HttpClientErrorException.TooManyRequests::class)
    fun handleRateLimit(ex: HttpClientErrorException.TooManyRequests): ResponseEntity<ErrorResponse> {
        log.warn("Upstream rate limit hit: {}", ex.message)
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse(error = "Tjenesten er midlertidig overbelastet. Prøv igjen om litt.", code = "RATE_LIMITED"))
    }

    @ExceptionHandler(HttpClientErrorException::class)
    fun handleHttpClientError(ex: HttpClientErrorException): ResponseEntity<ErrorResponse> {
        log.error("Upstream HTTP error: {} {}", ex.statusCode, ex.message)
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(error = "En ekstern tjeneste returnerte en feil. Prøv igjen.", code = "UPSTREAM_ERROR"))
    }

    @ExceptionHandler(TimeoutException::class)
    fun handleTimeout(ex: TimeoutException): ResponseEntity<ErrorResponse> {
        log.error("Request timed out: {}", ex.message)
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
            .body(ErrorResponse(error = "Forespørselen tok for lang tid. Prøv igjen.", code = "TIMEOUT"))
    }

    @ExceptionHandler(ResourceAccessException::class)
    fun handleResourceAccess(ex: ResourceAccessException): ResponseEntity<ErrorResponse> {
        log.error("Resource access error (network/timeout): {}", ex.message)
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
            .body(ErrorResponse(error = "Kunne ikke nå en ekstern tjeneste. Prøv igjen.", code = "CONNECTION_ERROR"))
    }

    @ExceptionHandler(RestClientException::class)
    fun handleRestClientError(ex: RestClientException): ResponseEntity<ErrorResponse> {
        log.error("REST client error: {}", ex.message, ex)
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(error = "En feil oppstod mot en ekstern tjeneste.", code = "REST_CLIENT_ERROR"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception: {}", ex.message, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(error = "En uventet feil oppstod. Prøv igjen senere.", code = "INTERNAL_ERROR"))
    }
}
