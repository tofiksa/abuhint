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

/**
 * Represents a chat completion request in OpenAI-compatible format.
 * @property model The model identifier to use for completion
 * @property messages The conversation history as a list of messages
 * @property maxCompletionTokens Maximum tokens to generate in the response
 * @property temperature Controls randomness in the response (0.0 = deterministic, 1.0 = creative)
 * @property stream Whether to stream the response or return it all at once
 */
data class OpenAiCompatibleChatCompletionRequest(
    val model: String = "gpt-4o",
    val messages: List<OpenAiCompatibleChatMessage>,
    val maxCompletionTokens: Int = 16384,
    val temperature: Float = 0.0f,
    val stream: Boolean = false,
    @JsonAlias("chat_id")
    val chatId: String? = null
)

/**
 * Represents a chat message in OpenAI-compatible format.
 * @property role The role of the message sender (e.g., "system", "user", "assistant")
 * @property content List of content items that can include text and images
 */
data class OpenAiCompatibleChatMessage(
    val role: String = "user",
    @JsonDeserialize(using = ContentDeserializer::class)
    @JsonSerialize(using = ContentSerializer::class)
    val content: List<OpenAiCompatibleContentItem>? = null
)

/**
 * Represents a single content item in a chat message.
 * @property type Content type identifier ("text" or "image_url")
 * @property text The text content if type is "text"
 * @property imageUrl The image URL details if type is "image_url"
 */
data class OpenAiCompatibleContentItem(
    val type: String = "text",
    val text: String? = null,
    @JsonProperty("image_url")
    val imageUrl: ImageUrl? = null
)

/**
 * Contains image URL information for image content items.
 * @property url The actual URL of the image (can be http(s) or base64 data URI)
 * @property detail The desired detail level for image analysis
 */
data class ImageUrl(
    val url: String,
    val detail: String? = "auto"
)

/**
 * Represents a complete response from the chat completion API.
 * @property id Unique identifier for the completion
 * @property object Type identifier for the response
 * @property created Timestamp of when the completion was created
 * @property model The model used for completion
 * @property choices List of completion choices/responses
 * @property usage Token usage statistics for the request
 */
data class OpenAiCompatibleChatCompletionResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAiCompatibleChoice>,
    val usage: OpenAiCompatibleUsage? = null
)

/**
 * Represents a single completion choice in the response.
 * @property message The generated message content
 * @property finishReason Why the completion stopped (e.g., "stop", "length")
 */
data class OpenAiCompatibleChoice(
    val message: OpenAiCompatibleChatMessage,
    val finishReason: String? = null
)

/**
 * Represents a chunk of the streaming response.
 * Used when stream=true in the request.
 */
data class OpenAiCompatibleChatCompletionChunk(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAiCompatibleChunkChoice>
)

/**
 * Represents a choice within a streaming response chunk.
 */
data class OpenAiCompatibleChunkChoice(
    val delta: OpenAiCompatibleDelta,
    val finishReason: String? = null
)

/**
 * Represents the incremental changes in a streaming response.
 */
data class OpenAiCompatibleDelta(
    val content: String? = null,
    val role: String? = null
)

/**
 * Contains token usage statistics for the request.
 * @property promptTokens Number of tokens in the input prompt
 * @property completionTokens Number of tokens in the generated completion
 * @property totalTokens Total tokens used (prompt + completion)
 */
data class OpenAiCompatibleUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * Custom serializer for chat message content.
 * Converts structured content arrays to string format for compatibility with litellm.
 */
class ContentSerializer : JsonSerializer<List<OpenAiCompatibleContentItem>>() {

    override fun serialize(
        value: List<OpenAiCompatibleContentItem>?,
        gen: JsonGenerator,
        serializers: SerializerProvider
    ) {
        when {
            value == null -> gen.writeNull()
            value.isEmpty() -> gen.writeString("")
            else -> {
                // Combine all text content into a single string
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
 * Converts legacy string content to the new structured format for compatibility.
 */
class ContentDeserializer : JsonDeserializer<List<OpenAiCompatibleContentItem>>() {

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): List<OpenAiCompatibleContentItem> {
        return when (p.currentToken) {
            JsonToken.VALUE_STRING -> {
                // Convert legacy string content to structured format
                listOf(OpenAiCompatibleContentItem(type = "text", text = p.valueAsString))
            }

            JsonToken.START_ARRAY -> {
                // Parse structured content array
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
