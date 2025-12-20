package no.josefus.abuhint.service

import no.josefus.abuhint.repository.CoachAssistantService
import no.josefus.abuhint.repository.LangChain4jAssistant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CoachAssistantServiceImpl(
    private val langChain4jAssistant: LangChain4jAssistant
) : CoachAssistantService {
    private val log = LoggerFactory.getLogger(CoachAssistantServiceImpl::class.java)

    override fun chat(chatId: String, userMessage: String, uuid: String): String {
        val dateTime = java.time.LocalDateTime.now().toString()
        log.info("meldingen som sendes til langchain4jAssistant: chatId={} userMessage=\"{}\" uuid={} dateTime={}",
            chatId,
            userMessage.take(100),
            uuid,
            dateTime
        )
        return langChain4jAssistant.chat(chatId, userMessage, uuid, dateTime)
    }

}