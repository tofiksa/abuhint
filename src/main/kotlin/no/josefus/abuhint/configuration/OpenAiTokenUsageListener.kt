package no.josefus.abuhint.configuration

import dev.langchain4j.model.chat.listener.ChatModelErrorContext
import dev.langchain4j.model.chat.listener.ChatModelListener
import dev.langchain4j.model.chat.listener.ChatModelRequestContext
import dev.langchain4j.model.chat.listener.ChatModelResponseContext
import dev.langchain4j.model.openai.OpenAiTokenUsage
import no.josefus.abuhint.service.ChatIdContextHolder
import no.josefus.abuhint.service.TokenUsageEntry
import no.josefus.abuhint.service.TokenUsageContext
import no.josefus.abuhint.service.TokenUsageContextHolder
import no.josefus.abuhint.service.TokenUsageStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class OpenAiTokenUsageListener(
    private val tokenUsageStore: TokenUsageStore,
) : ChatModelListener {

    private val log = LoggerFactory.getLogger(OpenAiTokenUsageListener::class.java)

    companion object {
        private const val CHAT_ID_ATTR = "chatId"
        private const val USAGE_CONTEXT_ATTR = "tokenUsageContext"
    }

    override fun onRequest(requestContext: ChatModelRequestContext) {
        val usageContext = TokenUsageContextHolder.get()
        if (usageContext != null) {
            requestContext.attributes()[USAGE_CONTEXT_ATTR] = usageContext
        }

        val chatId = usageContext?.chatId ?: ChatIdContextHolder.get()
        if (chatId != null) {
            requestContext.attributes()[CHAT_ID_ATTR] = chatId
        }
    }

    override fun onResponse(responseContext: ChatModelResponseContext) {
        val usageContext = responseContext.attributes()[USAGE_CONTEXT_ATTR] as? TokenUsageContext
        val chatId = usageContext?.chatId ?: responseContext.attributes()[CHAT_ID_ATTR] as? String
        if (chatId == null) {
            log.debug("No chatId in listener context; skipping token usage recording")
            return
        }

        val tokenUsage = responseContext.chatResponse().tokenUsage()
        val modelName = responseContext.chatResponse().modelName() ?: "unknown"

        val inputTokens = tokenUsage?.inputTokenCount() ?: 0
        val outputTokens = tokenUsage?.outputTokenCount() ?: 0
        val cachedTokens = if (tokenUsage is OpenAiTokenUsage) {
            tokenUsage.inputTokensDetails()?.cachedTokens() ?: 0
        } else {
            0
        }

        val entry = TokenUsageEntry(
            inputTokens = inputTokens,
            cachedInputTokens = cachedTokens,
            outputTokens = outputTokens,
            modelName = modelName,
        )

        if (usageContext != null) {
            tokenUsageStore.record(usageContext, entry)
        } else {
            tokenUsageStore.record(chatId, entry)
        }

        log.info(
            "Token usage for userId={} chatId={} assistant={} platform={}: input={} (cached={}) output={} model={}",
            usageContext?.userId ?: "unknown",
            chatId,
            usageContext?.assistant ?: "unknown",
            usageContext?.clientPlatform ?: "unknown",
            inputTokens,
            cachedTokens,
            outputTokens,
            modelName,
        )
    }

    override fun onError(errorContext: ChatModelErrorContext) {
        val chatId = errorContext.attributes()[CHAT_ID_ATTR] as? String
        log.warn("Chat model error for chatId={}: {}", chatId ?: "unknown", errorContext.error().message)
    }
}
