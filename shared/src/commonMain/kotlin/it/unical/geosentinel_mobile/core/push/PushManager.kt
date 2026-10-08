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
 * decide `App` (da loggati, a ogni avvio e a ogni token nuovo), la rimozione `SessionManager.logout()`, e a ogni fine
 * sessione `SessionManager` chiama [sessionEnded].
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

    /**
     * Dalla piattaforma: invalida il token FCM (`deleteToken`) e ne chiede uno nuovo, che torna da [onNewToken].
     * Null senza Firebase (CI, progetto senza google-services.json) e nei test.
     */
    var tokenRevoker: (() -> Unit)? = null

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

    /**
     * Fine sessione (logout, sessione scaduta, account cancellato). Il token FCM si invalida: se `DELETE /devices` non è
     * arrivata (senza rete, o token non ancora noto) il server resta con un token morto e questo telefono non riceve più
     * le push dell'utente uscito. Anche una registrazione in volo finita dopo il logout registra un token morto.
     * Una notifica toccata e non ancora aperta non deve valere per chi entra dopo.
     */
    fun sessionEnded() {
        _opened.value = null
        if (_token.value == null && tokenRevoker == null) return
        _token.value = null
        tokenRevoker?.invoke()
    }

    // Senza rete fallisce e basta: si riprova al prossimo avvio o al prossimo token. Il server tollera i doppioni.
    suspend fun register(token: String): Result<Unit> =
        apiCall { devicesApi.registerDevice(RegisterDeviceRequest(platform, token)) }
}
