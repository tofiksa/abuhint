package no.josefus.abuhint.service

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.service.TokenStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import no.josefus.abuhint.repository.ConcretePineconeChatMemoryStore
import no.josefus.abuhint.repository.LangChain4jAssistant
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux

@Service
class ChatService(
    private val assistant: LangChain4jAssistant,
    private val concretePineconeChatMemoryStore: ConcretePineconeChatMemoryStore
) {

    // Function to start a chat session using the startConversation in LangChain4jAssistant
    fun startChat(chatId: String): TokenStream {
        val uuid = UUID.randomUUID().toString()
        return assistant.startConversation(chatId, uuid)
    }

    fun processChat(chatId: String, userMessage: String): TokenStream {
        val logger = org.slf4j.LoggerFactory.getLogger(ChatService::class.java)
        val uuid = UUID.randomUUID().toString()

        // Retrieve relevant context from Pinecone
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

        return assistant.chat(chatId, userMessage, uuid)
    }

    /**
     * Process a chat message and convert the TokenStream to a Flux with proper formatting (spacing,
     * newlines, etc.)
     */
    fun processChatAsFlux(chatId: String?, userMessage: String): Pair<String, Flux<String>> {
        val logger = org.slf4j.LoggerFactory.getLogger(ChatService::class.java)
        // Generate a chat ID if not provided
        val effectiveChatId = chatId ?: UUID.randomUUID().toString()

        // Get token stream from the assistant
        val tokenStream = processChat(effectiveChatId, userMessage)

        logger.info("chatid: $effectiveChatId")
        logger.info("tokenStream: ${tokenStream.toString()}")
        // Flags to track formatting state
        val isFirstToken = AtomicBoolean(true)
        val needsSpace = AtomicBoolean(false)
        val lastWasNewline = AtomicBoolean(false)

        // Convert to Flux with proper formatting
        val flux =
                Flux.create { emitter ->
                    tokenStream
                            .onNext { token ->
                                val formattedToken =
                                        formatToken(token, isFirstToken, needsSpace, lastWasNewline)
                                if (formattedToken.isNotEmpty()) {
                                    emitter.next(formattedToken)
                                }
                            }
                            .onComplete { emitter.complete() }
                            .onError { error -> emitter.error(error) }
                            .start()
                }

        return Pair(effectiveChatId, flux)
    }

    /** Format token with proper spacing and newlines */
    private fun formatToken(
            token: String,
            isFirstToken: AtomicBoolean,
            needsSpace: AtomicBoolean,
            lastWasNewline: AtomicBoolean
    ): String {
        // Ignore empty tokens
        if (token.isBlank()) {
            return ""
        }

        val formattedToken = StringBuilder()

        // Handle first token - no leading space
        if (isFirstToken.getAndSet(false)) {
            formattedToken.append(token)
        } else {
            // Check if token starts with punctuation that doesn't need a leading space
            val startsWithPunctuation =
                    token.firstOrNull()?.let {
                        it in listOf(',', '.', '!', '?', ':', ';', ')', ']', '}', '\'', '"')
                    }
                            ?: false

            // Check if token contains newline
            val containsNewline = token.contains("\n")

            // Add space if needed
            if (needsSpace.get() && !startsWithPunctuation && !lastWasNewline.get()) {
                formattedToken.append(" ")
            }

            formattedToken.append(token)

            // Update newline state
            lastWasNewline.set(containsNewline)
        }

        // Determine if next token needs a space
        val endsWithPunctuation =
                token.lastOrNull()?.let {
                    it in listOf(',', '.', '!', '?', ':', ';', '(', '[', '{', '\'', '"')
                }
                        ?: false

        // Space needed if token doesn't end with punctuation or newline
        needsSpace.set(!endsWithPunctuation && !token.endsWith("\n"))

        return formattedToken.toString()
    }
}
