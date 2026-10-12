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

/**
 * M8.1: coda delle segnalazioni, stato del sentiero e nuovo POI; M8.3: posti del rifugio e nuova squadra. Contro un
 * backend finto con le regole del dominio.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StaffViewModelsTest {

    private val backend = FakeBackend()
    private val dao = FakeCatalogDao()
    private val clock = Instant.fromEpochSeconds(1_790_000_000)

    // ---- stato del server finto ----
    private val hazardStatus = mutableMapOf("h1" to "SUBMITTED", "h2" to "VERIFIED")
    private var trailStatus = "CLOSED"
    // un collega gestisce h1 un attimo prima di noi
    private var colleagueRejectsFirst = false
    private var forbidden = false
    private val poiBodies = mutableListOf<String>()
    // rifugio con 10 ospiti confermati in una delle prossime notti: sotto, 409 capacity-exceeded
    private var refugeCapacity = 24
    private val confirmedGuests = 10
    private val teamNames = mutableSetOf("Soccorso Sila")
    private val teamBodies = mutableListOf<String>()

    private fun hazardJson(id: String) =
        """{"id":"$id","trailId":"$SEED_TRAIL_ID","type":"FALLEN_TREE","severity":"HIGH","status":"${hazardStatus[id]}","location":{"lat":39.3,"lon":16.5},"createdAt":"2026-10-05T10:00:00Z"}"""

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
                        // come il server per il gestore: SUBMITTED e VERIFIED, mai REJECTED o RESOLVED
                        val queue = hazardStatus.filterValues { it == "SUBMITTED" || it == "VERIFIED" }.keys.map { hazardJson(it) }
                        respond("[${queue.joinToString(",")}]", HttpStatusCode.OK, ok)
                    }
                    request.method == HttpMethod.Get && path == "/hazards/pending" -> {
                        // come il server: le SUBMITTED di tutti, ovunque siano
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
                        // la regola del dominio: da CLOSED non si passa a WARNING
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
                        // come il server: il nome è unico, senza distinguere le maiuscole (422 business-rule)
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

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun HazardQueueViewModel.statusOf(id: String) = state.value.hazards.firstOrNull { it.id == id }?.status

    // ---- coda delle segnalazioni ----

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

    @Test
    fun `risolvere una segnalazione la toglie dalla coda`() = runTest {
        val (hazards, staff, _) = repositories()
        val viewModel = HazardQueueViewModel(SEED_TRAIL_ID, hazards, staff)
        advanceUntilIdle()

        viewModel.change(viewModel.state.value.hazards.first { it.id == "h2" }, UpdateHazardStatusRequest.Status.RESOLVED)
        advanceUntilIdle()

        assertEquals(listOf("h1"), viewModel.state.value.hazards.map { it.id })
    }

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

    // ---- stato del sentiero ----

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

    // ---- nuovo POI ----

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

    // ---- posti del rifugio (M8.3) ----

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

    // ---- nuova squadra (M8.3) ----

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

    private fun summary(status: String) = TrailSummaryEntity(
        id = SEED_TRAIL_ID, name = "Sentiero", difficulty = "EASY", lengthMeters = 1000.0, status = status,
        simplifiedGeoJson = SEED_TRAIL_PATH, minLon = 16.48, minLat = 39.29, maxLon = 16.52, maxLat = 39.31, updatedAt = clock,
    )
}
