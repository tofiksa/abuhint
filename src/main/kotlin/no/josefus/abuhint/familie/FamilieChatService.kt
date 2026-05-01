package no.josefus.abuhint.familie

import dev.langchain4j.data.message.ChatMessage
import no.josefus.abuhint.repository.ConcretePineconeChatMemoryStore
import no.josefus.abuhint.service.ChatIdContextHolder
import no.josefus.abuhint.service.TokenUsageContext
import no.josefus.abuhint.service.TokenUsageContextHolder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

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
    private val properties: FamilieplanleggernProperties,
) {

    private val log = LoggerFactory.getLogger(FamilieChatService::class.java)

    fun processChat(
        chatId: String,
        userMessage: String,
        userId: String,
        metadata: FamilieClientMetadata?,
        usageContext: TokenUsageContext,
    ): String {
        val dateTime = buildDateTimeContext(metadata)
        FamilieUserChatRegistry.bind(chatId, userId)
        ChatIdContextHolder.set(chatId)
        TokenUsageContextHolder.set(usageContext.copy(chatId = chatId, userId = userId))
        return try {
            assistant.chat(chatId, userMessage, dateTime)
        } catch (e: Exception) {
            log.error("Familieplanleggern chat failed for chatId={}: {}", chatId, e.message, e)
            "Beklager, teknisk feil mot Familieplanleggern. Prøv igjen om litt."
        } finally {
            FamilieUserChatRegistry.unbind(chatId)
            ChatIdContextHolder.clear()
            TokenUsageContextHolder.clear()
        }
    }

    fun processChatStream(
        chatId: String,
        userMessage: String,
        userId: String,
        metadata: FamilieClientMetadata?,
        usageContext: TokenUsageContext,
    ): SseEmitter {
        val emitter = SseEmitter(120_000L)
        val dateTime = buildDateTimeContext(metadata)

        FamilieUserChatRegistry.bind(chatId, userId)
        ChatIdContextHolder.set(chatId)
        TokenUsageContextHolder.set(usageContext.copy(chatId = chatId, userId = userId))
        val cleanedUp = AtomicBoolean(false)

        fun cleanup() {
            if (!cleanedUp.compareAndSet(false, true)) return
            FamilieUserChatRegistry.unbind(chatId)
            ChatIdContextHolder.clear()
            TokenUsageContextHolder.clear()
        }

        val tokenStream = try {
            assistant.chatStream(chatId, userMessage, dateTime)
        } catch (e: Exception) {
            cleanup()
            log.error("Familieplanleggern stream setup failed for chatId={}: {}", chatId, e.message, e)
            try {
                emitter.completeWithError(e)
            } catch (_: Exception) { /* client gone */ }
            return emitter
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
                } finally {
                    cleanup()
                }
            }
            .onError { err ->
                log.error("Familie streaming error: {}", err.message, err)
                try {
                    emitter.send(SseEmitter.event().data("{\"error\":\"${err.message}\"}"))
                    emitter.completeWithError(err)
                } catch (_: Exception) { /* client likely gone */ }
                finally {
                    cleanup()
                }
            }
        emitter.onCompletion { cleanup() }

        try {
            tokenStream.start()
        } catch (e: Exception) {
            log.error("Familie tokenStream.start failed for chatId={}: {}", chatId, e.message, e)
            cleanup()
            try {
                emitter.completeWithError(e)
            } catch (_: Exception) { /* ignore */ }
        }
        return emitter
    }

    fun getChatHistory(chatId: String): List<ChatMessage> = chatMemoryStore.getMessages(chatId)

    /**
     * Richer than raw server local time: uses optional [metadata] from the client so the model
     * can interpret «om 5 dager» relative to the user's timezone and clock.
     */
    internal fun buildDateTimeContext(metadata: FamilieClientMetadata?): String {
        val zone = metadata?.timezone?.let { z ->
            runCatching { ZoneId.of(z.trim()) }.getOrNull()
        } ?: ZoneId.of(properties.defaultTimezone)
        val instant = metadata?.sentAt?.let { s ->
            runCatching { Instant.parse(s.trim()) }.getOrNull()
        } ?: Instant.now()
        val local = instant.atZone(zone)
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return buildString {
            append("Brukerens referanseklokke: ")
            append(local.format(fmt))
            append(" (")
            append(zone.id)
            append("). ISO-instant: ")
            append(instant)
            append(". Bruk dette når du tolker relative uttrykk som «i morgen», «neste uke» eller «om 5 dager».")
        }
    }
}
