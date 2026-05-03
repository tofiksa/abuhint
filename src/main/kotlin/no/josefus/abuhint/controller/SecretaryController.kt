package no.josefus.abuhint.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import no.josefus.abuhint.dto.ChatRequest
import no.josefus.abuhint.dto.OpenAiCompatibleContentItem
import no.josefus.abuhint.repository.SecretaryAssistant
import no.josefus.abuhint.service.ChatIdContextHolder
import no.josefus.abuhint.service.TokenUsageContext
import no.josefus.abuhint.service.TokenUsageContextHolder
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Tag(name = "Secretary")
@RestController
@RequestMapping("/api/secretary")
class SecretaryController(
    private val secretaryAssistant: SecretaryAssistant,
) {

    private val log = org.slf4j.LoggerFactory.getLogger(SecretaryController::class.java)

    @Operation(
        summary = "Send melding til sekretær-assistenten",
        description = """
            Sender en melding til sekretær-agenten som planlegger oppgaver og delegerer til arbeidere.
            Send med chatId for å fortsette en eksisterende samtale.
        """,
        parameters = [
            Parameter(
                name = "chatId",
                description = "Valgfri samtale-ID (UUID) for å opprettholde kontekst.",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                required = false,
            ),
        ],
        responses = [
            ApiResponse(
                responseCode = "200",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = ArraySchema(schema = Schema(implementation = OpenAiCompatibleContentItem::class)),
                )],
            ),
        ],
    )
    @PostMapping("/chat", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun chat(
        @RequestParam(required = false) chatId: String?,
        @RequestHeader(value = ChatController.CLIENT_PLATFORM_HEADER, required = false) clientPlatform: String? = null,
        @RequestBody message: ChatRequest,
    ): ResponseEntity<List<OpenAiCompatibleContentItem>> {
        val sessionId = chatId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val uuid = UUID.randomUUID().toString()
        val dateTime = LocalDateTime.now().toString()
        val ctx = usageContext(sessionId, clientPlatform)

        ChatIdContextHolder.set(sessionId)
        TokenUsageContextHolder.set(ctx)
        val reply = try {
            secretaryAssistant.chat(sessionId, message.message, uuid, dateTime)
        } finally {
            ChatIdContextHolder.clear()
            TokenUsageContextHolder.clear()
        }

        val contentItems = listOf(OpenAiCompatibleContentItem(type = "text", text = reply))
        return ResponseEntity.ok(contentItems)
    }

    @Operation(
        summary = "Stream melding til sekretær-assistenten (SSE)",
        description = "Streamer svaret fra sekretær-agenten token for token via Server-Sent Events.",
    )
    @PostMapping("/chat/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chatStream(
        @RequestParam(required = false) chatId: String?,
        @RequestHeader(value = ChatController.CLIENT_PLATFORM_HEADER, required = false) clientPlatform: String? = null,
        @RequestBody message: ChatRequest,
    ): SseEmitter {
        val sessionId = chatId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val uuid = UUID.randomUUID().toString()
        val dateTime = LocalDateTime.now().toString()
        val ctx = usageContext(sessionId, clientPlatform)

        val emitter = SseEmitter(120_000L)
        val cleanedUp = AtomicBoolean(false)

        fun cleanup() {
            if (!cleanedUp.compareAndSet(false, true)) return
            ChatIdContextHolder.clear()
            TokenUsageContextHolder.clear()
        }

        ChatIdContextHolder.set(sessionId)
        TokenUsageContextHolder.set(ctx)

        val tokenStream = try {
            secretaryAssistant.chatStream(sessionId, message.message, uuid, dateTime)
        } catch (e: Exception) {
            cleanup()
            throw e
        }

        tokenStream
            .onPartialResponse { token ->
                try {
                    emitter.send(SseEmitter.event().data(token))
                } catch (e: Exception) {
                    log.debug("Client disconnected during streaming: ${e.message}")
                }
            }
            .onCompleteResponse { _ ->
                try {
                    emitter.send(SseEmitter.event().data("[DONE]"))
                    emitter.complete()
                } catch (e: Exception) {
                    log.debug("Error completing SSE stream: ${e.message}")
                } finally {
                    cleanup()
                }
            }
            .onError { error ->
                log.error("Secretary streaming error: ${error.message}", error)
                try {
                    emitter.send(SseEmitter.event().data("{\"error\": \"${error.message}\"}"))
                    emitter.completeWithError(error)
                } catch (e: Exception) {
                    log.debug("Error sending error event: ${e.message}")
                } finally {
                    cleanup()
                }
            }

        try {
            tokenStream.start()
        } catch (e: Exception) {
            cleanup()
            throw e
        }

        emitter.onCompletion { cleanup() }
        emitter.onTimeout {
            log.warn("Secretary SSE stream timed out for chatId=$sessionId")
            cleanup()
        }
        emitter.onError {
            log.debug("Secretary SSE emitter error for chatId=$sessionId: ${it.message}")
            cleanup()
        }

        return emitter
    }

    private fun usageContext(chatId: String, clientPlatform: String?) = TokenUsageContext(
        userId = SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("No authenticated user in SecurityContext"),
        chatId = chatId,
        assistant = "SECRETARY",
        clientPlatform = clientPlatform?.trim()?.takeIf { it.isNotBlank() } ?: "unknown",
    )
}
