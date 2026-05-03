package no.josefus.abuhint.secretary

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.agent.tool.ToolMemoryId
import no.josefus.abuhint.service.TokenUsageContextHolder
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

@Component("secretaryTaskTool")
class SecretaryTaskTool(
    private val taskService: SecretaryTaskService,
) {

    private val json = jacksonObjectMapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private fun userId(): String =
        SecurityContextHolder.getContext().authentication?.name?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Ingen autentisert bruker")

    private fun clientChatId(memoryId: String): String = SecretaryChatIds.clientChatIdFromMemory(memoryId)

    private fun usageOrThrow() =
        TokenUsageContextHolder.get() ?: throw IllegalStateException("Mangler TokenUsageContext (intern feil)")

    @Tool("List alle oppgaver for denne samtalen som JSON.")
    fun listSecretaryTasks(@ToolMemoryId memoryId: String): String {
        val tasks = taskService.listTasks(clientChatId(memoryId))
        return json.writeValueAsString(tasks.map { it.toView() })
    }

    @Tool("Oppsummer todo-listen som lesbar tekst for brukeren.")
    fun summarizeSecretaryTaskList(@ToolMemoryId memoryId: String): String =
        taskService.summarizeList(clientChatId(memoryId))

    @Tool("Opprett en ny oppgave. status settes til proposed. assignedAgentId er valgfritt (research|delivery|github|coach|tech|calendar).")
    fun createSecretaryTask(
        @ToolMemoryId memoryId: String,
        @P("Kort tittel") title: String,
        @P("Beskrivelse") description: String?,
        @P("Hvilken worker som skal utføre når du delegérer") assignedAgentId: String?,
        @P("True hvis endringer krever eksplisitt brukerbekreftelse") requiresConfirmation: Boolean,
        @P("Akseptkriterier / forventet resultat") acceptanceCriteria: String?,
    ): String {
        val created = taskService.createTask(
            userId = userId(),
            clientChatId = clientChatId(memoryId),
            title = title,
            description = description,
            assignedAgentId = assignedAgentId,
            requiresConfirmation = requiresConfirmation,
            acceptanceCriteria = acceptanceCriteria,
        )
        return json.writeValueAsString(created.toView())
    }

    @Tool("Oppdater en oppgave. taskId er UUID. status kan være proposed|blocked|ready|delegated|waiting_for_confirmation|running|done|failed.")
    fun updateSecretaryTask(
        @ToolMemoryId memoryId: String,
        @P("Task UUID") taskId: String,
        @P("Valgfri ny status") status: String?,
        @P("Valgfri ny tittel") title: String?,
        @P("Valgfri ny beskrivelse") description: String?,
        @P("assignedAgentId") assignedAgentId: String?,
        @P("delegert brief til worker (smal kontekst)") delegatedBrief: String?,
        @P("requiresConfirmation") requiresConfirmation: Boolean?,
        @P("acceptanceCriteria") acceptanceCriteria: String?,
    ): String {
        val id = UUID.fromString(taskId)
        val st = status?.let { parseStatus(it) }
        val updated = taskService.updateTask(
            taskId = id,
            userId = userId(),
            status = st,
            title = title,
            description = description,
            assignedAgentId = assignedAgentId,
            delegatedBrief = delegatedBrief,
            requiresConfirmation = requiresConfirmation,
            acceptanceCriteria = acceptanceCriteria,
        )
        return json.writeValueAsString(updated.toView())
    }

    @Tool("Sett status ready (klar til delegasjon).")
    fun markSecretaryTaskReady(@ToolMemoryId memoryId: String, @P("Task UUID") taskId: String): String =
        updateSecretaryTask(memoryId, taskId, SecretaryTaskStatus.ready.name, null, null, null, null, null, null)

    @Tool("Sett status blocked og beskriv årsak i beskrivelse ved behov.")
    fun markSecretaryTaskBlocked(
        @ToolMemoryId memoryId: String,
        @P("Task UUID") taskId: String,
        @P("Kort hvorfor blokkert") reason: String,
    ): String =
        updateSecretaryTask(memoryId, taskId, SecretaryTaskStatus.blocked.name, null, reason, null, null, null, null)

    @Tool("Marker oppgave som ferdig manuelt (uten worker).")
    fun markSecretaryTaskDone(@ToolMemoryId memoryId: String, @P("Task UUID") taskId: String): String {
        val id = UUID.fromString(taskId)
        val t = taskService.markDone(id, userId())
        return json.writeValueAsString(t.toView())
    }

    @Tool("Delegér oppgaven til valgt worker. Krev at assignedAgentId og delegatedBrief er satt.")
    fun delegateSecretaryTask(@ToolMemoryId memoryId: String, @P("Task UUID") taskId: String): String {
        val id = UUID.fromString(taskId)
        val ctx = usageOrThrow()
        val delegated = taskService.delegateTask(id, userId(), ctx)
        return json.writeValueAsString(delegated.toView())
    }

    @Tool("Hent én oppgave som JSON.")
    fun getSecretaryTask(@ToolMemoryId memoryId: String, @P("Task UUID") taskId: String): String {
        val id = UUID.fromString(taskId)
        val t = taskService.getTask(id, userId()) ?: return """{"error":"not found"}"""
        return json.writeValueAsString(t.toView())
    }

    private fun parseStatus(raw: String): SecretaryTaskStatus =
        SecretaryTaskStatus.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("Ugyldig status: $raw")

    private fun SecretaryTaskEntity.toView() = mapOf(
        "id" to id.toString(),
        "chatId" to chatId,
        "title" to title,
        "description" to description,
        "status" to status.name,
        "assignedAgentId" to assignedAgentId,
        "delegatedBrief" to delegatedBrief,
        "resultSummary" to resultSummary,
        "errorMessage" to errorMessage,
        "requiresConfirmation" to requiresConfirmation,
        "acceptanceCriteria" to acceptanceCriteria,
        "sortOrder" to sortOrder,
    )
}
