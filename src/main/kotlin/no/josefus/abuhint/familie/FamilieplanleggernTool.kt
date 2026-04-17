package no.josefus.abuhint.familie

import com.github.benmanes.caffeine.cache.Caffeine
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * LangChain4j-exposed tool surface for the Familieplanleggern agent.
 *
 * All mutating operations (create event, create calendar, delete event) use a
 * **propose → confirm** two-step gate. The LLM first calls `proposeX(...)` which
 * stashes a pending action keyed by a random token and returns a human-readable
 * summary. After the *user* confirms (via chat), the LLM calls `confirmX(token)`
 * to actually hit Google. This prevents accidental mutations from an over-eager
 * model.
 *
 * Read-only operations (list) run immediately.
 *
 * User identity is resolved from the Spring [SecurityContextHolder] (JWT subject).
 */
@Component
class FamilieplanleggernTool(
    private val calendarClient: GoogleCalendarClient,
    private val credentialStore: UserGoogleCredentialStore,
    private val proposalStore: InMemoryProposalStore,
    private val properties: FamilieplanleggernProperties,
) {

    private val log = LoggerFactory.getLogger(FamilieplanleggernTool::class.java)
    private val json = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Tool("List the user's Google Calendars (id, name, color). Use this before creating or listing events so you know which calendar to use.")
    fun listCalendars(): String = withUser { userId ->
        val calendars = calendarClient.listCalendars(userId)
        if (calendars.isEmpty()) "{\"calendars\":[], \"message\":\"Brukeren har ingen kalendere.\"}"
        else json.writeValueAsString(mapOf("calendars" to calendars))
    }

    @Tool("List upcoming events in a calendar for the next N days. Use for read-only overviews of the user's schedule.")
    fun listUpcomingEvents(
        @P("The Google Calendar id (from listCalendars). Use 'primary' for the primary calendar.") calendarId: String,
        @P("Number of days ahead to include, 1-30. Default 7.") daysAhead: Int,
    ): String = withUser { userId ->
        val now = Instant.now()
        val end = now.plusSeconds(daysAhead.coerceIn(1, 30) * 24L * 3600L)
        val events = calendarClient.listEvents(userId, calendarId, now, end, maxResults = 50)
        json.writeValueAsString(mapOf("events" to events))
    }

    @Tool("Propose creating a calendar event. Does NOT execute — returns a confirmationToken that the user must approve. Call confirmCreateEvent with the token once the user confirms in plain language.")
    fun proposeCreateEvent(
        @P("Target Google Calendar id.") calendarId: String,
        @P("Event title, e.g. 'Tannlege'.") summary: String,
        @P("Start time in ISO-8601 with offset, e.g. 2026-05-10T09:00:00+02:00.") startIso: String,
        @P("End time in ISO-8601 with offset.") endIso: String,
        @P("Optional description / notes.") description: String?,
        @P("Optional location string.") location: String?,
        @P("Optional comma-separated list of attendee emails.") attendeesCsv: String?,
    ): String = withUser { userId ->
        val start = parseIsoOrNull(startIso)
            ?: return@withUser json.writeValueAsString(mapOf("error" to "Ugyldig startdato: '$startIso'. Bruk ISO-8601 (f.eks. 2026-05-10T09:00:00+02:00)."))
        val end = parseIsoOrNull(endIso)
            ?: return@withUser json.writeValueAsString(mapOf("error" to "Ugyldig sluttdato: '$endIso'. Bruk ISO-8601."))
        if (!end.isAfter(start)) {
            return@withUser json.writeValueAsString(mapOf("error" to "Sluttdato må være etter startdato."))
        }

        val attendees = attendeesCsv
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val proposal = PendingProposal(
            userId = userId,
            kind = ProposalKind.CREATE_EVENT,
            createEvent = CreateEventProposal(
                calendarId = calendarId,
                summary = summary,
                start = start,
                end = end,
                description = description,
                location = location,
                attendees = attendees,
            ),
        )
        val token = proposalStore.put(proposal)

        json.writeValueAsString(
            mapOf(
                "confirmationToken" to token,
                "action" to "createEvent",
                "summary" to summary,
                "calendarId" to calendarId,
                "start" to start.toString(),
                "end" to end.toString(),
                "description" to description,
                "location" to location,
                "attendees" to attendees,
                "instruction" to "Vis denne planen til brukeren på norsk og spør om bekreftelse. Kall deretter confirmCreateEvent(confirmationToken).",
            )
        )
    }

    @Tool("Execute a previously proposed event creation. Requires the confirmationToken from proposeCreateEvent and that the user has explicitly confirmed.")
    fun confirmCreateEvent(@P("Token returned by proposeCreateEvent.") confirmationToken: String): String = withUser { userId ->
        val proposal = proposalStore.consume(confirmationToken, userId, ProposalKind.CREATE_EVENT)
            ?: return@withUser invalidTokenResponse()
        val p = proposal.createEvent!!
        val created = calendarClient.createEvent(
            userId = userId,
            calendarId = p.calendarId,
            event = NewCalendarEvent(
                summary = p.summary,
                start = p.start,
                end = p.end,
                description = p.description,
                location = p.location,
                attendees = p.attendees,
                timezone = properties.defaultTimezone,
            ),
        )
        json.writeValueAsString(mapOf("created" to created))
    }

    @Tool("Propose creating a new Google Calendar (used as a category, e.g. 'Trening', 'Jobb'). Returns a confirmationToken.")
    fun proposeCreateCalendar(
        @P("Calendar display name.") name: String,
        @P("Optional 6-digit hex color like '#3366ff'. Null to use the Google default.") colorHex: String?,
    ): String = withUser { userId ->
        val proposal = PendingProposal(
            userId = userId,
            kind = ProposalKind.CREATE_CALENDAR,
            createCalendar = CreateCalendarProposal(name = name, colorHex = colorHex),
        )
        val token = proposalStore.put(proposal)
        json.writeValueAsString(
            mapOf(
                "confirmationToken" to token,
                "action" to "createCalendar",
                "name" to name,
                "colorHex" to colorHex,
                "instruction" to "Bekreft med brukeren og kall confirmCreateCalendar.",
            )
        )
    }

    @Tool("Execute a previously proposed calendar creation.")
    fun confirmCreateCalendar(@P("Token returned by proposeCreateCalendar.") confirmationToken: String): String = withUser { userId ->
        val proposal = proposalStore.consume(confirmationToken, userId, ProposalKind.CREATE_CALENDAR)
            ?: return@withUser invalidTokenResponse()
        val p = proposal.createCalendar!!
        val created = calendarClient.createCalendar(userId, p.name, p.colorHex)
        json.writeValueAsString(mapOf("created" to created))
    }

    @Tool("Propose deleting an event from a calendar. Returns a confirmationToken.")
    fun proposeDeleteEvent(
        @P("Calendar id containing the event.") calendarId: String,
        @P("Event id to delete.") eventId: String,
    ): String = withUser { userId ->
        val proposal = PendingProposal(
            userId = userId,
            kind = ProposalKind.DELETE_EVENT,
            deleteEvent = DeleteEventProposal(calendarId = calendarId, eventId = eventId),
        )
        val token = proposalStore.put(proposal)
        json.writeValueAsString(
            mapOf(
                "confirmationToken" to token,
                "action" to "deleteEvent",
                "calendarId" to calendarId,
                "eventId" to eventId,
                "instruction" to "Bekreft sletting med brukeren og kall confirmDeleteEvent.",
            )
        )
    }

    @Tool("Execute a previously proposed event deletion.")
    fun confirmDeleteEvent(@P("Token returned by proposeDeleteEvent.") confirmationToken: String): String = withUser { userId ->
        val proposal = proposalStore.consume(confirmationToken, userId, ProposalKind.DELETE_EVENT)
            ?: return@withUser invalidTokenResponse()
        val p = proposal.deleteEvent!!
        calendarClient.deleteEvent(userId, p.calendarId, p.eventId)
        json.writeValueAsString(mapOf("deleted" to true, "eventId" to p.eventId, "melding" to "Hendelse slettet."))
    }

    private fun invalidTokenResponse(): String =
        json.writeValueAsString(mapOf("error" to "Ugyldig eller utløpt confirmationToken. Be brukeren bekrefte på nytt."))

    private inline fun <R> withUser(block: (String) -> R): String {
        val auth = SecurityContextHolder.getContext().authentication
        val userId = auth?.name
        if (userId.isNullOrBlank()) {
            return json.writeValueAsString(mapOf("error" to "Ingen autentisert bruker i kontekst."))
        }
        val creds = credentialStore.load(userId)
        if (creds == null) {
            return json.writeValueAsString(
                mapOf(
                    "error" to "Brukeren er ikke koblet til Google ennå. Be brukeren koble til via Familieplanleggern → Koble til Google.",
                    "notConnected" to true,
                )
            )
        }
        return try {
            block(userId).toString()
        } catch (e: FamilieplanleggernNotConnectedException) {
            json.writeValueAsString(mapOf("error" to "Google-konto ikke tilkoblet.", "notConnected" to true))
        } catch (e: Exception) {
            log.error("Tool call failed for userId={}: {}", userId, e.message, e)
            json.writeValueAsString(mapOf("error" to "Teknisk feil mot Google Calendar: ${e.message}"))
        }
    }

    private fun parseIsoOrNull(value: String): Instant? {
        if (value.isBlank()) return null
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                ZonedDateTime.parse(value).toInstant()
            } catch (_: DateTimeParseException) {
                try {
                    val local = java.time.LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    local.atZone(ZoneId.of(properties.defaultTimezone)).toInstant()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }
}

/**
 * In-memory short-lived store of pending Familieplanleggern proposals. Each
 * proposal is keyed by a random token and bound to the user that created it so
 * one user cannot confirm another's pending action.
 */
@Component
class InMemoryProposalStore {

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .maximumSize(10_000)
        .build<String, PendingProposal>()

    fun put(proposal: PendingProposal): String {
        val token = UUID.randomUUID().toString()
        cache.put(token, proposal)
        return token
    }

    fun consume(token: String, userId: String, expectedKind: ProposalKind): PendingProposal? {
        val proposal = cache.getIfPresent(token) ?: return null
        if (proposal.userId != userId || proposal.kind != expectedKind) return null
        cache.invalidate(token)
        return proposal
    }
}

data class PendingProposal(
    val userId: String,
    val kind: ProposalKind,
    val createEvent: CreateEventProposal? = null,
    val createCalendar: CreateCalendarProposal? = null,
    val deleteEvent: DeleteEventProposal? = null,
)

enum class ProposalKind { CREATE_EVENT, CREATE_CALENDAR, DELETE_EVENT }

data class CreateEventProposal(
    val calendarId: String,
    val summary: String,
    val start: Instant,
    val end: Instant,
    val description: String?,
    val location: String?,
    val attendees: List<String>,
)

data class CreateCalendarProposal(val name: String, val colorHex: String?)
data class DeleteEventProposal(val calendarId: String, val eventId: String)
