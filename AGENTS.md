# AGENTS.md

This guide is for agentic coding tools working in this repository.
It summarizes build/test commands and local code style expectations.

## Project Snapshot
- Stack: Kotlin 2.0.21, Java 21, Spring Boot 3.4.4, Maven wrapper.
- Source layout: `src/main/kotlin/no/josefus/abuhint`.
- Tests: `src/test/kotlin/no/josefus/abuhint` (JUnit 5 + Mockito).
- External services: OpenAI, Pinecone, Resend, GitHub, Tavily/Brave web search.

## Build, Run, and Test Commands
Use the Maven wrapper (`./mvnw`) from repo root.

### Build
- Clean build (skips nothing): `./mvnw clean package`
- Build without tests: `./mvnw clean package -DskipTests`
- Run the app: `./mvnw spring-boot:run`

### Tests
- Run all tests: `./mvnw test`
- Run a single test class: `./mvnw -Dtest=WebSearchToolTest test`
- Run a single test method: `./mvnw -Dtest=WebSearchToolTest#searchWeb_returns_formatted_results_when_enabled test`
- Run a package of tests (wildcard): `./mvnw -Dtest=*WebSearch* test`

### Lint / Format
- No dedicated lint/format task is configured in `pom.xml`.
- Follow existing Kotlin formatting and IntelliJ defaults.

## Code Style Guidelines
These are inferred from the codebase; keep changes consistent.

### Kotlin and Java Basics
- Kotlin is primary; keep Java 21 compatibility in mind.
- Prefer `val` over `var`; use `var` only when needed.
- Use nullable types (`String?`) only when truly optional.
- Prefer Kotlin stdlib helpers (`takeIf`, `isNullOrBlank`, `trim`, `map`) for clarity.
- Favor concise data classes for DTOs and request/response models.

### Formatting and Layout
- Indentation: 4 spaces.
- Braces on the same line for class and function declarations.
- Leave a blank line between logical sections (imports, annotations, functions).
- Keep lines reasonably short; wrap long argument lists across lines.
- Use trailing commas in multiline argument lists and constructors where already used.

### Imports
- Order: package declaration, blank line, imports, blank line, declarations.
- Prefer explicit imports; wildcard imports are used for Spring annotations in some files and are acceptable when consistent with nearby code.
- Do not reorder imports unless you are touching the file and can keep the style consistent.

### Naming Conventions
- Packages: all lowercase (e.g., `no.josefus.abuhint`).
- Classes/objects: `PascalCase` (e.g., `ChatService`).
- Functions/variables: `camelCase`.
- Test names: backtick-quoted descriptive names are common for unit tests.
- Constants: `UPPER_SNAKE_CASE` only when needed; otherwise prefer `val` in companion objects.

### Spring and DI
- Use constructor injection (`class Foo(private val dep: Dep)`), avoid field injection.
- Use Spring stereotypes: `@Service`, `@RestController`, `@Configuration`.
- Use `@Value` with `lateinit var` for configuration when necessary.
- Keep configuration beans in `configuration/` and service logic in `service/`.

### Error Handling and Logging
- Use `org.slf4j.LoggerFactory.getLogger(Class::class.java)` for logging.
- Log warnings/errors for unexpected conditions; avoid `println`.
- For user-facing responses, return a safe fallback message on errors.
- For reactive flows, prefer `Mono.error(...)` over throwing.
- Avoid swallowing exceptions; wrap only when needed and log the error.

### API and DTOs
- Controllers should return `ResponseEntity` and accept DTOs with `@RequestBody`.
- Keep DTOs in `dto/` and ensure JSON annotations are explicit when needed.
- Prefer explicit `@JsonProperty` for non-standard JSON names.
- For OpenAI-compatible models, keep default values in DTOs consistent with current defaults.

### Tests
- Use JUnit 5 (`@Test`) and Mockito (`@ExtendWith(MockitoExtension::class)`).
- Prefer clear arrange/act/assert sections; use `assertEquals`, `assertTrue`.
- Keep tests deterministic; mock external services (`WebSearchClient`, GitHub API, etc.).

## Environment and Configuration
- Sensitive values are provided via environment variables (see `README.md`).
- Do not commit secrets or `.env` files.
- `application.yml` (in `src/main/resources`) holds default app config; keep new properties in this file.

## Repository Structure
- `src/main/kotlin/no/josefus/abuhint` app code.
- `src/test/kotlin/no/josefus/abuhint` tests.
- `docs/` contains behavior/design notes for the chatbot system.
- `assets/` contains images.

## Cursor/Copilot Rules
- No `.cursor/rules/`, `.cursorrules`, or `.github/copilot-instructions.md` found in this repo.

## Practical Tips for Agents
- Use the Maven wrapper for consistency across environments.
- When adding new files, follow the existing package and directory layout.
- Keep changes scoped; avoid formatting unrelated code.
- If adding new external calls, wire them through services/configuration classes.
