package no.josefus.abuhint.familie

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FamilieControllerPreconditionTest {

    private val chatService: FamilieChatService = mock()
    private val credentialStore: UserGoogleCredentialStore = mock()
    private val controller = FamilieController(chatService, credentialStore)

    @BeforeEach
    fun auth() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("user-42", null, emptyList())
    }

    @Test
    fun `send returns 412 when google is not connected and does not call chat service`() {
        whenever(credentialStore.load("user-42")).thenReturn(null)

        val response = controller.sendMessage(
            chatId = null,
            request = FamilieMessageRequest("hei"),
        )

        assertEquals(412, response.statusCode.value())
        val text = response.body!!.first().text
        assertTrue(text!!.contains("Google", ignoreCase = true))
        verify(chatService, never()).processChat(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `send delegates to chat service when google connection exists`() {
        whenever(credentialStore.load("user-42")).thenReturn(
            GoogleCredentials("user-42", "rt", "at", null, "scope", "e@x", "UTC")
        )
        whenever(chatService.processChat(any(), any(), any(), anyOrNull())).thenReturn("Klart!")

        val response = controller.sendMessage(
            chatId = "chat-1",
            request = FamilieMessageRequest("hei"),
        )

        assertEquals(200, response.statusCode.value())
        assertEquals("Klart!", response.body!!.first().text)
        verify(chatService).processChat("chat-1", "hei", "user-42", null)
    }
}
