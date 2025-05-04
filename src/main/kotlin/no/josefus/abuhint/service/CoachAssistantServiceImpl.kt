package no.josefus.abuhint.service

import no.josefus.abuhint.repository.CoachAssistantService
import no.josefus.abuhint.repository.LangChain4jAssistant
import org.springframework.stereotype.Service

@Service
class CoachAssistantServiceImpl(
    private val langChain4jAssistant: LangChain4jAssistant
) : CoachAssistantService {

    override fun chat(chatId: String, userMessage: String, uuid: String): String {
        return langChain4jAssistant.chat(chatId, userMessage, uuid)
    }

}