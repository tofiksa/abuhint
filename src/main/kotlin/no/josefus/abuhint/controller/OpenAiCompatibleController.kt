package no.josefus.abuhint.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import no.josefus.abuhint.dto.OpenAiCompatibleChatCompletionRequest
import no.josefus.abuhint.dto.OpenAiCompatibleChatCompletionResponse
import no.josefus.abuhint.service.OpenAiCompatibleService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "OpenAI-kompatibel")
@RestController
@RequestMapping("/v1/chat/completions")
class OpenAiCompatibleController(
    private val openAiCompatibleService: OpenAiCompatibleService,
) {

    @Operation(
        summary = "OpenAI Chat Completions-kompatibelt endepunkt",
        description = """
            Drop-in-erstatning for OpenAI `/v1/chat/completions`. Kan brukes direkte med klienter som
            støtter OpenAI-protokollen, f.eks. **LiteLLM**, **OpenWebUI**, **Continue.dev** eller egne integrasjoner.

            ### Konfigurasjon i OpenAI-kompatible klienter
            Sett base URL til `https://<host>/v1` og bruk en vilkårlig API-nøkkel (autentisering er ikke påkrevd).

            ### Støttede felt
            | Felt | Støtte | Merknad |
            |------|--------|---------|
            | `model` | Ignoreres | Konfigurert i application.yml |
            | `messages` | Ja | Støtter string-innhold og strukturerte content-arrays |
            | `temperature` | Ignoreres | Satt i serverkonfigurasjon |
            | `stream` | Nei | Returnerer alltid komplett svar |
            | `chat_id` | Ja | Eget felt for samtaleminne (ikke standard OpenAI) |

            ### Samtaleminne via `chat_id`
            Send med `chat_id` i request-kroppen for å opprettholde samtalehistorikk på tvers av kall.
            Dette er et Abuhint-spesifikt tillegg og ignoreres av standard OpenAI-klienter.
        """,
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = OpenAiCompatibleChatCompletionRequest::class),
                examples = [
                    ExampleObject(
                        name = "Enkel melding",
                        summary = "Minimal forespørsel",
                        value = """{
  "model": "default",
  "messages": [
    {"role": "user", "content": "Forklar forskjellen mellom Kotlin data classes og vanlige klasser."}
  ]
}""",
                    ),
                    ExampleObject(
                        name = "Med samtaleminne",
                        summary = "Fortsetter en eksisterende samtale",
                        value = """{
  "model": "default",
  "chat_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "messages": [
    {"role": "user", "content": "Hva anbefalte du i stad?"}
  ]
}""",
                    ),
                    ExampleObject(
                        name = "Strukturert innhold",
                        summary = "Med bilde-URL (multimodalt)",
                        value = """{
  "model": "default",
  "messages": [
    {
      "role": "user",
      "content": [
        {"type": "text", "text": "Hva ser du på dette bildet?"},
        {"type": "image_url", "image_url": {"url": "https://example.com/bilde.jpg"}}
      ]
    }
  ]
}""",
                    ),
                ],
            )],
        ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Vellykket svar i OpenAI Chat Completions-format",
                content = [Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = Schema(implementation = OpenAiCompatibleChatCompletionResponse::class),
                    examples = [ExampleObject(
                        name = "Standard svar",
                        value = """{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1748732400,
  "model": "configured-in-application-yml",
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "Kotlin data classes genererer automatisk equals(), hashCode(), toString() og copy()..."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 18,
    "completion_tokens": 64,
    "total_tokens": 82
  }
}""",
                    )],
                )],
            ),
        ],
    )
    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun createChatCompletion(
        @RequestBody request: OpenAiCompatibleChatCompletionRequest,
    ): ResponseEntity<OpenAiCompatibleChatCompletionResponse> {
        val response = openAiCompatibleService.createChatCompletion(request)
        return ResponseEntity.ok(response)
    }

    @Operation(
        summary = "OpenAI-kompatibel streaming (SSE)",
        description = "Streamer svaret token for token via Server-Sent Events, kompatibelt med OpenAI stream-protokollen.",
    )
    @PostMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun createStreamingChatCompletion(
        @RequestBody request: OpenAiCompatibleChatCompletionRequest,
    ): SseEmitter {
        return openAiCompatibleService.createStreamingChatCompletion(request)
    }
}
