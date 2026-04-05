package no.josefus.abuhint.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Chat completion-forespørsel i OpenAI-kompatibelt format")
data class OpenAiCompatibleChatCompletionRequest(
    @field:Schema(
        description = "Modell-identifikator. Ignoreres av serveren – modellen er konfigurert i application.yml.",
        example = "default",
    )
    val model: String = "default",

    @field:Schema(description = "Samtalehistorikk som en liste med meldinger. Minst én melding er påkrevd.")
    val messages: List<OpenAiCompatibleChatMessage>,

    @field:Schema(
        description = "Maksimalt antall tokens i svaret. Ignoreres – satt i serverkonfigurasjon.",
        example = "16384",
    )
    val maxCompletionTokens: Int = 16384,

    @field:Schema(
        description = "Kreativitetsnivå (0.0 = deterministisk, 1.0 = kreativ). Ignoreres – satt i serverkonfigurasjon.",
        example = "0.0",
    )
    val temperature: Float = 0.0f,

    @field:Schema(
        description = "Streaming-modus. Støttes ikke – returnerer alltid komplett svar.",
        example = "false",
    )
    val stream: Boolean = false,

    @JsonAlias("chat_id")
    @field:Schema(
        description = "Abuhint-spesifikt felt for samtaleminne. Send samme UUID på tvers av kall for å opprettholde kontekst.",
        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    )
    val chatId: String? = null,
)

@Schema(description = "En enkelt melding i samtalen")
data class OpenAiCompatibleChatMessage(
    @field:Schema(
        description = "Avsenderrollen. Gyldige verdier: `system`, `user`, `assistant`.",
        example = "user",
        allowableValues = ["system", "user", "assistant"],
    )
    val role: String = "user",

    @field:Schema(
        description = "Innholdet i meldingen. Kan være en enkel streng eller en strukturert liste med innholdselementer (tekst og bilder).",
        example = "Hva er forskjellen mellom Kotlin og Java?",
    )
    @JsonDeserialize(using = ContentDeserializer::class)
    @JsonSerialize(using = ContentSerializer::class)
    val content: List<OpenAiCompatibleContentItem>? = null,
)

@Schema(description = "Et enkelt innholdselement i en melding")
data class OpenAiCompatibleContentItem(
    @field:Schema(
        description = "Innholdstype. `text` for tekstinnhold, `image_url` for bilde-URL.",
        example = "text",
        allowableValues = ["text", "image_url"],
    )
    val type: String = "text",

    @field:Schema(
        description = "Tekstinnholdet. Brukes når `type` er `text`.",
        example = "Hva er forskjellen mellom Kotlin og Java?",
    )
    val text: String? = null,

    @JsonProperty("image_url")
    @field:Schema(description = "Bilde-URL-detaljer. Brukes når `type` er `image_url`.")
    val imageUrl: ImageUrl? = null,
)

@Schema(description = "Bilde-URL med analysedetalj-nivå")
data class ImageUrl(
    @field:Schema(
        description = "URL til bildet. Kan være http(s)-URL eller base64 data URI.",
        example = "https://example.com/bilde.jpg",
    )
    val url: String,

    @field:Schema(
        description = "Ønsket detaljeringsnivå for bildeanalyse.",
        example = "auto",
        allowableValues = ["auto", "low", "high"],
    )
    val detail: String? = "auto",
)

@Schema(description = "Komplett svar fra chat completion API i OpenAI-format")
data class OpenAiCompatibleChatCompletionResponse(
    @field:Schema(description = "Unik identifikator for svaret", example = "chatcmpl-abc123")
    val id: String,

    @field:Schema(description = "Objekttype", example = "chat.completion")
    val `object`: String,

    @field:Schema(description = "Unix-tidsstempel for når svaret ble generert", example = "1748732400")
    val created: Long,

    @field:Schema(description = "Modellen som ble brukt (konfigurert i application.yml)")
    val model: String,

    @field:Schema(description = "Liste med svaralternativer. Inneholder alltid ett element.")
    val choices: List<OpenAiCompatibleChoice>,

    @field:Schema(description = "Token-bruksstatistikk for forespørselen")
    val usage: OpenAiCompatibleUsage? = null,
)

@Schema(description = "Et enkelt svaralternativ")
data class OpenAiCompatibleChoice(
    @field:Schema(description = "Den genererte meldingen")
    val message: OpenAiCompatibleChatMessage,

    @field:Schema(
        description = "Årsak til at genereringen stoppet",
        example = "stop",
        allowableValues = ["stop", "length", "content_filter"],
    )
    val finishReason: String? = null,
)

@Schema(description = "Streaming-chunk (ikke støttet i denne implementasjonen)")
data class OpenAiCompatibleChatCompletionChunk(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAiCompatibleChunkChoice>,
)

@Schema(description = "Svaralternativ i en streaming-chunk")
data class OpenAiCompatibleChunkChoice(
    val delta: OpenAiCompatibleDelta,
    val finishReason: String? = null,
)

@Schema(description = "Inkrementell endring i et streaming-svar")
data class OpenAiCompatibleDelta(
    @field:Schema(description = "Nytt innhold i denne chunken")
    val content: String? = null,

    @field:Schema(description = "Rolle i denne chunken")
    val role: String? = null,
)

@Schema(description = "Token-bruksstatistikk")
data class OpenAiCompatibleUsage(
    @field:Schema(description = "Antall tokens i inndataprompt", example = "18")
    val promptTokens: Int,

    @field:Schema(description = "Antall tokens i det genererte svaret", example = "64")
    val completionTokens: Int,

    @field:Schema(description = "Totalt antall tokens brukt", example = "82")
    val totalTokens: Int,
)

/**
 * Custom serializer for chat message content.
 * Converts structured content arrays to string format for compatibility with litellm.
 */
class ContentSerializer : JsonSerializer<List<OpenAiCompatibleContentItem>>() {

    override fun serialize(
        value: List<OpenAiCompatibleContentItem>?,
        gen: JsonGenerator,
        serializers: SerializerProvider,
    ) {
        when {
            value == null -> gen.writeNull()
            value.isEmpty() -> gen.writeString("")
            else -> {
                val combinedText = value.mapNotNull { item ->
                    when (item.type) {
                        "text" -> item.text
                        else -> null
                    }
                }.joinToString("\n")
                gen.writeString(combinedText)
            }
        }
    }
}

/**
 * Custom deserializer for chat message content.
 * Handles both string-only content and structured content arrays.
 */
class ContentDeserializer : JsonDeserializer<List<OpenAiCompatibleContentItem>>() {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): List<OpenAiCompatibleContentItem> {
        return when (p.currentToken) {
            JsonToken.VALUE_STRING -> {
                listOf(OpenAiCompatibleContentItem(type = "text", text = p.valueAsString))
            }
            JsonToken.START_ARRAY -> {
                val typeRef = object : TypeReference<List<OpenAiCompatibleContentItem>>() {}
                p.codec.readValue(p, typeRef)
            }
            JsonToken.VALUE_NULL -> {
                emptyList()
            }
            else -> {
                throw ctxt.weirdStringException(p.text, List::class.java, "Unexpected JSON token")
            }
        }
    }
}
