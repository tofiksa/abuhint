package no.josefus.abuhint.service.impl

import org.springframework.stereotype.Service
import no.josefus.abuhint.repository.TechAdvisorAssistant
import no.josefus.abuhint.repository.TechAdvisorService

@Service
class TechAdvisorServiceImpl(
    private val techAdvisorAssistant: TechAdvisorAssistant
) : TechAdvisorService {

    override fun chat(chatId: String, userMessage: String, uuid: String): String {
        return techAdvisorAssistant.chat(chatId, userMessage, uuid)
    }

}