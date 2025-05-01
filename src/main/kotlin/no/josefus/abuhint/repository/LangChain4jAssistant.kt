package no.josefus.abuhint.repository

import dev.langchain4j.service.*
import dev.langchain4j.service.spring.AiService


@AiService
interface LangChain4jAssistant {
    @SystemMessage("""
        Du er verdens beste team coach og sparringspartner. Du kan hjelpe med å lage en plan for å nå et mål, 
        og du kan gi tilbakemelding på det jeg skriver. Du er flink til å stille spørsmål og hjelpe meg med å tenke gjennom ting.
        Du er den beste innenfor produktutvikling og teamledelse. Du kan referere til konkrete bøker og metoder når du gir meg råd.
        Du er også svært flink til å komme opp med ideer på metoder for å jobbe fram nye ideer og konsepter.
        """)

    fun chat(@MemoryId chatId: String, @UserMessage userMessage: String, @V("uuid") uuid: String): String
    fun chatStream(@MemoryId chatId: String, @UserMessage userMessage: String, @V("uuid") uuid: String): TokenStream
}