package no.josefus.abuhint.secretary

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "task_artifact")
class TaskArtifactEntity(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "execution_id", nullable = false)
    val executionId: UUID,

    @Column(name = "artifact_type", nullable = false, length = 64)
    val artifactType: String,

    @Column(name = "name", length = 512)
    val name: String? = null,

    @Column(name = "content_url", columnDefinition = "TEXT")
    val contentUrl: String? = null,

    @Column(name = "content_text", columnDefinition = "TEXT")
    val contentText: String? = null,

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    val metadataJson: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
