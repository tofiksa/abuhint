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
    tools = ["webSearchTool"],
)
interface ResearchWorkerAssistant {

    @SystemMessage(
        """
        Du er research-worker for AbuHint. Du får et kort delegert oppdrag (brief) — ikke hele brukerens historikk.
        - Utfør kun det som står i oppdraget.
        - Bruk verktøy "webSearchTool" når du trenger oppdaterte kilder.
        - Skill fakta fra antakelser; kilder skal oppgis som klikkbare markdown-lenker.
        - Svar på norsk med mindre brukeren ber om annet.

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
