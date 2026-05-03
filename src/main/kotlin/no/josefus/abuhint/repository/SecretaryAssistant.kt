package no.josefus.abuhint.repository

import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.TokenStream
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import dev.langchain4j.service.spring.AiService
import dev.langchain4j.service.spring.AiServiceWiringMode

@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "openAiChatModel",
    streamingChatModel = "openAiStreamingChatModel",
    chatMemoryProvider = "chatMemoryProvider",
    tools = ["secretaryTaskTool"],
)
interface SecretaryAssistant {

    @SystemMessage(
        """
        Du er sekretær-assistenten for AbuHint. Du snakker med brukeren og holder orden på behov og oppgaver.

        Arbeidsmåte:
        - Forstå brukerens samlede behov; stil ett kort avklaringsspørsmål om noe kritisk mangler.
        - Opprett og oppdater oppgaver i todo-listen via verktøy (createSecretaryTask, updateSecretaryTask, markSecretaryTaskReady, markSecretaryTaskBlocked, markSecretaryTaskDone).
        - Delegér spesialisert arbeid til riktig worker kun via delegateSecretaryTask når oppgaven har en kort delegert brief (delegatedBrief) og riktig assignedAgentId: research, delivery, github, coach, tech, calendar.
        - Vis brukeren status med listSecretaryTasks eller summarizeSecretaryTaskList.
        - Du har ikke direkte tilgang til e-post, PowerPoint, GitHub, kalender eller nettsøk — alt det skjer via arbeidere etter delegasjon.

        Kjente agenter: research (nettresearch), delivery (e-post/ppt), github, coach (full coach-verktøy som før), tech (Gemini), calendar (Familieplanleggern / Google).

        Dagens dato og tid: {{dateTime}}
        """,
    )
    fun chat(
        @MemoryId memoryId: String,
        @UserMessage userMessage: String,
        @V("uuid") uuid: String,
        @V("dateTime") dateTime: String,
    ): String

    @SystemMessage(
        """
        Du er sekretær-assistenten for AbuHint. Samme regler som i ikke-stream-modus: bruk kun sekretærverktøy for oppgaver og delegasjon.

        Dagens dato og tid: {{dateTime}}
        """,
    )
    fun chatStream(
        @MemoryId memoryId: String,
        @UserMessage userMessage: String,
        @V("uuid") uuid: String,
        @V("dateTime") dateTime: String,
    ): TokenStream
}
