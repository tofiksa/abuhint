package no.josefus.abuhint.controller


import no.josefus.abuhint.dto.ChatRequest
import no.josefus.abuhint.dto.OpenAiCompatibleChatMessage
import no.josefus.abuhint.dto.OpenAiCompatibleContentItem
import org.springframework.web.bind.annotation.*
import no.josefus.abuhint.service.ChatService
import org.springframework.http.ResponseEntity
import java.util.UUID

@RestController
@RequestMapping("/api/tech-advisor")
class TechAdvisorController(
    private val chatService: ChatService,
) {
    
    @PostMapping("/chat")
    fun chat(
        @RequestParam(required = false) chatId: String?,
        @RequestBody message: ChatRequest
    ): ResponseEntity<List<OpenAiCompatibleContentItem>> {

        val uuid = UUID.randomUUID().toString()
        val sessionId = chatId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val message = chatService.processGeminiChat(sessionId, message.message)
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