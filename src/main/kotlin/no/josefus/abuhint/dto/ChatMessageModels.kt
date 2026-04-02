package no.josefus.abuhint.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Forespørsel med brukerens melding")
data class ChatRequest(
    @field:Schema(description = "Meldingsteksten som sendes til assistenten", example = "Hva er de viktigste prinsippene for god teamledelse?")
    val message: String,
)

@Schema(description = "Svar fra assistenten")
data class ChatResponse(
    @field:Schema(description = "Svarteksten fra assistenten")
    val reply: String,
    @field:Schema(description = "Unik identifikator for svaret", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    val uuid: String,
)

@Schema(description = "En melding i samtalehistorikken")
data class ChatHistoryMessage(
    @field:Schema(description = "Meldingstype: USER, AI, eller SYSTEM", example = "USER")
    val role: String,
    @field:Schema(description = "Meldingsinnholdet")
    val content: String,
)

@Schema(description = "Paginert samtalehistorikk")
data class ChatHistoryResponse(
    @field:Schema(description = "Samtale-ID")
    val chatId: String,
    @field:Schema(description = "Meldingene i samtalen")
    val messages: List<ChatHistoryMessage>,
    @field:Schema(description = "Totalt antall meldinger")
    val total: Int,
    @field:Schema(description = "Offset brukt i forespørselen")
    val offset: Int,
    @field:Schema(description = "Maks antall meldinger returnert")
    val limit: Int,
)

@Schema(description = "Tokenforbruk for en samtale")
data class TokenUsageResponse(
    @field:Schema(description = "Samtale-ID")
    val chatId: String,
    @field:Schema(description = "Totalt antall input-tokens sendt til API")
    val inputTokens: Long,
    @field:Schema(description = "Antall input-tokens som traff cache (redusert kostnad)")
    val cachedInputTokens: Long,
    @field:Schema(description = "Totalt antall output-tokens mottatt fra API")
    val outputTokens: Long,
    @field:Schema(description = "Sum av input + output tokens")
    val totalTokens: Long,
    @field:Schema(description = "Antall API-kall for denne samtalen")
    val requestCount: Int,
    @field:Schema(description = "Siste modell brukt")
    val modelName: String,
)

@Schema(description = "Informasjon om et enkelt AI-verktøy")
data class ToolInfo(
    @field:Schema(description = "Verktøyets unike navn", example = "searchWeb")
    val name: String,
    @field:Schema(description = "Beskrivelse av hva verktøyet gjør", example = "Utfør et kort websøk for å hente oppdatert informasjon")
    val description: String,
)

@Schema(description = "Liste over tilgjengelige AI-verktøy")
data class ToolsResponse(
    @field:Schema(description = "Sortert liste over alle registrerte verktøy")
    val tools: List<ToolInfo>,
)
