package it.unical.geosentinel_mobile.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
import it.unical.geosentinel_mobile.refuge.ui.BookRefugeScreen
import it.unical.geosentinel_mobile.refuge.ui.BookRefugeViewModel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// "Prenota" (M9.1): il form con il ViewModel e l'outbox veri, il server finto
@RunWith(RobolectricTestRunner::class)
class BookRefugeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val backend = FakeBackend()
    private val outbox = FakeOutboxDao()
    private val posts = mutableListOf<String>() // body dei POST /bookings
    private var full = false // il server risponde 409 capacity-exceeded

    private fun show(capacity: Int) {
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
        startKoin {
            modules(module { viewModel { (id: String, cap: Int) -> BookRefugeViewModel(id, cap, bookings, ownerId = { "u1" }) } })
        }
        compose.setContent { TestTheme { BookRefugeScreen("r1", "Rifugio Monte Botte Donato", capacity, onBack = {}) } }
    }

    @After
    fun tearDown() = stopKoin()

    private val more = hasContentDescription("Una in più")
    private fun guestsMore() = compose.onAllNodes(more)[1] // il primo è delle notti
    private fun send() = compose.onNode(hasClickAction() and hasText("Prenota"))

    @Test
    fun `prenotazione confermata con le persone scelte`() {
        show(capacity = 4)
        guestsMore().performScrollTo().performClick()
        send().performClick()

        compose.waitForText("Prenotazione confermata")
        assertEquals(1, posts.size)
        assertTrue("\"guests\":2" in posts.single())
        assertTrue(outbox.rows.value.isEmpty()) // risposta definitiva: niente in coda
    }

    @Test
    fun `posti finiti - il form resta con il motivo`() {
        full = true
        show(capacity = 4)
        send().performClick()

        compose.waitForText("Non ci sono abbastanza posti liberi nelle date scelte.")
        send().assertExists()
        assertTrue(outbox.rows.value.isEmpty())
    }

    @Test
    fun `le persone non superano i posti del rifugio`() {
        show(capacity = 2)
        guestsMore().performScrollTo().assertIsEnabled().performClick()
        guestsMore().assertIsNotEnabled()
    }
}
