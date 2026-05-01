package no.josefus.abuhint.service

import no.josefus.abuhint.dto.OpenAiCompatibleChatCompletionRequest
import no.josefus.abuhint.dto.OpenAiCompatibleChatCompletionResponse
import no.josefus.abuhint.dto.OpenAiCompatibleChatMessage
import no.josefus.abuhint.dto.OpenAiCompatibleChoice
import no.josefus.abuhint.dto.OpenAiCompatibleContentItem
import no.josefus.abuhint.dto.OpenAiCompatibleUsage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class OpenAiCompatibleServiceImpl(
    private val chatService: ChatService,
    @Value("\${langchain4j.open-ai.chat-model.model-name}") private val modelName: String,
    private val tokenUsageStore: TokenUsageStore,
) : OpenAiCompatibleService {
    override fun createChatCompletion(
        request: OpenAiCompatibleChatCompletionRequest,
        userId: String,
        clientPlatform: String,
    ): OpenAiCompatibleChatCompletionResponse {
        if (request.messages.isEmpty()) {
            throw IllegalArgumentException("Messages cannot be empty")
        }
        if (request.maxCompletionTokens <= 0) {
            throw IllegalArgumentException("maxCompletionTokens must be greater than 0")
        }

        // Find the latest user message (role == "user")
        val userMessage = request.messages.lastOrNull { it.role == "user" }?.content?.joinToString("\n") { it.text ?: "" }
            ?: throw IllegalArgumentException("No user message found in request")

        val chatId = request.chatId?.takeIf { it.isNotBlank() } ?: "openai-${System.currentTimeMillis()}"
        val beforeUsage = tokenUsageStore.getUsageForUserChat(userId, chatId)
        val reply = chatService.processChat(
            chatId,
            userMessage,
            TokenUsageContext(
                userId = userId,
                chatId = chatId,
                assistant = "OPENAI_COMPATIBLE",
                clientPlatform = clientPlatform,
            ),
        )
        val usage = usageDelta(beforeUsage, tokenUsageStore.getUsageForUserChat(userId, chatId))

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
            model = modelName,
            choices = listOf(choice),
            usage = usage,
        )
    }

    override fun createStreamingChatCompletion(
        request: OpenAiCompatibleChatCompletionRequest,
        userId: String,
        clientPlatform: String,
    ): SseEmitter {
        if (request.messages.isEmpty()) {
            throw IllegalArgumentException("Messages cannot be empty")
        }
        val userMessage = request.messages.lastOrNull { it.role == "user" }?.content?.joinToString("\n") { it.text ?: "" }
            ?: throw IllegalArgumentException("No user message found in request")
        val chatId = request.chatId?.takeIf { it.isNotBlank() } ?: "openai-${System.currentTimeMillis()}"
        return chatService.processChatStream(
            chatId,
            userMessage,
            TokenUsageContext(
                userId = userId,
                chatId = chatId,
                assistant = "OPENAI_COMPATIBLE",
                clientPlatform = clientPlatform,
            ),
        )
    }

    private fun usageDelta(before: TokenUsageSummary, after: TokenUsageSummary): OpenAiCompatibleUsage? {
        val promptTokens = (after.inputTokens - before.inputTokens).coerceAtLeast(0)
        val completionTokens = (after.outputTokens - before.outputTokens).coerceAtLeast(0)
        val totalTokens = promptTokens + completionTokens
        if (totalTokens == 0L) return null
        return OpenAiCompatibleUsage(
            promptTokens = promptTokens.toSafeInt(),
            completionTokens = completionTokens.toSafeInt(),
            totalTokens = totalTokens.toSafeInt(),
        )
    }

    private fun Long.toSafeInt(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

