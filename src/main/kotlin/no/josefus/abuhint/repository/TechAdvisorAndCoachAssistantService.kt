package no.josefus.abuhint.repository

import dev.langchain4j.service.UserMessage

interface CoachAssistantService {
    fun chat(chatId: String, @UserMessage("hei jeg heter {{chatId}}!") userMessage: String, uuid: String): String

}

interface TechAdvisorService {
    fun chat(chatId: String, @UserMessage("hei jeg heter {{chatId}}!") userMessage: String, uuid: String, dateTime: String): String

}