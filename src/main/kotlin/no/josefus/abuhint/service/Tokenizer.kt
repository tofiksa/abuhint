package no.josefus.abuhint.service

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

