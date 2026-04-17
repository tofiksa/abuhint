package no.josefus.abuhint.familie

import java.time.Instant

/**
 * Narrow facade over Google Calendar v3 scoped to the current authenticated user.
 * All methods take a `userId` and look up stored credentials via [UserGoogleCredentialStore],
 * refreshing the access token transparently when needed.
 */
interface GoogleCalendarClient {

    fun listCalendars(userId: String): List<CalendarSummary>

    fun createCalendar(userId: String, name: String, colorHex: String? = null): CalendarSummary

    fun deleteCalendar(userId: String, calendarId: String)

    fun listEvents(
        userId: String,
        calendarId: String,
        timeMin: Instant,
        timeMax: Instant,
        maxResults: Int = 50,
    ): List<CalendarEvent>

    fun createEvent(userId: String, calendarId: String, event: NewCalendarEvent): CalendarEvent

    fun deleteEvent(userId: String, calendarId: String, eventId: String)
}

data class CalendarSummary(
    val id: String,
    val name: String,
    val colorHex: String?,
    val timeZone: String?,
    val primary: Boolean,
)

data class CalendarEvent(
    val id: String,
    val calendarId: String,
    val summary: String,
    val description: String?,
    val location: String?,
    val start: Instant,
    val end: Instant,
    val htmlLink: String?,
)

data class NewCalendarEvent(
    val summary: String,
    val start: Instant,
    val end: Instant,
    val description: String? = null,
    val location: String? = null,
    val attendees: List<String> = emptyList(),
    val timezone: String? = null,
)
