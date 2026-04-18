package no.josefus.abuhint.familie

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import no.josefus.abuhint.dto.ChatHistoryMessage
import no.josefus.abuhint.dto.ChatHistoryResponse
import no.josefus.abuhint.dto.OpenAiCompatibleContentItem
import no.josefus.abuhint.service.ChatMessageUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@Tag(
    name = "Familieplanleggern",
    description = "Chat-endepunkter for Familieplanleggern-agenten. Krever gyldig JWT og at brukeren har koblet til Google via /api/google/oauth.",
)
@RestController
@RequestMapping("/api/familie")
class FamilieController(
    private val chatService: FamilieChatService,
    private val credentialStore: UserGoogleCredentialStore,
) {

    @Operation(
        summary = "Send melding til Familieplanleggern",
        description = """
            Sender en melding til Familieplanleggern-agenten og returnerer svaret. Agenten har tilgang til brukerens Google Calendar
            via propose → confirm-verktøy (listCalendars, listUpcomingEvents, proposeCreateEvent/confirmCreateEvent,
            proposeCreateCalendar/confirmCreateCalendar, proposeDeleteEvent/confirmDeleteEvent).

            Kall `GET /api/google/oauth/status` først for å sjekke at brukeren er tilkoblet Google.
        """,
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = FamilieMessageRequest::class),
                examples = [ExampleObject(
                    name = "Enkel melding",
                    value = """{"message": "Legg til tannlege for Mia på fredag 09:00"}""",
                )],
            )],
        ),
    )
    @PostMapping(value = ["/send"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun sendMessage(
        @RequestParam(required = false) chatId: String?,
        @RequestBody request: FamilieMessageRequest,
    ): ResponseEntity<List<OpenAiCompatibleContentItem>> {
        val preconditionError = requireGoogleConnected()
        if (preconditionError != null) return preconditionError
        val sessionId = chatId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val userId = currentUserId()
        val reply = chatService.processChat(sessionId, request.message, userId, request.metadata)
        return ResponseEntity.ok(listOf(OpenAiCompatibleContentItem(type = "text", text = reply)))
    }

    @Operation(
        summary = "Stream melding til Familieplanleggern (SSE)",
        description = "Streamer agentens svar token for token via Server-Sent Events.",
    )
    @PostMapping(value = ["/stream"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamMessage(
        @RequestParam(required = false) chatId: String?,
        @RequestBody request: FamilieMessageRequest,
    ): Any {
        val preconditionError = requireGoogleConnected()
        if (preconditionError != null) return preconditionError
        val sessionId = chatId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val userId = currentUserId()
        return chatService.processChatStream(sessionId, request.message, userId, request.metadata)
    }

    @Operation(summary = "Hent samtalehistorikk for Familieplanleggern")
    @GetMapping("/{chatId}/history", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getHistory(
        @PathVariable chatId: String,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): ResponseEntity<ChatHistoryResponse> {
        val all = chatService.getChatHistory(chatId)
        val paged = all.drop(offset).take(limit).map { message ->
            val role = when (message) {
                is UserMessage -> "USER"
                is AiMessage -> "AI"
                is SystemMessage -> "SYSTEM"
                else -> "UNKNOWN"
            }
            ChatHistoryMessage(role = role, content = ChatMessageUtils.getMessageText(message))
        }
        return ResponseEntity.ok(
            ChatHistoryResponse(
                chatId = chatId,
                messages = paged,
                total = all.size,
                offset = offset,
                limit = limit,
            )
        )
    }

    private fun currentUserId(): String =
        SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("No authenticated user in SecurityContext")

    /**
     * Guard: the Familieplanleggern chat endpoints are useless without a Google connection,
     * so we return HTTP 412 Precondition Failed up front instead of letting the LLM flounder.
     */
    private fun requireGoogleConnected(): ResponseEntity<List<OpenAiCompatibleContentItem>>? {
        val credentials = credentialStore.load(currentUserId())
        if (credentials != null) return null
        val body = listOf(
            OpenAiCompatibleContentItem(
                type = "text",
                text = "Du må koble til Google-kontoen din før Familieplanleggern kan hjelpe med kalender. Gå til Familieplanleggern → Koble til Google i appen.",
            )
        )
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(body)
    }
}
