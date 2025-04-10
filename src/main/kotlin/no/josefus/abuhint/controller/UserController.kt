package no.josefus.abuhint.controller

import no.josefus.abuhint.model.User
import no.josefus.abuhint.service.UserService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    @GetMapping
    fun getUsers(): List<User> = userService.getAllUsers()

    @PostMapping
    fun createUser(@RequestBody user: User): User = userService.saveUser(user)
}