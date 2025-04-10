package no.josefus.abuhint.service

import no.josefus.abuhint.model.User
import no.josefus.abuhint.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class UserService(private val userRepository: UserRepository) {
    fun getAllUsers(): List<User> = userRepository.findAll()
    fun saveUser(user: User): User = userRepository.save(user)
}