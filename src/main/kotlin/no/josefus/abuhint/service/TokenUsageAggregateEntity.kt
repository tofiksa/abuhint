package no.josefus.abuhint.service

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "token_usage_aggregate",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_token_usage_aggregate",
            columnNames = ["user_id", "chat_id", "assistant", "client_platform", "model_name"],
        ),
    ],
    indexes = [
        Index(name = "idx_token_usage_aggregate_user_updated", columnList = "user_id, updated_at"),
    ],
)
class TokenUsageAggregateEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "user_id", length = 128, nullable = false)
    var userId: String = "",

    @Column(name = "chat_id", length = 128, nullable = false)
    var chatId: String = "",

    @Column(name = "assistant", length = 64, nullable = false)
    var assistant: String = "",

    @Column(name = "client_platform", length = 64, nullable = false)
    var clientPlatform: String = "",

    @Column(name = "model_name", length = 128, nullable = false)
    var modelName: String = "",

    @Column(name = "input_tokens", nullable = false)
    var inputTokens: Long = 0,

    @Column(name = "cached_input_tokens", nullable = false)
    var cachedInputTokens: Long = 0,

    @Column(name = "output_tokens", nullable = false)
    var outputTokens: Long = 0,

    @Column(name = "request_count", nullable = false)
    var requestCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
)
