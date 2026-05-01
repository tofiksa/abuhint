package no.josefus.abuhint.service

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TokenUsageAggregateRepository : JpaRepository<TokenUsageAggregateEntity, Long> {
    fun findByUserIdAndChatIdAndAssistantAndClientPlatformAndModelName(
        userId: String,
        chatId: String,
        assistant: String,
        clientPlatform: String,
        modelName: String,
    ): TokenUsageAggregateEntity?

    fun findAllByUserIdOrderByUpdatedAtDesc(userId: String): List<TokenUsageAggregateEntity>

    fun findAllByUserIdAndChatIdOrderByUpdatedAtDesc(
        userId: String,
        chatId: String,
    ): List<TokenUsageAggregateEntity>
}
