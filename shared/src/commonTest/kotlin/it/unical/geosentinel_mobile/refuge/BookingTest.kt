// Test delle prenotazioni dei rifugi: BookingRepository con l'outbox (la chiave di idempotenza si salva prima
// della richiesta e si cancella solo con una risposta definitiva), il form e il calendario del foglio "Prenota",
// la divisione delle prenotazioni in sezioni e la schermata "Le mie prenotazioni".
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

/** Verifica invio, coda, rifiuti e reinvio delle prenotazioni e i ViewModel che le mostrano. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookingTest {

    private val backend = FakeBackend()
    /** Outbox in memoria su cui il repository salva le prenotazioni in attesa. */
    private val outbox = FakeOutboxDao()
    /** Data usata come "oggi" dai ViewModel. */
    private val today = LocalDate(2026, 10, 5)
    /** Prenotazione di prova: due notti per due persone nel rifugio "r1". */
    private val payload = BookingPayload("r1", LocalDate(2026, 10, 10), LocalDate(2026, 10, 12), 2)
    /** Contatore usato per generare chiavi di idempotenza prevedibili ("k1", "k2", ...). */
    private var keys = 0

    /** Stato con cui il server risponde a POST /bookings; se null risponde 201 con la prenotazione. */
    private var bookingReply: HttpStatusCode? = null
    /** Slug del ProblemDetail usato quando bookingReply è impostato. */
    private var bookingSlug = ""
    /** Se vero, le richieste di prenotazione falliscono con un errore di rete. */
    private var offline = false
    /**
     * Per ogni POST /bookings: chiave di idempotenza, corpo e se la chiave era già nell'outbox quando la richiesta è
     * arrivata al server.
     */
    private val posts = mutableListOf<Triple<String?, String, Boolean>>()
    /** Id delle prenotazioni cancellate con DELETE. */
    private val cancelled = mutableListOf<String>()

    /** JSON di una prenotazione con id, stato e data di arrivo indicati. */
    private fun bookingJson(id: String, status: String = "CONFIRMED", checkIn: String = "2026-10-10") =
        """{"id":"$id","refugeId":"r1","userId":"u1","checkIn":"$checkIn","checkOut":"2026-10-12","guests":2,"status":"$status"}"""

    /**
     * Installa le rotte finte di prenotazioni e rifugio, fa l'accesso e restituisce il repository delle prenotazioni
     * (con chiavi prevedibili) e quello dei rifugi.
     */
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

    /** Ripristina il dispatcher principale dopo ogni test. */
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()


    /**
     * La chiave di idempotenza viene salvata nell'outbox prima che parta la richiesta, la richiesta contiene i dati
     * della prenotazione e con il 201 la chiave sparisce dall'outbox.
     */
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

    /**
     * Senza rete la prenotazione resta in coda; il reinvio usa la stessa chiave e lo stesso corpo, così il server non
     * può creare due prenotazioni per la stessa azione dell'utente.
     */
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

    /** Un 409 per posti esauriti è una risposta definitiva: l'azione esce dalla coda e il motivo viene restituito. */
    @Test
    fun `posti finiti chiude l'azione e restituisce il motivo`() = runTest {
        val (bookings) = client()
        bookingReply = HttpStatusCode.Conflict
        bookingSlug = "capacity-exceeded"

        assertIs<BookingOutcome.Rejected>(bookings.book("u1", payload))
        assertTrue(outbox.rows.value.isEmpty())
    }

    /** Anche un 422 è definitivo e chiude l'azione. */
    @Test
    fun `un 422 chiude l'azione`() = runTest {
        val (bookings) = client()
        bookingReply = HttpStatusCode.UnprocessableEntity
        bookingSlug = "business-rule"

        assertIs<BookingOutcome.Rejected>(bookings.book("u1", payload))
        assertTrue(outbox.rows.value.isEmpty())
    }

    /** Un errore 500 non è definitivo: l'azione resta in coda per un nuovo tentativo. */
    @Test
    fun `un errore del server lascia l'azione in coda`() = runTest {
        val (bookings) = client()
        bookingReply = HttpStatusCode.InternalServerError
        bookingSlug = "internal-error"

        assertEquals(BookingOutcome.Queued, bookings.book("u1", payload))
        assertEquals(setOf("k1"), outbox.rows.value.keys)
    }

    /**
     * Il reinvio delle azioni in coda si ferma al primo errore di rete: la seconda prenotazione fallirebbe allo
     * stesso modo, quindi non viene nemmeno tentata.
     */
    @Test
    fun `il reinvio si ferma al primo errore di rete`() = runTest {
        val (bookings) = client()
        offline = true
        bookings.book("u1", payload)
        bookings.book("u1", payload.copy(guests = 3))
        posts.clear()

        val outcomes = bookings.resendPending("u1")

        assertEquals(listOf<BookingOutcome>(BookingOutcome.Queued), outcomes)
        // Il client ripete più volte la prima chiave, ma la seconda prenotazione non parte.
        assertEquals(setOf("k1"), posts.map { it.first }.toSet())
        assertEquals(2, outbox.rows.value.size)
    }

    /**
     * Una prenotazione in coda che il server rifiuta in seguito resta visibile come rifiutata, con il motivo, finché
     * l'utente non la chiude.
     */
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
        // Non è più in coda, ma è salvata come rifiutata finché l'utente non la legge.
        assertEquals(listOf(ActionKind.BOOKING_REJECTED), outbox.rows.value.values.map { it.kind })
        viewModel.dismissRejected(rejected.key)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.rejected.isEmpty())
        assertTrue(outbox.rows.value.isEmpty())
    }

    /** Una prenotazione in coda da più di 24 ore non viene inviata e risulta scaduta (un rifiuto senza errore del server). */
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

    /** Un rifiuto resta salvato anche dopo un riavvio dell'app ed è visibile solo al suo utente. */
    @Test
    fun `un rifiuto resta anche dopo un riavvio e solo per il suo utente`() = runTest {
        val (bookings) = client()
        offline = true
        bookings.book("u1", payload)
        offline = false
        bookingReply = HttpStatusCode.NotFound
        bookings.resendPending("u1")

        // Un repository nuovo sulla stessa outbox simula il riavvio dell'app.
        val afterRestart = BookingRepository({ error("nessuna richiesta") }, TEST_BASE_URL, outbox)
        assertEquals(ApiError.NotFound(null), afterRestart.observeRejected("u1").first().single().error)
        assertTrue(afterRestart.observeRejected("u2").first().isEmpty())
        assertTrue(afterRestart.observePending("u1").first().isEmpty(), "un rifiuto non è più in invio")
    }

    /**
     * Alla scadenza della sessione l'outbox resta intatta (un SOS deve ripartire al nuovo login); al logout si
     * cancellano le azioni dell'utente tranne la verifica di un annullamento dell'SOS; alla cancellazione
     * dell'account sparisce tutto. Le azioni di altri utenti non vengono toccate.
     */
    @Test
    fun `al logout spariscono le azioni dell'utente tranne la verifica di un annullamento`() = runTest {
        val at = Instant.parse("2099-01-01T00:00:00Z")
        // Crea una riga dell'outbox per il tipo e l'utente indicati.
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

    /** Una riga dell'outbox con un contenuto non leggibile viene tolta senza crash, e le altre vengono comunque inviate. */
    @Test
    fun `una riga illeggibile si toglie senza crash e le altre partono`() = runTest {
        val (bookings) = client()
        outbox.insert(PendingActionEntity("rotta", ActionKind.BOOKING, "u1", "non è json", Instant.parse("2099-01-01T00:00:00Z")))

        bookings.resendPending("u1")

        assertTrue(outbox.rows.value.isEmpty())
        assertTrue(bookings.observePending("u1").first().isEmpty())
    }

    /**
     * Si reinviano solo le azioni dell'utente attuale: inviare quelle di un altro account creerebbe una prenotazione
     * a nome di chi è autenticato adesso.
     */
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


    /** Crea il ViewModel del form di prenotazione per un rifugio da 4 posti, con il proprietario indicato. */
    private fun TestScope.form(bookings: BookingRepository, owner: String? = "u1") =
        BookRefugeViewModel("r1", capacity = 4, bookings = bookings, ownerId = { owner }, today = { today })

    /** Dieci tocchi su "Prenota" inviano una sola prenotazione. */
    @Test
    fun `dieci tocchi su Prenota mandano una sola prenotazione`() = runTest {
        val viewModel = form(client().first)

        repeat(10) { viewModel.submit() }
        advanceUntilIdle()

        assertEquals(1, posts.size)
        assertEquals(BookingDone.BOOKED, viewModel.state.value.done)
    }

    /**
     * Notti e persone restano nei limiti: al massimo 14 notti e i posti del rifugio, almeno una notte e una persona;
     * la data di partenza si aggiorna di conseguenza.
     */
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

    /** Con un arrivo nel passato la prenotazione non parte e compare il messaggio. */
    @Test
    fun `un arrivo nel passato non parte`() = runTest {
        val viewModel = form(client().first)

        viewModel.onArrivalChange(LocalDate(2026, 10, 4))
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(posts.isEmpty())
        assertEquals(UiMessage(Res.string.booking_arrival_past), viewModel.state.value.error)
    }

    /** Senza l'id dell'utente la prenotazione non parte, perché non si saprebbe a chi intestare l'azione in coda. */
    @Test
    fun `senza profilo non parte`() = runTest {
        val viewModel = form(client().first, owner = null)

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(posts.isEmpty())
        assertEquals(UiMessage(Res.string.booking_no_profile), viewModel.state.value.error)
    }

    /** Se i posti sono finiti il form resta compilato con il messaggio d'errore. */
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

    /** Senza rete la conferma dice che la prenotazione è in invio e l'azione resta nell'outbox. */
    @Test
    fun `senza rete la conferma dice in invio`() = runTest {
        val viewModel = form(client().first)
        offline = true

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(BookingDone.QUEUED, viewModel.state.value.done)
        assertEquals(1, outbox.rows.value.size)
    }

    /** Il toString dello stato del form non contiene le date della prenotazione. */
    @Test
    fun `lo stato del form nei log non ha le date`() = runTest {
        val viewModel = form(client().first)

        assertFalse("2026" in viewModel.state.value.toString())
    }


    /** Date di partenza delle pagine di disponibilità richieste dal calendario, nell'ordine. */
    private val pagesAsked = mutableListOf<LocalDate>()

    /**
     * Crea il ViewModel del foglio "Prenota" con una disponibilità simulata: 8 posti per notte, tranne le notti in
     * [full], che sono piene. Ogni pagina richiesta viene registrata in pagesAsked.
     */
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

    /** La data che cade [n] giorni dopo oggi. */
    private fun day(n: Int) = today.plus(DatePeriod(days = n))

    /** Il primo tocco sceglie l'arrivo e il secondo la partenza; un terzo tocco ricomincia scegliendo un nuovo arrivo. */
    @Test
    fun `arrivo e partenza con due tocchi`() = runTest {
        val viewModel = sheet(client().first)
        viewModel.open(day(2))
        advanceUntilIdle()

        viewModel.onDayTapped(day(5))
        assertEquals(day(2), viewModel.state.value.arrival)
        assertEquals(3, viewModel.state.value.nights)
        assertFalse(viewModel.state.value.pickingDeparture)
        viewModel.onDayTapped(day(7))
        assertEquals(day(7), viewModel.state.value.arrival)
        assertEquals(1, viewModel.state.value.nights)
    }

    /**
     * Una partenza che farebbe dormire in una notte piena non viene accettata e diventa invece un nuovo arrivo; una
     * notte piena non si può scegliere come arrivo.
     */
    @Test
    fun `una notte piena in mezzo non fa da partenza ma diventa un nuovo arrivo`() = runTest {
        val viewModel = sheet(client().first, full = setOf(day(3)))
        viewModel.open(day(1))
        advanceUntilIdle()

        viewModel.onDayTapped(day(5))
        assertEquals(day(5), viewModel.state.value.arrival)
        assertEquals(1, viewModel.state.value.nights)
        viewModel.onDayTapped(day(3))
        assertEquals(day(5), viewModel.state.value.arrival)
    }

    /** Una partenza oltre le 14 notti diventa un nuovo arrivo, e i giorni passati non si possono scegliere. */
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

    /** Se il foglio si apre senza un giorno scelto, l'arrivo è la prima notte con posti liberi. */
    @Test
    fun `senza giorno l'arrivo è la prima notte libera`() = runTest {
        val viewModel = sheet(client().first, full = setOf(day(0), day(1)))
        viewModel.open(null)
        advanceUntilIdle()

        assertEquals(day(2), viewModel.state.value.arrival)
    }

    /**
     * Il numero di persone non supera il massimo assoluto di 20 né i posti liberi della notte più piena del
     * soggiorno.
     */
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
        // Tre notti, e la seconda ha solo 3 posti liberi.
        viewModel.onDayTapped(day(3))
        viewModel.onGuestsChange(9)
        assertEquals(3, viewModel.state.value.guests)
    }

    /** Ogni pagina del calendario si chiede una volta sola, e non si può tornare a settimane passate. */
    @Test
    fun `le pagine si chiedono una volta e mai nel passato`() = runTest {
        val viewModel = sheet(client().first)
        viewModel.open(day(0))
        advanceUntilIdle()
        viewModel.onPage(forward = true)
        advanceUntilIdle()
        viewModel.onPage(forward = false)
        advanceUntilIdle()
        // Questa pagina sarebbe prima della settimana corrente: non si va.
        viewModel.onPage(forward = false)
        advanceUntilIdle()

        assertEquals(listOf(today, day(14)), pagesAsked)
    }

    /**
     * Le prenotazioni si dividono in tre sezioni: il prossimo soggiorno (anche se è già in corso), quelli in arrivo in
     * ordine di data e le passate, tra cui quelle annullate.
     */
    @Test
    fun `prenotazioni divise in prossimo soggiorno in arrivo e passate`() {
        // Crea una prenotazione con le date e lo stato indicati.
        fun booking(id: String, checkIn: LocalDate, checkOut: LocalDate, status: BookingResponse.Status = BookingResponse.Status.CONFIRMED) =
            BookingResponse(checkIn = checkIn, checkOut = checkOut, guests = 1, id = id, refugeId = "r1", status = status, userId = "u1")
        val list = listOf(
            booking("far", day(20), day(22)),
            booking("near", day(3), day(4)),
            booking("cancelled", day(5), day(6), BookingResponse.Status.CANCELLED),
            booking("past", day(-10), day(-8)),
            // Soggiorno in corso.
            booking("now", day(-1), day(1)),
        )

        val sections = list.sections(today)

        assertEquals("now", sections.next?.id)
        assertEquals(listOf("near", "far"), sections.upcoming.map { it.id })
        assertEquals(listOf("cancelled", "past"), sections.past.map { it.id })
    }


    /**
     * All'apertura di "Le mie prenotazioni" si reinviano prima quelle in coda, poi si carica la lista con i nomi dei
     * rifugi.
     */
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

    /** Si può annullare solo una prenotazione confermata non ancora iniziata; dopo l'annullamento lo stato si aggiorna. */
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
