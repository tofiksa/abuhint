package no.josefus.abuhint.service

import org.slf4j.LoggerFactory
import java.lang.reflect.Method

/**
 * Simple tokenizer interface for estimating token counts.
 * In LangChain4j 1.9+, Tokenizer is not available as a separate class,
 * so we provide a simple implementation based on character count approximation.
 */
interface Tokenizer {
    fun estimateTokenCount(text: String): Int
}

/**
 * Simple tokenizer implementation using character-based approximation.
 * Approximate: 1 token ≈ 4 characters (common for English text)
 */
class SimpleTokenizer : Tokenizer {
    override fun estimateTokenCount(text: String): Int {
        // Simple approximation: 1 token ≈ 4 characters
        // This is a rough estimate, but sufficient for limiting context size
        return (text.length / 4).coerceAtLeast(1)
    }
}

/**
 * Tokenizer that tries to use model-aware tokenizers when available.
 * Falls back to the simple tokenizer if no model tokenizer is found.
 */
class ModelAwareTokenizer(
    private val modelName: String,
    private val fallback: Tokenizer = SimpleTokenizer()
) : Tokenizer {
    private val logger = LoggerFactory.getLogger(ModelAwareTokenizer::class.java)
    private val delegate = createDelegate()
    private val countMethod = delegate?.let { resolveCountMethod(it) }

    override fun estimateTokenCount(text: String): Int {
        if (delegate == null || countMethod == null) {
            return fallback.estimateTokenCount(text)
        }
        return try {
            val result = countMethod.invoke(delegate, text)
            when (result) {
                is Int -> result
                is Long -> result.toInt()
                is Number -> result.toInt()
                else -> fallback.estimateTokenCount(text)
            }
        } catch (e: Exception) {
            logger.debug("Failed to count tokens with model tokenizer for {}: {}", modelName, e.message)
            fallback.estimateTokenCount(text)
        }
    }

    private fun createDelegate(): Any? {
        val isGemini = modelName.lowercase().contains("gemini")
        val classNames = if (isGemini) {
            listOf(
                "dev.langchain4j.model.googleai.GeminiTokenizer",
                "dev.langchain4j.model.googleai.GoogleAiGeminiTokenizer"
            )
        } else {
            listOf("dev.langchain4j.model.openai.OpenAiTokenizer")
        }

        for (className in classNames) {
            val clazz = try {
                Class.forName(className)
            } catch (_: ClassNotFoundException) {
                null
            }
            if (clazz != null) {
                val instance = instantiateTokenizer(clazz, modelName)
                if (instance != null && resolveCountMethod(instance) != null) {
                    return instance
                }
            }
        }
        logger.debug("Model-aware tokenizer not available for {}; using fallback", modelName)
        return null
    }

    private fun instantiateTokenizer(clazz: Class<*>, modelName: String): Any? {
        clazz.constructors.firstOrNull { constructor ->
            val params = constructor.parameterTypes
            params.size == 1 && params[0] == String::class.java
        }?.let { constructor ->
            return try {
                constructor.newInstance(modelName)
            } catch (_: Exception) {
                null
            }
        }

        clazz.constructors.firstOrNull { it.parameterTypes.isEmpty() }?.let { constructor ->
            return try {
                constructor.newInstance()
            } catch (_: Exception) {
                null
            }
        }

        return null
    }

    private fun resolveCountMethod(instance: Any): Method? {
        return instance.javaClass.methods.firstOrNull { method ->
            method.parameterCount == 1 &&
                method.parameterTypes[0] == String::class.java &&
                (method.name == "estimateTokenCount" || method.name == "countTokens")
        }
    }
}
