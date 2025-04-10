# ABUHINT - AI-drevet chatapplikasjon

## Oversikt
ABUHINT er en chatapplikasjon bygget med Kotlin og Spring Boot som utnytter AI-funksjonalitet gjennom LangChain4j og OpenAIs GPT-4o-modell. Applikasjonen tilbyr et reaktivt strømmings-API for sanntids chatinteraksjoner.

## Funksjoner
- Sanntidschat med strømmende svar
- AI-drevne samtaler ved bruk av GPT-4o
- Persistente chatøkter med unike identifikatorer
- Token-for-token strømmende respons

## Teknologistabel
- Kotlin 1.9.25
- Java 21
- Spring Boot 3.4.4
- Project Reactor for reaktiv programmering
- LangChain4j 0.30.0 for AI-integrasjon
- Spring Data JPA med H2-database for persistens
- Maven for byggautomatisering

## Kom i gang

### Forutsetninger
- JDK 21+
- Maven 3.6+
- OpenAI API-nøkkel

### Konfigurasjon
1. Sett din OpenAI API-nøkkel som en miljøvariabel:
   ```
   export EASTER_API_KEY=din_openai_api_nøkkel
   ```

2. Applikasjonen bruker H2 in-memory database som standard, konfigurert i `application.yml`

### Kjøring av applikasjonen
```bash
./mvnw spring-boot:run
```

Applikasjonen vil starte på port 8081.

## API-bruk

Send en melding til chatten:
```
POST /api/chat/send
```

Forespørselsinnhold:
```json
{
  "message": "Din melding her"
}
```

Spørreparameter:
- `chatId` (valgfri): Unik identifikator for chatøkten. Hvis ikke oppgitt, vil en ny UUID genereres.

API-et returnerer en Server-Sent Events (SSE) strøm hvor hvert token leveres etter hvert som det blir tilgjengelig.

## Lisens
Dette prosjektet er lisensiert under Apache License 2.0 - se LICENSE-filen for detaljer.