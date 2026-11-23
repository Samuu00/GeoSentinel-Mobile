// Test dei ViewModel delle funzioni per il personale (gestori del parco e amministratori): coda delle segnalazioni
// da verificare, stato del sentiero, nuovo POI, posti letto di un rifugio e nuova squadra di soccorso.
package it.unical.geosentinel_mobile.staff

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_forbidden
import geosentinel_mobile.shared.generated.resources.staff_capacity_below_booked
import geosentinel_mobile.shared.generated.resources.staff_queue_already_handled
import geosentinel_mobile.shared.generated.resources.staff_team_name_taken
import geosentinel_mobile.shared.generated.resources.staff_trail_status_rejected
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import it.unical.geosentinel_mobile.api.model.CreatePoiRequest
import it.unical.geosentinel_mobile.api.model.HazardResponse
import it.unical.geosentinel_mobile.api.model.TrailDetailResponse
import it.unical.geosentinel_mobile.api.model.UpdateHazardStatusRequest
import it.unical.geosentinel_mobile.api.model.UpdateTrailStatusRequest
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.hazard.data.HazardRepository
import it.unical.geosentinel_mobile.staff.data.StaffRepository
import it.unical.geosentinel_mobile.staff.ui.HazardQueueViewModel
import it.unical.geosentinel_mobile.staff.ui.PoiCreateViewModel
import it.unical.geosentinel_mobile.staff.ui.RefugeCapacityViewModel
import it.unical.geosentinel_mobile.staff.ui.RescueTeamCreateViewModel
import it.unical.geosentinel_mobile.staff.ui.TrailStatusViewModel
import it.unical.geosentinel_mobile.trail.FakeCatalogDao
import it.unical.geosentinel_mobile.trail.SEED_TRAIL_ID
import it.unical.geosentinel_mobile.trail.SEED_TRAIL_PATH
import it.unical.geosentinel_mobile.trail.data.TrailRepository
import it.unical.geosentinel_mobile.trail.data.local.TrailSummaryEntity
import it.unical.geosentinel_mobile.trail.trailDetailJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Verifica i ViewModel della sezione Gestione contro un backend finto che applica le regole del dominio. */
@OptIn(ExperimentalCoroutinesApi::class)
class StaffViewModelsTest {

    private val backend = FakeBackend()
    /** Cache del catalogo in memoria, per controllare che le risposte del server vengano salvate. */
    private val dao = FakeCatalogDao()
    /** Istante fisso usato come data di aggiornamento nella cache. */
    private val clock = Instant.fromEpochSeconds(1_790_000_000)

    /** Stato di ogni segnalazione sul server finto. */
    private val hazardStatus = mutableMapOf("h1" to "SUBMITTED", "h2" to "VERIFIED")
    /** Stato del sentiero di prova sul server finto. */
    private var trailStatus = "CLOSED"
    /** Se vero, un collega respinge la segnalazione un attimo prima che il test provi a cambiarne lo stato. */
    private var colleagueRejectsFirst = false
    /** Se vero, tutte le rotte del personale rispondono 403. */
    private var forbidden = false
    /** Corpi delle richieste di creazione dei POI ricevute. */
    private val poiBodies = mutableListOf<String>()
    /** Posti letto del rifugio sul server finto. */
    private var refugeCapacity = 24
    /** Ospiti confermati in una delle prossime notti: una capienza inferiore riceve 409 capacity-exceeded. */
    private val confirmedGuests = 10
    /** Nomi delle squadre già esistenti, unici senza distinguere maiuscole e minuscole. */
    private val teamNames = mutableSetOf("Soccorso Sila")
    /** Corpi delle richieste di creazione delle squadre accettate. */
    private val teamBodies = mutableListOf<String>()

    /** JSON della segnalazione con lo stato corrente del server finto. */
    private fun hazardJson(id: String) =
        """{"id":"$id","trailId":"$SEED_TRAIL_ID","type":"FALLEN_TREE","severity":"HIGH","status":"${hazardStatus[id]}","location":{"lat":39.3,"lon":16.5},"createdAt":"2026-10-05T10:00:00Z"}"""

    /** Installa le rotte finte del personale, fa l'accesso e restituisce i repository di pericoli, personale e sentieri. */
    private suspend fun TestScope.repositories(): Triple<HazardRepository, StaffRepository, TrailRepository> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        backend.extraRoutes = { request, _ ->
            val path = request.url.encodedPath.removePrefix("/api/v1")
            val ok = headersOf(HttpHeaders.ContentType, "application/json")
            suspend fun body() = request.body.toByteArray().decodeToString()
            with(backend) {
                when {
                    forbidden -> problem(HttpStatusCode.Forbidden, "forbidden")
                    request.method == HttpMethod.Get && path == "/hazards/trail/$SEED_TRAIL_ID" -> {
                        // Come il server per il gestore del sentiero: solo SUBMITTED e VERIFIED, mai REJECTED o RESOLVED.
                        val queue = hazardStatus.filterValues { it == "SUBMITTED" || it == "VERIFIED" }.keys.map { hazardJson(it) }
                        respond("[${queue.joinToString(",")}]", HttpStatusCode.OK, ok)
                    }
                    request.method == HttpMethod.Get && path == "/hazards/pending" -> {
                        // Come il server: le segnalazioni SUBMITTED di tutti gli utenti, in qualsiasi zona del parco.
                        val pending = hazardStatus.filterValues { it == "SUBMITTED" }.keys.map { hazardJson(it) }
                        respond(
                            """{"content":[${pending.joinToString(",")}],"page":{"size":50,"number":0,"totalElements":${pending.size},"totalPages":1}}""",
                            HttpStatusCode.OK,
                            ok,
                        )
                    }
                    request.method == HttpMethod.Patch && path.startsWith("/hazards/") && path.endsWith("/status") -> {
                        val id = path.removePrefix("/hazards/").removeSuffix("/status")
                        if (colleagueRejectsFirst) hazardStatus[id] = "REJECTED"
                        val target = Regex("\"status\"\\s*:\\s*\"([A-Z]+)\"").find(body())!!.groupValues[1]
                        val current = hazardStatus.getValue(id)
                        // Transizioni ammesse: da SUBMITTED a VERIFIED o REJECTED, da VERIFIED a RESOLVED.
                        val allowed = (current == "SUBMITTED" && target in setOf("VERIFIED", "REJECTED")) || (current == "VERIFIED" && target == "RESOLVED")
                        if (!allowed) {
                            problem(HttpStatusCode.UnprocessableEntity, "business-rule")
                        } else {
                            hazardStatus[id] = target
                            respond(hazardJson(id), HttpStatusCode.OK, ok)
                        }
                    }
                    request.method == HttpMethod.Patch && path == "/trails/$SEED_TRAIL_ID/status" -> {
                        val target = Regex("\"status\"\\s*:\\s*\"([A-Z]+)\"").find(body())!!.groupValues[1]
                        // Regola del dominio: un sentiero chiuso non può passare direttamente ad "attenzione".
                        if (trailStatus == "CLOSED" && target == "WARNING") {
                            problem(HttpStatusCode.UnprocessableEntity, "business-rule")
                        } else {
                            trailStatus = target
                            respond(trailDetailJson(status = trailStatus), HttpStatusCode.OK, ok)
                        }
                    }
                    request.method == HttpMethod.Post && path == "/pois" -> {
                        poiBodies += body()
                        respond(
                            """{"id":"p1","trailId":"$SEED_TRAIL_ID","name":"Fonte del Faggio","type":"WATER_SOURCE","location":{"lat":39.3,"lon":16.5}}""",
                            HttpStatusCode.Created,
                            ok,
                        )
                    }
                    request.method == HttpMethod.Patch && path == "/refuges/r1/capacity" -> {
                        val capacity = Regex("\"capacity\"\\s*:\\s*(\\d+)").find(body())!!.groupValues[1].toInt()
                        if (capacity < confirmedGuests) {
                            problem(HttpStatusCode.Conflict, "capacity-exceeded")
                        } else {
                            refugeCapacity = capacity
                            respond(
                                """{"id":"r1","name":"Rifugio","capacity":$refugeCapacity,"location":{"lat":39.3,"lon":16.5},"managerId":"m1","amenities":[]}""",
                                HttpStatusCode.OK,
                                ok,
                            )
                        }
                    }
                    request.method == HttpMethod.Post && path == "/rescue-teams" -> {
                        val sent = body()
                        val name = Regex("\"name\"\\s*:\\s*\"([^\"]*)\"").find(sent)!!.groupValues[1]
                        // Come il server: il nome della squadra è unico senza distinguere le maiuscole (altrimenti 422 business-rule).
                        if (teamNames.any { it.equals(name, ignoreCase = true) }) {
                            problem(HttpStatusCode.UnprocessableEntity, "business-rule")
                        } else {
                            teamNames += name
                            teamBodies += sent
                            respond(
                                """{"id":"t1","name":"$name","callSign":"Alfa 1","contactNumber":"0984 000000","baseLocation":{"lat":39.3,"lon":16.5},"status":"AVAILABLE"}""",
                                HttpStatusCode.Created,
                                ok,
                            )
                        }
                    }
                    else -> null
                }
            }
        }
        val (session, client) = sessionWith(backend)
        session.restore()
        assertTrue(session.login("hiker@test.dev", "password-giusta-1").isSuccess)
        return Triple(
            HazardRepository({ client }, TEST_BASE_URL),
            StaffRepository({ client }, TEST_BASE_URL),
            TrailRepository({ client }, TEST_BASE_URL, dao, now = { clock }, converting = StandardTestDispatcher(testScheduler)),
        )
    }

    /** Ripristina il dispatcher principale dopo ogni test. */
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Stato della segnalazione [id] nella coda mostrata, o null se non c'è. */
    private fun HazardQueueViewModel.statusOf(id: String) = state.value.hazards.firstOrNull { it.id == id }?.status


    /** Verificare una segnalazione la porta a VERIFIED, la coda viene riletta e non restano azioni in corso o errori. */
    @Test
    fun `verificare una segnalazione la porta a VERIFIED e la lista si rilegge`() = runTest {
        val (hazards, staff, _) = repositories()
        val viewModel = HazardQueueViewModel(SEED_TRAIL_ID, hazards, staff)
        advanceUntilIdle()
        assertEquals(HazardResponse.Status.SUBMITTED, viewModel.statusOf("h1"))

        viewModel.change(viewModel.state.value.hazards.first { it.id == "h1" }, UpdateHazardStatusRequest.Status.VERIFIED)
        advanceUntilIdle()

        assertEquals(HazardResponse.Status.VERIFIED, viewModel.statusOf("h1"))
        assertNull(viewModel.state.value.busyHazardId)
        assertNull(viewModel.state.value.actionError)
    }

    /** Risolvere una segnalazione verificata la toglie dalla coda del sentiero. */
    @Test
    fun `risolvere una segnalazione la toglie dalla coda`() = runTest {
        val (hazards, staff, _) = repositories()
        val viewModel = HazardQueueViewModel(SEED_TRAIL_ID, hazards, staff)
        advanceUntilIdle()

        viewModel.change(viewModel.state.value.hazards.first { it.id == "h2" }, UpdateHazardStatusRequest.Status.RESOLVED)
        advanceUntilIdle()

        assertEquals(listOf("h1"), viewModel.state.value.hazards.map { it.id })
    }

    /** Se un collega ha già gestito la segnalazione, il 422 diventa un messaggio chiaro e la riga sparisce dalla coda. */
    @Test
    fun `se un collega l'ha gia gestita il 422 diventa un messaggio chiaro e la riga sparisce`() = runTest {
        val (hazards, staff, _) = repositories()
        val viewModel = HazardQueueViewModel(SEED_TRAIL_ID, hazards, staff)
        advanceUntilIdle()
        colleagueRejectsFirst = true

        viewModel.change(viewModel.state.value.hazards.first { it.id == "h1" }, UpdateHazardStatusRequest.Status.VERIFIED)
        advanceUntilIdle()

        assertEquals(Res.string.staff_queue_already_handled, viewModel.state.value.actionError?.text)
        assertEquals(listOf("h2"), viewModel.state.value.hazards.map { it.id })
        assertNull(viewModel.state.value.busyHazardId)
    }

    /** La coda dell'intero parco mostra solo le segnalazioni da verificare; una volta verificata, una segnalazione ne esce. */
    @Test
    fun `la coda del parco mostra solo le segnalazioni da verificare e una verificata ne esce`() = runTest {
        val (hazards, staff, _) = repositories()
        val viewModel = HazardQueueViewModel(null, hazards, staff)
        advanceUntilIdle()
        assertEquals(listOf("h1"), viewModel.state.value.hazards.map { it.id })

        viewModel.change(viewModel.state.value.hazards.single(), UpdateHazardStatusRequest.Status.VERIFIED)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hazards.isEmpty())
        assertEquals("VERIFIED", hazardStatus["h1"])
    }


    /** Passare da CLOSED a WARNING viene rifiutato con 422: compare il messaggio e la cache non viene toccata. */
    @Test
    fun `da CLOSED a WARNING il server risponde 422 e la cache resta com'era`() = runTest {
        val (_, staff, trails) = repositories()
        val viewModel = TrailStatusViewModel(SEED_TRAIL_ID, staff, trails)

        viewModel.change(UpdateTrailStatusRequest.Status.WARNING)
        advanceUntilIdle()

        assertEquals(Res.string.staff_trail_status_rejected, viewModel.state.value.error?.text)
        assertFalse(viewModel.state.value.busy)
        assertEquals("CLOSED", trailStatus)
        assertNull(trails.observeDetail(SEED_TRAIL_ID).first())
    }

    /** Riaprire il sentiero aggiorna subito, dalla risposta del server, sia il dettaglio sia il riassunto usato dalla mappa. */
    @Test
    fun `riaprire il sentiero aggiorna subito dettaglio e mappa dalla risposta`() = runTest {
        val (_, staff, trails) = repositories()
        dao.upsertSummaries(listOf(summary(status = "CLOSED")))
        val viewModel = TrailStatusViewModel(SEED_TRAIL_ID, staff, trails)

        viewModel.change(UpdateTrailStatusRequest.Status.OPEN)
        advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals(TrailDetailResponse.Status.OPEN, trails.observeDetail(SEED_TRAIL_ID).first()?.value?.status)
        assertEquals("OPEN", dao.summaries.value.getValue(SEED_TRAIL_ID).status)
    }


    /**
     * Il POI si salva solo con un nome di almeno 2 caratteri (il minimo del server) e una posizione; il nome si invia
     * senza spazi ai lati e il POI creato finisce nella cache del sentiero.
     */
    @Test
    fun `il POI si salva solo con un nome valido e una posizione e finisce in cache`() = runTest {
        val (_, staff, trails) = repositories()
        val viewModel = PoiCreateViewModel(SEED_TRAIL_ID, staff, trails)

        viewModel.onNameChange("F")
        viewModel.onLocationPicked(GeoPoint(39.3, 16.5))
        assertFalse(viewModel.state.value.canSave, "un carattere solo: il server vuole almeno 2")
        viewModel.save()
        advanceUntilIdle()
        assertTrue(poiBodies.isEmpty())

        viewModel.onNameChange("  Fonte del Faggio  ")
        viewModel.onTypeChange(CreatePoiRequest.Type.WATER_SOURCE)
        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.created)
        val sent = poiBodies.single()
        assertTrue("\"name\":\"Fonte del Faggio\"" in sent, sent)
        assertTrue("\"trailId\":\"$SEED_TRAIL_ID\"" in sent, sent)
        assertTrue("\"type\":\"WATER_SOURCE\"" in sent, sent)
        assertEquals(listOf("p1"), trails.observePoisOf(SEED_TRAIL_ID).first().map { it.id })
    }

    /** Un 403 alla creazione del POI lascia l'utente nel form con il messaggio di permesso negato. */
    @Test
    fun `un 403 sul POI resta nel form senza crearlo`() = runTest {
        val (_, staff, trails) = repositories()
        forbidden = true
        val viewModel = PoiCreateViewModel(null, staff, trails)
        viewModel.onNameChange("Belvedere")
        viewModel.onLocationPicked(GeoPoint(39.3, 16.5))

        viewModel.save()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.created)
        assertFalse(viewModel.state.value.busy)
        assertEquals(Res.string.error_forbidden, viewModel.state.value.error?.text)
    }


    /** Una capienza inferiore agli ospiti già confermati riceve 409: il messaggio spiega il motivo e la capienza resta quella di prima. */
    @Test
    fun `sotto gli ospiti confermati il 409 dice perche e la capacita resta`() = runTest {
        val (_, staff, _) = repositories()
        val viewModel = RefugeCapacityViewModel("r1", 24, staff)

        viewModel.onDraftChange("8")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(Res.string.staff_capacity_below_booked, viewModel.state.value.error?.text)
        assertEquals(24, viewModel.state.value.capacity)
        assertFalse(viewModel.state.value.saved)
        assertEquals(24, refugeCapacity)
    }

    /**
     * Il campo della capienza accetta solo cifre, richiede almeno un posto e un valore diverso da quello attuale;
     * dopo il salvataggio la capienza mostrata è quella confermata dal server.
     */
    @Test
    fun `la capacita salvata e quella del server e il campo accetta solo cifre`() = runTest {
        val (_, staff, _) = repositories()
        val viewModel = RefugeCapacityViewModel("r1", 24, staff)

        viewModel.onDraftChange("0")
        assertFalse(viewModel.state.value.canSave, "almeno un posto")
        viewModel.onDraftChange("24")
        assertFalse(viewModel.state.value.canSave, "uguale a prima: niente da salvare")
        viewModel.onDraftChange("3a0")
        assertEquals("30", viewModel.state.value.draft)

        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saved)
        assertEquals(30, viewModel.state.value.capacity)
        assertFalse(viewModel.state.value.canSave)
        assertEquals(30, refugeCapacity)
    }


    /**
     * La nuova squadra si salva solo con tutti i campi validi (il telefono deve avere almeno 5 caratteri) e una base;
     * il nome si invia senza spazi ai lati, insieme alle coordinate della base.
     */
    @Test
    fun `la squadra parte solo con i campi validi e una base e il nome va senza spazi`() = runTest {
        val (_, staff, _) = repositories()
        val viewModel = RescueTeamCreateViewModel(staff)

        viewModel.onNameChange("  Soccorso Alpino Camigliatello ")
        viewModel.onCallSignChange("Alfa 1")
        viewModel.onContactChange("0984")
        viewModel.onLocationPicked(GeoPoint(39.3, 16.5))
        assertFalse(viewModel.state.value.canSave, "telefono troppo corto: il server vuole almeno 5 caratteri")
        viewModel.onContactChange("0984 000000")

        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.created)
        val sent = teamBodies.single()
        assertTrue("\"name\":\"Soccorso Alpino Camigliatello\"" in sent, sent)
        assertTrue("\"lat\":39.3" in sent && "\"lon\":16.5" in sent, sent)
    }

    /** Un nome di squadra già usato, anche con maiuscole diverse, diventa un messaggio chiaro e il form resta compilato. */
    @Test
    fun `un nome gia usato diventa un messaggio chiaro e il form resta`() = runTest {
        val (_, staff, _) = repositories()
        val viewModel = RescueTeamCreateViewModel(staff)
        viewModel.onNameChange("soccorso sila")
        viewModel.onCallSignChange("Bravo 2")
        viewModel.onContactChange("0984 111111")
        viewModel.onLocationPicked(GeoPoint(39.3, 16.5))

        viewModel.save()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.created)
        assertFalse(viewModel.state.value.busy)
        assertEquals(Res.string.staff_team_name_taken, viewModel.state.value.error?.text)
        assertEquals("soccorso sila", viewModel.state.value.name)
    }

    /** Oltre i 1000 posti accettati dal backend la capienza non si può salvare. */
    @Test
    fun `oltre il tetto del server la capacita non si salva`() = runTest {
        val (_, staff, _) = repositories()
        val viewModel = RefugeCapacityViewModel("r1", 24, staff)

        viewModel.onDraftChange("1001")
        assertFalse(viewModel.state.value.canSave, "il backend accetta al massimo 1000 posti")
        viewModel.onDraftChange("1000")
        assertTrue(viewModel.state.value.canSave)
    }

    /** Dopo la creazione della squadra un secondo tocco su "Salva" non invia un doppione. */
    @Test
    fun `dopo la squadra creata un secondo tocco non manda un doppione`() = runTest {
        val (_, staff, _) = repositories()
        val viewModel = RescueTeamCreateViewModel(staff)
        viewModel.onNameChange("Soccorso Alpino Lorica")
        viewModel.onCallSignChange("Delta 4")
        viewModel.onContactChange("0984 222222")
        viewModel.onLocationPicked(GeoPoint(39.3, 16.5))
        viewModel.save()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.created)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, teamBodies.size)
        assertFalse(viewModel.state.value.canSave)
    }

    /** Riassunto del sentiero di prova con lo stato indicato, da mettere in cache prima del test. */
    private fun summary(status: String) = TrailSummaryEntity(
        id = SEED_TRAIL_ID, name = "Sentiero", difficulty = "EASY", lengthMeters = 1000.0, status = status,
        simplifiedGeoJson = SEED_TRAIL_PATH, minLon = 16.48, minLat = 39.29, maxLon = 16.52, maxLat = 39.31, updatedAt = clock,
    )
}
