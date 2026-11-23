// Gestione condivisa delle notifiche push (Firebase Cloud Messaging).
// Il codice di piattaforma riceve token e notifiche e li consegna qui; da qui partono la registrazione del
// dispositivo sul backend e gli eventi che la UI osserva per aggiornarsi o aprire la schermata giusta.
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
 * Collegamento tra le notifiche push della piattaforma e il codice condiviso; è un singleton in Koin.
 *
 * Non conosce la sessione: quando registrare il dispositivo lo decide `App` (da utente autenticato, a ogni avvio
 * e a ogni nuovo token), la rimozione dal server la fa `SessionManager.logout()` e, a ogni fine sessione,
 * `SessionManager` chiama [sessionEnded].
 *
 * @param httpClient fornisce il client autenticato; è una funzione perché il client viene creato solo al primo uso.
 * @param apiBaseUrl indirizzo del backend.
 * @param platform piattaforma con cui il dispositivo si registra (Android o iOS).
 */
class PushManager(
    httpClient: () -> HttpClient,
    apiBaseUrl: String,
    private val platform: RegisterDeviceRequest.Platform,
) : PushTokenSink {

    /** Client HTTP, creato al primo uso. */
    private val client by lazy(httpClient)
    /** API generata per la registrazione dei dispositivi. */
    private val devicesApi by lazy { DevicesApi(apiBaseUrl, client) }

    private val _token = MutableStateFlow<String?>(null)
    /** Il token FCM attuale del dispositivo; null finché Firebase non lo fornisce o dopo una fine sessione. */
    val token: StateFlow<String?> = _token.asStateFlow()

    // Notifiche arrivate con l'app in primo piano. Il buffer tiene fino a 16 eventi e, se si riempie, scarta i più
    // vecchi: emettere non deve mai bloccare il thread della piattaforma.
    private val _received = MutableSharedFlow<PushEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Eventi ricevuti con l'app aperta: per esempio la schermata SOS dell'escursionista si aggiorna subito. */
    val received: SharedFlow<PushEvent> = _received.asSharedFlow()

    // Notifica toccata dall'utente. Resta memorizzata finché App non la consuma, perché con un avvio a freddo
    // arriva prima che l'interfaccia sia pronta a navigare.
    private val _opened = MutableStateFlow<PushEvent?>(null)
    /** L'ultima notifica toccata e non ancora aperta, oppure null. */
    val opened: StateFlow<PushEvent?> = _opened.asStateFlow()

    /**
     * Funzione fornita dalla piattaforma che invalida il token FCM (`deleteToken`) e ne chiede uno nuovo, che poi
     * arriva da [onNewToken]. È null quando Firebase non è configurato (CI, progetto senza google-services.json)
     * e nei test.
     */
    var tokenRevoker: (() -> Unit)? = null

    /** Memorizza il nuovo token; `App` lo osserva e lo registra sul backend. */
    override fun onNewToken(token: String) {
        _token.value = token
    }

    /** Riceve il payload di una notifica arrivata con l'app aperta; i payload non validi sono ignorati. */
    fun onMessage(data: Map<String, String?>) {
        PushEvent.fromPayload(data)?.let { _received.tryEmit(it) }
    }

    /** Riceve il payload di una notifica toccata dall'utente; i payload non validi sono ignorati. */
    fun onNotificationOpened(data: Map<String, String?>) {
        PushEvent.fromPayload(data)?.let { _opened.value = it }
    }

    /**
     * Segna come gestita la notifica toccata. Si azzera solo se è ancora [event]: se nel frattempo ne è arrivata
     * un'altra, quella non va persa.
     */
    fun consumeOpened(event: PushEvent) {
        _opened.compareAndSet(event, null)
    }

    /**
     * Da chiamare a ogni fine sessione (logout, sessione scaduta, account cancellato).
     *
     * Il token FCM viene invalidato per due motivi: se la `DELETE /devices` non è arrivata al server (assenza di
     * rete, token non ancora noto), il server resta con un token che non funzionerà più e il telefono smette di
     * ricevere le notifiche dell'utente uscito; inoltre una registrazione ancora in corso che si conclude dopo il
     * logout registrerebbe anch'essa un token ormai inutile. Viene scartata anche la notifica toccata e non ancora
     * aperta, che non deve valere per l'utente che entra dopo.
     */
    fun sessionEnded() {
        _opened.value = null
        if (_token.value == null && tokenRevoker == null) return
        _token.value = null
        tokenRevoker?.invoke()
    }

    /**
     * Registra il token sul backend, associandolo all'utente autenticato. Senza rete fallisce e basta: si riprova al prossimo avvio
     * o al prossimo token. Il server tollera le registrazioni doppie dello stesso token.
     */
    suspend fun register(token: String): Result<Unit> =
        apiCall { devicesApi.registerDevice(RegisterDeviceRequest(platform, token)) }
}
