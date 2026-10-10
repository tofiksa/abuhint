# Sekretær: statushendelser via SSE (steg 1)

> **For agentic workers:** Implementer oppgave for oppgave. Steg bruker checkbox-syntaks (`- [ ]`) for sporing. Se også [steg 2](2026-10-10-secretary-hybrid-delegation.md) og [veikart](2026-10-10-secretary-delegation-roadmap.md).

**Mål:** Klienten skal se en levende oppgaveliste (created → running → done/failed/needs_confirmation) mens sekretæren delegerer, i stedet for å vente i blinde.

**Arkitektur:** Domenehendelser (`SecretaryTaskEvent`) publiseres via Spring `ApplicationEventPublisher` ved hver statusendring. En in-memory `SecretaryTaskEventHub` lytter og pusher til abonnenter via `SseEmitter`, filtrert på `chatId` + `userId`. Kjøring forblir synkron i dette steget.

**Tech Stack:** Kotlin, Spring Boot 4.1.1, Spring MVC `SseEmitter`, Spring Data JPA.

## Bakgrunn / funn i koden

- `SecretaryTaskService.delegateTask` og `SecretaryDelegationService.delegate` er begge `@Transactional`. Hele worker-kjøringen (30–60 s) skjer i én DB-transaksjon: holder DB-connection, og `running` er ikke synlig for andre lesere før alt er ferdig. Må splittes før hendelser gir mening.
- Ingen `@EnableScheduling` / `@EnableAsync` finnes i prosjektet.
- JWT ligger i `Authorization`-header → nettleserens `EventSource` kan ikke brukes. Klient må bruke fetch-basert SSE (f.eks. `@microsoft/fetch-event-source`).
- Eksisterende `/api/secretary/chat/stream` sender tokens som rå `data:`-linjer — ikke bland inn hendelser der uten opt-in.

## Globale begrensninger

- Ingen ny Flyway-migrasjon (eksisterende kolonner dekker behovet).
- Ikke bryt eksisterende klienter av `/chat` og `/chat/stream`.
- Én serverinstans antas (in-memory hub). Multi-instans er utenfor scope.
- Minimal diff, ingen urelaterte refaktoreringer.

---

### Oppgave 1: Splitt den lange transaksjonen

**Filer:** `secretary/SecretaryDelegationService.kt`, `secretary/SecretaryTaskService.kt`

- [ ] Fjern `@Transactional` fra `SecretaryDelegationService.delegate`
- [ ] Fjern `@Transactional` fra `SecretaryTaskService.delegateTask`
- [ ] Sørg for at hver statusendring persisteres med egen kort `save` (Spring Data gir egen tx per `save`); bruk `TransactionTemplate` der flere skrivinger må være atomiske
- [ ] Flyt: lagre `delegated` → lagre `running` + `TaskExecutionEntity` → kjør worker (uten tx) → lagre `done`/`failed` + oppdater execution
- [ ] Eksisterende tester i `SecretaryDelegationAndTaskServiceTest.kt` er grønne

### Oppgave 2: Hendelsesmodell og publisering

**Filer:** ny `secretary/SecretaryTaskEvent.kt`, `secretary/SecretaryTaskService.kt`, `secretary/SecretaryDelegationService.kt`

- [ ] Opprett:
  ```kotlin
  data class SecretaryTaskEvent(
      val type: String, // task.created | task.updated | task.running | task.done | task.failed | task.needs_confirmation
      val taskId: String,
      val chatId: String,
      val userId: String,
      val title: String,
      val status: String,
      val assignedAgentId: String?,
      val resultSummary: String?,
      val errorMessage: String?,
      val at: Instant = Instant.now(),
  )
  ```
  - Vurder en `companion fun from(type, entity)` for å unngå duplisering
- [ ] Injiser `ApplicationEventPublisher` i `SecretaryTaskService`; publiser `task.created` (createTask), `task.updated` (updateTask), `task.done` (markDone)
- [ ] Injiser `ApplicationEventPublisher` i `SecretaryDelegationService`; publiser `task.needs_confirmation` (samtykke-gate), `task.running`, `task.done`, `task.failed`
- [ ] Hendelsen bærer all data selv — lytter skal ikke re-lese DB

### Oppgave 3: `SecretaryTaskEventHub`

**Filer:** ny `secretary/SecretaryTaskEventHub.kt`, ny `configuration/SchedulingConfiguration.kt`

- [ ] `ConcurrentHashMap<String /*chatId*/, CopyOnWriteArraySet<Subscriber>>`, `Subscriber(userId, emitter)`
- [ ] `subscribe(chatId, userId, timeoutMs): SseEmitter` — registrerer `onCompletion`/`onTimeout`/`onError` som fjerner abonnenten
- [ ] `@EventListener fun on(event: SecretaryTaskEvent)` — send `SseEmitter.event().name("task").data(event /*JSON*/)` til abonnenter med samme `chatId` **og** `userId`; fjern ved `IOException`
- [ ] `@Scheduled(fixedRate = 20_000)` heartbeat (`SseEmitter.event().comment("ping")`) til alle abonnenter
- [ ] `SchedulingConfiguration` med `@EnableScheduling`

### Oppgave 4: Endepunkter

**Filer:** `controller/SecretaryController.kt` (eller ny `controller/SecretaryTaskController.kt`)

- [ ] `GET /api/secretary/tasks?chatId=` → JSON-liste (gjenbruk view-mapping fra `SecretaryTaskTool.toView()` — flytt til felles extension)
  - Filtrer på `userId` fra `SecurityContext` (repo har `findAllByChatIdOrderBySortOrderAsc`; legg til `userId`-filter)
- [ ] `GET /api/secretary/tasks/events?chatId=` (`text/event-stream`, timeout ~30 min)
  - Første event: `event: task` med `type=task.snapshot` og alle oppgaver for `chatId`+`userId`
  - Deretter live-hendelser via hub
- [ ] Valgfritt: `includeTaskEvents: Boolean = false` på `/chat/stream` — når `true`, abonner samme emitter på hub mens turen pågår (`event: task`), avregistrer ved `[DONE]`/feil
- [ ] OpenAPI-annotasjoner i samme stil som eksisterende endepunkter
- [ ] Bekreft at `SecurityConfiguration` krever autentisering for nye stier (default for `/api/**`)

### Oppgave 5: Tester

**Filer:** ny `src/test/.../secretary/SecretaryTaskEventHubTest.kt`, `SecretaryDelegationAndTaskServiceTest.kt`

- [ ] Hub: event leveres kun til riktig `chatId` + `userId`
- [ ] Hub: død emitter (send kaster) fjernes
- [ ] Endepunkt/hub: snapshot sendes ved tilkobling
- [ ] Delegation: publiserer `task.running` → `task.done` i rekkefølge
- [ ] Delegation: publiserer `task.failed` med `errorMessage` ved exception
- [ ] Delegation: publiserer `task.needs_confirmation` ved samtykke-gate
- [ ] `./mvnw test` grønn

---

## Ferdig når

- Klient koblet til `/api/secretary/tasks/events` ser `running` straks delegering starter og `done`/`failed` når worker er ferdig
- Ingen DB-transaksjon spenner over worker-kjøring
- Eksisterende chat-endepunkter oppfører seg uendret
