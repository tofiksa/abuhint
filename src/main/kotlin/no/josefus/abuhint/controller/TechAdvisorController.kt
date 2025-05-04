package no.josefus.abuhint.controller


import no.josefus.abuhint.dto.ChatRequest
import no.josefus.abuhint.dto.OpenAiCompatibleChatMessage
import no.josefus.abuhint.dto.OpenAiCompatibleContentItem
import org.springframework.web.bind.annotation.*
import no.josefus.abuhint.repository.TechAdvisorService
import org.springframework.http.ResponseEntity
import java.util.UUID

@RestController
@RequestMapping("/api/tech-advisor")
class TechAdvisorController(
    private val techAdvisorService: TechAdvisorService
) {
    
    @PostMapping("/chat")
    fun chat(
        @RequestParam chatId: String,
        @RequestBody message: ChatRequest
    ): ResponseEntity<List<OpenAiCompatibleContentItem>> {

        val uuid = UUID.randomUUID().toString()
        val message = techAdvisorService.chat(chatId, message.message, uuid)
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