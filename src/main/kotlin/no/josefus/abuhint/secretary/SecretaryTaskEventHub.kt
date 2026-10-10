package no.josefus.abuhint.secretary

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
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

    fun subscribe(chatId: String, userId: String, timeoutMs: Long): SseEmitter {
        val emitter = SseEmitter(timeoutMs)
        val subscriber = Subscriber(userId, emitter)
        subscribersByChatId.computeIfAbsent(chatId) { CopyOnWriteArraySet() }.add(subscriber)

        val remove = { removeSubscriber(chatId, subscriber) }
        emitter.onCompletion(remove)
        emitter.onTimeout(remove)
        emitter.onError { remove() }

        return emitter
    }

    @EventListener
    fun on(event: SecretaryTaskEvent) {
        val subscribers = subscribersByChatId[event.chatId] ?: return
        for (subscriber in subscribers) {
            if (subscriber.userId != event.userId) {
                continue
            }
            try {
                subscriber.emitter.send(
                    SseEmitter.event()
                        .name("task")
                        .data(event),
                )
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
                    subscriber.emitter.send(SseEmitter.event().comment("ping"))
                } catch (e: Exception) {
                    log.debug("Removing SSE subscriber after heartbeat failure chatId={}", chatId)
                    removeSubscriber(chatId, subscriber)
                }
            }
        }
    }

    private fun removeSubscriber(chatId: String, subscriber: Subscriber) {
        subscribersByChatId[chatId]?.remove(subscriber)
        subscribersByChatId.computeIfPresent(chatId) { _, set ->
            if (set.isEmpty()) null else set
        }
    }
}
