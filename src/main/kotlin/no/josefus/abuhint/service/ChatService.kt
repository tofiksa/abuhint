package no.josefus.abuhint.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.Tokenizer
import java.util.UUID
import no.josefus.abuhint.repository.ConcretePineconeChatMemoryStore
import no.josefus.abuhint.repository.LangChain4jAssistant
import org.springframework.stereotype.Service

@Service
class ChatService(
    private val assistant: LangChain4jAssistant,
    private val concretePineconeChatMemoryStore: ConcretePineconeChatMemoryStore,
    private val tokenizer: Tokenizer
) {


    fun processChat(chatId: String, userMessage: String): String {
        val logger = org.slf4j.LoggerFactory.getLogger(ChatService::class.java)
        val uuid = UUID.randomUUID().toString()
        val maxContextTokens = 8192
        val tokenizer = this.tokenizer

        val relevantEmbeddingMatches =
            concretePineconeChatMemoryStore.retrieveFromPineconeWithTokenLimit(
                chatId, userMessage, maxContextTokens, tokenizer
            )
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

        // Calculate token count
        val totalTokens = tokenizer.estimateTokenCountInText(enhancedMessage)
        logger.info("Total tokens in message: $totalTokens")
        if (totalTokens > maxContextTokens) {
            // Trim the context to fit within the token limit
            val contextTokens = tokenizer.estimateTokenCountInText(contextBuilder.toString())
            val excessTokens = totalTokens - maxContextTokens
            if (contextTokens > excessTokens) {
                val trimmedContext = contextBuilder.toString().take(contextBuilder.length - excessTokens)
                logger.warn("Context trimmed to fit within token limit")
                return assistant.chat(chatId, "$trimmedContext\nUser: $userMessage", uuid)
            } else {
                logger.error("Message exceeds token limit even after trimming context")
                throw IllegalArgumentException("Message exceeds token limit")
            }
        }

        return assistant.chat(chatId, enhancedMessage, uuid)
    }

}
