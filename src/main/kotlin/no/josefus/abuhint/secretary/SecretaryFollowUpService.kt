package no.josefus.abuhint.secretary

import no.josefus.abuhint.repository.SecretaryAssistant
import no.josefus.abuhint.service.ChatIdContextHolder
import no.josefus.abuhint.service.TokenUsageContext
import no.josefus.abuhint.service.TokenUsageContextHolder
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

/** Published when a task that the secretary had to background has finished (done or failed). */
data class SecretaryTaskBackgroundCompletedEvent(
    val taskId: UUID,
    val context: TokenUsageContext,
)

/**
 * Lets the secretary itself answer the user once a backgrounded task finishes, so the result arrives as a
 * normal secretary message instead of raw worker output. The result is fed into the secretary's chat memory
 * as an `[Oppgaveresultat]` system note, and the reply is pushed over SSE as [SecretaryAssistantMessageEvent].
 */
@Service
class SecretaryFollowUpService(
    private val secretaryAssistant: SecretaryAssistant,
    private val taskRepository: SecretaryTaskRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    private val log = LoggerFactory.getLogger(SecretaryFollowUpService::class.java)

    @EventListener
    fun on(event: SecretaryTaskBackgroundCompletedEvent) {
        val task = taskRepository.findById(event.taskId).orElse(null) ?: return
        if (task.status != SecretaryTaskStatus.done && task.status != SecretaryTaskStatus.failed) {
            log.warn("Skipping follow-up for task {} in status {}", task.id, task.status)
            return
        }

        val reply = try {
            askSecretary(task, event.context)
        } catch (e: Exception) {
            log.error("Secretary follow-up failed for task {}; sending fallback", task.id, e)
            fallbackText(task)
        }

        eventPublisher.publishEvent(
            SecretaryAssistantMessageEvent(
                taskId = task.id.toString(),
                chatId = task.chatId,
                userId = task.userId,
                text = reply,
            ),
        )
    }

    private fun askSecretary(task: SecretaryTaskEntity, baseContext: TokenUsageContext): String {
        val ctx = baseContext.copy(
            chatId = task.chatId,
            userId = task.userId,
            assistant = "SECRETARY",
            taskId = task.id.toString(),
        )
        val prevChat = ChatIdContextHolder.get()
        val prevUsage = TokenUsageContextHolder.get()
        ChatIdContextHolder.set(task.chatId)
        TokenUsageContextHolder.set(ctx)
        return try {
            secretaryAssistant.chat(
                task.chatId,
                resultNote(task),
                UUID.randomUUID().toString(),
                LocalDateTime.now().toString(),
                SecretaryInvocationContext.from(ctx),
            )
        } finally {
            if (prevChat != null) ChatIdContextHolder.set(prevChat) else ChatIdContextHolder.clear()
            if (prevUsage != null) TokenUsageContextHolder.set(prevUsage) else TokenUsageContextHolder.clear()
        }
    }

    private fun resultNote(task: SecretaryTaskEntity): String = buildString {
        appendLine("[Oppgaveresultat]")
        appendLine("Oppgave: ${task.title}")
        if (task.status == SecretaryTaskStatus.failed) {
            appendLine("Status: feilet")
            appendLine("Feil: ${task.errorMessage ?: "ukjent feil"}")
        } else {
            appendLine("Status: ferdig")
            appendLine("Resultat:")
            appendLine(task.resultSummary.orEmpty())
        }
    }.trim()

    private fun fallbackText(task: SecretaryTaskEntity): String =
        if (task.status == SecretaryTaskStatus.failed) {
            "Jeg fikk dessverre ikke svar på «${task.title}». Vil du at jeg prøver igjen?"
        } else {
            task.resultSummary?.takeIf { it.isNotBlank() } ?: "«${task.title}» er ferdig."
        }
}
