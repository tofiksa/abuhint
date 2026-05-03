package no.josefus.abuhint.secretary

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TaskExecutionRepository : JpaRepository<TaskExecutionEntity, UUID> {
    fun findAllByTaskIdOrderByStartedAtDesc(taskId: UUID): List<TaskExecutionEntity>
    fun findAllByUserIdOrderByStartedAtDesc(userId: String): List<TaskExecutionEntity>
}

interface TaskArtifactRepository : JpaRepository<TaskArtifactEntity, UUID> {
    fun findAllByExecutionId(executionId: UUID): List<TaskArtifactEntity>
}
