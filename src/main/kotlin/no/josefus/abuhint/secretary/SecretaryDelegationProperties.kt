package no.josefus.abuhint.secretary

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "abuhint.secretary.delegation")
data class SecretaryDelegationProperties(
    val syncWaitMs: Long = 8000,
    val workerTimeoutMs: Long = 180000,
    val maxConcurrent: Int = 8,
)
