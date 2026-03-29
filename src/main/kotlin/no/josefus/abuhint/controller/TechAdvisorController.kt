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
import no.josefus.abuhint.dto.OpenAiCompatibleChatMessage
import no.josefus.abuhint.dto.OpenAiCompatibleContentItem
import no.josefus.abuhint.service.ChatService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@Tag(name = "Tech Advisor")
@RestController
@RequestMapping("/api/tech-advisor")
class TechAdvisorController(
    private val chatService: ChatService,
) {

    @Operation(
        summary = "Send melding til teknisk rådgiver",
        description = """
            Sender en melding til Abdikverrulant – en teknisk rådgiver drevet av Google Gemini 2.5 Flash.

            Spesialisert på tekniske beslutninger, systemarkitektur, teknologivalg og kodepraksis.
            Har tilgang til **nettsøk** (via Tavily) for å hente oppdatert informasjon om teknologier og beste praksis.

            Egner seg for spørsmål som:
            - Arkitekturvalg og designmønstre
            - Sammenligning av rammeverk eller biblioteker
            - Kodegjennomgang og refaktoreringsforslag
            - Oppdatert informasjon om verktøy og teknologier

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
                    name = "Arkitekturspørsmål",
                    value = """{"message": "Hva er forskjellen mellom event-driven og request-response arkitektur, og når bør jeg velge hva?"}""",
                )],
            )],
        ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Vellykket svar fra teknisk rådgiver",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = ArraySchema(schema = Schema(implementation = OpenAiCompatibleContentItem::class)),
                    examples = [ExampleObject(
                        name = "Teknisk råd",
                        value = """[{"type": "text", "text": "Event-driven arkitektur egner seg best når du trenger løs kobling og høy skalerbarhet..."}]""",
                    )],
                )],
            ),
        ],
    )
    @PostMapping("/chat", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun chat(
        @RequestParam(required = false) chatId: String?,
        @RequestBody message: ChatRequest,
    ): ResponseEntity<List<OpenAiCompatibleContentItem>> {
        val sessionId = chatId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val message = chatService.processGeminiChat(sessionId, message.message)
        val contentItems = List(1) {
            OpenAiCompatibleContentItem(
                type = "text",
                text = message,
            )
        }

        OpenAiCompatibleChatMessage(
            role = "assistant",
            content = contentItems,
        )
        return ResponseEntity.ok(contentItems)
    }

    @Operation(
        summary = "Stream melding til teknisk rådgiver (SSE)",
        description = "Streamer svaret fra Abdikverrulant token for token via Server-Sent Events.",
    )
    @PostMapping("/chat/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chatStream(
        @RequestParam(required = false) chatId: String?,
        @RequestBody message: ChatRequest,
    ): SseEmitter {
        val sessionId = chatId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        return chatService.processGeminiChatStream(sessionId, message.message)
    }
}
