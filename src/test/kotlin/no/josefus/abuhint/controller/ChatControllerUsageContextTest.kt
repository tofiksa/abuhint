package no.josefus.abuhint.controller

import no.josefus.abuhint.service.ChatService
import no.josefus.abuhint.service.TokenUsageBreakdown
import no.josefus.abuhint.service.TokenUsageContext
import no.josefus.abuhint.service.TokenUsageSummary
import no.josefus.abuhint.service.TokenUsageStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import kotlin.test.assertEquals

class ChatControllerUsageContextTest {

    private val chatService: ChatService = mock()
    private val tokenUsageStore: TokenUsageStore = mock()
    private val controller = ChatController(chatService, tokenUsageStore)

    @BeforeEach
    fun auth() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("user-42", null, emptyList())
    }

    @Test
    fun `send passes current user platform and abuhint assistant to chat service`() {
        whenever(chatService.processChat(any(), any(), any<TokenUsageContext>())).thenReturn("Hei!")

        val response = controller.sendMessage(
            chatId = "chat-1",
            credentials = null,
            clientPlatform = "android",
            message = ChatController.MessageRequest("hei"),
        )

        assertEquals(200, response.statusCode.value())
        verify(chatService).processChat(
            "chat-1",
            "hei",
            TokenUsageContext(
                userId = "user-42",
                chatId = "chat-1",
                assistant = "ABUHINT",
                clientPlatform = "android",
            ),
        )
    }

    @Test
    fun `usage endpoint returns current user summary from persistent store`() {
        whenever(tokenUsageStore.getUsageForUser("user-42")).thenReturn(
            TokenUsageSummary(
                userId = "user-42",
                inputTokens = 10,
                cachedInputTokens = 2,
                outputTokens = 5,
                requestCount = 1,
                breakdown = listOf(
                    TokenUsageBreakdown(
                        chatId = "chat-1",
                        assistant = "ABUHINT",
                        clientPlatform = "android",
                        modelName = "gpt-5.4-mini",
                        inputTokens = 10,
                        cachedInputTokens = 2,
                        outputTokens = 5,
                        requestCount = 1,
                        updatedAt = Instant.parse("2026-05-01T00:00:00Z"),
                    ),
                ),
            )
        )

        val response = controller.getAllTokenUsage()

        assertEquals(200, response.statusCode.value())
        assertEquals("user-42", response.body!!.userId)
        assertEquals(15, response.body!!.totalTokens)
        assertEquals("ABUHINT", response.body!!.breakdown.single().assistant)
        verify(tokenUsageStore).getUsageForUser("user-42")
    }
}
