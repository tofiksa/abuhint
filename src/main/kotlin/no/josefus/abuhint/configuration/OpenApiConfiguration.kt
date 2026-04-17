package no.josefus.abuhint.configuration

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT utstedt av josefus-highscore. Logg inn via /auth/signin for å hente token.")
            )
        )
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
        .info(
            Info()
                .title("Abuhint API")
                .version("1.0.0")
                .description(
                    """
                    REST API for Abuhint – en AI-drevet chatbot-tjeneste med støtte for flere personas og verktøy.

                    ## Personas
                    - **Abu-hint** (`/api/chat`, `/api/coach`) – teamleder-coach drevet av OpenAI (modell konfigurert i application.yml). Har tilgang til e-post, PowerPoint, nettsøk og GitHub-verktøy.
                    - **Abdikverrulant** (`/api/tech-advisor`) – teknisk rådgiver drevet av Google Gemini 2.5 Flash. Har tilgang til nettsøk.
                    - **Familieplanleggern** (`/api/familie`, `/api/google/oauth/*`) – husholdnings-coach drevet av OpenAI. Kobler mot brukerens Google Calendar (OAuth2) for å lese/opprette hendelser og lage kategori-kalendere. Kall `POST /api/google/oauth/start` for å starte OAuth-flyten og `GET /api/google/oauth/status` for å sjekke tilkoblingsstatus. Alle mutasjoner bruker propose → confirm-tokens for å unngå utilsiktede endringer.

                    ## OpenAI-kompatibelt endepunkt
                    `/v1/chat/completions` er kompatibelt med OpenAI Chat Completions API og kan brukes med klienter som LiteLLM, OpenWebUI eller andre OpenAI-kompatible verktøy.

                    ## Samtaleminne
                    Send med `chatId` (UUID) for å opprettholde kontekst på tvers av forespørsler. Utelates `chatId` startes en ny økt automatisk.
                    """.trimIndent()
                )
                .contact(
                    Contact()
                        .name("Josefus")
                        .url("https://github.com/josefus/abuhint")
                )
                .license(
                    License()
                        .name("MIT")
                )
        )
        .addServersItem(Server().url("/").description("Gjeldende server"))
        .addTagsItem(
            Tag()
                .name("Chat")
                .description("Direktechat med Abu-hint (OpenAI, modell konfigurert i application.yml). Støtter e-post, PowerPoint, nettsøk og GitHub-verktøy.")
        )
        .addTagsItem(
            Tag()
                .name("Coach")
                .description("Teamleder-coach persona. Samme modell og verktøy som Chat, men med coach-systemsprompt.")
        )
        .addTagsItem(
            Tag()
                .name("Tech Advisor")
                .description("Teknisk rådgiver drevet av Google Gemini 2.5 Flash. Spesialisert på arkitektur og tekniske beslutninger.")
        )
        .addTagsItem(
            Tag()
                .name("OpenAI-kompatibel")
                .description("OpenAI Chat Completions-kompatibelt endepunkt. Kan brukes som drop-in-erstatning i OpenAI-kompatible klienter.")
        )
        .addTagsItem(
            Tag()
                .name("Verktøy")
                .description("Oversikt over tilgjengelige AI-verktøy og deres beskrivelser.")
        )
        .addTagsItem(
            Tag()
                .name("Familieplanleggern")
                .description("Chat-endepunkter for Familieplanleggern-agenten. Krever at brukeren er tilkoblet Google via /api/google/oauth.")
        )
        .addTagsItem(
            Tag()
                .name("Familieplanleggern – Google OAuth")
                .description("OAuth 2.0-flyt for å koble brukerens Google-konto til Familieplanleggern. Deep-link-redirect til Android-appen etter vellykket tilkobling.")
        )
}
