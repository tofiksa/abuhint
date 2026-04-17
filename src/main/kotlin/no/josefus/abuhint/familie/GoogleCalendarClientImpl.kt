package no.josefus.abuhint.familie

import com.google.api.client.auth.oauth2.BearerToken
import com.google.api.client.auth.oauth2.ClientParametersAuthentication
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.CredentialRefreshListener
import com.google.api.client.auth.oauth2.TokenErrorResponse
import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Production [GoogleCalendarClient]. Looks up the user's stored refresh token,
 * builds an auto-refreshing OAuth [Credential], and calls Google Calendar v3.
 *
 * Access tokens are re-persisted after a refresh so subsequent calls reuse them
 * until they expire.
 */
@Service
class GoogleCalendarClientImpl(
    private val credentialStore: UserGoogleCredentialStore,
    private val properties: FamilieplanleggernProperties,
) : GoogleCalendarClient {

    private val log = LoggerFactory.getLogger(GoogleCalendarClientImpl::class.java)
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport: NetHttpTransport by lazy { GoogleNetHttpTransport.newTrustedTransport() }

    override fun listCalendars(userId: String): List<CalendarSummary> {
        val calendar = calendarFor(userId)
        return calendar.calendarList().list().execute().items
            .orEmpty()
            .map { it.toSummary() }
    }

    override fun createCalendar(userId: String, name: String, colorHex: String?): CalendarSummary {
        val calendar = calendarFor(userId)
        val body = com.google.api.services.calendar.model.Calendar().apply {
            summary = name
            timeZone = properties.defaultTimezone
        }
        val created = calendar.calendars().insert(body).execute()

        if (!colorHex.isNullOrBlank()) {
            val entry = CalendarListEntry().apply {
                backgroundColor = colorHex
                foregroundColor = "#ffffff"
            }
            runCatching {
                calendar.calendarList()
                    .update(created.id, entry)
                    .setColorRgbFormat(true)
                    .execute()
            }.onFailure { log.warn("Failed to set colorHex={} on calendar {}: {}", colorHex, created.id, it.message) }
        }

        return CalendarSummary(
            id = created.id,
            name = created.summary ?: name,
            colorHex = colorHex,
            timeZone = created.timeZone,
            primary = false,
        )
    }

    override fun deleteCalendar(userId: String, calendarId: String) {
        calendarFor(userId).calendars().delete(calendarId).execute()
    }

    override fun listEvents(
        userId: String,
        calendarId: String,
        timeMin: Instant,
        timeMax: Instant,
        maxResults: Int,
    ): List<CalendarEvent> {
        val calendar = calendarFor(userId)
        val items = calendar.events().list(calendarId)
            .setTimeMin(DateTime(timeMin.toEpochMilli()))
            .setTimeMax(DateTime(timeMax.toEpochMilli()))
            .setSingleEvents(true)
            .setOrderBy("startTime")
            .setMaxResults(maxResults)
            .execute()
            .items
            .orEmpty()
        return items.map { it.toCalendarEvent(calendarId) }
    }

    override fun createEvent(userId: String, calendarId: String, event: NewCalendarEvent): CalendarEvent {
        val body = Event().apply {
            summary = event.summary
            description = event.description
            location = event.location
            start = toEventDateTime(event.start, event.timezone ?: properties.defaultTimezone)
            end = toEventDateTime(event.end, event.timezone ?: properties.defaultTimezone)
            if (event.attendees.isNotEmpty()) {
                attendees = event.attendees.map { EventAttendee().setEmail(it) }
            }
        }
        val created = calendarFor(userId).events().insert(calendarId, body).execute()
        return created.toCalendarEvent(calendarId)
    }

    override fun deleteEvent(userId: String, calendarId: String, eventId: String) {
        calendarFor(userId).events().delete(calendarId, eventId).execute()
    }

    private fun calendarFor(userId: String): Calendar {
        val stored = credentialStore.load(userId)
            ?: throw FamilieplanleggernNotConnectedException(userId)
        val credential = buildCredential(userId, stored)
        return Calendar.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("Familieplanleggern")
            .build()
    }

    private fun buildCredential(userId: String, stored: GoogleCredentials): Credential {
        val credential = Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
            .setTransport(httpTransport)
            .setJsonFactory(jsonFactory)
            .setTokenServerUrl(GenericUrl("https://oauth2.googleapis.com/token"))
            .setClientAuthentication(
                ClientParametersAuthentication(properties.googleClientId, properties.googleClientSecret)
            )
            .addRefreshListener(object : CredentialRefreshListener {
                override fun onTokenResponse(credential: Credential, tokenResponse: TokenResponse) {
                    val newExpiry = credential.expirationTimeMilliseconds?.let { Instant.ofEpochMilli(it) }
                    credentialStore.save(
                        stored.copy(
                            accessToken = credential.accessToken,
                            accessTokenExpiresAt = newExpiry,
                        )
                    )
                }

                override fun onTokenErrorResponse(credential: Credential, tokenErrorResponse: TokenErrorResponse?) {
                    log.warn("Google token refresh failed for userId={}: {}", userId, tokenErrorResponse?.error)
                }
            })
            .build()
        credential.refreshToken = stored.refreshToken
        credential.accessToken = stored.accessToken
        credential.expirationTimeMilliseconds = stored.accessTokenExpiresAt?.toEpochMilli()
        return credential
    }

    private fun toEventDateTime(instant: Instant, timeZone: String): EventDateTime =
        EventDateTime()
            .setDateTime(DateTime(instant.toEpochMilli()))
            .setTimeZone(timeZone)

    private fun CalendarListEntry.toSummary(): CalendarSummary = CalendarSummary(
        id = id,
        name = summary ?: id,
        colorHex = backgroundColor,
        timeZone = timeZone,
        primary = primary == true,
    )

    private fun Event.toCalendarEvent(calendarId: String): CalendarEvent = CalendarEvent(
        id = id,
        calendarId = calendarId,
        summary = summary.orEmpty(),
        description = description,
        location = location,
        start = start?.dateTime?.let { Instant.ofEpochMilli(it.value) }
            ?: start?.date?.let { Instant.ofEpochMilli(it.value) }
            ?: Instant.EPOCH,
        end = end?.dateTime?.let { Instant.ofEpochMilli(it.value) }
            ?: end?.date?.let { Instant.ofEpochMilli(it.value) }
            ?: Instant.EPOCH,
        htmlLink = htmlLink,
    )

    companion object {
        val REQUIRED_SCOPES: List<String> = listOf(CalendarScopes.CALENDAR, CalendarScopes.CALENDAR_EVENTS)
    }
}

class FamilieplanleggernNotConnectedException(val userId: String) :
    RuntimeException("User $userId has not connected a Google account yet")
