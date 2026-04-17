package no.josefus.abuhint.familie

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserGoogleCredentialRepository : JpaRepository<UserGoogleCredentialEntity, String>
