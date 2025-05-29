package no.josefus.abuhint.controller

import no.josefus.abuhint.dto.OpenAiCompatibleChatCompletionRequest
import no.josefus.abuhint.dto.OpenAiCompatibleChatCompletionResponse
import no.josefus.abuhint.service.OpenAiCompatibleService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/chat/completions")
class OpenAiCompatibleController(
    private val openAiCompatibleService: OpenAiCompatibleService
) {
    @PostMapping
    fun createChatCompletion(
        @RequestBody request: OpenAiCompatibleChatCompletionRequest
    ): ResponseEntity<OpenAiCompatibleChatCompletionResponse> {
        val response = openAiCompatibleService.createChatCompletion(request)
        return ResponseEntity.ok(response)
    }
}

