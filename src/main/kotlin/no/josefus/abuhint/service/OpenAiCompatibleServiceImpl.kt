package no.josefus.abuhint.service

import no.josefus.abuhint.dto.OpenAiCompatibleChatCompletionRequest
import no.josefus.abuhint.dto.OpenAiCompatibleChatCompletionResponse
import no.josefus.abuhint.dto.OpenAiCompatibleChatMessage
import no.josefus.abuhint.dto.OpenAiCompatibleChoice
import no.josefus.abuhint.dto.OpenAiCompatibleContentItem
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class OpenAiCompatibleServiceImpl(
    private val chatService: ChatService
) : OpenAiCompatibleService {
    override fun createChatCompletion(request: OpenAiCompatibleChatCompletionRequest): OpenAiCompatibleChatCompletionResponse {
        if (request.messages.isEmpty()) {
            throw IllegalArgumentException("Messages cannot be empty")
        }
        if (request.maxCompletionTokens <= 0) {
            throw IllegalArgumentException("maxCompletionTokens must be greater than 0")
        }

        // Find the latest user message (role == "user")
        val userMessage = request.messages.lastOrNull { it.role == "user" }?.content?.joinToString("\n") { it.text ?: "" }
            ?: throw IllegalArgumentException("No user message found in request")

        // Use a random chatId for now (could be improved to use a real session)
        val chatId = "openai-${System.currentTimeMillis()}"
        val reply = chatService.processChat(chatId, userMessage)

        val responseMessage = OpenAiCompatibleChatMessage(
            role = "assistant",
            content = listOf(OpenAiCompatibleContentItem(type = "text", text = reply))
        )
        val choice = OpenAiCompatibleChoice(
            message = responseMessage,
            finishReason = "stop"
        )
        return OpenAiCompatibleChatCompletionResponse(
            id = "chatcmpl-${System.currentTimeMillis()}-${(1000..9999).random()}",
            `object` = "chat.completion",
            created = System.currentTimeMillis() / 1000,
            model = request.model,
            choices = listOf(choice),
            usage = null // You can fill this if you want token usage stats
        )
    }

    override fun createStreamingChatCompletion(request: OpenAiCompatibleChatCompletionRequest): SseEmitter {
        // Dummy implementation for demonstration
        return SseEmitter()
    }
}

