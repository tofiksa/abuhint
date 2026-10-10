package no.josefus.abuhint.secretary

data class SecretaryTaskView(
    val id: String,
    val chatId: String,
    val title: String,
    val description: String?,
    val status: String,
    val assignedAgentId: String?,
    val delegatedBrief: String?,
    val resultSummary: String?,
    val errorMessage: String?,
    val requiresConfirmation: Boolean,
    val acceptanceCriteria: String?,
    val sortOrder: Int,
)

data class SecretaryTaskSnapshot(
    val type: String = "task.snapshot",
    val tasks: List<SecretaryTaskView>,
)

fun SecretaryTaskEntity.toView() = SecretaryTaskView(
    id = id.toString(),
    chatId = chatId,
    title = title,
    description = description,
    status = status.name,
    assignedAgentId = assignedAgentId,
    delegatedBrief = delegatedBrief,
    resultSummary = resultSummary,
    errorMessage = errorMessage,
    requiresConfirmation = requiresConfirmation,
    acceptanceCriteria = acceptanceCriteria,
    sortOrder = sortOrder,
)
