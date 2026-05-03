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
@Table(name = "task_execution")
class TaskExecutionEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "task_id", nullable = false)
    val taskId: UUID,

    @Column(name = "agent_id", nullable = false, length = 64)
    val agentId: String,

    @Column(name = "user_id", nullable = false, length = 128)
    val userId: String,

    @Column(name = "chat_id", nullable = false, length = 128)
    val chatId: String,

    @Column(name = "brief", nullable = false, columnDefinition = "TEXT")
    val brief: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: TaskExecutionStatus = TaskExecutionStatus.running,

    @Column(name = "result_summary", columnDefinition = "TEXT")
    var resultSummary: String? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "started_at", nullable = false)
    val startedAt: Instant = Instant.now(),

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "duration_ms")
    var durationMs: Long? = null,
)

enum class TaskExecutionStatus {
    running, done, failed
}
