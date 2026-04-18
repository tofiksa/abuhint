package no.josefus.abuhint.familie

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Valgfri klient-metadata slik at agenten kan tolke relative datoer i brukerens tidssone.")
data class FamilieClientMetadata(
    @field:Schema(description = "IANA tidssone, f.eks. Europe/Oslo", example = "Europe/Oslo")
    val timezone: String? = null,
    @field:Schema(description = "ISO-8601 tidspunkt når meldingen ble sendt fra enheten", example = "2026-04-18T18:59:44Z")
    val sentAt: String? = null,
)

@Schema(description = "Forespørsel med brukerens melding til Familieplanleggern")
data class FamilieMessageRequest(
    @field:Schema(description = "Meldingsteksten", example = "Legg til middag med svigerforeldrene på lørdag 18:00")
    val message: String,
    val metadata: FamilieClientMetadata? = null,
)
