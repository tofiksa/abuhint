package no.josefus.abuhint.familie

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.service.AiServices
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class DeviceCalendarAssistantTest {
    @Test fun `real AI service parses structured action without any Google tools`() {
        val model = mock<ChatModel>()
        whenever(model.chat(any<ChatRequest>())).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(
            """{"text":"Forslag","action":{"type":"create_event","start":"2026-09-24T16:00:00Z","end":"2026-09-24T17:00:00Z","timezone":"Europe/Oslo","title":"Tannlege","allDay":false}}"""
        )).build())
        val assistant = AiServices.builder(DeviceCalendarAssistant::class.java).chatModel(model).build()
        assertEquals("Tannlege", assistant.respond("[]", "2026-09-20T12:00:00Z").action?.title)
        val request = argumentCaptor<ChatRequest>()
        verify(model).chat(request.capture())
        assertTrue(request.firstValue.toolSpecifications().isNullOrEmpty())
    }
}
