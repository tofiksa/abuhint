package no.josefus.abuhint.secretary

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "secretary_task")
class SecretaryTaskEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false, length = 128)
    var userId: String,

    @Column(name = "chat_id", nullable = false, length = 128)
    var chatId: String,

    @Column(name = "title", nullable = false, length = 512)
    var title: String,

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: SecretaryTaskStatus,

    @Column(name = "assigned_agent_id", length = 64)
    var assignedAgentId: String?,

    @Column(name = "delegated_brief", columnDefinition = "TEXT")
    var delegatedBrief: String?,

    @Column(name = "result_summary", columnDefinition = "TEXT")
    var resultSummary: String?,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String?,

    @Column(name = "requires_confirmation", nullable = false)
    var requiresConfirmation: Boolean = false,

    @Column(name = "acceptance_criteria", columnDefinition = "TEXT")
    var acceptanceCriteria: String?,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "list_version", nullable = false)
    var listVersion: Int = 0,

    @Column(name = "artifacts_json", columnDefinition = "TEXT")
    var artifactsJson: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
