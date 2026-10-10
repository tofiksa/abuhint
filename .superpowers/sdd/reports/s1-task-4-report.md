# Steg 1 Oppgave 4 – rapport

## Status

Fullført.

- La til `GET /api/secretary/tasks?chatId=` med filtrering på autentisert `userId`.
- La til `GET /api/secretary/tasks/events?chatId=` med 30 minutters SSE-timeout, første `task.snapshot`-hendelse og deretter live-hendelser via `SecretaryTaskEventHub`.
- Flyttet oppgavevisningen fra `SecretaryTaskTool` til en delt `SecretaryTaskEntity.toView()`-extension.
- La til OpenAPI-annotasjoner.
- Bekreftet at `SecurityConfiguration.anyRequest().authenticated()` beskytter de nye rutene.

## Commit

- `3baa1f3 feat(secretary): expose task list and event stream`

## Tester

- `./mvnw -Dtest=SecretaryTaskControllerTest,SecretaryTaskServiceTest,SecretaryStreamingContextTest test`
  - 8 tester, 0 feil.
- `./mvnw test`
  - 108 tester, 0 feil.

## Bekymringer

- Valgfri `includeTaskEvents` på `/chat/stream` ble ikke implementert, for å unngå å endre eksisterende stream-adferd.
- Snapshot leses rett før hub-abonnementet opprettes. Dette garanterer at snapshot er første SSE-hendelse, men gir et svært smalt vindu hvor en samtidig task-hendelse kan oppstå mellom DB-lesing og abonnement.
