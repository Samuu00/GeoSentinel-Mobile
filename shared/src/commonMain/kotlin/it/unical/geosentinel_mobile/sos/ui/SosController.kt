package it.unical.geosentinel_mobile.sos.ui

import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.db.ActionKind
import it.unical.geosentinel_mobile.core.db.OutboxDao
import it.unical.geosentinel_mobile.core.db.PendingActionEntity
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.network.isTransient
import it.unical.geosentinel_mobile.sos.data.SosPayload
import it.unical.geosentinel_mobile.sos.data.SosRepository
import it.unical.geosentinel_mobile.sos.data.SosState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * L'SOS dell'escursionista (M6.1, posizione e stato in M6.2). Una sola istanza per processo (singleton Koin) con un suo [scope]: l'invio va avanti
 * anche se l'utente lascia la schermata. La regola è una: l'utente deve sempre sapere se il soccorso ha la richiesta,
 * e [state] lo dice.
 *
 * La `Idempotency-Key` nasce con l'SOS, si salva nell'outbox PRIMA della richiesta e si cancella solo con una risposta
 * definitiva: si ritenta finché non c'è, anche dopo un riavvio ([restore]), e il server non crea doppioni.
 * La posizione arriva da fuori: il provider di MapLibre vive nella UI (A11).
 */
@OptIn(ExperimentalUuidApi::class)
class SosController(
    private val sos: SosRepository,
    private val outbox: OutboxDao,
    // l'utente loggato (anche senza profilo letto), null da ospite: l'outbox lega a lui l'SOS
    private val ownerId: suspend () -> String?,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val newKey: () -> String = { Uuid.random().toString() },
    // attesa dopo il tentativo fallito n (1, 2, …): cresce fino a 30 s, per non consumare la batteria senza rete
    private val retryDelay: (attempt: Int) -> Duration = { attempt -> (5.seconds * attempt).coerceAtMost(30.seconds) },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<SosState>(SosState.Idle)
    val state: StateFlow<SosState> = _state.asStateFlow()

    // raise, restore, reset e cancel uno alla volta: dieci tocchi veloci fanno un solo SOS
    private val lock = Mutex()
    private var sending: Job? = null

    /**
     * Lancia l'SOS in [at]. Da ospite (o con la sessione scaduta) restituisce false e non fa nulla: la UI propone 112
     * e accesso. Mentre un invio è già in corso non ne parte un altro.
     */
    suspend fun raise(at: GeoPoint, medicalNotes: String?): Boolean = lock.withLock {
        val owner = ownerId() ?: return@withLock false
        if (_state.value is SosState.Sending) return@withLock true
        val payload = SosPayload(at.lat, at.lon, medicalNotes?.trim()?.takeIf { it.isNotEmpty() })
        val action = PendingActionEntity(
            idempotencyKey = newKey(),
            kind = ActionKind.SOS,
            ownerId = owner,
            payloadJson = json.encodeToString(SosPayload.serializer(), payload),
            createdAt = clock.now(),
        )
        outbox.insert(action)
        startSending(action)
        true
    }

    /**
     * All'avvio da loggati (e a ogni login): un SOS rimasto in coda riparte con la sua chiave, altrimenti si chiede al
     * server se c'è un'allerta attiva (anche lanciata da un altro dispositivo). Senza rete lo stato resta com'è.
     */
    suspend fun restore(): Unit = lock.withLock {
        sending?.cancelAndJoin()
        val owner = ownerId()
        if (owner == null) {
            _state.value = SosState.Idle
            return@withLock
        }
        val queued = outbox.pending(ActionKind.SOS, owner).firstOrNull()
        if (queued != null) {
            startSending(queued)
            return@withLock
        }
        applyActive(sos.active())
    }

    /**
     * M6.2: manda la posizione nuova dell'allerta attiva (dalla UI, ogni 60 s in primo piano). La risposta aggiorna anche
     * lo stato (es. la squadra assegnata). Senza allerta non fa nulla; un errore di rete si lascia al giro successivo.
     * Gira nello [scope] del controller (IO, non il thread della UI che la chiama) e senza il lock durante la richiesta,
     * così un annullamento non aspetta una rete lenta.
     */
    suspend fun updateLocation(at: GeoPoint): Unit = scope.async {
        val alertId = raisedAlertId() ?: return@async
        val result = sos.updateLocation(alertId, at)
        lock.withLock {
            if (raisedAlertId() != alertId) return@withLock // annullata (o cambiata) nel frattempo
            result.fold(
                onSuccess = { _state.value = SosState.Raised(it) },
                // 404 o 422: l'allerta non è più attiva (chiusa dal soccorso), si chiede al server com'è adesso
                onFailure = { if (it.apiError().let { e -> e is ApiError.NotFound || e is ApiError.BusinessRule }) applyActive(sos.active()) },
            )
        }
    }.await()

    /** M6.2: rilegge l'allerta attiva (stato, squadra) se ce n'è una. Ogni 30 s con la schermata aperta, finché mancano le push (M7). */
    suspend fun refresh(): Unit = scope.async {
        val alertId = raisedAlertId() ?: return@async
        val result = sos.active()
        lock.withLock { if (raisedAlertId() == alertId) applyActive(result) }
    }.await()

    private fun raisedAlertId(): String? = (_state.value as? SosState.Raised)?.alert?.id

    // 404 = nessuna allerta attiva (chiusa dal soccorso o annullata altrove); senza rete lo stato resta com'è
    private fun applyActive(result: Result<SosAlertResponse>) {
        result.fold(
            onSuccess = { _state.value = SosState.Raised(it) },
            onFailure = { if (it.apiError() is ApiError.NotFound) _state.value = SosState.Idle },
        )
    }

    /** Logout o sessione chiusa: si smette di inviare, l'SOS resta nell'outbox del suo utente e riparte al suo login. */
    suspend fun reset(): Unit = lock.withLock {
        sending?.cancelAndJoin()
        _state.value = SosState.Idle
    }

    /**
     * Annulla l'SOS. Gira nello [scope] del controller: lasciare la schermata a metà non lo interrompe.
     * Un errore (di solito la rete) lascia l'allerta com'è e si restituisce, così la UI lo mostra.
     */
    suspend fun cancel(): Result<Unit> = scope.async {
        lock.withLock {
            when (val current = _state.value) {
                is SosState.Sending -> cancelQueued()
                is SosState.Raised -> cancelRaised(current.alert.id)
                else -> Result.success(Unit).also { _state.value = SosState.Idle }
            }
        }
    }.await()

    // Ancora senza risposta: si toglie dalla coda. Se la richiesta era arrivata (risposta persa) l'allerta esiste già,
    // quindi si chiude anche quella; senza rete resta, e al prossimo restore() l'utente la rivede e può annullarla.
    private suspend fun cancelQueued(): Result<Unit> {
        sending?.cancelAndJoin()
        ownerId()?.let { owner -> outbox.pending(ActionKind.SOS, owner).forEach { outbox.delete(it.idempotencyKey) } }
        sos.active().onSuccess { sos.cancel(it.id) }
        _state.value = SosState.Idle
        return Result.success(Unit)
    }

    private suspend fun cancelRaised(alertId: String): Result<Unit> {
        val result = sos.cancel(alertId)
        val failure = result.exceptionOrNull() ?: return Result.success(Unit).also { _state.value = SosState.Idle }
        // 404 o 422 (già chiusa dal soccorso): l'allerta non è più attiva, che è quello che l'utente voleva
        val error = failure.apiError()
        if (error is ApiError.NotFound || error is ApiError.BusinessRule) {
            _state.value = SosState.Idle
            return Result.success(Unit)
        }
        return Result.failure(failure)
    }

    // Da chiamare con il lock preso
    private fun startSending(action: PendingActionEntity) {
        val payload = json.decodeFromString(SosPayload.serializer(), action.payloadJson)
        val key = action.idempotencyKey
        _state.value = SosState.Sending(action.attempts, action.createdAt)
        sending = scope.launch {
            var attempts = action.attempts
            while (true) {
                val result = sos.raise(key, payload)
                val error = result.exceptionOrNull()?.apiError()
                when {
                    result.isSuccess -> {
                        outbox.delete(key)
                        _state.value = SosState.Raised(result.getOrThrow())
                        return@launch
                    }
                    error != null && error.isTransient() -> {
                        outbox.incrementAttempts(key)
                        attempts++
                        _state.value = SosState.Sending(attempts, action.createdAt)
                        delay(retryDelay(attempts))
                    }
                    else -> {
                        // un 4xx definitivo (es. 403): ripeterlo uguale non serve, ma l'utente deve saperlo subito
                        outbox.delete(key)
                        _state.value = SosState.Failed(error ?: ApiError.Network)
                        return@launch
                    }
                }
            }
        }
    }
}

private fun Throwable.apiError(): ApiError? = (this as? ApiException)?.error
