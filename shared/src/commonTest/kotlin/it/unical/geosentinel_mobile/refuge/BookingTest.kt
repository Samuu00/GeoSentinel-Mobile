package it.unical.geosentinel_mobile.refuge

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.booking_arrival_past
import geosentinel_mobile.shared.generated.resources.booking_no_profile
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.api.model.BookingResponse
import it.unical.geosentinel_mobile.core.db.ActionKind
import it.unical.geosentinel_mobile.core.db.OutboxDao
import it.unical.geosentinel_mobile.core.db.PendingActionEntity
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.refuge.data.BookingOutcome
import it.unical.geosentinel_mobile.refuge.data.BookingPayload
import it.unical.geosentinel_mobile.refuge.data.BookingRepository
import it.unical.geosentinel_mobile.refuge.data.RefugeRepository
import it.unical.geosentinel_mobile.refuge.ui.BookRefugeViewModel
import it.unical.geosentinel_mobile.refuge.ui.BookingDone
import it.unical.geosentinel_mobile.refuge.ui.MyBookingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// L'outbox in memoria: la SQL vera di Room si prova sull'emulatore
private class FakeOutboxDao : OutboxDao {
    val rows = MutableStateFlow<Map<String, PendingActionEntity>>(emptyMap())

    override suspend fun insert(action: PendingActionEntity) {
        check(action.idempotencyKey !in rows.value) { "chiave già presente" }
        rows.value += action.idempotencyKey to action
    }

    override suspend fun pending(kind: ActionKind, ownerId: String) =
        rows.value.values.filter { it.kind == kind && it.ownerId == ownerId }.sortedBy { it.createdAt }

    override fun observePending(kind: ActionKind, ownerId: String): Flow<List<PendingActionEntity>> =
        rows.map { all -> all.values.filter { it.kind == kind && it.ownerId == ownerId }.sortedBy { it.createdAt } }

    override suspend fun incrementAttempts(key: String) {
        rows.value[key]?.let { rows.value += key to it.copy(attempts = it.attempts + 1) }
    }

    override suspend fun delete(key: String) {
        rows.value -= key
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BookingTest {

    private val backend = FakeBackend()
    private val outbox = FakeOutboxDao()
    private val today = LocalDate(2026, 10, 5)
    private val payload = BookingPayload("r1", LocalDate(2026, 10, 10), LocalDate(2026, 10, 12), 2)
    private var keys = 0

    // cosa risponde il server a POST /bookings: null = 201 con la prenotazione
    private var bookingReply: HttpStatusCode? = null
    private var bookingSlug = ""
    private var offline = false
    // per ogni POST /bookings: chiave, body e se la chiave era già nell'outbox quando è arrivata la richiesta
    private val posts = mutableListOf<Triple<String?, String, Boolean>>()
    private val cancelled = mutableListOf<String>()

    private fun bookingJson(id: String, status: String = "CONFIRMED", checkIn: String = "2026-10-10") =
        """{"id":"$id","refugeId":"r1","userId":"u1","checkIn":"$checkIn","checkOut":"2026-10-12","guests":2,"status":"$status"}"""

    private suspend fun TestScope.client(): Pair<BookingRepository, RefugeRepository> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        backend.extraRoutes = { request, _ ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Post && path == "/api/v1/bookings" -> {
                    val key = request.headers["Idempotency-Key"]
                    posts += Triple(key, (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(), key in outbox.rows.value)
                    if (offline) throw IOException("rete assente")
                    when (val status = bookingReply) {
                        null -> respond(bookingJson("b-new"), HttpStatusCode.Created, json)
                        else -> respond(
                            """{"type":"https://geosentinel.dev/problems/$bookingSlug","title":"x","status":${status.value}}""",
                            status,
                            headersOf(HttpHeaders.ContentType, "application/problem+json"),
                        )
                    }
                }
                request.method == HttpMethod.Get && path == "/api/v1/bookings/my" ->
                    respond(
                        """{"content":[${bookingJson("b1")},${bookingJson("b0", status = "CANCELLED", checkIn = "2026-09-01")}],"page":{"size":20,"number":0,"totalElements":2,"totalPages":1}}""",
                        HttpStatusCode.OK,
                        json,
                    )
                request.method == HttpMethod.Delete && path.startsWith("/api/v1/bookings/") -> {
                    cancelled += path.substringAfterLast('/')
                    respond("", HttpStatusCode.NoContent)
                }
                request.method == HttpMethod.Get && path == "/api/v1/refuges/r1" ->
                    respond("""{"id":"r1","name":"Rifugio Monte Botte Donato","location":{"lat":39.278,"lon":16.452},"capacity":4,"managerId":"m1"}""", HttpStatusCode.OK, json)
                else -> null
            }
        }
        val (session, client) = sessionWith(backend)
        session.restore()
        assertTrue(session.login("hiker@test.dev", "password-giusta-1").isSuccess)
        val bookings = BookingRepository({ client }, TEST_BASE_URL, outbox, newKey = { "k${++keys}" })
        return bookings to RefugeRepository({ client }, TEST_BASE_URL)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // ---- Repository e outbox ----

    @Test
    fun `la chiave si salva prima della richiesta e sparisce con il 201`() = runTest {
        val (bookings) = client()

        val outcome = bookings.book("u1", payload)

        assertIs<BookingOutcome.Booked>(outcome)
        val (key, body, savedBefore) = posts.single()
        assertEquals("k1", key)
        assertTrue(savedBefore, "la chiave era già nell'outbox quando è partita la richiesta")
        assertTrue(""""checkIn":"2026-10-10"""" in body && """"checkOut":"2026-10-12"""" in body && """"guests":2""" in body && """"refugeId":"r1"""" in body)
        assertTrue(outbox.rows.value.isEmpty())
    }

    @Test
    fun `senza rete resta in coda e il retry usa la stessa chiave e lo stesso body`() = runTest {
        val (bookings) = client()
        offline = true

        assertEquals(BookingOutcome.Queued, bookings.book("u1", payload))
        assertEquals(setOf("k1"), outbox.rows.value.keys)

        offline = false
        val outcomes = bookings.resendPending("u1")

        assertIs<BookingOutcome.Booked>(outcomes.single())
        assertTrue(outbox.rows.value.isEmpty())
        assertEquals(1, posts.map { it.first }.distinct().size, "una sola chiave per un'azione")
        assertEquals(posts.first().second, posts.last().second)
    }

    @Test
    fun `posti finiti chiude l'azione e restituisce il motivo`() = runTest {
        val (bookings) = client()
        bookingReply = HttpStatusCode.Conflict
        bookingSlug = "capacity-exceeded"

        assertIs<BookingOutcome.Rejected>(bookings.book("u1", payload))
        assertTrue(outbox.rows.value.isEmpty())
    }

    @Test
    fun `un 422 chiude l'azione`() = runTest {
        val (bookings) = client()
        bookingReply = HttpStatusCode.UnprocessableEntity
        bookingSlug = "business-rule"

        assertIs<BookingOutcome.Rejected>(bookings.book("u1", payload))
        assertTrue(outbox.rows.value.isEmpty())
    }

    @Test
    fun `un errore del server lascia l'azione in coda`() = runTest {
        val (bookings) = client()
        bookingReply = HttpStatusCode.InternalServerError
        bookingSlug = "internal-error"

        assertEquals(BookingOutcome.Queued, bookings.book("u1", payload))
        assertEquals(setOf("k1"), outbox.rows.value.keys)
    }

    @Test
    fun `si rimandano solo le azioni dello stesso utente`() = runTest {
        val (bookings) = client()
        offline = true
        bookings.book("altro-utente", payload)
        offline = false
        posts.clear()

        assertTrue(bookings.resendPending("u1").isEmpty())
        assertTrue(posts.isEmpty(), "con un altro account creerebbe una prenotazione a nome suo")
        assertEquals(setOf("k1"), outbox.rows.value.keys)
    }

    // ---- Form ----

    private fun TestScope.form(bookings: BookingRepository, owner: String? = "u1") =
        BookRefugeViewModel("r1", capacity = 4, bookings = bookings, ownerId = { owner }, today = { today })

    @Test
    fun `dieci tocchi su Prenota mandano una sola prenotazione`() = runTest {
        val viewModel = form(client().first)

        repeat(10) { viewModel.submit() }
        advanceUntilIdle()

        assertEquals(1, posts.size)
        assertEquals(BookingDone.BOOKED, viewModel.state.value.done)
    }

    @Test
    fun `notti e persone restano nei limiti`() = runTest {
        val viewModel = form(client().first)

        viewModel.onNightsChange(20)
        viewModel.onGuestsChange(9)
        assertEquals(14, viewModel.state.value.nights)
        assertEquals(4, viewModel.state.value.guests)
        viewModel.onNightsChange(0)
        viewModel.onGuestsChange(0)
        assertEquals(1, viewModel.state.value.nights)
        assertEquals(1, viewModel.state.value.guests)
        assertEquals(LocalDate(2026, 10, 6), viewModel.state.value.departure)
    }

    @Test
    fun `un arrivo nel passato non parte`() = runTest {
        val viewModel = form(client().first)

        viewModel.onArrivalChange(LocalDate(2026, 10, 4))
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(posts.isEmpty())
        assertEquals(UiMessage(Res.string.booking_arrival_past), viewModel.state.value.error)
    }

    @Test
    fun `senza profilo non parte`() = runTest {
        val viewModel = form(client().first, owner = null)

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(posts.isEmpty())
        assertEquals(UiMessage(Res.string.booking_no_profile), viewModel.state.value.error)
    }

    @Test
    fun `posti finiti lasciano il form compilato con il messaggio`() = runTest {
        val viewModel = form(client().first)
        bookingReply = HttpStatusCode.Conflict
        bookingSlug = "capacity-exceeded"
        viewModel.onGuestsChange(3)

        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.done)
        assertEquals(3, state.guests)
        assertTrue(state.error != null)
        assertFalse(state.submitting)
    }

    @Test
    fun `senza rete la conferma dice in invio`() = runTest {
        val viewModel = form(client().first)
        offline = true

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(BookingDone.QUEUED, viewModel.state.value.done)
        assertEquals(1, outbox.rows.value.size)
    }

    @Test
    fun `lo stato del form nei log non ha le date`() = runTest {
        val viewModel = form(client().first)

        assertFalse("2026" in viewModel.state.value.toString())
    }

    // ---- Le mie prenotazioni ----

    @Test
    fun `all'apertura rimanda quelle in coda e poi carica la lista con i nomi`() = runTest {
        val (bookings, refuges) = client()
        offline = true
        bookings.book("u1", payload)
        offline = false

        val viewModel = MyBookingsViewModel(bookings, refuges, ownerId = { "u1" }, today = { today })
        advanceUntilIdle()

        assertTrue(outbox.rows.value.isEmpty(), "la prenotazione in coda è partita")
        assertEquals(listOf("b1", "b0"), viewModel.state.value.items.map { it.id })
        assertEquals("Rifugio Monte Botte Donato", viewModel.state.value.refugeNames["r1"])
        assertTrue(viewModel.state.value.pending.isEmpty())
    }

    @Test
    fun `si annulla solo una confermata non ancora iniziata e lo stato si aggiorna`() = runTest {
        val (bookings, refuges) = client()
        val viewModel = MyBookingsViewModel(bookings, refuges, ownerId = { "u1" }, today = { today })
        advanceUntilIdle()
        val (future, past) = viewModel.state.value.items

        assertTrue(viewModel.canCancel(future))
        assertFalse(viewModel.canCancel(past))

        viewModel.cancel(future.id)
        advanceUntilIdle()

        assertEquals(listOf("b1"), cancelled)
        assertEquals(BookingResponse.Status.CANCELLED, viewModel.state.value.items.first().status)
    }
}
