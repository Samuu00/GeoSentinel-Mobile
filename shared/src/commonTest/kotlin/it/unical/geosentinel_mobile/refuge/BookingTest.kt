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
import it.unical.geosentinel_mobile.core.db.FakeOutboxDao
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
import it.unical.geosentinel_mobile.refuge.ui.sections
import it.unical.geosentinel_mobile.refuge.data.NightAvailability
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.io.IOException
import it.unical.geosentinel_mobile.core.session.SessionEnd
import it.unical.geosentinel_mobile.core.db.clearAfterSession
import it.unical.geosentinel_mobile.core.network.ApiError
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first
import it.unical.geosentinel_mobile.core.db.ActionKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
                    respond("""{"id":"r1","name":"Rifugio Monte Botte Donato","location":{"lat":39.278,"lon":16.452},"capacity":4,"managerId":"m1","amenities":[]}""", HttpStatusCode.OK, json)
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
    fun `il reinvio si ferma al primo errore di rete`() = runTest {
        val (bookings) = client()
        offline = true
        bookings.book("u1", payload)
        bookings.book("u1", payload.copy(guests = 3))
        posts.clear()

        val outcomes = bookings.resendPending("u1")

        assertEquals(listOf<BookingOutcome>(BookingOutcome.Queued), outcomes)
        // i retry del client sulla stessa chiave sì, la seconda prenotazione no: fallirebbe uguale
        assertEquals(setOf("k1"), posts.map { it.first }.toSet())
        assertEquals(2, outbox.rows.value.size)
    }

    @Test
    fun `una prenotazione in coda rifiutata dopo resta visibile finche l'utente non la chiude`() = runTest {
        val (bookings, refuges) = client()
        offline = true
        bookings.book("u1", payload)
        offline = false
        bookingReply = HttpStatusCode.Conflict
        bookingSlug = "capacity-exceeded"

        val viewModel = MyBookingsViewModel(bookings, refuges, ownerId = { "u1" }, today = { today })
        advanceUntilIdle()

        val rejected = viewModel.state.value.rejected.single()
        assertEquals(payload, rejected.payload)
        assertEquals(ApiError.Conflict("capacity-exceeded", null), rejected.error)
        // non più in coda, ma salvata come rifiutata finché l'utente non la legge
        assertEquals(listOf(ActionKind.BOOKING_REJECTED), outbox.rows.value.values.map { it.kind })
        viewModel.dismissRejected(rejected.key)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.rejected.isEmpty())
        assertTrue(outbox.rows.value.isEmpty())
    }

    @Test
    fun `una prenotazione in coda da piu di 24 ore non parte e si dice scaduta`() = runTest {
        val (bookings) = client()
        val json = Json.encodeToString(BookingPayload.serializer(), payload)
        outbox.insert(PendingActionEntity("vecchia", ActionKind.BOOKING, "u1", json, Instant.parse("2020-01-01T00:00:00Z")))

        bookings.resendPending("u1")

        assertTrue(posts.isEmpty())
        assertEquals(listOf(ActionKind.BOOKING_REJECTED), outbox.rows.value.values.map { it.kind })
        assertNull(bookings.observeRejected("u1").first().single().error)
    }

    @Test
    fun `un rifiuto resta anche dopo un riavvio e solo per il suo utente`() = runTest {
        val (bookings) = client()
        offline = true
        bookings.book("u1", payload)
        offline = false
        bookingReply = HttpStatusCode.NotFound
        bookings.resendPending("u1")

        // un repository nuovo sulla stessa outbox: come dopo un riavvio dell'app
        val afterRestart = BookingRepository({ error("nessuna richiesta") }, TEST_BASE_URL, outbox)
        assertEquals(ApiError.NotFound(null), afterRestart.observeRejected("u1").first().single().error)
        assertTrue(afterRestart.observeRejected("u2").first().isEmpty())
        assertTrue(afterRestart.observePending("u1").first().isEmpty(), "un rifiuto non è più in invio")
    }

    @Test
    fun `al logout spariscono le azioni dell'utente tranne la verifica di un annullamento`() = runTest {
        val at = Instant.parse("2099-01-01T00:00:00Z")
        fun row(key: String, kind: ActionKind, owner: String = "u1") = PendingActionEntity(key, kind, owner, "{}", at)
        listOf(
            row("b", ActionKind.BOOKING), row("r", ActionKind.BOOKING_REJECTED), row("s", ActionKind.SOS),
            row("c", ActionKind.SOS_CANCEL), row("altro", ActionKind.SOS, owner = "u2"),
        ).forEach { outbox.insert(it) }

        outbox.clearAfterSession("u1", SessionEnd.EXPIRED)
        assertEquals(5, outbox.rows.value.size, "sessione scaduta: l'SOS deve ripartire al login")

        outbox.clearAfterSession("u1", SessionEnd.LOGOUT)
        assertEquals(setOf("c", "altro"), outbox.rows.value.keys)

        outbox.clearAfterSession("u1", SessionEnd.ACCOUNT_DELETED)
        assertEquals(setOf("altro"), outbox.rows.value.keys)
    }

    @Test
    fun `una riga illeggibile si toglie senza crash e le altre partono`() = runTest {
        val (bookings) = client()
        outbox.insert(PendingActionEntity("rotta", ActionKind.BOOKING, "u1", "non è json", Instant.parse("2099-01-01T00:00:00Z")))

        bookings.resendPending("u1")

        assertTrue(outbox.rows.value.isEmpty())
        assertTrue(bookings.observePending("u1").first().isEmpty())
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

    // ---- Calendario del foglio "Prenota" (redesign 11/2026) ----

    // posti per notte: 8 tranne quelle in [full]; si ricorda da dove ha chiesto
    private val pagesAsked = mutableListOf<LocalDate>()

    private fun sheet(bookings: BookingRepository, capacity: Int = 4, full: Set<LocalDate> = emptySet()) =
        BookRefugeViewModel(
            "r1",
            capacity = capacity,
            bookings = bookings,
            ownerId = { "u1" },
            today = { today },
            loadNights = { from, nights ->
                pagesAsked += from
                Result.success(List(nights) { from.plus(DatePeriod(days = it)).let { d -> NightAvailability(d, if (d in full) 0 else 8) } })
            },
        )

    private fun day(n: Int) = today.plus(DatePeriod(days = n))

    @Test
    fun `arrivo e partenza con due tocchi`() = runTest {
        val viewModel = sheet(client().first)
        viewModel.open(day(2))
        advanceUntilIdle()

        viewModel.onDayTapped(day(5))
        assertEquals(day(2), viewModel.state.value.arrival)
        assertEquals(3, viewModel.state.value.nights)
        assertFalse(viewModel.state.value.pickingDeparture)
        // terzo tocco: si ricomincia dall'arrivo
        viewModel.onDayTapped(day(7))
        assertEquals(day(7), viewModel.state.value.arrival)
        assertEquals(1, viewModel.state.value.nights)
    }

    @Test
    fun `una notte piena in mezzo non fa da partenza ma diventa un nuovo arrivo`() = runTest {
        val viewModel = sheet(client().first, full = setOf(day(3)))
        viewModel.open(day(1))
        advanceUntilIdle()

        viewModel.onDayTapped(day(5))
        assertEquals(day(5), viewModel.state.value.arrival)
        assertEquals(1, viewModel.state.value.nights)
        // la notte piena non si sceglie come arrivo
        viewModel.onDayTapped(day(3))
        assertEquals(day(5), viewModel.state.value.arrival)
    }

    @Test
    fun `al massimo 14 notti e mai giorni passati`() = runTest {
        val viewModel = sheet(client().first)
        viewModel.open(day(0))
        advanceUntilIdle()

        viewModel.onDayTapped(day(15))
        assertEquals(day(15), viewModel.state.value.arrival, "15 notti: diventa un nuovo arrivo")
        viewModel.onDayTapped(today.plus(DatePeriod(days = -1)))
        assertEquals(day(15), viewModel.state.value.arrival)
    }

    @Test
    fun `senza giorno l'arrivo è la prima notte libera`() = runTest {
        val viewModel = sheet(client().first, full = setOf(day(0), day(1)))
        viewModel.open(null)
        advanceUntilIdle()

        assertEquals(day(2), viewModel.state.value.arrival)
    }

    @Test
    fun `le persone si fermano ai posti della notte più piena e a 20`() = runTest {
        val (bookings) = client()
        val big = sheet(bookings, capacity = 60)
        big.onGuestsChange(50)
        assertEquals(20, big.state.value.guests)

        val viewModel = BookRefugeViewModel(
            "r1",
            10,
            bookings,
            ownerId = { "u1" },
            today = { today },
            loadNights = { from, nights -> Result.success(List(nights) { NightAvailability(from.plus(DatePeriod(days = it)), if (it == 1) 3 else 8) }) },
        )
        viewModel.open(day(0))
        advanceUntilIdle()
        viewModel.onDayTapped(day(3)) // tre notti: la seconda ha 3 posti
        viewModel.onGuestsChange(9)
        assertEquals(3, viewModel.state.value.guests)
    }

    @Test
    fun `le pagine si chiedono una volta e mai nel passato`() = runTest {
        val viewModel = sheet(client().first)
        viewModel.open(day(0))
        advanceUntilIdle()
        viewModel.onPage(forward = true)
        advanceUntilIdle()
        viewModel.onPage(forward = false)
        advanceUntilIdle()
        viewModel.onPage(forward = false) // prima di questa settimana: non si va
        advanceUntilIdle()

        assertEquals(listOf(today, day(14)), pagesAsked)
    }

    @Test
    fun `prenotazioni divise in prossimo soggiorno in arrivo e passate`() {
        fun booking(id: String, checkIn: LocalDate, checkOut: LocalDate, status: BookingResponse.Status = BookingResponse.Status.CONFIRMED) =
            BookingResponse(checkIn = checkIn, checkOut = checkOut, guests = 1, id = id, refugeId = "r1", status = status, userId = "u1")
        val list = listOf(
            booking("far", day(20), day(22)),
            booking("near", day(3), day(4)),
            booking("cancelled", day(5), day(6), BookingResponse.Status.CANCELLED),
            booking("past", day(-10), day(-8)),
            booking("now", day(-1), day(1)), // in corso
        )

        val sections = list.sections(today)

        assertEquals("now", sections.next?.id)
        assertEquals(listOf("near", "far"), sections.upcoming.map { it.id })
        assertEquals(listOf("cancelled", "past"), sections.past.map { it.id })
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
