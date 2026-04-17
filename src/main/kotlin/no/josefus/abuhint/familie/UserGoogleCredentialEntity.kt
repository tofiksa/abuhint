package no.josefus.abuhint.familie

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "user_google_credential")
class UserGoogleCredentialEntity(
    @Id
    @Column(name = "user_id", length = 128, nullable = false)
    var userId: String = "",

    @Column(name = "encrypted_refresh_token", length = 2048, nullable = false)
    var encryptedRefreshToken: String = "",

    @Column(name = "encrypted_access_token", length = 4096)
    var encryptedAccessToken: String? = null,

    @Column(name = "access_token_expires_at")
    var accessTokenExpiresAt: Instant? = null,

    @Column(name = "scope", length = 1024, nullable = false)
    var scope: String = "",

    @Column(name = "email", length = 320)
    var email: String? = null,

    @Column(name = "timezone", length = 64)
    var timezone: String? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
)
