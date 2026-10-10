# Sekretær-delegering: veikart for bedre brukeropplevelse

Oversikt over hvordan sekretæren (`SecretaryAssistant`) og delegering til workere bør utvikles. Detaljplaner finnes for steg 1 og 2; resten er skissert her og skal detaljplanlegges før implementering.

## Dagens tilstand (2026-10-10)

- `delegateSecretaryTask` kjører worker **synkront** inne i verktøykallet (`SecretaryDelegationService.delegate`).
- Resultat lagres i `secretary_task.result_summary` (+ `task_execution`) og returneres som JSON til sekretær-LLM-en i samme tur.
- Ingen callback/event/polling. Brukeren venter i blinde; HTTP-request blokkert mens worker kjører.
- `waiting_for_confirmation` krever nytt `delegateSecretaryTask`-kall etter brukerens ja.

## Steg

| # | Steg | Status | Plan |
|---|---|---|---|
| 1 | SSE-hendelser for statusendringer (+ splitt lang transaksjon) | Planlagt | [2026-10-10-secretary-task-events-sse.md](2026-10-10-secretary-task-events-sse.md) |
| 2 | Hybrid synkron/asynkron delegering med tidsgrense | Planlagt | [2026-10-10-secretary-hybrid-delegation.md](2026-10-10-secretary-hybrid-delegation.md) |
| 3 | Sekretæren melder fra selv når bakgrunnsjobb er ferdig | Skisse | — |
| 4 | Bekreftelse som knapp | Skisse | — |
| 5 | Parallelle oppgaver | Skisse | — |
| 6 | Robusthet: avbryt, retry | Skisse | — |
| 7 | Rike resultater via artefakter | Skisse | — |

## Skisser for steg 3–7

### 3. Proaktiv oppsummering
- Ved `task.done` for bakgrunnsjobb: legg en melding i sekretærens chat-minne (`SecretaryChatIds.memoryId(chatId)`) — f.eks. «Oppgave X ferdig: …».
- Kjør en kort sekretær-tur som oppsummerer for brukeren; push via SSE (`event: assistant_message`).
- Bruker offline: oppsummeringen ligger klar ved neste tilkobling; evt. push/e-post.
- Avklar: hvordan unngå samtidige skrivinger til chat-minnet hvis brukeren skriver samtidig.

### 4. Bekreftelse som knapp
- `task.needs_confirmation` med strukturert payload (hva skal gjøres, hvilken agent, brief).
- `POST /api/secretary/tasks/{id}/confirm` og `/reject` → starter delegering direkte uten LLM-runde.

### 5. Parallelle oppgaver
- Sekretæren kan delegere flere uavhengige oppgaver i samme tur; hver kjører på executoren (steg 2) samtidig.
- Prompt-veiledning for når parallell delegering er riktig.

### 6. Robusthet
- `POST /api/secretary/tasks/{id}/cancel` (markér `failed`/ny `cancelled`-status — krever enum-utvidelse).
- Automatisk én retry ved transient feil; «Prøv igjen»-endepunkt.
- Multi-instans: bytt in-memory hub med Postgres `LISTEN/NOTIFY` eller Redis pub/sub bak samme `@EventListener`.

### 7. Rike resultater
- Bruk `TaskArtifactEntity` (finnes, V4-migrasjon) for lenker, filer (PPT via `S3FileLinkService`), kalenderhendelser.
- Inkluder artefakter i `task.done`-hendelse; klient viser kort.
