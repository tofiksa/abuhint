package no.josefus.abuhint.service

import no.josefus.abuhint.dto.OpenAiCompatibleChatCompletionRequest
import no.josefus.abuhint.dto.OpenAiCompatibleChatMessage
import no.josefus.abuhint.dto.OpenAiCompatibleContentItem
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class OpenAiCompatibleServiceImplTest {

    private val chatService: ChatService = mock()
    private val tokenUsageStore: TokenUsageStore = mock()
    private val service = OpenAiCompatibleServiceImpl(chatService, "gpt-5.4-mini", tokenUsageStore)

    @Test
    fun `non streaming response includes token usage delta for request`() {
        val request = OpenAiCompatibleChatCompletionRequest(
            messages = listOf(
                OpenAiCompatibleChatMessage(
                    role = "user",
                    content = listOf(OpenAiCompatibleContentItem(type = "text", text = "hei")),
                )
            ),
            chatId = "chat-1",
        )
        whenever(tokenUsageStore.getUsageForUserChat("user-42", "chat-1"))
            .thenReturn(emptySummary("user-42"))
            .thenReturn(
                TokenUsageSummary(
                    userId = "user-42",
                    inputTokens = 10,
                    cachedInputTokens = 0,
                    outputTokens = 5,
                    requestCount = 1,
                    breakdown = emptyList(),
                )
            )
        whenever(chatService.processChat(eq("chat-1"), eq("hei"), any<TokenUsageContext>())).thenReturn("svar")

        val response = service.createChatCompletion(request, userId = "user-42", clientPlatform = "web")
        val usage = response.usage!!

        assertEquals(10, usage.promptTokens)
        assertEquals(5, usage.completionTokens)
        assertEquals(15, usage.totalTokens)
        verify(chatService).processChat(
            "chat-1",
            "hei",
            TokenUsageContext(
                userId = "user-42",
                chatId = "chat-1",
                assistant = "OPENAI_COMPATIBLE",
                clientPlatform = "web",
            ),
        )
    }

    private fun emptySummary(userId: String) = TokenUsageSummary(
        userId = userId,
        inputTokens = 0,
        cachedInputTokens = 0,
        outputTokens = 0,
        requestCount = 0,
        breakdown = emptyList(),
    )
}
