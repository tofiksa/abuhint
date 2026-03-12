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
