package no.josefus.abuhint.repository

import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.store.memory.chat.ChatMemoryStore
import com.github.benmanes.caffeine.cache.Caffeine
import no.josefus.abuhint.configuration.LangChain4jConfiguration
import no.josefus.abuhint.service.EmbeddingCache
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Component
abstract class PineconeChatMemoryStore(
    val langChain4jConfiguration: LangChain4jConfiguration,
    private val embeddingCache: EmbeddingCache
) : ChatMemoryStore {
    private val logger = LoggerFactory.getLogger(PineconeChatMemoryStore::class.java)

    // Bounded cache for session messages
    private val memoryCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(2, TimeUnit.HOURS)
        .build<String, MutableList<ChatMessage>>()

    // Track the last stored message count per memory ID
    protected val lastStoredCount = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(2, TimeUnit.HOURS)
        .build<String, Int>()

    // Message counter for unique IDs
    private val messageCounters = ConcurrentHashMap<String, AtomicLong>()

    private val summaryStoredUpTo = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(2, TimeUnit.HOURS)
        .build<String, Int>()

    @org.springframework.beans.factory.annotation.Value("\${pinecone.load-recent-on-cache-miss:false}")
    private var loadRecentOnCacheMiss: Boolean = false

    override fun getMessages(memoryId: Any): MutableList<ChatMessage> {
        val id = memoryId.toString()
        
        // Return from cache if available
        val cachedMessages = memoryCache.getIfPresent(id)
        if (cachedMessages != null && cachedMessages.isNotEmpty()) {
            return cachedMessages.toMutableList()
        }
        
        // Optionally skip remote warm-load to avoid extra latency on cold start
        if (!loadRecentOnCacheMiss) {
            logger.debug("Cache miss for ID: $id; skipping Pinecone warm-load (loadRecentOnCacheMiss=false)")
            return mutableListOf()
        }

        // If cache is empty, try to load recent messages from Pinecone
        // This ensures long-term memory is available even after restart
        // Note: This is best-effort - if it fails, cache will be populated as new messages arrive
        try {
            logger.debug("Cache is empty for ID: $id, attempting to load from Pinecone")
            val recentMessages = loadRecentMessagesFromPinecone(id)
            if (recentMessages.isNotEmpty()) {
                memoryCache.put(id, recentMessages.toMutableList())
                logger.info("Loaded ${recentMessages.size} recent messages from Pinecone for ID: $id")
                return recentMessages.toMutableList()
            } else {
                logger.debug("No messages found in Pinecone for ID: $id (this is normal for new conversations)")
            }
        } catch (e: Exception) {
            // This is not critical - cache will be populated as new messages arrive
            logger.debug("Could not load recent messages from Pinecone for ID: $id: ${e.message}. " +
                    "This is normal and cache will be populated as new messages arrive.")
        }
        
        return mutableListOf()
    }
    
    /**
     * Loads the most recent messages from Pinecone for a given memory ID.
     * This is used to populate cache when it's empty (e.g., after restart).
     * 
     * Note: Pinecone doesn't support querying all messages in a namespace directly.
     * We use a generic query that should match conversation content to retrieve messages.
     */
    protected fun loadRecentMessagesFromPinecone(memoryId: String): List<ChatMessage> {
        try {
            val embeddingStore = langChain4jConfiguration.embeddingStore(
                langChain4jConfiguration.embeddingModel(),
                memoryId
            )
            
            val embeddingModel = langChain4jConfiguration.embeddingModel()
            
            // Use a generic query that should match conversation content
            // Since we can't query by namespace directly, we use a query that semantically
            // matches typical conversation patterns
            val queryText = "conversation chat message user assistant"
            val queryEmbedding = embeddingCache.getOrCompute(queryText, embeddingModel)
            
            logger.debug("Loading recent messages from Pinecone for memoryId: $memoryId (namespace: ${memoryId.ifEmpty { "startup" }})")
            
            val searchResults: List<dev.langchain4j.store.embedding.EmbeddingMatch<TextSegment>> =
                ConcretePineconeChatMemoryStore.searchWithRequest(
                    embeddingStore = embeddingStore,
                    queryEmbedding = queryEmbedding,
                    maxResults = 100,
                    minScore = 0.0,
                    logger = logger
                )
            
            // Parse results to messages
            val messages = searchResults.mapNotNull { match ->
                val parsed = parseSegmentToChatMessage(match.embedded())
                if (parsed == null) {
                    logger.debug("Failed to parse message segment: ${match.embedded().text().take(100)}")
                }
                parsed
            }
            
            // Don't update lastStoredCount here - we don't know the actual count
            // It will be updated when updateMessages is called with the actual message list
            // This prevents issues where we load fewer messages than actually exist
            
            logger.info("Loaded ${messages.size} messages from Pinecone for ID: $memoryId (from ${searchResults.size} search results)")
            if (messages.isEmpty() && searchResults.isNotEmpty()) {
                logger.warn("Found ${searchResults.size} search results but parsed 0 messages. This might indicate a format mismatch.")
                // Log first few results for debugging
                searchResults.take(3).forEach { match ->
                    logger.debug("Sample search result: ${match.embedded().text().take(200)}")
                }
            }
            return messages
        } catch (e: Exception) {
            logger.error("Failed to load recent messages from Pinecone: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Returns true if we have local cache or stored count for this memoryId,
     * indicating prior messages exist.
     */
    fun hasStoredMessages(memoryId: String): Boolean {
        if (memoryCache.getIfPresent(memoryId)?.isNotEmpty() == true) return true
        return (lastStoredCount.getIfPresent(memoryId) ?: 0) > 0
    }
    
    /**
     * Normalizes message type from LangChain4j format to our storage format.
     * Examples: "USER_MESSAGE" -> "USER", "AI_MESSAGE" -> "AI", "SYSTEM_MESSAGE" -> "SYSTEM"
     */
    private fun normalizeMessageType(type: String): String {
        return when {
            type.uppercase().contains("USER") -> "USER"
            type.uppercase().contains("AI") || type.uppercase().contains("ASSISTANT") -> "AI"
            type.uppercase().contains("SYSTEM") -> "SYSTEM"
            else -> type.uppercase()
        }
    }
    
    /**
     * Helper method to parse TextSegment to ChatMessage.
     */
    private fun parseSegmentToChatMessage(segment: TextSegment): ChatMessage? {
        val text = segment.text().trim()
        if (text.isBlank()) return null
        
        val colonIndex = text.indexOf(": ")
        if (colonIndex == -1) return null
        
        val messageType = text.substring(0, colonIndex).trim()
        val messageContent = text.substring(colonIndex + 2).trim()
        
        if (messageContent.isBlank()) return null
        
        return when (messageType.uppercase()) {
            "USER" -> UserMessage(messageContent)
            "AI" -> AiMessage(messageContent)
            "SYSTEM" -> SystemMessage(messageContent)
            else -> null
        }
    }

    override fun updateMessages(memoryId: Any, messages: List<ChatMessage>) {
        try {
            val id = memoryId.toString()
            val embeddingStore = langChain4jConfiguration.embeddingStore(
                langChain4jConfiguration.embeddingModel(), 
                id
            )
            val embeddingModel = langChain4jConfiguration.embeddingModel()
            
            // Get previous message count
            val previousCount = lastStoredCount.getIfPresent(id) ?: 0
            val currentCount = messages.size

            // Update cache
            memoryCache.put(id, messages.toMutableList())

            // Only store NEW messages to Pinecone (incremental storage)
            // Check if we have new messages compared to what's stored
            if (currentCount > previousCount) {
                val newMessages = messages.subList(previousCount, currentCount)
                storeMessagesToPinecone(id, newMessages, embeddingStore, embeddingModel, previousCount)
                lastStoredCount.put(id, currentCount)

                logger.info("Stored ${newMessages.size} new messages to Pinecone for ID: $id (total: $currentCount)")
                maybeStoreSummary(id, messages, embeddingStore, embeddingModel, currentCount)
            } else if (currentCount == previousCount && previousCount == 0 && messages.isNotEmpty()) {
                // Special case: if we loaded messages from Pinecone but lastStoredCount wasn't set,
                // we need to check if these messages are already in Pinecone
                // For now, we'll assume they are and just update the count
                lastStoredCount.put(id, currentCount)
                logger.debug("Updated lastStoredCount for ID: $id to $currentCount (messages were loaded from Pinecone)")
                maybeStoreSummary(id, messages, embeddingStore, embeddingModel, currentCount)
            } else {
                // If count decreased or stayed same, update cache only
                logger.debug("No new messages to store for ID: $id (count: $currentCount, previous: $previousCount)")
            }
        } catch (e: Exception) {
            logger.error("Failed to update chat memory in Pinecone: ${e.message}", e)
        }
    }
    
    /**
     * Stores individual messages to Pinecone for efficient semantic search.
     * Each message is stored separately with a unique ID.
     */
    protected fun storeMessagesToPinecone(
        memoryId: String,
        messages: List<ChatMessage>,
        embeddingStore: dev.langchain4j.store.embedding.EmbeddingStore<TextSegment>,
        embeddingModel: dev.langchain4j.model.embedding.EmbeddingModel,
        startIndex: Int
    ) {
        val counter = messageCounters.computeIfAbsent(memoryId) { AtomicLong(0) }
        
        messages.forEachIndexed { index, message ->
            try {
                // Create unique ID for this message
                val order = counter.getAndIncrement()
                val timestamp = System.currentTimeMillis()
                val messageId = "${memoryId}_msg_${order}_${timestamp}"
                
                // Get message text using reflection (same approach as ChatService)
                val messageText = getMessageText(message)
                
                // Format message text with type prefix for parsing later
                // Normalize message type: "USER_MESSAGE" -> "USER", "AI_MESSAGE" -> "AI", etc.
                val messageType = normalizeMessageType(message.type().toString())
                val formattedMessageText = "$messageType: $messageText"
                val metadata = Metadata.from(
                    mapOf(
                        "ts" to timestamp,
                        "order" to order
                    )
                )
                val textSegment = TextSegment.from(formattedMessageText, metadata)
                
                // Generate embedding using cache for repeated text
                val embedding = embeddingCache.getOrCompute(formattedMessageText, embeddingModel)
                
                // Store in Pinecone - ID is auto-generated, messageId can be stored in metadata if needed
                embeddingStore.add(embedding, textSegment)
                
                logger.debug("Stored message $messageId to Pinecone: $messageType (namespace: $memoryId)")
            } catch (e: Exception) {
                logger.error("Failed to store individual message to Pinecone: ${e.message}", e)
            }
        }
    }

    private fun maybeStoreSummary(
        memoryId: String,
        messages: List<ChatMessage>,
        embeddingStore: dev.langchain4j.store.embedding.EmbeddingStore<TextSegment>,
        embeddingModel: dev.langchain4j.model.embedding.EmbeddingModel,
        currentCount: Int
    ) {
        val summaryThreshold = 50
        val keepRecent = 10
        val summaryStep = 25
        val lastSummaryAt = summaryStoredUpTo.getIfPresent(memoryId) ?: 0

        if (currentCount < summaryThreshold) return
        if (currentCount - lastSummaryAt < summaryStep) return

        val summary = buildSummary(messages, keepRecent)
        if (summary.isBlank()) return

        val summaryText = "SYSTEM: Summary (through turn ${currentCount - keepRecent}): $summary"
        val metadata = Metadata.from(
            mapOf(
                "ts" to System.currentTimeMillis(),
                "order" to currentCount.toLong(),
                "summary" to true
            )
        )
        val textSegment = TextSegment.from(summaryText, metadata)
        try {
            val embedding = embeddingCache.getOrCompute(summaryText, embeddingModel)
            embeddingStore.add(embedding, textSegment)
            summaryStoredUpTo.put(memoryId, currentCount)
            logger.info("Stored summary for ID: $memoryId (through turn ${currentCount - keepRecent})")
        } catch (e: Exception) {
            logger.error("Failed to store summary for ID: $memoryId: ${e.message}", e)
        }
    }

    private fun buildSummary(messages: List<ChatMessage>, keepRecent: Int): String {
        if (messages.size <= keepRecent) return ""
        val older = messages.dropLast(keepRecent)
        val limited = older.takeLast(40)
        val parts = limited.mapNotNull { message ->
            val content = getMessageText(message).trim()
            if (content.isBlank()) return@mapNotNull null
            val prefix = normalizeMessageType(message.type().toString())
            "$prefix: ${content.take(200)}"
        }
        return parts.joinToString(" | ").take(1200)
    }

    override fun deleteMessages(memoryId: Any) {
        val id = memoryId.toString()
        memoryCache.invalidate(id)
        lastStoredCount.invalidate(id)
        messageCounters.remove(id)
        
        logger.info("Deleted chat memory cache for ID: $id (Pinecone data remains)")
    }
    
    private fun getMessageText(message: ChatMessage): String = no.josefus.abuhint.service.ChatMessageUtils.getMessageText(message)
}
