package it.unical.geosentinel_mobile.sos.ui

import it.unical.geosentinel_mobile.api.model.SosAlertResponse
import it.unical.geosentinel_mobile.core.db.ActionKind
import it.unical.geosentinel_mobile.core.db.OutboxDao
import it.unical.geosentinel_mobile.core.db.PendingActionEntity
import it.unical.geosentinel_mobile.core.location.GeoPoint
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.network.ContractViolationException
import it.unical.geosentinel_mobile.core.network.isTransient
import it.unical.geosentinel_mobile.sos.data.SosPayload
import it.unical.geosentinel_mobile.sos.data.SosRepository
import it.unical.geosentinel_mobile.sos.data.SosState
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.SerializationException
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

    // Stato, outbox e job si cambiano solo con il lock preso; il lock non avvolge MAI una richiesta di rete, così un
    // SOS non aspetta una rete lenta (restore all'avvio, annullamento, posizione). Dieci tocchi veloci fanno un solo SOS.
    private val lock = Mutex()
    private var sending: Job? = null
    // verifica sul server dopo l'annullamento di un SOS ancora senza risposta (vedi [cancelQueued])
    private var verifyCancel: Job? = null

    /**
     * Lancia l'SOS in [at]. Da ospite (o con la sessione scaduta) restituisce false e non fa nulla: la UI propone 112
     * e accesso. Mentre un invio è già in corso non ne parte un altro. Gira nello [scope] del controller: se la
     * schermata si chiude subito dopo il tocco, l'SOS parte lo stesso.
     */
    suspend fun raise(at: GeoPoint, medicalNotes: String?): Boolean = scope.async {
        lock.withLock {
            val owner = ownerId() ?: return@withLock false
            if (_state.value is SosState.Sending) return@withLock true
            // un SOS nuovo: la verifica di un annullamento precedente non deve chiudere questo
            verifyCancel?.cancel()
            outbox.pending(ActionKind.SOS_CANCEL, owner).forEach { outbox.delete(it.idempotencyKey) }
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
    }.await()

    /**
     * All'avvio da loggati (e a ogni login): un SOS rimasto in coda riparte con la sua chiave, altrimenti si chiede al
     * server se c'è un'allerta attiva (anche lanciata da un altro dispositivo). Senza rete lo stato resta com'è.
     */
    suspend fun restore(): Unit = scope.async {
        val askServer = lock.withLock {
            sending?.cancelAndJoin()
            verifyCancel?.cancel()
            val owner = ownerId()
            if (owner == null) {
                _state.value = SosState.Idle
                return@withLock false
            }
            val queued = outbox.pending(ActionKind.SOS, owner).firstOrNull()
            if (queued != null) {
                startSending(queued)
                return@withLock false
            }
            // un annullamento ancora da verificare (app chiusa nel frattempo): l'utente voleva annullare, si riprende
            // la verifica invece di mostrargli di nuovo l'allerta
            val cancelling = outbox.pending(ActionKind.SOS_CANCEL, owner).firstOrNull()
            if (cancelling != null) {
                _state.value = SosState.Idle
                verifyCancel = scope.launch { verifyCancelled(cancelling.idempotencyKey) }
                return@withLock false
            }
            true
        }
        if (!askServer) return@async
        val result = call { sos.active() }
        // nel frattempo può essere partito un SOS (Sending o Raised): quello vince
        lock.withLock { if (_state.value.isSettled()) applyActive(result) }
    }.await()

    /**
     * M6.2: manda la posizione nuova dell'allerta attiva (dalla UI, ogni 60 s in primo piano). La risposta aggiorna anche
     * lo stato (es. la squadra assegnata). Senza allerta non fa nulla; un errore di rete si lascia al giro successivo.
     */
    suspend fun updateLocation(at: GeoPoint): Unit = scope.async {
        val alertId = raisedAlertId() ?: return@async
        val result = call { sos.updateLocation(alertId, at) }
        // 404 o 422: l'allerta non è più attiva (chiusa dal soccorso), si chiede al server com'è adesso
        val closed = result.exceptionOrNull()?.apiError().let { it is ApiError.NotFound || it is ApiError.BusinessRule }
        val now = if (closed) call { sos.active() } else result
        lock.withLock { if (raisedAlertId() == alertId) applyActive(now) }
    }.await()

    /**
     * Rilegge l'allerta attiva: ogni 30 s con la schermata aperta (M6.2) e a ogni push `SOS_DISPATCHED`/`SOS_RESOLVED`
     * (M7.1). Anche da Idle o Failed: la push può arrivare dopo una risposta persa o un riavvio senza rete. Durante
     * l'invio no: la risposta arriva al job, con la stessa chiave.
     */
    suspend fun refresh(): Unit = scope.async {
        if (_state.value is SosState.Sending) return@async
        val alertId = raisedAlertId()
        val result = call { sos.active() }
        lock.withLock {
            if (alertId != null) {
                if (raisedAlertId() == alertId) applyActive(result)
            } else if (_state.value.isSettled()) {
                applyActive(result)
            }
        }
    }.await()

    private fun raisedAlertId(): String? = (_state.value as? SosState.Raised)?.alert?.id

    // Idle o Failed: nessun invio in corso e nessuna allerta a schermo
    private fun SosState.isSettled() = this is SosState.Idle || this is SosState.Failed

    // 404 = nessuna allerta attiva (chiusa dal soccorso o annullata altrove); senza rete lo stato resta com'è
    private fun applyActive(result: Result<SosAlertResponse>) {
        result.fold(
            onSuccess = { _state.value = SosState.Raised(it) },
            onFailure = { if (it.apiError() is ApiError.NotFound) _state.value = SosState.Idle },
        )
    }

    /**
     * Logout o sessione chiusa: si smette di inviare e di verificare. Con la sessione scaduta l'SOS resta in coda e
     * riparte al login dello stesso utente; con il logout la coda la svuota SessionManager (resta solo la verifica di un
     * annullamento, che riparte al suo login).
     */
    suspend fun reset(): Unit = lock.withLock {
        sending?.cancelAndJoin()
        verifyCancel?.cancel()
        _state.value = SosState.Idle
    }

    /**
     * Annulla l'SOS. Gira nello [scope] del controller: lasciare la schermata a metà non lo interrompe.
     * Un errore (di solito la rete) lascia l'allerta com'è e si restituisce, così la UI lo mostra.
     */
    suspend fun cancel(): Result<Unit> = scope.async {
        when (val current = _state.value) {
            is SosState.Sending -> lock.withLock { cancelQueued() }
            is SosState.Raised -> cancelRaised(current.alert.id)
            else -> {
                lock.withLock { if (_state.value.isSettled()) _state.value = SosState.Idle }
                Result.success(Unit)
            }
        }
    }.await()

    // Ancora senza risposta: si smette di inviare e si toglie dalla coda. La richiesta però può essere arrivata (risposta
    // persa, o in volo al momento dell'annullamento): finché il server non risponde, verifyCancelled continua a
    // chiederlo e chiude l'allerta se c'è. La verifica è un'azione dell'outbox (SOS_CANCEL, senza dati personali):
    // se l'app si chiude prima, riparte al riavvio (restore) invece di perdersi.
    private suspend fun cancelQueued(): Result<Unit> {
        sending?.cancelAndJoin()
        verifyCancel?.cancel()
        val owner = ownerId()
        if (owner != null) {
            outbox.pending(ActionKind.SOS, owner).forEach { outbox.delete(it.idempotencyKey) }
            val verification = PendingActionEntity(
                idempotencyKey = newKey(),
                kind = ActionKind.SOS_CANCEL,
                ownerId = owner,
                payloadJson = "{}",
                createdAt = clock.now(),
            )
            outbox.insert(verification)
            verifyCancel = scope.launch { verifyCancelled(verification.idempotencyKey) }
        }
        _state.value = SosState.Idle
        return Result.success(Unit)
    }

    // Finita la verifica (allerta chiusa, o mai arrivata) l'azione si toglie dall'outbox
    private suspend fun verifyCancelled(key: String) {
        try {
            verifyUntilAnswered()
            outbox.delete(key)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // database guasto: la riga resta e la verifica riparte al prossimo restore
        }
    }

    private suspend fun verifyUntilAnswered() {
        var attempt = 0
        var notFound = 0
        while (true) {
            val active = call { sos.active() }
            val error = active.exceptionOrNull()?.apiError()
            when {
                active.isSuccess -> {
                    val closed = call { sos.cancel(active.getOrThrow().id) }
                    val closeError = closed.exceptionOrNull()?.apiError()
                    if (closed.isSuccess || closeError is ApiError.NotFound || closeError is ApiError.BusinessRule) return
                }
                // nessuna allerta: si ricontrolla una volta dopo una pausa, perché una richiesta in volo può arrivare dopo
                error is ApiError.NotFound -> if (++notFound >= 2) return
                error != null && !error.isTransient() -> return
            }
            delay(retryDelay(++attempt))
        }
    }

    // La richiesta senza il lock: un SOS lanciato nel frattempo non aspetta la rete
    private suspend fun cancelRaised(alertId: String): Result<Unit> {
        val result = call { sos.cancel(alertId) }
        val failure = result.exceptionOrNull()
        // 404 o 422 (già chiusa dal soccorso): l'allerta non è più attiva, che è quello che l'utente voleva
        val error = failure?.apiError()
        if (failure != null && error !is ApiError.NotFound && error !is ApiError.BusinessRule) return Result.failure(failure)
        lock.withLock { if (raisedAlertId() == alertId) _state.value = SosState.Idle }
        return Result.success(Unit)
    }

    // Da chiamare con il lock preso
    private suspend fun startSending(action: PendingActionEntity) {
        val key = action.idempotencyKey
        val payload = try {
            json.decodeFromString(SosPayload.serializer(), action.payloadJson)
        } catch (_: SerializationException) {
            // riga illeggibile (es. formato cambiato in un aggiornamento): non si può mandare, e l'utente deve saperlo
            outbox.delete(key)
            _state.value = SosState.Failed(NotConfirmed)
            return
        }
        _state.value = SosState.Sending(action.attempts, action.createdAt)
        sending = scope.launch {
            var attempts = action.attempts
            try {
                while (true) {
                    val result = call { sos.raise(key, payload) }
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
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // il database ha fallito: niente crash. L'SOS resta in coda per il prossimo restore e la UI propone il 112
                _state.value = SosState.Failed(NotConfirmed)
            }
        }
    }

    /**
     * Le chiamate del controller non lanciano mai: una risposta 2xx fuori contratto (un bug) vale come un errore del
     * server, che si ritenta. Niente crash, e niente crash a ogni avvio con l'SOS in coda.
     */
    private suspend fun <T> call(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: ContractViolationException) {
        Result.failure(ApiException(ApiError.Server(status = 200, traceId = null)))
    }

    private companion object {
        // azione in coda illeggibile o database guasto: per l'utente è "non confermato", con il 112 in primo piano
        val NotConfirmed = ApiError.Server(status = 0, traceId = null)
    }
}

private fun Throwable.apiError(): ApiError? = (this as? ApiException)?.error
