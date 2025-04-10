package no.josefus.abuhint.repository

import no.josefus.abuhint.model.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long>