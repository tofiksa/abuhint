package no.josefus.abuhint.familie

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

@Entity
@Table(name = "device_calendar_session")
class DeviceCalendarSessionEntity(
    @Id @Column(length = 64) var id: String = "",
    @Column(nullable = false, columnDefinition = "text") var payload: String = "",
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
    @Version var version: Long? = null,
)

interface DeviceCalendarSessionRepository : JpaRepository<DeviceCalendarSessionEntity, String>
