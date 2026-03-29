package no.josefus.abuhint.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage

object ChatMessageUtils {

    fun getMessageText(message: ChatMessage): String {
        return when (message) {
            is UserMessage -> extractText(message)
            is AiMessage -> extractText(message)
            is SystemMessage -> extractText(message)
            else -> ""
        }
    }

    private fun extractText(message: Any): String {
        return try {
            message.javaClass.getMethod("singleText").invoke(message) as? String
                ?: message.javaClass.getMethod("text").invoke(message) as? String
                ?: ""
        } catch (e: Exception) {
            message.toString().substringAfter("text=").substringBefore(",").substringBefore(")") ?: ""
        }
    }
}
