package no.josefus.abuhint.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Service
class TokenUsageStore {

    private val usageMap = ConcurrentHashMap<String, TokenUsageRecord>()

    fun record(chatId: String, entry: TokenUsageEntry) {
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

    fun getUsage(chatId: String): TokenUsageRecord? = usageMap[chatId]

    fun getAllUsage(): Map<String, TokenUsageRecord> = usageMap.toMap()
}

data class TokenUsageEntry(
    val inputTokens: Int,
    val cachedInputTokens: Int,
    val outputTokens: Int,
    val modelName: String,
)

class TokenUsageRecord(
    val chatId: String,
    val inputTokens: AtomicLong = AtomicLong(0),
    val cachedInputTokens: AtomicLong = AtomicLong(0),
    val outputTokens: AtomicLong = AtomicLong(0),
    val requestCount: AtomicInteger = AtomicInteger(0),
    @Volatile var lastModelName: String = "",
)
