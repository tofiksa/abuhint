package no.josefus.abuhint.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import no.josefus.abuhint.dto.ChatHistoryMessage
import no.josefus.abuhint.dto.ChatHistoryResponse
import no.josefus.abuhint.dto.OpenAiCompatibleContentItem
import no.josefus.abuhint.dto.TokenUsageResponse
import no.josefus.abuhint.service.ChatService
import no.josefus.abuhint.service.TokenUsageStore
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@Tag(name = "Chat")
@RestController
@RequestMapping("/api/chat")
class ChatController(
    private val chatService: ChatService,
    private val tokenUsageStore: TokenUsageStore,
) {

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
        val sessionId = chatId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val reply = chatService.processChat(sessionId, message.message)
        val contentItems = listOf(OpenAiCompatibleContentItem(type = "text", text = reply))
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

    @Operation(
        summary = "Hent samtalehistorikk",
        description = "Henter meldingshistorikk for en gitt samtale-ID med paginering.",
    )
    @GetMapping("/{chatId}/history", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getChatHistory(
        @PathVariable chatId: String,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): ResponseEntity<ChatHistoryResponse> {
        val allMessages = chatService.getChatHistory(chatId)
        val paged = allMessages.drop(offset).take(limit)
        val historyMessages = paged.map { message ->
            val role = when (message) {
                is UserMessage -> "USER"
                is AiMessage -> "AI"
                is SystemMessage -> "SYSTEM"
                else -> "UNKNOWN"
            }
            ChatHistoryMessage(role = role, content = no.josefus.abuhint.service.ChatMessageUtils.getMessageText(message))
        }
        return ResponseEntity.ok(ChatHistoryResponse(
            chatId = chatId,
            messages = historyMessages,
            total = allMessages.size,
            offset = offset,
            limit = limit,
        ))
    }

    @Operation(
        summary = "Hent tokenforbruk for en samtale",
        description = "Returnerer akkumulert tokenforbruk (input, cached, output) for en gitt samtale-ID.",
        parameters = [
            Parameter(
                name = "chatId",
                description = "Samtale-ID (UUID) å hente tokenforbruk for",
                example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                required = true,
            ),
        ],
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Tokenforbruk for samtalen",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = TokenUsageResponse::class),
                    examples = [ExampleObject(
                        name = "Eksempel",
                        value = """{"chatId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","inputTokens":2048,"cachedInputTokens":1024,"outputTokens":512,"totalTokens":2560,"requestCount":3,"modelName":"gpt-4.1-mini"}""",
                    )],
                )],
            ),
            ApiResponse(responseCode = "404", description = "Ingen tokenforbruk funnet for denne samtale-IDen"),
        ],
    )
    @GetMapping("/usage/{chatId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getTokenUsage(@PathVariable chatId: String): ResponseEntity<TokenUsageResponse> {
        val usage = tokenUsageStore.getUsage(chatId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(usage.toResponse())
    }

    @Operation(
        summary = "Hent tokenforbruk for alle samtaler",
        description = "Returnerer akkumulert tokenforbruk for alle aktive samtale-IDer i minnet.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Liste over tokenforbruk for alle samtaler",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = ArraySchema(schema = Schema(implementation = TokenUsageResponse::class)),
                )],
            ),
        ],
    )
    @GetMapping("/usage", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAllTokenUsage(): ResponseEntity<List<TokenUsageResponse>> {
        val all = tokenUsageStore.getAllUsage().values.map { it.toResponse() }
        return ResponseEntity.ok(all)
    }

    private fun no.josefus.abuhint.service.TokenUsageRecord.toResponse() = TokenUsageResponse(
        chatId = chatId,
        inputTokens = inputTokens.get(),
        cachedInputTokens = cachedInputTokens.get(),
        outputTokens = outputTokens.get(),
        totalTokens = inputTokens.get() + outputTokens.get(),
        requestCount = requestCount.get(),
        modelName = lastModelName,
    )

    @Schema(description = "Forespørsel med brukerens melding")
    data class MessageRequest(
        @field:Schema(description = "Meldingsteksten som sendes til Abu-hint", example = "Hva er de viktigste prinsippene for god teamledelse?")
        val message: String,
    )
}
