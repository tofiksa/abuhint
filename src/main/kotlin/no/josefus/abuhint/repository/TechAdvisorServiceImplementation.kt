package no.josefus.abuhint.repository

import org.springframework.stereotype.Service

@Service
class TechAdvisorServiceImpl(
    private val techAdvisorAssistant: TechAdvisorAssistant
) : TechAdvisorService {

    override fun chat(chatId: String, userMessage: String, uuid: String): String {
        return techAdvisorAssistant.chat(chatId, userMessage, uuid)
    }

}