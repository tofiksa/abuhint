package no.josefus.abuhint.secretary

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SecretaryTaskRepository : JpaRepository<SecretaryTaskEntity, UUID> {
    fun findAllByChatIdOrderBySortOrderAsc(chatId: String): List<SecretaryTaskEntity>
    fun findByIdAndUserId(id: UUID, userId: String): SecretaryTaskEntity?
}
