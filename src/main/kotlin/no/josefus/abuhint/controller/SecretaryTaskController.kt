package no.josefus.abuhint.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import no.josefus.abuhint.secretary.SecretaryTaskEventHub
import no.josefus.abuhint.secretary.SecretaryTaskService
import no.josefus.abuhint.secretary.SecretaryTaskSnapshot
import no.josefus.abuhint.secretary.SecretaryTaskView
import no.josefus.abuhint.secretary.toView
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "Secretary")
@RestController
@RequestMapping("/api/secretary/tasks")
class SecretaryTaskController(
    private val taskService: SecretaryTaskService,
    private val eventHub: SecretaryTaskEventHub,
) {

    @Operation(
        summary = "List oppgaver for en sekretær-samtale",
        parameters = [
            Parameter(
                name = "chatId",
                description = "Samtale-ID som oppgavene tilhører.",
                required = true,
            ),
        ],
        responses = [
            ApiResponse(
                responseCode = "200",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = ArraySchema(schema = Schema(implementation = SecretaryTaskView::class)),
                )],
            ),
        ],
    )
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listTasks(@RequestParam chatId: String): List<SecretaryTaskView> =
        taskService.listTasks(chatId, authenticatedUserId()).map { it.toView() }

    @Operation(
        summary = "Abonner på oppgavehendelser (SSE)",
        description = "Sender først et task.snapshot, deretter levende task-hendelser for samtalen.",
        parameters = [
            Parameter(
                name = "chatId",
                description = "Samtale-ID som hendelsene tilhører.",
                required = true,
            ),
        ],
        responses = [
            ApiResponse(
                responseCode = "200",
                content = [Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = Schema(implementation = SecretaryTaskSnapshot::class),
                )],
            ),
        ],
    )
    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun taskEvents(@RequestParam chatId: String): SseEmitter {
        val userId = authenticatedUserId()
        val emitter = eventHub.subscribe(chatId, userId, TASK_EVENT_TIMEOUT_MS)
        val tasks = taskService.listTasks(chatId, userId).map { it.toView() }
        try {
            emitter.send(
                SseEmitter.event()
                    .name("task")
                    .data(SecretaryTaskSnapshot(tasks = tasks)),
            )
        } catch (error: Exception) {
            emitter.completeWithError(error)
        }
        return emitter
    }

    private fun authenticatedUserId(): String =
        SecurityContextHolder.getContext().authentication?.name
            ?: throw IllegalStateException("No authenticated user in SecurityContext")

    private companion object {
        const val TASK_EVENT_TIMEOUT_MS = 30 * 60 * 1000L
    }
}
