package it.unical.geosentinel_mobile.core.push

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import it.unical.geosentinel_mobile.api.model.RegisterDeviceRequest
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.FakeSecureTokenStore
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushManagerTest {

    private val backend = FakeBackend().apply {
        // POST /devices e DELETE /devices/{token} come nel backend: 204, il corpo si tiene per i controlli
        extraRoutes = { request, owner ->
            if (owner != null && request.url.encodedPath.startsWith("/api/v1/devices")) {
                bodies += request.bodyText()
                respond("", HttpStatusCode.NoContent)
            } else {
                null
            }
        }
    }
    private val bodies = mutableListOf<String>()

    // ---- payload ---------------------------------------------------------------------------------------------

    @Test
    fun `i tre type del backend diventano eventi con il loro alertId`() {
        assertEquals(PushEvent.SosRaised("a1"), PushEvent.fromPayload(mapOf("type" to "SOS_RAISED", "alertId" to "a1")))
        assertEquals(PushEvent.SosDispatched("a2"), PushEvent.fromPayload(mapOf("type" to "SOS_DISPATCHED", "alertId" to "a2")))
        assertEquals(PushEvent.SosResolved("a3"), PushEvent.fromPayload(mapOf("type" to "SOS_RESOLVED", "alertId" to "a3")))
    }

    @Test
    fun `un type sconosciuto o senza alertId si ignora`() {
        assertNull(PushEvent.fromPayload(mapOf("type" to "SOS_ESCALATED", "alertId" to "a1")))
        assertNull(PushEvent.fromPayload(mapOf("type" to "SOS_RAISED")))
        assertNull(PushEvent.fromPayload(mapOf("type" to "SOS_RAISED", "alertId" to "")))
        assertNull(PushEvent.fromPayload(mapOf("type" to null, "alertId" to "a1")))
    }

    // ---- notifica toccata ------------------------------------------------------------------------------------

    @Test
    fun `la notifica toccata resta finche non si apre e una doppia apre la stessa allerta`() = runTest {
        val (_, client) = sessionWith(backend)
        val push = PushManager({ client }, TEST_BASE_URL, RegisterDeviceRequest.Platform.ANDROID)
        val payload = mapOf("type" to "SOS_DISPATCHED", "alertId" to "a1")

        push.onNotificationOpened(payload)
        push.onNotificationOpened(payload)
        assertEquals(PushEvent.SosDispatched("a1"), push.opened.value)

        push.consumeOpened(PushEvent.SosDispatched("a1"))
        assertNull(push.opened.value)
    }

    @Test
    fun `aprire un evento vecchio non cancella quello arrivato dopo`() = runTest {
        val (_, client) = sessionWith(backend)
        val push = PushManager({ client }, TEST_BASE_URL, RegisterDeviceRequest.Platform.ANDROID)

        push.onNotificationOpened(mapOf("type" to "SOS_RAISED", "alertId" to "nuova"))
        push.consumeOpened(PushEvent.SosRaised("vecchia"))

        assertEquals(PushEvent.SosRaised("nuova"), push.opened.value)
    }

    // ---- registrazione e logout ------------------------------------------------------------------------------

    @Test
    fun `la registrazione manda token e piattaforma con il bearer dell utente`() = runTest {
        val (session, client) = sessionWith(backend)
        session.login("rescuer@test.dev", "password-giusta-2")
        val push = PushManager({ client }, TEST_BASE_URL, RegisterDeviceRequest.Platform.ANDROID)

        val result = push.register("fcm-1")

        assertTrue(result.isSuccess, result.toString())
        assertTrue("POST /api/v1/devices Bearer a1" in backend.requests, backend.requests.toString())
        val body = bodies.single()
        assertTrue("\"token\":\"fcm-1\"" in body && "\"platform\":\"ANDROID\"" in body, body)
    }

    @Test
    fun `senza rete la registrazione fallisce senza eccezioni`() = runTest {
        val (session, client) = sessionWith(backend)
        session.login("rescuer@test.dev", "password-giusta-2")
        backend.offline = true
        val push = PushManager({ client }, TEST_BASE_URL, RegisterDeviceRequest.Platform.ANDROID)

        assertTrue(push.register("fcm-1").isFailure)
    }

    @Test
    fun `il logout toglie il dispositivo prima di revocare la sessione`() = runTest {
        val store = FakeSecureTokenStore()
        lateinit var push: PushManager
        val (session, client) = sessionWith(backend, store, pushToken = { push.token.value })
        push = PushManager({ client }, TEST_BASE_URL, RegisterDeviceRequest.Platform.ANDROID)
        push.onNewToken("fcm-1")
        session.login("rescuer@test.dev", "password-giusta-2")

        session.logout()

        val unregister = backend.requests.indexOf("DELETE /api/v1/devices/fcm-1 Bearer a1")
        val logout = backend.requests.indexOfFirst { it.startsWith("POST /api/v1/auth/logout") }
        assertTrue(unregister in 0 until logout, backend.requests.toString())
        assertNull(store.tokens)
    }
}

private fun HttpRequestData.bodyText(): String = when (val content = body) {
    is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
    is io.ktor.http.content.TextContent -> content.text
    else -> content::class.simpleName.orEmpty()
}
