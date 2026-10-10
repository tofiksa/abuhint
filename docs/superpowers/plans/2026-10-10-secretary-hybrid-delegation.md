# Sekretær: hybrid synkron/asynkron delegering (steg 2)

> **For agentic workers:** Implementer oppgave for oppgave. Steg bruker checkbox-syntaks (`- [ ]`) for sporing. **Forutsetter at [steg 1](2026-10-10-secretary-task-events-sse.md) er implementert** (splittet transaksjon + `SecretaryTaskEvent` + hub). Se også [veikart](2026-10-10-secretary-delegation-roadmap.md).

**Mål:** Raske oppgaver svarer i samme tur som i dag. Trege oppgaver blokkerer ikke brukeren: sekretæren kvitterer, workeren fortsetter i bakgrunnen, og resultatet leveres via SSE-hendelse.

**Arkitektur:** `SecretaryDelegationService.delegate` deles i `prepare` (kort tx, idempotent `running`) → `launch` (worker på egen executor via `CompletableFuture` med timeout) → `complete` (skriver resultat kun hvis fortsatt `running`). Verktøykallet venter maks `sync-wait-ms`; ved timeout returneres `running` + `backgrounded=true`.

**Tech Stack:** Kotlin, Spring Boot 4.1.1, Java 25 virtuelle tråder, `CompletableFuture`, Spring Data JPA.

## Bakgrunn / funn i koden

- `WorkerExecutionService` setter selv `ChatIdContextHolder`/`TokenUsageContextHolder` og rydder i `finally` → fungerer på executor-tråd.
- `FamilieChatService.processChat` tar `userId` eksplisitt → `SecurityContext` trengs ikke i workeren.
- `SecretaryAssistant` (både `chat` og `chatStream`) har prompt-seksjon «Synkron delegasjon» som forbyr å si «kommer tilbake» — må skrives om.
- Ingen executor/async-config finnes i dag.

## Globale begrensninger

- Ingen ny Flyway-migrasjon.
- Én serverinstans antas.
- Timeout avbryter ikke selve LLM-kallet — kun markerer `failed` og ignorerer sent resultat.
- Minimal diff.

---

### Oppgave 1: Konfigurasjon og executor

**Filer:** ny `secretary/SecretaryDelegationProperties.kt`, ny `configuration/SecretaryAsyncConfiguration.kt`, `src/main/resources/application.yml`

- [ ] Legg til i `application.yml`:
  ```yaml
  abuhint:
    secretary:
      delegation:
        sync-wait-ms: ${SECRETARY_SYNC_WAIT_MS:8000}
        worker-timeout-ms: ${SECRETARY_WORKER_TIMEOUT_MS:180000}
        max-concurrent: ${SECRETARY_MAX_CONCURRENT:8}
  ```
- [ ] `@ConfigurationProperties("abuhint.secretary.delegation") data class SecretaryDelegationProperties(...)` + registrer (`@EnableConfigurationProperties` / `@ConfigurationPropertiesScan`)
- [ ] Bean `secretaryWorkerExecutor`: `SimpleAsyncTaskExecutor` med `setVirtualThreads(true)` og `concurrencyLimit = maxConcurrent`
- [ ] Oppdater env-tabellen i `CLAUDE.md` med de tre nye variablene

### Oppgave 2: Idempotent statusovergang

**Filer:** `secretary/SecretaryTaskRepository.kt`

- [ ] `@Modifying @Query("UPDATE SecretaryTaskEntity t SET t.status = 'running', t.updatedAt = :now WHERE t.id = :id AND t.status <> 'running'") fun markRunningIfNotRunning(id, now): Int`
- [ ] Tilsvarende betinget `complete`-spørring, eller les-og-sjekk i `TransactionTemplate` (skriv kun hvis status == `running`)

### Oppgave 3: prepare / launch / complete

**Filer:** `secretary/SecretaryDelegationService.kt`

- [ ] `prepare(task, userId)` (kort tx):
  - samtykke-gate → `waiting_for_confirmation` + `task.needs_confirmation`, returner tidlig
  - `markRunningIfNotRunning` — returnerer 0 ⇒ allerede kjørende, returner nåværende tilstand uten ny kjøring
  - opprett `TaskExecutionEntity`, publiser `task.running`
- [ ] `launch(...)`: `CompletableFuture.supplyAsync({ runWorker(...) }, secretaryWorkerExecutor).orTimeout(workerTimeoutMs, MILLISECONDS)`; `whenComplete { result, err -> complete(taskId, executionId, result, err) }`
  - Behold `DelegatedAgentRunner`-sløyfa inne i `runWorker`
- [ ] `complete(...)`: les oppgave på nytt; skriv `done`/`failed` + execution kun hvis status fortsatt `running`; publiser `task.done`/`task.failed`. `TimeoutException` → `errorMessage = "Worker brukte for lang tid"`
- [ ] `delegate(...)`: `prepare` → `launch` → `future.get(syncWaitMs)`
  - ferdig ⇒ returner fersk entity (`done`/`failed`) som i dag
  - `TimeoutException` ⇒ returner entity med `running` og marker som backgrounded (returtype f.eks. `DelegationOutcome(task, backgrounded: Boolean)`)
- [ ] Logg feil i `complete` — exceptions i `whenComplete` må ikke forsvinne stille

### Oppgave 4: Verktøy og prompt

**Filer:** `secretary/SecretaryTaskTool.kt`, `secretary/SecretaryTaskService.kt`, `repository/SecretaryAssistant.kt`

- [ ] `delegateTask` returnerer `DelegationOutcome`; `delegateSecretaryTask` legger `"backgrounded"` i JSON
- [ ] Ny `@Tool`-beskrivelse: «Delegér oppgaven til valgt worker. Venter kort; returnerer enten status=done med resultSummary, eller status=running med backgrounded=true hvis workeren fortsatt jobber.»
- [ ] Erstatt seksjonen «Synkron delegasjon» i **både** `chat` og `chatStream`:
  - `status=done` → svar med innholdet i `resultSummary`
  - `status=failed` → forklar kort feilen, tilby å prøve igjen
  - `status=running` + `backgrounded=true` → si kort at workeren jobber og at resultatet dukker opp i oppgavelisten; ikke dikt opp resultat
  - Ved ny tur der brukeren spør om fremdrift: kall `getSecretaryTask`/`listSecretaryTasks` før du svarer
  - `ready` er fortsatt ikke et gyldig stoppunkt
  - Behold regelen om `waiting_for_confirmation`

### Oppgave 5: Opprydding ved oppstart

**Filer:** ny `secretary/SecretaryTaskRecovery.kt`

- [ ] `@EventListener(ApplicationReadyEvent::class)`: alle `SecretaryTaskEntity` med status `running`/`delegated` → `failed`, `errorMessage = "Avbrutt pga. omstart av tjenesten"`; tilsvarende `TaskExecutionEntity` `running` → `failed`
- [ ] Legg til nødvendige repo-spørringer (`findAllByStatusIn` e.l.)
- [ ] Logg antall gjenopprettede oppgaver

### Oppgave 6: Tester

**Filer:** `src/test/.../secretary/SecretaryDelegationAndTaskServiceTest.kt`, ny `SecretaryTaskRecoveryTest.kt`

Bruk kontrollerbar executor / `CountDownLatch` i mock-worker; korte `sync-wait-ms`/`worker-timeout-ms` i test.

- [ ] Rask worker (< sync-wait): verktøy returnerer `done` + `resultSummary`, `backgrounded=false`
- [ ] Treg worker: returnerer `running` + `backgrounded=true`; senere `done` + `task.done` publisert
- [ ] Worker over `worker-timeout-ms`: `failed`; sent resultat overskriver ikke
- [ ] Dobbel delegering samtidig: kun én worker-kjøring
- [ ] Recovery: `running` → `failed` ved oppstart
- [ ] `./mvnw test` grønn

---

## Ferdig når

- Oppgaver under ~8 s oppfører seg som før (svar i samme tur)
- Lengre oppgaver gir umiddelbar kvittering, og `task.done` kommer via SSE når de er ferdige
- Samme oppgave kan ikke kjøres to ganger samtidig
- Omstart etterlater ingen oppgaver hengende i `running`

## Bevisst utenfor scope (se veikart)

- Sekretæren melder ikke proaktivt fra når en bakgrunnsjobb er ferdig (steg 3)
- Multi-instans hendelsesdistribusjon
- Avbryt-endepunkt og retry
