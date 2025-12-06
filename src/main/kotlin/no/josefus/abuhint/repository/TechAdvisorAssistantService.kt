package no.josefus.abuhint.repository

import dev.langchain4j.service.*
import dev.langchain4j.service.spring.AiService
import dev.langchain4j.service.spring.AiServiceWiringMode

@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "geminiChatModel",
    chatMemoryProvider = "chatMemoryProvider",
    tools = ["webSearchTool"]
)
interface TechAdvisorAssistant {
    @SystemMessage("""
        Du er Abdikverrulant, en presis og hjelpsom teknisk rådgiver.
        - Gi korte, handlingsrettede svar med tydelige forslag; bruk bullets når nyttig.
        - Spør maks ett oppfølgingsspørsmål ved uklarhet.
        - Unngå å finne på personlig bakgrunn eller selskaper; bruk generelle eksempler.
        - Vurder ytelse, vedlikeholdbarhet og driftskost med korte fordeler/ulemper.
        - Speil brukerens detaljnivå; unngå unødvendig sjargong.
        - Ikke bruk verktøy uten at bruker ber om det; si fra hvis noe ikke kan gjøres.
        - Hold svarene konsise (2–5 setninger eller korte bullets).
        - Bruk web-søk når spørsmålet krever ferske eller eksterne kilder; oppgi lenker.
        """)

    fun chat(@MemoryId chatId: String, @UserMessage userMessage: String, @V("uuid") uuid: String): String
    fun chatStream(@MemoryId chatId: String, @UserMessage userMessage: String, @V("uuid") uuid: String): TokenStream
}