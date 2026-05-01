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
import no.josefus.abuhint.service.ChatService
import no.josefus.abuhint.service.TokenUsageContext
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@Tag(name = "Coach")
@RestController
@RequestMapping("/api/coach")
class CoachAssistantController(
    private val chatService: ChatService,
) {

    @Operation(
        summary = "Send melding til Coach-assistenten",
        description = """
            Sender en melding til Abu-hint i rollen som teamleder-coach og returnerer svaret.

            Samme underliggende modell (konfigurert i application.yml) og verktøysett som Chat-endepunktet,
            men med et coach-tilpasset systemsprompt som fokuserer på teamdynamikk, motivasjon og ledelse.

            Send med `chatId` for å fortsette en eksisterende samtale.
        """,
        parameters = [
            Parameter(
                name = "chatId",
                description = "Valgfri samtale-ID (UUID) for å opprettholde kontekst. Genereres automatisk hvis ikke oppgitt.",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                required = false,
            ),
        ],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ChatRequest::class),
                examples = [ExampleObject(
                    name = "Coach-forespørsel",
                    value = """{"message": "Teamet mitt sliter med kommunikasjon på tvers av avdelinger. Hva bør jeg gjøre?"}""",
                )],
            )],
        ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Vellykket svar fra coach-assistenten",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = ArraySchema(schema = Schema(implementation = OpenAiCompatibleContentItem::class)),
                    examples = [ExampleObject(
                        name = "Coach-råd",
                        value = """[{"type": "text", "text": "Et godt utgangspunkt er å etablere faste møtepunkter på tvers av avdelingene..."}]""",
                    )],
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
        val reply = chatService.processChat(
            sessionId,
            message.message,
            usageContext(sessionId, "COACH", clientPlatform),
        )
        val contentItems = listOf(OpenAiCompatibleContentItem(type = "text", text = reply))
        return ResponseEntity.ok(contentItems)
    }

    @Operation(
        summary = "Stream melding til Coach-assistenten (SSE)",
        description = "Streamer svaret fra coach-assistenten token for token via Server-Sent Events.",
    )
    @PostMapping("/chat/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chatStream(
        @RequestParam(required = false) chatId: String?,
        @RequestHeader(value = ChatController.CLIENT_PLATFORM_HEADER, required = false) clientPlatform: String? = null,
        @RequestBody message: ChatRequest,
    ): SseEmitter {
        val sessionId = chatId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        return chatService.processChatStream(
            sessionId,
            message.message,
            usageContext(sessionId, "COACH", clientPlatform),
        )
    }

    private fun usageContext(chatId: String, assistant: String, clientPlatform: String?) = TokenUsageContext(
        userId = SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("No authenticated user in SecurityContext"),
        chatId = chatId,
        assistant = assistant,
        clientPlatform = clientPlatform?.trim()?.takeIf { it.isNotBlank() } ?: "unknown",
    )
}
