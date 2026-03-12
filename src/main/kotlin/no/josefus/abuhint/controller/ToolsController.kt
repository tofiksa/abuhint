package no.josefus.abuhint.controller

import dev.langchain4j.agent.tool.Tool
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import no.josefus.abuhint.dto.ToolInfo
import no.josefus.abuhint.dto.ToolsResponse
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Verktøy")
@RestController
@RequestMapping("/api/tools")
class ToolsController(private val applicationContext: ApplicationContext) {

    @Operation(
        summary = "Hent tilgjengelige AI-verktøy",
        description = """
            Returnerer en liste over alle AI-verktøy som er registrert og tilgjengelige for assistentene.

            Verktøyene er annotert med `@Tool` (LangChain4j) og kan kalles av AI-modellen under en samtale.
            Denne listen oppdateres automatisk når nye verktøy legges til i applikasjonen.

            ### Verktøyoversikt
            | Verktøy | Tilgjengelig for |
            |---------|-----------------|
            | `sendEmail` | Abu-hint, Coach |
            | `generatePresentation` | Abu-hint, Coach |
            | `generateAndEmail` | Abu-hint, Coach |
            | `searchWeb` | Abu-hint, Coach, Tech Advisor |
            | `createPullRequest` | Abu-hint, Coach |
            | `createBranchAndCommit` | Abu-hint, Coach |
            | `getBranch` | Abu-hint, Coach |
            | `pushToMain` | Abu-hint, Coach |
        """,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Liste over tilgjengelige verktøy",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = ToolsResponse::class),
                    examples = [ExampleObject(
                        name = "Alle verktøy",
                        value = """{
  "tools": [
    {"name": "createBranchAndCommit", "description": "Opprett en ny gren og gjør en commit med gitt innhold"},
    {"name": "createPullRequest", "description": "Opprett en pull request i GitHub-repositoriet"},
    {"name": "generateAndEmail", "description": "Generer en PowerPoint-presentasjon og send den som vedlegg på e-post"},
    {"name": "generatePresentation", "description": "Generer en PowerPoint-presentasjon og last den opp for nedlasting"},
    {"name": "getBranch", "description": "Hent innholdet i README.md fra en gitt gren"},
    {"name": "pushToMain", "description": "Flett en gren inn i main ved å opprette en pull request"},
    {"name": "searchWeb", "description": "Utfør et kort websøk for å hente oppdatert informasjon"},
    {"name": "sendEmail", "description": "Send en e-post med valgfri vedlegg via Resend API"}
  ]
}""",
                    )],
                )],
            ),
        ],
    )
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listTools(): ResponseEntity<ToolsResponse> {
        val tools = applicationContext.beanDefinitionNames
            .flatMap { beanName ->
                val bean = runCatching { applicationContext.getBean(beanName) }.getOrNull()
                    ?: return@flatMap emptyList()
                bean::class.java.methods
                    .mapNotNull { method ->
                        val annotation = method.getAnnotation(Tool::class.java) ?: return@mapNotNull null
                        val name = annotation.name.takeIf { it.isNotBlank() } ?: method.name
                        val description = annotation.value.joinToString(" ").trim().takeIf { it.isNotBlank() }
                            ?: name
                        ToolInfo(name = name, description = description)
                    }
            }
            .distinctBy { it.name }
            .sortedBy { it.name }

        return ResponseEntity.ok(ToolsResponse(tools = tools))
    }
}
