package no.josefus.abuhint.secretary

import java.time.Instant

/** Anything pushed to a user's secretary SSE stream; routed by [chatId] + [userId]. */
sealed interface SecretaryStreamEvent {
    val chatId: String
    val userId: String
}

data class SecretaryTaskEvent(
    val type: String,
    val taskId: String,
    override val chatId: String,
    override val userId: String,
    val title: String,
    val status: String,
    val assignedAgentId: String?,
    val resultSummary: String?,
    val errorMessage: String?,
    val at: Instant = Instant.now(),
) : SecretaryStreamEvent {
    companion object {
        fun from(type: String, entity: SecretaryTaskEntity): SecretaryTaskEvent =
            SecretaryTaskEvent(
                type = type,
                taskId = entity.id.toString(),
                chatId = entity.chatId,
                userId = entity.userId,
                title = entity.title,
                status = entity.status.name,
                assignedAgentId = entity.assignedAgentId,
                resultSummary = entity.resultSummary,
                errorMessage = entity.errorMessage,
            )
    }
}

/** A secretary reply produced outside a chat turn (e.g. after a backgrounded task finished). */
data class SecretaryAssistantMessageEvent(
    val taskId: String,
    override val chatId: String,
    override val userId: String,
    val text: String,
    val type: String = "assistant.message",
    val at: Instant = Instant.now(),
) : SecretaryStreamEvent
