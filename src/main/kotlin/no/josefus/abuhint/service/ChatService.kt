package no.josefus.abuhint.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import java.util.UUID
import no.josefus.abuhint.repository.ConcretePineconeChatMemoryStore
import no.josefus.abuhint.repository.LangChain4jAssistant
import org.springframework.stereotype.Service

@Service
class ChatService(
    private val assistant: LangChain4jAssistant,
    private val concretePineconeChatMemoryStore: ConcretePineconeChatMemoryStore
) {


    fun processChat(chatId: String, userMessage: String): String {
        val logger = org.slf4j.LoggerFactory.getLogger(ChatService::class.java)
        val uuid = UUID.randomUUID().toString()


        val relevantEmbeddingMatches =
            concretePineconeChatMemoryStore.retrieveFromPinecone(chatId, userMessage)
        val relevantMessages =
            concretePineconeChatMemoryStore.parseResultsToMessages(relevantEmbeddingMatches)

        logger.info("Relevant messages: $relevantMessages")

        // Format relevant context
        val contextBuilder = StringBuilder()
        if (relevantMessages.isNotEmpty()) {
            contextBuilder.append("Previous relevant conversation context:\n")
            relevantMessages.forEach { message ->
                when (message) {
                    is UserMessage -> contextBuilder.append("User: ${message.text()}\n")
                    is AiMessage -> contextBuilder.append("Assistant: ${message.text()}\n")
                    is SystemMessage -> contextBuilder.append("System: ${message.text()}\n")
                }
            }
            contextBuilder.append("\nCurrent conversation:\n")
        }

        logger.info("Retrieved ${relevantMessages.size} relevant messages from chat memory")

        // Combine context with current message
        val enhancedMessage =
            if (contextBuilder.isNotEmpty()) {
                "${contextBuilder}\nUser: $userMessage"
            } else {
                userMessage
            }

        return assistant.chat(chatId, enhancedMessage, uuid)
    }

}
