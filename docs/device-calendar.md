# Device calendar chat

Android's current Family Planner uses `/api/familie/device`, not Google OAuth.
The legacy `/api/familie/send`, `/stream`, history and OAuth endpoints remain
available for older clients. Deploy this backend before the updated Android app.

## Endpoints (JWT required)

- `GET /api/familie/device/{chatId}` returns `DeviceTurnResponse`.
- `POST /api/familie/device/{chatId}/turn` accepts `DeviceTurnRequest` with a unique
  `requestId`, IANA `timezone`, and exactly one of `message` or `result`.
- A response contains visible `messages` and at most one `pendingOperation`.
- Operations: `read_events` or `create_event`, with ISO offset timestamps `start`,
  `end`, `timezone`, `allDay`, and optional `title`/`location`. Creation requires title.
- Results reference the server-issued operation ID; statuses are `success`,
  `cancelled`, `permission_denied`, `error`. Successful creation requires `eventId`.
  Reads carry at most 200 events plus a `truncated` flag.
- Calendar reads span at most 31 days. All-day events use UTC midnight boundaries
  and an exclusive end date. Only single events can be created.

`DeviceCalendarAssistant` is a separate structured-output AI service with **no Google
tools**. It proposes an operation; Android reads selected calendars or shows a
confirmation card before executing it. Permission grants alone do not confirm writes.
Device responses are treated as data, not user instructions.

## Persistence and concurrency

Flyway V5 adds `device_calendar_session`. User ID + chat ID are hashed together as
the row key. Model context, visible messages and recent idempotent responses survive
server restarts. Production must use the existing persistent database configuration.
Striped locks serialize same-session turns within a process; optimistic JPA versions
reject conflicting updates across replicas. Retry with the same request ID.

Retains the latest 100 visible messages, 40 context entries and 20 request hashes +
responses per session. Shared calendar fields are part of persisted model context;
they are not indexed in Pinecone. There is currently no automatic row expiry/deletion.
New conversation starts a new row rather than deleting previous conversations.

The phone keeps an encrypted, account-scoped request outbox. Its event insert includes
an operation marker for deduplication after process death. The phone is the only writer;
the backend does not claim Google has synchronized the inserted event.

Tests: `./mvnw '-Dtest=DeviceCalendar*Test' test` exercises state transitions, account
isolation, replay, real LangChain4j structured-output parsing and Flyway/JPA persistence.
Android details and manual scenarios: `../abuhint-android/DEVICE_CALENDAR_MIGRATION.md`.
