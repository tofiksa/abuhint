package no.josefus.abuhint.controller

import no.josefus.abuhint.dto.OpenAiCompatibleChatMessage
import no.josefus.abuhint.dto.OpenAiCompatibleContentItem
import no.josefus.abuhint.service.ChatService
import no.josefus.abuhint.service.ScoreService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/chat")
class ChatController(private val chatService: ChatService, private val scoreService: ScoreService) {


    @PostMapping(value = ["/send"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun sendMessage(
        @RequestParam(required = false) chatId: String,
        @RequestParam(required = false) credentials : String?,
        @RequestBody message: MessageRequest
    ): ResponseEntity<List<OpenAiCompatibleContentItem>> {
        val gameId = scoreService.fetchAndReturnGameId(credentials)

        val message = chatService.processChat(chatId, message.message)
        val contentItems = List(1) {
            OpenAiCompatibleContentItem(
                type = "text",
                text = message,
            )
        }

        OpenAiCompatibleChatMessage (
            role = "assistant",
            content = contentItems,
        )
        return ResponseEntity.ok(contentItems)
    }


    data class MessageRequest(val message: String)

}