package no.josefus.abuhint

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry

@SpringBootApplication
@EnableRetry
class AbuhintApplication

fun main(args: Array<String>) {
	runApplication<AbuhintApplication>(*args)
}
