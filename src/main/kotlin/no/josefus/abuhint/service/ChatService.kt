package no.josefus.abuhint.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
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
            retrieveRelevantContext(
                chatId, userMessage, maxContextTokens, tokenizer
            )
        val relevantMessages =
            concretePineconeChatMemoryStore.parseResultsToMessages(relevantEmbeddingMatches)

        logger.info("Relevant messages: $relevantMessages")


        logger.info("Retrieved ${relevantMessages.size} relevant messages from chat memory")

        // Combine context with current message
        val enhancedMessage =
            formatMessagesToContext(relevantMessages)

        // Calculate token count
        val totalTokens = tokenizer.estimateTokenCountInText(enhancedMessage)
        logger.info("Total tokens in message: $totalTokens")
        // Better context trimming
        if (totalTokens > maxContextTokens) {
            // Instead of just taking characters, prioritize keeping complete messages
            // Split into message units
            val messages = relevantMessages.toList()
            val trimmedMessages = mutableListOf<ChatMessage>()
            var currentTokenCount = tokenizer.estimateTokenCountInText(userMessage)

            for (message in messages) {
                val messageTokens = tokenizer.estimateTokenCountInText(message.text())
                if (currentTokenCount + messageTokens <= maxContextTokens) {
                    trimmedMessages.add(message)
                    currentTokenCount += messageTokens
                } else {
                    break
                }
            }

            // Rebuild context with only the messages that fit
            val trimmedContext = formatMessagesToContext(trimmedMessages)
            return assistant.chat(chatId, "$trimmedContext\nUser: $userMessage", uuid)
        }
        return assistant.chat(chatId, "$enhancedMessage\nUser: $userMessage", uuid)

    }


        private fun formatMessagesToContext(messages: List<ChatMessage>): String {
            val contextBuilder = StringBuilder()
            if (messages.isNotEmpty()) {
                contextBuilder.append("Previous relevant conversation context:\n")
                messages.forEach { message ->
                    when (message) {
                        is UserMessage -> contextBuilder.append("User: ${message.text()}\n")
                        is AiMessage -> contextBuilder.append("Assistant: ${message.text()}\n")
                        is SystemMessage -> contextBuilder.append("System: ${message.text()}\n")
                    }
                }
                contextBuilder.append("\nCurrent conversation:\n")
            }
            return contextBuilder.toString()
        }


    fun retrieveRelevantContext(
        memoryId: String,
        query: String,
        maxTokens: Int,
        tokenizer: Tokenizer
    ) = concretePineconeChatMemoryStore.retrieveFromPineconeWithTokenLimit(memoryId, query, maxTokens, tokenizer)

}
