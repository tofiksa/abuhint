package no.josefus.abuhint.dto

data class ChatRequest(
    val message: String
)

data class ChatResponse(
    val reply: String,
    val uuid: String
)