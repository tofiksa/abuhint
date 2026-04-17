package no.josefus.abuhint.familie

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FamilieplanleggernToolTest {

    private val calendarClient: GoogleCalendarClient = mock()
    private val credentialStore: UserGoogleCredentialStore = mock()
    private val proposalStore = InMemoryProposalStore()
    private val props = FamilieplanleggernProperties(
        googleClientId = "cid",
        googleClientSecret = "secret",
        googleRedirectUri = "redirect",
        defaultTimezone = "Europe/Oslo",
        tokenEncryptionKeyBase64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    )

    private val tool = FamilieplanleggernTool(calendarClient, credentialStore, proposalStore, props)

    @BeforeEach
    fun setupAuth() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("user-1", null, emptyList())
        whenever(credentialStore.load("user-1")).thenReturn(
            GoogleCredentials(
                userId = "user-1",
                refreshToken = "rt",
                accessToken = "at",
                accessTokenExpiresAt = null,
                scope = "scope",
                email = "u@x",
                timezone = "Europe/Oslo",
            )
        )
    }

    @Test
    fun `listCalendars returns user's calendars when connected`() {
        whenever(calendarClient.listCalendars("user-1")).thenReturn(
            listOf(
                CalendarSummary("cal1", "Jobb", "#ff0000", "Europe/Oslo", primary = true),
                CalendarSummary("cal2", "Trening", "#00ff00", "Europe/Oslo", primary = false),
            )
        )

        val result = tool.listCalendars()

        assertTrue(result.contains("Jobb"))
        assertTrue(result.contains("cal1"))
        assertTrue(result.contains("Trening"))
    }

    @Test
    fun `listCalendars returns not-connected hint when user has not linked google`() {
        whenever(credentialStore.load("user-1")).thenReturn(null)

        val result = tool.listCalendars()

        assertTrue(result.contains("ikke koblet", ignoreCase = true) || result.contains("not connected", ignoreCase = true))
        verify(calendarClient, never()).listCalendars(any())
    }

    @Test
    fun `proposeCreateEvent stores proposal and returns confirmation token without hitting google`() {
        val result = tool.proposeCreateEvent(
            calendarId = "cal1",
            summary = "Tannlege",
            startIso = "2026-05-10T09:00:00+02:00",
            endIso = "2026-05-10T10:00:00+02:00",
            description = "Tannrens",
            location = null,
            attendeesCsv = null,
        )

        assertTrue(result.contains("confirmationToken"))
        assertTrue(result.contains("Tannlege"))
        verify(calendarClient, never()).createEvent(any(), any(), any())
    }

    @Test
    fun `confirmCreateEvent executes pending proposal and clears it`() {
        val proposeResult = tool.proposeCreateEvent(
            calendarId = "cal1",
            summary = "Middag",
            startIso = "2026-05-10T18:00:00+02:00",
            endIso = "2026-05-10T19:30:00+02:00",
            description = null,
            location = "Hjemme",
            attendeesCsv = "mor@x.com,far@x.com",
        )
        val token = extractToken(proposeResult)
        whenever(calendarClient.createEvent(eq("user-1"), eq("cal1"), any())).thenReturn(
            CalendarEvent("evt1", "cal1", "Middag", null, "Hjemme",
                Instant.parse("2026-05-10T16:00:00Z"), Instant.parse("2026-05-10T17:30:00Z"), "https://goo.gl/x")
        )

        val confirmResult = tool.confirmCreateEvent(token)

        assertTrue(confirmResult.contains("evt1"))
        verify(calendarClient).createEvent(eq("user-1"), eq("cal1"), any())
        val second = tool.confirmCreateEvent(token)
        assertTrue(second.contains("ugyldig", ignoreCase = true) || second.contains("invalid", ignoreCase = true))
    }

    @Test
    fun `confirmCreateEvent refuses to execute proposal created by another user`() {
        val proposeResult = tool.proposeCreateEvent(
            calendarId = "cal1",
            summary = "Secret",
            startIso = "2026-05-10T09:00:00+02:00",
            endIso = "2026-05-10T10:00:00+02:00",
            description = null,
            location = null,
            attendeesCsv = null,
        )
        val token = extractToken(proposeResult)

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("user-2", null, emptyList())
        whenever(credentialStore.load("user-2")).thenReturn(
            GoogleCredentials("user-2", "rt", "at", null, "scope", "b@x", "UTC")
        )

        val confirmResult = tool.confirmCreateEvent(token)

        assertTrue(
            confirmResult.contains("ugyldig", ignoreCase = true) ||
                confirmResult.contains("invalid", ignoreCase = true)
        )
        verify(calendarClient, never()).createEvent(any(), any(), any())
    }

    @Test
    fun `proposeCreateCalendar followed by confirm creates calendar`() {
        val propose = tool.proposeCreateCalendar("Familie", "#3366ff")
        val token = extractToken(propose)
        whenever(calendarClient.createCalendar("user-1", "Familie", "#3366ff")).thenReturn(
            CalendarSummary("new-cal-id", "Familie", "#3366ff", "Europe/Oslo", primary = false)
        )

        val confirm = tool.confirmCreateCalendar(token)

        assertTrue(confirm.contains("new-cal-id"))
        verify(calendarClient).createCalendar("user-1", "Familie", "#3366ff")
    }

    @Test
    fun `proposeDeleteEvent followed by confirm deletes event`() {
        val propose = tool.proposeDeleteEvent("cal1", "evt1")
        val token = extractToken(propose)

        val confirm = tool.confirmDeleteEvent(token)

        assertTrue(confirm.contains("slettet", ignoreCase = true) || confirm.contains("deleted", ignoreCase = true))
        verify(calendarClient).deleteEvent("user-1", "cal1", "evt1")
    }

    @Test
    fun `listUpcomingEvents returns events sorted and formatted`() {
        whenever(calendarClient.listEvents(eq("user-1"), eq("cal1"), any(), any(), any())).thenReturn(
            listOf(
                CalendarEvent("e1", "cal1", "Yoga", null, null,
                    Instant.parse("2026-05-11T06:00:00Z"), Instant.parse("2026-05-11T07:00:00Z"), null),
                CalendarEvent("e2", "cal1", "Tannlege", "Kontroll", "Storgata 1",
                    Instant.parse("2026-05-12T08:00:00Z"), Instant.parse("2026-05-12T09:00:00Z"), null),
            )
        )

        val result = tool.listUpcomingEvents("cal1", daysAhead = 7)

        assertTrue(result.contains("Yoga"))
        assertTrue(result.contains("Tannlege"))
    }

    @Test
    fun `confirm with unknown token returns helpful error`() {
        val result = tool.confirmCreateEvent("not-a-real-token")
        assertTrue(result.contains("ugyldig", ignoreCase = true) || result.contains("invalid", ignoreCase = true))
        verify(calendarClient, never()).createEvent(any(), any(), any())
    }

    @Test
    fun `propose rejects invalid iso8601 dates`() {
        val result = tool.proposeCreateEvent("cal1", "X", "not-a-date", "2026-05-10T10:00:00+02:00", null, null, null)
        assertFalse(result.contains("confirmationToken"))
        assertTrue(result.contains("dato", ignoreCase = true) || result.contains("date", ignoreCase = true))
    }

    private fun extractToken(proposeResult: String): String {
        val match = Regex("\"confirmationToken\"\\s*:\\s*\"([^\"]+)\"").find(proposeResult)
        return match?.groupValues?.get(1) ?: error("No confirmationToken in: $proposeResult")
    }
}
