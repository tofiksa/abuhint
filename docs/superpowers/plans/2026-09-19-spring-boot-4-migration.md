# Spring Boot 4 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade AbuHint from Spring Boot 3.5.13 to Spring Boot 4.1.x with Java 25, keeping the app buildable and tests green.

**Architecture:** Bump Boot parent + LangChain4j Boot4 starters, keep Jackson 2 via `spring-boot-jackson2` as a deliberate interim (minimize DTO churn), then fix Flyway/springdoc/test annotations and compile/runtime breakages iteratively.

**Tech Stack:** Kotlin 2.3.20, Java 25, Spring Boot 4.1.1, LangChain4j 1.20.0 / spring 1.20.0-beta30, springdoc-openapi 3.0.3

## Global Constraints

- Target Spring Boot parent: `4.1.1`
- Target Java: `25` (LTS; user also verified higher JDKs work locally)
- LangChain4j Boot 4 artifacts must use `*-spring-boot4-starter` naming
- Do not commit secrets; do not push unless asked
- Prefer minimal diffs; no unrelated refactors

---

### Task 1: Bump Maven dependencies (`pom.xml`)

**Files:** `pom.xml`

- [x] Set `spring-boot-starter-parent` to `4.1.1`
- [x] Keep/set `java.version` to `25`
- [x] Bump `langchain.version` → `1.20.0`, `langchain.spring.version` → `1.20.0-beta30`
- [x] Replace `langchain4j-spring-boot-starter` → `langchain4j-spring-boot4-starter`
- [x] Replace `langchain4j-open-ai-spring-boot-starter` → `langchain4j-open-ai-spring-boot4-starter`
- [x] Point `langchain4j-pinecone` at `${langchain.spring.version}` (beta artifact)
- [x] Replace bare Flyway deps with `spring-boot-starter-flyway` (keep postgres flyway module if still needed)
- [x] Bump springdoc to `3.0.3`
- [x] Add `spring-boot-jackson2` (interim Jackson 2 path)
- [x] Add temporary `spring-boot-properties-migrator` (runtime)

### Task 2: Align Jackson config with Boot 4 Jackson2 module

**Files:** `src/main/kotlin/.../configuration/JsonConfiguration.kt`

- [x] Ensure `ObjectMapper` bean still works with Boot 4 + `spring-boot-jackson2` (adjust builder type/package if compile fails)

### Task 3: Dockerfile / runtime Java 25

**Files:** `Dockerfile`

- [x] Update Temurin images from 24 → 25
- [x] Add `system.properties` with `java.runtime.version=25` for Heroku

### Task 4: Fix compile and test breakages

**Files:** as revealed by `./mvnw -DskipTests compile` / `./mvnw test`

- [x] Fix RestTemplateBuilder package + timeout API; add `spring-boot-starter-restclient`
- [x] Remove unused Spring Retry / AOP starter
- [x] Fix `@DataJpaTest` / `@EntityScan` / `@AutoConfigureTestDatabase` package moves + jpa-test starter
- [x] Fix Jackson 3 null-for-primitives on `TavilyResponse.cacheHit`

### Task 5: Verify

- [x] `./mvnw -DskipTests package` succeeds
- [x] `./mvnw test` succeeds — 90 tests, 0 failures (Java 25)
- [x] Leave `spring-boot-properties-migrator` temporarily for startup diagnostics

### Task 6: Stop and report

- [x] Summarize remaining risks (Jackson 3 follow-up, LangChain4j behavior) for the user
