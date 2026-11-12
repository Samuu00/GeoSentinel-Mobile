package it.unical.geosentinel_mobile.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.core.db.FakeOutboxDao
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.refuge.data.BookingRepository
import it.unical.geosentinel_mobile.refuge.data.NightAvailability
import it.unical.geosentinel_mobile.refuge.ui.BookRefugeViewModel
import it.unical.geosentinel_mobile.refuge.ui.BookingSheet
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

// Foglio "Prenota" (redesign 11/2026, prima il form M9.1): ViewModel e outbox veri, il server finto, i posti delle notti finti
@RunWith(RobolectricTestRunner::class)
class BookingSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val backend = FakeBackend()
    private val outbox = FakeOutboxDao()
    private val posts = mutableListOf<String>() // body dei POST /bookings
    private var full = false // il server risponde 409 capacity-exceeded
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    private fun show(capacity: Int, freeBeds: (LocalDate) -> Int = { 8 }) {
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        backend.extraRoutes = { request, _ ->
            if (request.method == HttpMethod.Post && request.url.encodedPath == "/api/v1/bookings") {
                posts += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                if (full) {
                    with(backend) { problem(HttpStatusCode.Conflict, "capacity-exceeded") }
                } else {
                    respond(
                        """{"id":"b1","refugeId":"r1","userId":"u1","checkIn":"2026-10-10","checkOut":"2026-10-11","guests":2,"status":"CONFIRMED"}""",
                        HttpStatusCode.Created,
                        json,
                    )
                }
            } else null
        }
        val client = uiSession(backend).second
        val bookings = BookingRepository({ client }, TEST_BASE_URL, outbox)
        val viewModel = BookRefugeViewModel(
            "r1",
            capacity,
            bookings,
            ownerId = { "u1" },
            loadNights = { from, nights ->
                Result.success(List(nights) { offset -> from.plus(DatePeriod(days = offset)).let { NightAvailability(it, freeBeds(it)) } })
            },
        )
        viewModel.open(today)
        compose.setContent {
            TestTheme { BookingSheet("Rifugio Monte Botte Donato", viewModel, onDismiss = {}, onBooked = {}, onOpenBookings = {}) }
        }
        compose.waitForText("Conferma")
    }

    private fun more() = compose.onNode(hasContentDescription("Una in più"))
    private fun confirm() = compose.onNode(hasClickAction() and hasText("Conferma"))

    // la cella di un giorno: lo screen reader la legge "sab 14 novembre, 8 posti liberi"
    private fun day(date: LocalDate) = compose.onNode(
        hasClickAction() and
            hasContentDescription("${Weekdays[date.dayOfWeek.isoDayNumber - 1]} ${date.day} ${Months[date.month.number - 1]},", substring = true),
    )

    @Test
    fun `prenotazione confermata con le persone scelte`() {
        show(capacity = 4)
        more().performScrollTo().performClick()
        confirm().performScrollTo().performClick()

        compose.waitForText("Prenotazione confermata")
        assertEquals(1, posts.size)
        assertTrue("\"guests\":2" in posts.single())
        assertTrue(outbox.rows.value.isEmpty()) // risposta definitiva: niente in coda
    }

    @Test
    fun `due tocchi sul calendario scelgono arrivo e partenza`() {
        show(capacity = 4)
        // arrivo già scelto (oggi): il tocco su dopodomani è la partenza
        val departure = today.plus(DatePeriod(days = 2))
        day(departure).performClick()

        compose.waitForSubstring("2 notti")
        confirm().performScrollTo().performClick()
        compose.waitForText("Prenotazione confermata")
        assertTrue("\"checkOut\":\"$departure\"" in posts.single())
    }

    @Test
    fun `una notte piena non si può scegliere`() {
        val tomorrow = today.plus(DatePeriod(days = 1))
        val afterTomorrow = today.plus(DatePeriod(days = 2))
        // domani e dopodomani pieni (arrivo oggi)
        show(capacity = 4, freeBeds = { if (it == tomorrow || it == afterTomorrow) 0 else 8 })

        compose.waitFor {
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)).fetchSemanticsNodes().isNotEmpty()
        }
        // domani si può scegliere come partenza (quella notte non si dorme lì); dopodomani no: né arrivo né partenza
        day(tomorrow).assertIsEnabled()
        day(afterTomorrow).assertIsNotEnabled()
        // tre giorni dopo non può essere la partenza (si dormirebbe in notti piene): il tocco lo fa diventare il nuovo arrivo
        val later = today.plus(DatePeriod(days = 3))
        day(later).performClick()
        confirm().performScrollTo().performClick()
        compose.waitForText("Prenotazione confermata")
        assertTrue("\"checkIn\":\"$later\"" in posts.single())
    }

    @Test
    fun `posti finiti - il foglio resta con il motivo`() {
        full = true
        show(capacity = 4)
        confirm().performScrollTo().performClick()

        compose.waitForText("Non ci sono abbastanza posti liberi nelle date scelte.")
        confirm().assertExists()
        assertTrue(outbox.rows.value.isEmpty())
    }

    @Test
    fun `le persone non superano i posti del rifugio`() {
        show(capacity = 2)
        more().performScrollTo().assertIsEnabled().performClick()
        more().assertIsNotEnabled()
    }

    private companion object {
        val Weekdays = listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom")
        val Months = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")
    }
}

private fun ComposeContentTestRule.waitForSubstring(text: String) =
    waitFor { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
