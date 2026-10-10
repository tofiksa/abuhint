package no.josefus.abuhint.secretary

import java.time.Instant

data class SecretaryTaskEvent(
    val type: String,
    val taskId: String,
    val chatId: String,
    val userId: String,
    val title: String,
    val status: String,
    val assignedAgentId: String?,
    val resultSummary: String?,
    val errorMessage: String?,
    val at: Instant = Instant.now(),
) {
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
