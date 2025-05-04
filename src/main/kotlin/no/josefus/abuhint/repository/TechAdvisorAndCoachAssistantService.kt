package no.josefus.abuhint.repository

interface CoachAssistantService {
    fun chat(chatId: String, userMessage: String, uuid: String): String

}

interface TechAdvisorService {
    fun chat(chatId: String, userMessage: String, uuid: String): String

}