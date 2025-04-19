package no.josefus.abuhint.controller

import no.josefus.abuhint.service.ChatService
import no.josefus.abuhint.service.ScoreService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import java.util.UUID

@RestController
@RequestMapping("/api/chat")
class ChatController(private val chatService: ChatService, private val scoreService: ScoreService) {

    // Endpoint to start a chat session using the startChat function from ChatService
    @GetMapping(value = ["/send"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun startChat(
        @RequestParam(required = false) chatId: String?,
        @RequestParam(required = false) credentials: String?
    ): Flux<String> {
        return try {
            val effectiveChatId = chatId ?: UUID.randomUUID().toString()
            val responseFlux = chatService.startChat(effectiveChatId)
            // get the game id from the high score service
            val gameId = scoreService.fetchAndReturnGameId(credentials)
            // logg the game id here
            gameId.subscribe { id ->
                println("Game ID: $id")
            }
            responseFlux as Flux<String>
        } catch (e: Exception) {
            Flux.error(e)
        }
    }



    @PostMapping(value = ["/send"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun sendMessage(
        @RequestParam(required = false) chatId: String?,
        @RequestParam(required = false) credentials: String?,
        @RequestBody message: MessageRequest
    ): Flux<String> {
        val gameId = scoreService.fetchAndReturnGameId(credentials)
        gameId.subscribe { id ->
            println("Game ID: $id")
        }
        val (effectiveChatId, responseFlux) = chatService.processChatAsFlux(chatId, message.message)
        return responseFlux
    }

    // If you need to expose the chat ID to the client, you could use this approach instead:
    @PostMapping("/send-with-id")
    fun sendMessageWithId(
        @RequestParam(required = false) chatId: String?,
        @RequestBody message: MessageRequest
    ): ResponseEntity<ChatResponse> {
        val (effectiveChatId, responseFlux) = chatService.processChatAsFlux(chatId, message.message)
        return ResponseEntity.ok(ChatResponse(effectiveChatId, responseFlux))
    }

    data class MessageRequest(val message: String)

    data class ChatResponse(val chatId: String, val stream: Flux<String>)
}