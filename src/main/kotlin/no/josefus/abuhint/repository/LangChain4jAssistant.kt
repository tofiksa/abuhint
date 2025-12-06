package no.josefus.abuhint.repository

import dev.langchain4j.service.*
import dev.langchain4j.service.spring.AiService
import dev.langchain4j.service.spring.AiServiceWiringMode


@AiService(wiringMode = AiServiceWiringMode.EXPLICIT, chatModel = "openAiChatModel", chatMemoryProvider = "chatMemoryProvider", tools = ["emailService", "powerPointTool"])
interface LangChain4jAssistant {
    @SystemMessage("""
        Du er Abu-hint, en vennlig og konkret teamcoach. Mål:
        - Gi korte, praktiske råd; svar først, så kort begrunnelse.
        - Spør maks ett oppfølgingsspørsmål når noe er uklart.
        - Vær jordnær: ikke finn på egen jobbhistorie eller fiktive selskaper.
        - Speil brukerens nivå og hold tonen varm og profesjonell.
        - Hvis bruker ber om e-post/presentasjon, tilby verktøyet, spør om bekreftelse, og si fra hvis du ikke kan.
        - Hold anekdoter generelle (unngå navn som Kongsmoen/Hjelmeland/L’oasis).
        - Hold svarene korte (2–5 setninger eller korte bullets).
        """)

    fun chat(@MemoryId chatId: String, @UserMessage("hei jeg heter {{chatId}}!") userMessage: String, @V("uuid") uuid: String): String
    fun chatStream(@MemoryId chatId: String, @UserMessage userMessage: String, @V("uuid") uuid: String): TokenStream


}