package no.josefus.abuhint.secretary

import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.TestPropertySource
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:secretary_task_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
])
class SecretaryTaskRepositoryTest {
    @Autowired lateinit var repository: SecretaryTaskRepository
    @Autowired lateinit var entityManager: EntityManager

    @Test fun `markDelegatedIfIdle claims an idle task only once`() {
        val task = repository.save(task(SecretaryTaskStatus.ready))
        entityManager.flush()

        assertEquals(1, repository.markDelegatedIfIdle(task.id, Instant.now()))
        assertEquals(0, repository.markDelegatedIfIdle(task.id, Instant.now()))
        entityManager.clear()
        assertEquals(SecretaryTaskStatus.delegated, repository.findById(task.id).orElseThrow().status)
    }

    @Test fun `markDelegatedIfIdle refuses running task and allows finished task`() {
        val running = repository.save(task(SecretaryTaskStatus.running))
        val done = repository.save(task(SecretaryTaskStatus.done))
        entityManager.flush()

        assertEquals(0, repository.markDelegatedIfIdle(running.id, Instant.now()))
        assertEquals(1, repository.markDelegatedIfIdle(done.id, Instant.now()))
    }

    @Test fun `markRunningIfNotRunning transitions delegated task once`() {
        val task = repository.save(task(SecretaryTaskStatus.delegated))
        entityManager.flush()

        assertEquals(1, repository.markRunningIfNotRunning(task.id, Instant.now()))
        assertEquals(0, repository.markRunningIfNotRunning(task.id, Instant.now()))
    }

    private fun task(status: SecretaryTaskStatus) = SecretaryTaskEntity(
        userId = "u1",
        chatId = "chat-a",
        title = "Task",
        description = null,
        status = status,
        assignedAgentId = "research",
        delegatedBrief = null,
        resultSummary = null,
        errorMessage = null,
        acceptanceCriteria = null,
        artifactsJson = null,
    )
}
