// Cuore della funzione SOS dell'app: lancio dell'allerta con l'outbox e i tentativi ripetuti, ripresa dopo un
// riavvio, aggiornamento della posizione e dello stato, annullamento con verifica sul server. È un singleton che
// sopravvive alle schermate, così un SOS non si perde chiudendo la UI.
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
 * Gestore dell'SOS dell'escursionista. Ne esiste una sola istanza per processo (singleton Koin), con un proprio
 * [scope]: l'invio continua anche se l'utente lascia la schermata. Il principio guida è uno solo: l'utente deve
 * sempre sapere se il soccorso ha ricevuto la richiesta, e [state] lo dice.
 *
 * La `Idempotency-Key` nasce con l'SOS, si salva nell'outbox prima della richiesta e si cancella solo con una
 * risposta definitiva. Fino ad allora si ritenta, anche dopo un riavvio ([restore]), e il server non crea doppioni.
 * La posizione arriva da fuori, perché il provider della posizione di MapLibre vive nella UI.
 *
 * @param ownerId restituisce l'id dell'utente loggato (anche senza profilo letto), null da ospite: l'outbox lega l'SOS
 * a quell'utente.
 * @param clock e [newKey] sono parametri per poterli sostituire nei test.
 * @param retryDelay attesa dopo il tentativo fallito numero n: cresce fino a 30 secondi, per non consumare la
 * batteria quando manca la rete.
 */
@OptIn(ExperimentalUuidApi::class)
class SosController(
    private val sos: SosRepository,
    private val outbox: OutboxDao,
    private val ownerId: suspend () -> String?,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val newKey: () -> String = { Uuid.random().toString() },
    private val retryDelay: (attempt: Int) -> Duration = { attempt -> (5.seconds * attempt).coerceAtMost(30.seconds) },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<SosState>(SosState.Idle)
    val state: StateFlow<SosState> = _state.asStateFlow()

    // Stato, outbox e job si modificano solo tenendo il lock. Il lock invece non avvolge mai una richiesta di rete:
    // così un SOS non resta in attesa dietro a una richiesta lenta (ripresa all'avvio, annullamento, posizione).
    // Grazie al lock, dieci tocchi veloci producono un solo SOS.
    private val lock = Mutex()
    private var sending: Job? = null
    // Verifica sul server dopo l'annullamento di un SOS ancora senza risposta (vedi cancelQueued).
    private var verifyCancel: Job? = null

    /**
     * Lancia l'SOS dalla posizione [at] con le note mediche (vuote: nessuna nota).
     *
     * Da ospite, o con la sessione scaduta, non fa nulla e restituisce false: la UI propone allora il 112 e l'accesso.
     * Se un invio è già in corso non ne parte un altro. Il lavoro gira nello [scope] del controller: anche se la
     * schermata si chiude subito dopo il tocco, l'SOS parte lo stesso.
     */
    suspend fun raise(at: GeoPoint, medicalNotes: String?): Boolean = scope.async {
        lock.withLock {
            val owner = ownerId() ?: return@withLock false
            if (_state.value is SosState.Sending) return@withLock true
            // Un SOS nuovo: la verifica di un annullamento precedente non deve chiudere proprio questo.
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
     * Ripristina lo stato all'avvio da loggati e a ogni login. Un SOS rimasto in coda riparte con la sua chiave; un
     * annullamento ancora da verificare riprende la verifica; altrimenti si chiede al server se c'è un'allerta attiva,
     * magari lanciata da un altro dispositivo. Senza rete lo stato resta com'è.
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
            // Un annullamento ancora da verificare (l'app si è chiusa nel frattempo): l'utente voleva annullare, quindi
            // si riprende la verifica invece di mostrargli di nuovo l'allerta.
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
        // Mentre si aspettava la rete può essere partito un SOS (Sending o Raised): in quel caso vince quello.
        lock.withLock { if (_state.value.isSettled()) applyActive(result) }
    }.await()

    /**
     * Invia la nuova posizione dell'allerta attiva; la chiama la UI ogni 60 secondi con l'app in primo piano. La
     * risposta aggiorna anche lo stato, per esempio con la squadra assegnata. Senza un'allerta non fa nulla; un errore
     * di rete si lascia al giro successivo.
     */
    suspend fun updateLocation(at: GeoPoint): Unit = scope.async {
        val alertId = raisedAlertId() ?: return@async
        val result = call { sos.updateLocation(alertId, at) }
        // Un 404 o un 422 vuol dire che l'allerta non è più attiva (chiusa dal soccorso): si chiede al server lo stato
        // attuale.
        val closed = result.exceptionOrNull()?.apiError().let { it is ApiError.NotFound || it is ApiError.BusinessRule }
        val now = if (closed) call { sos.active() } else result
        lock.withLock { if (raisedAlertId() == alertId) applyActive(now) }
    }.await()

    /**
     * Rilegge l'allerta attiva: ogni 30 secondi con la schermata SOS aperta e a ogni notifica push `SOS_DISPATCHED` o
     * `SOS_RESOLVED`. Funziona anche da Idle o Failed, perché la notifica può arrivare dopo una risposta persa o un
     * riavvio senza rete. Durante l'invio invece non fa nulla: la risposta arriverà al job di invio, con la stessa
     * chiave.
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

    /** Id dell'allerta a schermo, se lo stato è Raised. */
    private fun raisedAlertId(): String? = (_state.value as? SosState.Raised)?.alert?.id

    /** Vero in Idle o Failed: nessun invio in corso e nessuna allerta a schermo. */
    private fun SosState.isSettled() = this is SosState.Idle || this is SosState.Failed

    /**
     * Applica la risposta sull'allerta attiva: con un'allerta lo stato diventa Raised; un 404 (nessuna allerta attiva,
     * perché chiusa dal soccorso o annullata altrove) riporta a Idle; con altri errori, come la rete assente, lo stato
     * resta com'è.
     */
    private fun applyActive(result: Result<SosAlertResponse>) {
        result.fold(
            onSuccess = { _state.value = SosState.Raised(it) },
            onFailure = { if (it.apiError() is ApiError.NotFound) _state.value = SosState.Idle },
        )
    }

    /**
     * Chiamata al logout o alla chiusura della sessione: smette di inviare e di verificare e torna a Idle. Con la
     * sessione scaduta l'SOS resta in coda e riparte al login dello stesso utente; con il logout la coda la svuota
     * SessionManager (resta solo la verifica di un annullamento, che riparte al login di quell'utente).
     */
    suspend fun reset(): Unit = lock.withLock {
        sending?.cancelAndJoin()
        verifyCancel?.cancel()
        _state.value = SosState.Idle
    }

    /**
     * Annulla l'SOS, sia ancora in invio sia già ricevuto. Gira nello [scope] del controller: lasciare la schermata a
     * metà non lo interrompe. Un errore (di solito la rete) lascia l'allerta com'è e viene restituito, così la UI lo
     * mostra.
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

    /**
     * Annulla un SOS ancora senza risposta: smette di inviarlo e lo toglie dalla coda. La richiesta però potrebbe
     * essere già arrivata al server (risposta persa, o richiesta in volo al momento dell'annullamento): per questo
     * verifyCancelled continua a chiedere al server e chiude l'allerta se esiste. La verifica è a sua volta un'azione
     * dell'outbox (`SOS_CANCEL`, senza dati personali): se l'app si chiude prima, riparte al riavvio con [restore]
     * invece di perdersi. Da chiamare con il lock preso.
     */
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

    /** Esegue la verifica dell'annullamento e, quando ha una risposta (allerta chiusa o mai arrivata), toglie l'azione [key] dall'outbox. */
    private suspend fun verifyCancelled(key: String) {
        try {
            verifyUntilAnswered()
            outbox.delete(key)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Errore del database: la riga resta e la verifica riparte al prossimo restore.
        }
    }

    /**
     * Chiede al server se c'è un'allerta attiva finché non ottiene una risposta definitiva. Se c'è, la annulla; se non
     * c'è, ricontrolla una seconda volta prima di concludere. Si ferma anche su un errore definitivo; sugli errori
     * temporanei attende [retryDelay] e riprova.
     */
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
                // Nessuna allerta: si ricontrolla una volta dopo una pausa, perché una richiesta ancora in volo può
                // arrivare al server più tardi.
                error is ApiError.NotFound -> if (++notFound >= 2) return
                error != null && !error.isTransient() -> return
            }
            delay(retryDelay(++attempt))
        }
    }

    /**
     * Annulla un'allerta già ricevuta dal server. La richiesta si fa senza il lock, così un SOS lanciato nel frattempo
     * non deve aspettare la rete.
     */
    private suspend fun cancelRaised(alertId: String): Result<Unit> {
        val result = call { sos.cancel(alertId) }
        val failure = result.exceptionOrNull()
        // Un 404 o un 422 (allerta già chiusa dal soccorso) vuol dire che non è più attiva, cioè proprio quello che
        // l'utente voleva: si tratta come un successo.
        val error = failure?.apiError()
        if (failure != null && error !is ApiError.NotFound && error !is ApiError.BusinessRule) return Result.failure(failure)
        lock.withLock { if (raisedAlertId() == alertId) _state.value = SosState.Idle }
        return Result.success(Unit)
    }

    /**
     * Avvia il job che invia l'azione SOS [action] e ritenta sugli errori temporanei, aggiornando [state] a ogni
     * tentativo. Da chiamare con il lock preso.
     */
    private suspend fun startSending(action: PendingActionEntity) {
        val key = action.idempotencyKey
        val payload = try {
            json.decodeFromString(SosPayload.serializer(), action.payloadJson)
        } catch (_: SerializationException) {
            // Riga illeggibile (per esempio formato cambiato con un aggiornamento dell'app): non si può inviare, e
            // l'utente deve saperlo.
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
                            // Un 4xx definitivo (per esempio 403): ripetere la stessa richiesta non serve, ma l'utente
                            // deve saperlo subito.
                            outbox.delete(key)
                            _state.value = SosState.Failed(error ?: ApiError.Network)
                            return@launch
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Errore del database: l'app non crasha. L'SOS resta in coda per il prossimo restore e la UI propone
                // il 112.
                _state.value = SosState.Failed(NotConfirmed)
            }
        }
    }

    /**
     * Esegue una chiamata del repository senza mai lanciare eccezioni (tranne la cancellazione). Una risposta 2xx che
     * non rispetta il contratto (un bug) vale come un errore del server e quindi si ritenta: così l'app non crasha,
     * nemmeno a ogni avvio con l'SOS in coda.
     */
    private suspend fun <T> call(block: suspend () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: ContractViolationException) {
        Result.failure(ApiException(ApiError.Server(status = 200, traceId = null)))
    }

    private companion object {
        /** Errore usato per un'azione in coda illeggibile o un database guasto: per l'utente è "non confermato", con il 112 in evidenza. */
        val NotConfirmed = ApiError.Server(status = 0, traceId = null)
    }
}

/** Errore dell'API contenuto nell'eccezione, oppure null se l'eccezione non viene dal server. */
private fun Throwable.apiError(): ApiError? = (this as? ApiException)?.error
