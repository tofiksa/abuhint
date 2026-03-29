package no.josefus.abuhint.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import no.josefus.abuhint.dto.OpenAiCompatibleChatMessage
import no.josefus.abuhint.dto.OpenAiCompatibleContentItem
import no.josefus.abuhint.service.ChatService
import no.josefus.abuhint.service.ScoreService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@Tag(name = "Chat")
@RestController
@RequestMapping("/api/chat")
class ChatController(private val chatService: ChatService, private val scoreService: ScoreService) {

    @Operation(
        summary = "Send melding til Abu-hint",
        description = """
            Sender en melding til Abu-hint og returnerer svaret som en liste med innholdselementer.

            Abu-hint er en AI-drevet teamleder-coach drevet av OpenAI `gpt-4.1-mini` med tilgang til følgende verktøy:
            - **sendEmail** – sender e-post via Resend API
            - **generatePresentation** – genererer PowerPoint og laster opp til S3
            - **generateAndEmail** – genererer PowerPoint og sender som e-postvedlegg
            - **searchWeb** – utfører nettsøk via Tavily
            - **createPullRequest**, **createBranchAndCommit**, **getBranch**, **pushToMain** – GitHub-operasjoner

            Send med `chatId` for å fortsette en eksisterende samtale. Utelates `chatId` starter en ny økt automatisk.
        """,
        parameters = [
            Parameter(
                name = "chatId",
                description = "Valgfri samtale-ID (UUID) for å opprettholde kontekst på tvers av forespørsler. Genereres automatisk hvis ikke oppgitt.",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                required = false,
            ),
            Parameter(
                name = "credentials",
                description = "Valgfri spillkredential for poengsystemintegrasjon.",
                required = false,
            ),
        ],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = MessageRequest::class),
                examples = [ExampleObject(
                    name = "Enkel melding",
                    value = """{"message": "Hva er de viktigste prinsippene for god teamledelse?"}""",
                )],
            )],
        ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Vellykket svar fra Abu-hint",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = ArraySchema(schema = Schema(implementation = OpenAiCompatibleContentItem::class)),
                    examples = [ExampleObject(
                        name = "Tekstsvar",
                        value = """[{"type": "text", "text": "God teamledelse handler om tydelig kommunikasjon, tillit og å gi teammedlemmer autonomi..."}]""",
                    )],
                )],
            ),
        ],
    )
    @PostMapping(value = ["/send"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun sendMessage(
        @RequestParam(required = false) chatId: String?,
        @RequestParam(required = false) credentials: String?,
        @RequestBody message: MessageRequest,
    ): ResponseEntity<List<OpenAiCompatibleContentItem>> {
        val gameId = scoreService.fetchAndReturnGameId(credentials)

        val sessionId = chatId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val message = chatService.processChat(sessionId, message.message)
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
        summary = "Stream melding til Abu-hint (SSE)",
        description = "Streamer svaret fra Abu-hint token for token via Server-Sent Events. Kompatibel med web- og Android-klienter.",
    )
    @PostMapping(value = ["/stream"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamMessage(
        @RequestParam(required = false) chatId: String?,
        @RequestBody message: MessageRequest,
    ): SseEmitter {
        val sessionId = chatId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        return chatService.processChatStream(sessionId, message.message)
    }

    @Schema(description = "Forespørsel med brukerens melding")
    data class MessageRequest(
        @field:Schema(description = "Meldingsteksten som sendes til Abu-hint", example = "Hva er de viktigste prinsippene for god teamledelse?")
        val message: String,
    )
}
