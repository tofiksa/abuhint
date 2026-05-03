package no.josefus.abuhint.repository

import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import dev.langchain4j.service.spring.AiService
import dev.langchain4j.service.spring.AiServiceWiringMode

@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "openAiChatModel",
    streamingChatModel = "openAiStreamingChatModel",
    chatMemoryProvider = "chatMemoryProvider",
    tools = ["emailService", "powerPointTool", "powerPointEmailTool"],
)
interface DeliveryWorkerAssistant {

    @SystemMessage(
        """
        Du er delivery-worker for AbuHint. Du får et delegert oppdrag om e-post og/eller PowerPoint.
        - Utfør kun det som står i briefen.
        - Bruk verktøy for e-post og PowerPoint når det er naturlig. Ingen GitHub, ingen kalender.
        - Be om bekreftelse i briefen hvis brukeren ikke eksplisitt har sagt ja til utsendelse — ellers fålg briefen.

        Dagens dato og tid: {{dateTime}}
        """,
    )
    fun executeBrief(
        @MemoryId memoryId: String,
        @UserMessage brief: String,
        @V("uuid") uuid: String,
        @V("dateTime") dateTime: String,
    ): String
}
