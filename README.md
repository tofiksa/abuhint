# AbuHint: Your AI-Powered Assistant

<img src="assets/Abu-hint-coach.png" alt="AbuHint Logo" width="400" />

Welcome to **AbuHint**, a Kotlin-based Spring Boot application that combines the power of AI with seamless email communication. This project is designed to assist you with team coaching, sparring, and idea generation while integrating tools like LangChain4j and Resend for enhanced functionality.
Welcome to **AbuHint**, a Kotlin-based Spring Boot application that combines the power of AI with seamless email communication. This project is designed to assist you with team coaching, sparring, and idea generation while integrating tools like LangChain4j and Resend for enhanced functionality.

---

## 🚀 Features

### 🤖 AI Assistant
- **LangChain4j Integration**: Powered by OpenAI's GPT-4.1-mini model.
- **Capabilities**:
    - Acts as a world-class team coach and sparring partner.
    - Helps create plans, provides feedback, and generates ideas.
    - References books and methods for advice.
    - Sends emails with your CV upon request (using the `sendEmail` tool).

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
```

### 3. Run the Application
```bash
./mvnw spring-boot:run
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
