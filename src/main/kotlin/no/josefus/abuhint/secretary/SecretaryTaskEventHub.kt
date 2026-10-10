package no.josefus.abuhint.secretary

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Service
class SecretaryTaskEventHub {

    private val log = LoggerFactory.getLogger(SecretaryTaskEventHub::class.java)

    private data class Subscriber(
        val userId: String,
        val emitter: SseEmitter,
    )

    private val subscribersByChatId = ConcurrentHashMap<String, CopyOnWriteArraySet<Subscriber>>()

    /**
     * Registers a subscriber and sends [initialEvent] (e.g. a snapshot) before any live event can reach it.
     * Sends to one emitter are serialized on its [Subscriber], so a live event published during subscribe
     * waits until the initial event has been sent.
     */
    fun subscribe(chatId: String, userId: String, timeoutMs: Long, initialEvent: (() -> Any)? = null): SseEmitter {
        val emitter = SseEmitter(timeoutMs)
        val subscriber = Subscriber(userId, emitter)

        val remove = { removeSubscriber(chatId, subscriber) }
        emitter.onCompletion(remove)
        emitter.onTimeout(remove)
        emitter.onError { remove() }

        synchronized(subscriber) {
            subscribersByChatId.computeIfAbsent(chatId) { CopyOnWriteArraySet() }.add(subscriber)
            if (initialEvent != null) {
                try {
                    emitter.send(taskEvent(initialEvent()))
                } catch (e: Exception) {
                    log.warn("Could not send initial SSE event chatId={}", chatId, e)
                    removeSubscriber(chatId, subscriber)
                    emitter.completeWithError(e)
                }
            }
        }

        return emitter
    }

    /** Delivered after commit when published inside a transaction, so clients that re-read see the same state. */
    @TransactionalEventListener(fallbackExecution = true)
    fun on(event: SecretaryStreamEvent) {
        val subscribers = subscribersByChatId[event.chatId] ?: return
        for (subscriber in subscribers) {
            if (subscriber.userId != event.userId) {
                continue
            }
            try {
                synchronized(subscriber) { subscriber.emitter.send(streamEvent(event)) }
            } catch (e: Exception) {
                log.debug("Removing SSE subscriber after send failure chatId={}", event.chatId)
                removeSubscriber(event.chatId, subscriber)
            }
        }
    }

    @Scheduled(fixedRate = 20_000)
    fun heartbeat() {
        for ((chatId, subscribers) in subscribersByChatId) {
            for (subscriber in subscribers) {
                try {
                    synchronized(subscriber) { subscriber.emitter.send(SseEmitter.event().comment("ping")) }
                } catch (e: Exception) {
                    log.debug("Removing SSE subscriber after heartbeat failure chatId={}", chatId)
                    removeSubscriber(chatId, subscriber)
                }
            }
        }
    }

    private fun taskEvent(data: Any): SseEmitter.SseEventBuilder = SseEmitter.event().name("task").data(data)

    private fun streamEvent(event: SecretaryStreamEvent): SseEmitter.SseEventBuilder = when (event) {
        is SecretaryTaskEvent -> taskEvent(event)
        is SecretaryAssistantMessageEvent -> SseEmitter.event().name("assistant").data(event)
    }

    private fun removeSubscriber(chatId: String, subscriber: Subscriber) {
        subscribersByChatId[chatId]?.remove(subscriber)
        subscribersByChatId.computeIfPresent(chatId) { _, set ->
            if (set.isEmpty()) null else set
        }
    }
}
