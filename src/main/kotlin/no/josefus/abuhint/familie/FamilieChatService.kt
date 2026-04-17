package no.josefus.abuhint.familie

import dev.langchain4j.data.message.ChatMessage
import no.josefus.abuhint.repository.ConcretePineconeChatMemoryStore
import no.josefus.abuhint.service.ChatIdContextHolder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * Thin orchestrator around [FamilieplanleggernAssistant]. Keeps the chat-memory wiring
 * (Pinecone via chatMemoryProvider) but intentionally skips the manual embedding-retrieval
 * pipeline used by `ChatService` — the propose → confirm pattern relies on tight,
 * deterministic tool flows where extra synthetic context would only add noise.
 */
@Service
class FamilieChatService(
    private val assistant: FamilieplanleggernAssistant,
    private val chatMemoryStore: ConcretePineconeChatMemoryStore,
) {

    private val log = LoggerFactory.getLogger(FamilieChatService::class.java)

    fun processChat(chatId: String, userMessage: String): String {
        val dateTime = java.time.LocalDateTime.now().toString()
        ChatIdContextHolder.set(chatId)
        return try {
            assistant.chat(chatId, userMessage, dateTime)
        } catch (e: Exception) {
            log.error("Familieplanleggern chat failed for chatId={}: {}", chatId, e.message, e)
            "Beklager, teknisk feil mot Familieplanleggern. Prøv igjen om litt."
        } finally {
            ChatIdContextHolder.clear()
        }
    }

    fun processChatStream(chatId: String, userMessage: String): SseEmitter {
        val emitter = SseEmitter(120_000L)
        val dateTime = java.time.LocalDateTime.now().toString()
        ChatIdContextHolder.set(chatId)
        val tokenStream = try {
            assistant.chatStream(chatId, userMessage, dateTime)
        } finally {
            ChatIdContextHolder.clear()
        }

        tokenStream
            .onPartialResponse { token ->
                try {
                    emitter.send(SseEmitter.event().data(token))
                } catch (e: Exception) {
                    log.debug("Client disconnected during familie stream: {}", e.message)
                }
            }
            .onCompleteResponse {
                try {
                    emitter.send(SseEmitter.event().data("[DONE]"))
                    emitter.complete()
                } catch (e: Exception) {
                    log.debug("Error completing familie SSE: {}", e.message)
                }
            }
            .onError { err ->
                log.error("Familie streaming error: {}", err.message, err)
                try {
                    emitter.send(SseEmitter.event().data("{\"error\":\"${err.message}\"}"))
                    emitter.completeWithError(err)
                } catch (_: Exception) { /* client likely gone */ }
            }
            .start()
        return emitter
    }

    fun getChatHistory(chatId: String): List<ChatMessage> = chatMemoryStore.getMessages(chatId)
}
