package no.josefus.abuhint.familie

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FamilieplanleggernConfiguration {

    private val log = org.slf4j.LoggerFactory.getLogger(FamilieplanleggernConfiguration::class.java)

    @Bean
    fun familieplanleggernProperties(
        @Value("\${familie.google.client-id:}") googleClientId: String,
        @Value("\${familie.google.client-secret:}") googleClientSecret: String,
        @Value("\${familie.google.redirect-uri:}") googleRedirectUri: String,
        @Value("\${familie.default-timezone:Europe/Oslo}") defaultTimezone: String,
        @Value("\${familie.token-encryption-key:}") tokenEncryptionKeyBase64: String,
    ): FamilieplanleggernProperties = FamilieplanleggernProperties(
        googleClientId = googleClientId,
        googleClientSecret = googleClientSecret,
        googleRedirectUri = googleRedirectUri,
        defaultTimezone = defaultTimezone,
        tokenEncryptionKeyBase64 = tokenEncryptionKeyBase64,
    )

    @Bean
    fun tokenCipher(properties: FamilieplanleggernProperties): TokenCipher {
        val keyB64 = properties.tokenEncryptionKeyBase64
        if (keyB64.isBlank()) {
            log.warn(
                "GOOGLE_TOKEN_ENC_KEY is not set — using a volatile in-memory key. " +
                    "Stored Google credentials will not survive restarts. Set the env var in prod."
            )
            val random = java.security.SecureRandom()
            val bytes = ByteArray(32).also { random.nextBytes(it) }
            return TokenCipher(java.util.Base64.getEncoder().encodeToString(bytes))
        }
        return TokenCipher(keyB64)
    }
}
