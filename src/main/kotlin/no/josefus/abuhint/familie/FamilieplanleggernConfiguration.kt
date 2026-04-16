package no.josefus.abuhint.familie

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FamilieplanleggernConfiguration {

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
}
