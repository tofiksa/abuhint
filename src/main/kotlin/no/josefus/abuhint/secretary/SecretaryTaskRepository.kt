package no.josefus.abuhint.secretary

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SecretaryTaskRepository : JpaRepository<SecretaryTaskEntity, UUID> {
    fun findAllByStatusIn(status: Collection<SecretaryTaskStatus>): List<SecretaryTaskEntity>

    fun findAllByChatIdOrderBySortOrderAsc(chatId: String): List<SecretaryTaskEntity>
    fun findAllByChatIdAndUserIdOrderBySortOrderAsc(chatId: String, userId: String): List<SecretaryTaskEntity>
    fun findByIdAndUserId(id: UUID, userId: String): SecretaryTaskEntity?

    /**
     * Idempotent transition to [SecretaryTaskStatus.running].
     *
     * @return rows updated (0 if already running or id not found).
     *
     * Done/failed completion is not a conditional UPDATE here: task 3 [SecretaryDelegationService.complete]
     * reloads the entity in a short transaction and writes result fields only when status is still `running`.
     */
    @Modifying
    @Query(
        """
        UPDATE SecretaryTaskEntity t
        SET t.status = 'running', t.updatedAt = :now
        WHERE t.id = :id AND t.status <> 'running'
        """
    )
    fun markRunningIfNotRunning(@Param("id") id: UUID, @Param("now") now: Instant): Int
}
