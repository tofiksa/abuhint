package no.josefus.abuhint.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Service
class TokenUsageStore(
    private val repository: TokenUsageAggregateRepository,
) {

    private val usageMap = ConcurrentHashMap<String, TokenUsageRecord>()

    fun record(chatId: String, entry: TokenUsageEntry) {
        if (!entry.hasAnyTokens()) return
        usageMap.compute(chatId) { _, existing ->
            (existing ?: TokenUsageRecord(chatId = chatId)).apply {
                inputTokens.addAndGet(entry.inputTokens.toLong())
                cachedInputTokens.addAndGet(entry.cachedInputTokens.toLong())
                outputTokens.addAndGet(entry.outputTokens.toLong())
                requestCount.incrementAndGet()
                lastModelName = entry.modelName
            }
        }
    }

    @Transactional
    fun record(context: TokenUsageContext, entry: TokenUsageEntry) {
        if (!entry.hasAnyTokens()) return
        record(context.chatId, entry)

        val normalized = context.normalized()
        val now = Instant.now()
        val entity = repository.findByUserIdAndChatIdAndAssistantAndClientPlatformAndModelName(
            userId = normalized.userId,
            chatId = normalized.chatId,
            assistant = normalized.assistant,
            clientPlatform = normalized.clientPlatform,
            modelName = entry.modelName.normalizedValue("unknown", 128),
        ) ?: TokenUsageAggregateEntity(
            userId = normalized.userId,
            chatId = normalized.chatId,
            assistant = normalized.assistant,
            clientPlatform = normalized.clientPlatform,
            modelName = entry.modelName.normalizedValue("unknown", 128),
            createdAt = now,
        )

        entity.inputTokens += entry.inputTokens.toLong()
        entity.cachedInputTokens += entry.cachedInputTokens.toLong()
        entity.outputTokens += entry.outputTokens.toLong()
        entity.requestCount += 1
        entity.updatedAt = now
        repository.save(entity)
    }

    fun getUsage(chatId: String): TokenUsageRecord? = usageMap[chatId]

    fun getAllUsage(): Map<String, TokenUsageRecord> = usageMap.toMap()

    @Transactional(readOnly = true)
    fun getUsageForUser(userId: String): TokenUsageSummary =
        repository.findAllByUserIdOrderByUpdatedAtDesc(userId)
            .toSummary(userId)

    @Transactional(readOnly = true)
    fun getUsageForUserChat(userId: String, chatId: String): TokenUsageSummary =
        repository.findAllByUserIdAndChatIdOrderByUpdatedAtDesc(userId, chatId)
            .toSummary(userId)

    private fun List<TokenUsageAggregateEntity>.toSummary(userId: String): TokenUsageSummary {
        val rows = map { it.toBreakdown() }
        return TokenUsageSummary(
            userId = userId,
            inputTokens = rows.sumOf { it.inputTokens },
            cachedInputTokens = rows.sumOf { it.cachedInputTokens },
            outputTokens = rows.sumOf { it.outputTokens },
            requestCount = rows.sumOf { it.requestCount },
            breakdown = rows,
        )
    }

    private fun TokenUsageAggregateEntity.toBreakdown() = TokenUsageBreakdown(
        chatId = chatId,
        assistant = assistant,
        clientPlatform = clientPlatform,
        modelName = modelName,
        inputTokens = inputTokens,
        cachedInputTokens = cachedInputTokens,
        outputTokens = outputTokens,
        requestCount = requestCount,
        updatedAt = updatedAt,
    )
}

data class TokenUsageEntry(
    val inputTokens: Int,
    val cachedInputTokens: Int,
    val outputTokens: Int,
    val modelName: String,
) {
    fun hasAnyTokens(): Boolean = inputTokens != 0 || cachedInputTokens != 0 || outputTokens != 0
}

data class TokenUsageContext(
    val userId: String,
    val chatId: String,
    val assistant: String,
    val clientPlatform: String,
    val taskId: String? = null,
    val parentAgent: String? = null,
    val workerAgent: String? = null,
) {
    fun normalized(): TokenUsageContext = copy(
        userId = userId.normalizedValue("unknown-user", 128),
        chatId = chatId.normalizedValue("unknown-chat", 128),
        assistant = assistant.normalizedValue("UNKNOWN", 64).uppercase(),
        clientPlatform = clientPlatform.normalizedValue("unknown", 64).lowercase(),
        taskId = taskId?.trim()?.takeIf { it.isNotBlank() },
        parentAgent = parentAgent?.trim()?.takeIf { it.isNotBlank() },
        workerAgent = workerAgent?.trim()?.takeIf { it.isNotBlank() },
    )
}

data class TokenUsageSummary(
    val userId: String,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val requestCount: Int,
    val breakdown: List<TokenUsageBreakdown>,
) {
    val totalTokens: Long = inputTokens + outputTokens
}

data class TokenUsageBreakdown(
    val chatId: String,
    val assistant: String,
    val clientPlatform: String,
    val modelName: String,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val requestCount: Int,
    val updatedAt: Instant,
) {
    val totalTokens: Long = inputTokens + outputTokens
}

class TokenUsageRecord(
    val chatId: String,
    val inputTokens: AtomicLong = AtomicLong(0),
    val cachedInputTokens: AtomicLong = AtomicLong(0),
    val outputTokens: AtomicLong = AtomicLong(0),
    val requestCount: AtomicInteger = AtomicInteger(0),
    @Volatile var lastModelName: String = "",
)

private fun String.normalizedValue(defaultValue: String, maxLength: Int): String =
    trim().takeIf { it.isNotBlank() }?.take(maxLength) ?: defaultValue
