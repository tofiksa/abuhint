package no.josefus.abuhint.service

import no.josefus.abuhint.dto.OpenAiCompatibleChatCompletionRequest
import no.josefus.abuhint.dto.OpenAiCompatibleChatCompletionResponse
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

interface OpenAiCompatibleService {
    fun createChatCompletion(request: OpenAiCompatibleChatCompletionRequest): OpenAiCompatibleChatCompletionResponse
    fun createStreamingChatCompletion(request: OpenAiCompatibleChatCompletionRequest): SseEmitter
}
