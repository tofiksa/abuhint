package no.josefus.abuhint.controller

import no.josefus.abuhint.dto.ChatRequest
import no.josefus.abuhint.dto.OpenAiCompatibleChatMessage
import no.josefus.abuhint.dto.OpenAiCompatibleContentItem
import no.josefus.abuhint.service.ChatService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/coach")
class CoachAssistantController(
    private val chatService: ChatService,
) {

    @PostMapping("/chat")
    fun chat(
        @RequestParam chatId: String,
        @RequestBody message: ChatRequest
    ): ResponseEntity<List<OpenAiCompatibleContentItem>> {
        val uuid = UUID.randomUUID().toString()
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

}