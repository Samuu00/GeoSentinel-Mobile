package it.unical.geosentinel_mobile.core.push

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.DevicesApi
import it.unical.geosentinel_mobile.api.model.RegisterDeviceRequest
import it.unical.geosentinel_mobile.core.network.apiCall
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ponte tra le push della piattaforma e `shared` (M7.1), singleton Koin. Non conosce la sessione: la registrazione la
 * decide `App` (da loggati, a ogni avvio e a ogni token nuovo), la rimozione `SessionManager.logout()`.
 */
class PushManager(
    httpClient: () -> HttpClient,
    apiBaseUrl: String,
    private val platform: RegisterDeviceRequest.Platform,
) : PushTokenSink {

    private val client by lazy(httpClient)
    private val devicesApi by lazy { DevicesApi(apiBaseUrl, client) }

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    // Push arrivate con l'app in primo piano (l'SOS dell'escursionista si rilegge subito)
    private val _received = MutableSharedFlow<PushEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val received: SharedFlow<PushEvent> = _received.asSharedFlow()

    // Notifica toccata: resta finché App non la apre (all'avvio a freddo arriva prima che la UI sia pronta)
    private val _opened = MutableStateFlow<PushEvent?>(null)
    val opened: StateFlow<PushEvent?> = _opened.asStateFlow()

    override fun onNewToken(token: String) {
        _token.value = token
    }

    fun onMessage(data: Map<String, String?>) {
        PushEvent.fromPayload(data)?.let { _received.tryEmit(it) }
    }

    fun onNotificationOpened(data: Map<String, String?>) {
        PushEvent.fromPayload(data)?.let { _opened.value = it }
    }

    fun consumeOpened(event: PushEvent) {
        _opened.compareAndSet(event, null)
    }

    // Senza rete fallisce e basta: si riprova al prossimo avvio o al prossimo token. Il server tollera i doppioni.
    suspend fun register(token: String): Result<Unit> =
        apiCall { devicesApi.registerDevice(RegisterDeviceRequest(platform, token)) }
}
