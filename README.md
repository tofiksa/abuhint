# AbuHint: Your AI-Powered Assistant

<img src="assets/Abu-hint-coach.png" alt="AbuHint Logo" width="400" />

Welcome to **AbuHint**, a Kotlin-based Spring Boot application that combines the power of AI with seamless email communication. This project is designed to assist you with team coaching, sparring, and idea generation while integrating tools like LangChain4j and Resend for enhanced functionality.
Welcome to **AbuHint**, a Kotlin-based Spring Boot application that combines the power of AI with seamless email communication. This project is designed to assist you with team coaching, sparring, and idea generation while integrating tools like LangChain4j and Resend for enhanced functionality.

---

## 🚀 Features

### 🤖 AI Assistants (personas)
- **Abu-hint** – teamleder-coach (OpenAI), tools: e-post, PowerPoint, nettsøk, GitHub.
- **Abdikverrulant** – techlead-coach (Gemini 2.5 Flash), tools: nettsøk.
- **Familieplanleggern** – husholdnings-coach (OpenAI) som kobler mot brukerens Google-konto via OAuth2 for å lese/opprette kalenderhendelser og lage kategori-kalendere. Alle endringer går gjennom en propose → confirm-gate slik at brukeren må bekrefte i chat før agenten faktisk skriver til Google.

### 📧 Email Service
- **Resend API Integration**:
    - Send beautifully crafted HTML emails.
    - Configurable sender, recipient, and subject via environment variables.

### 🛠️ Developer Goodies
- H2 in-memory database for quick testing.
- Hibernate auto-update for schema management.
- Debug-friendly logging for LangChain4j and Resend.

---

## 🏗️ Tech Stack

- **Languages**: Kotlin 2.0.21, Java 21
- **Framework**: Spring Boot 3.4.4
- **AI Framework**: LangChain4j 1.9.1
- **Email Service**: Resend API 4.4.0
- **Database**: H2 (in-memory)
- **Document Processing**: Apache POI 5.5.1
- **Reactive**: Reactor Core 3.7.4 (Spring Boot managed)

---

## 🛠️ Setup Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/tofiksa/abuhint.git
cd abuhint
```

### 2. Configure Environment Variables
Create a `.env` file or set the following environment variables:
```bash
GITHUB_REPO_URL=<your-github-repo-url>
GITHUB_JOSEFUS_TOKEN=<your-github-token>
EASTER_API_KEY=<your-openai-api-key>
PINECONE_API_KEY=<your-pinecone-api-key>
RESEND_API_KEY=<your-resend-api-key>
RESEND_FROM_EMAIL=<sender-email>
RESEND_TO_EMAIL=<recipient-email>
RESEND_SUBJECT=<email-subject>
WEB_SEARCH_ENABLED=false            # set true to enable the web search tool
WEB_SEARCH_PROVIDER=tavily          # tavily (default) or brave
WEB_SEARCH_API_KEY=<your-search-api-key>
WEB_SEARCH_BASE_URL=https://api.tavily.com
WEB_SEARCH_TIMEOUT_MS=5000
WEB_SEARCH_MAX_RESULTS=6
WEB_SEARCH_LOCALE=nb-NO
WEB_SEARCH_SEARCH_DEPTH=basic

# Optional fallback
BRAVE_API_KEY=<your-brave-api-key>
WEB_SEARCH_CACHE_TTL_S=300
WEB_SEARCH_SAFE_MODE=moderate

# Familieplanleggern (Google Calendar OAuth + credential store)
GOOGLE_CLIENT_ID=<google-oauth-client-id>
GOOGLE_CLIENT_SECRET=<google-oauth-client-secret>
GOOGLE_REDIRECT_URI=http://localhost:8080/api/google/oauth/callback
GOOGLE_TOKEN_ENC_KEY=<base64-32-byte-aes-key>        # openssl rand -base64 32
FAMILIE_DEFAULT_TIMEZONE=Europe/Oslo
FAMILIE_DEEP_LINK_SUCCESS_URI=familieplanleggern://oauth/done   # default; override for your Android app scheme

# Persistence (H2 in-mem default; override for Postgres in prod)
DATASOURCE_URL=jdbc:h2:mem:abuhint;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
DATASOURCE_USERNAME=sa
DATASOURCE_PASSWORD=
DATASOURCE_DRIVER=org.h2.Driver                    # org.postgresql.Driver for prod
JPA_DDL_AUTO=update
```

### Google OAuth setup (Familieplanleggern)

1. Gå til [Google Cloud Console](https://console.cloud.google.com/) → opprett / velg et prosjekt.
2. Aktiver **Google Calendar API** under `APIs & Services → Library`.
3. Under `APIs & Services → Credentials`, opprett en **OAuth 2.0 Client ID** av type **Web application**.
4. Legg til autorisert redirect URI: `http://localhost:8080/api/google/oauth/callback` (og/eller prod-URL).
5. Kopier **Client ID** og **Client secret** inn i miljøvariablene over.
6. Scopes som brukes: `https://www.googleapis.com/auth/calendar`, `https://www.googleapis.com/auth/calendar.events`, `openid`, `email` (de to siste brukes kun for å hente brukerens e-post fra `id_token`).
7. Generer en AES-256-nøkkel for tokenkryptering: `openssl rand -base64 32` → `GOOGLE_TOKEN_ENC_KEY`. Uten denne bruker appen en volatil in-memory-nøkkel og varsler i logg; tokens overlever da ikke restart.

### Familieplanleggern API

| Metode | Path | Beskrivelse |
|--------|------|-------------|
| `POST` | `/api/google/oauth/start` | Returnerer `{ authUrl, state }`. Åpne `authUrl` i Custom Tab / nettleser. Krever JWT. |
| `GET`  | `/api/google/oauth/callback` | Google redirecter hit. Bytter kode mot tokens, krypterer og lagrer, og 302-redirecter deretter til deep-link med `?status=ok|access_denied|invalid_state|missing_code|token_exchange_failed`. Krever *ikke* JWT (browser-hop). |
| `GET`  | `/api/google/oauth/status` | `{ connected, email, timezone }` for innlogget bruker. Krever JWT. |
| `DELETE` | `/api/google/oauth` | Sletter lagrede OAuth-tokens for innlogget bruker. Krever JWT. |
| `POST` | `/api/familie/send` | Send melding til Familieplanleggern (JSON `{ message }`). Returnerer `412 Precondition Failed` hvis brukeren ikke har koblet Google. |
| `POST` | `/api/familie/stream` | SSE-streamet svar (token-for-token). Samme 412-gate som `/send`. |
| `GET`  | `/api/familie/{chatId}/history` | Pagineret chat-historikk. |

### Android deep-link

Etter vellykket OAuth-callback redirecter serveren browseren til `FAMILIE_DEEP_LINK_SUCCESS_URI` (default `familieplanleggern://oauth/done`). Registrer et intent-filter i Android-appen for å fange den og lukke Custom Tab:

```xml
<activity android:name=".OAuthCallbackActivity" android:exported="true">
    <intent-filter android:autoVerify="false">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="familieplanleggern" android:host="oauth" android:path="/done" />
    </intent-filter>
</activity>
```

Les `status`-query-parameteren (`ok`, `access_denied`, `invalid_state`, `missing_code`, `token_exchange_failed`) og vis riktig UI. Bruk et eget schema for appen din (f.eks. `no.josefus.familieplanleggern://...`) og sett det via `FAMILIE_DEEP_LINK_SUCCESS_URI`.

### Propose → confirm

Alle mutasjoner (opprette hendelse, opprette kalender, slette hendelse) utføres i to steg:

1. LLM kaller `proposeCreateEvent(...)` (eller tilsvarende). Verktøyet stashe'r handlingen og returnerer en `confirmationToken` + oppsummering.
2. LLM viser oppsummeringen til brukeren og spør "Skal jeg gjøre dette?".
3. Først når brukeren bekrefter eksplisitt, kaller LLM `confirmCreateEvent(confirmationToken)` som faktisk hitter Google.

Tokens er bundet til `(userId, kind)` og er én-gangs med 15 min TTL — en annen bruker kan ikke konsumere tokenet, og det kan heller ikke gjenbrukes for en annen operasjon.

### 3. Run the Application

#### Option A — Maven (local development)
```bash
./mvnw spring-boot:run
```

#### Option B — Docker (standalone)
Build the image and run it, passing your `.env` file:
```bash
docker build -t abuhint .
docker run -p 8080:8080 --env-file .env abuhint
```

Or set individual variables inline:
```bash
docker run -p 8080:8080 \
  -e EASTER_API_KEY=... \
  -e PINECONE_API_KEY=... \
  -e GITHUB_REPO_URL=... \
  -e GITHUB_JOSEFUS_TOKEN=... \
  -e RESEND_API_KEY=... \
  -e RESEND_FROM_EMAIL=... \
  -e GEMINIAI_API_KEY=... \
  -e GCP_PROJECT_ID=... \
  -e GCP_LOCATION=... \
  abuhint
```

To also persist logs on the host:
```bash
docker run -p 8080:8080 --env-file .env -v "$(pwd)/logs:/app/logs" abuhint
```

#### Option C — Docker Compose
Populate your `.env` file with the variables listed above, then:
```bash
docker compose up --build
```

To run in the background:
```bash
docker compose up --build -d
docker compose logs -f   # stream logs
docker compose down      # stop and remove containers
```

### 4. Access the Application
- **H2 Console**: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
- **LangChain4j Assistant**: Interact with the AI assistant via API endpoints.

---

## 🧩 Key Components

### `LangChain4jAssistant`
- An AI service interface that handles user interactions.
- Responds to user messages and streams tokenized responses.

### `EmailService`
- A Spring component for sending emails using the Resend API.
- Logs success or failure of email delivery.

### `application.yml`
- Centralized configuration for server, database, AI, and email properties.
- See `docs/web-search.md` for web search env and failure modes.
- `familie.*` block holds Google OAuth client + default timezone + AES-GCM token-encryption key. `spring.datasource` / `spring.jpa` default to H2 in-mem; override via `DATASOURCE_*` env vars for Postgres prod.

### `familie/` package
- `FamilieplanleggernProperties` / `FamilieplanleggernConfiguration` – binds the `familie.*` yml block and wires the `TokenCipher` bean (AES-256-GCM) used to encrypt Google tokens at rest.
- `TokenCipher` + `JpaUserGoogleCredentialStore` – per-user credential persistence. Refresh-tokens never touch the DB in plaintext.
- `GoogleOAuthController` + `GoogleOAuthServiceImpl` – start/callback/status/disconnect endpoints, one-shot state cache, deep-link redirect.
- `GoogleCalendarClient` / `GoogleCalendarClientImpl` – auto-refreshing Calendar v3 facade. Re-persists rotated access tokens on refresh.
- `FamilieplanleggernTool` + `InMemoryProposalStore` – LangChain4j `@Tool` surface with the propose → confirm gate.
- `FamilieplanleggernAssistant` + `FamilieChatService` + `FamilieController` – the agent's @AiService wiring, orchestration, and JWT-gated REST endpoints.

---

## 🐛 Debugging & Logs
- **LangChain4j Logs**: Set to `DEBUG` for detailed request/response logs.
- **Resend Logs**: Captures email API interactions.

---

## 🛡️ Security
- Sensitive keys and tokens are managed via environment variables.
- Avoid hardcoding credentials in the codebase.

---

## 🤝 Contributing
We welcome contributions! Feel free to fork the repo, create a branch, and submit a pull request.

---

## 📜 License
This project is licensed under the MIT License. See the `LICENSE` file for details.

---

## 🎉 Have Fun!
AbuHint is here to make your life easier, whether you're brainstorming ideas, coaching a team, or sending emails. Enjoy the ride! 🚀
