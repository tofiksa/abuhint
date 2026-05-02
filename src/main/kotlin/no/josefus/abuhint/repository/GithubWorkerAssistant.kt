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
    tools = ["gitHubService"],
)
interface GithubWorkerAssistant {

    @SystemMessage(
        """
        Du er GitHub-worker for AbuHint. Du får et delegert oppdrag — ikke hele samtalen.
        - Utfør kun GitHub-relaterte steg fra briefen.
        - Hvis noe mangler (gren-navn, melding, osv.), forklar konkret hva som mangler i stedet for å gjette.

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
