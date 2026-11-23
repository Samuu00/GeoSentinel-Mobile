// Implementazione in memoria di OutboxDao, l'accesso alla tabella delle azioni in attesa di invio (l'outbox).
// La usano i test comuni che non possono aprire un database: le query SQL vere di Room si provano a parte, nei test
// sul database reale.
package it.unical.geosentinel_mobile.core.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Outbox finta basata su una mappa chiave di idempotenza -> azione. Riproduce il comportamento della DAO vera
 * (filtri per tipo e proprietario, ordinamento per data di creazione) e permette ai test di ispezionare le righe.
 */
class FakeOutboxDao : OutboxDao {
    /** Contenuto corrente dell'outbox, osservabile come la tabella vera. */
    val rows = MutableStateFlow<Map<String, PendingActionEntity>>(emptyMap())

    /** Inserisce un'azione; come il vincolo di chiave primaria nel database, fallisce se la chiave esiste già. */
    override suspend fun insert(action: PendingActionEntity) {
        check(action.idempotencyKey !in rows.value) { "chiave già presente" }
        rows.value += action.idempotencyKey to action
    }

    /** Restituisce le azioni di un tipo e di un utente, dalla più vecchia alla più recente. */
    override suspend fun pending(kind: ActionKind, ownerId: String) =
        rows.value.values.filter { it.kind == kind && it.ownerId == ownerId }.sortedBy { it.createdAt }

    /** Come pending, ma come flusso che si aggiorna a ogni modifica. */
    override fun observePending(kind: ActionKind, ownerId: String): Flow<List<PendingActionEntity>> =
        rows.map { all -> all.values.filter { it.kind == kind && it.ownerId == ownerId }.sortedBy { it.createdAt } }

    /** Aumenta di uno il numero di tentativi dell'azione indicata, se esiste. */
    override suspend fun incrementAttempts(key: String) {
        rows.value[key]?.let { rows.value += key to it.copy(attempts = it.attempts + 1) }
    }

    /** Cancella l'azione con la chiave indicata. */
    override suspend fun delete(key: String) {
        rows.value -= key
    }

    /** Cancella le azioni dei tipi indicati che appartengono a un utente (usato al logout). */
    override suspend fun deleteOwner(ownerId: String, kinds: List<ActionKind>) {
        rows.value = rows.value.filterValues { it.ownerId != ownerId || it.kind !in kinds }
    }

    /** Cambia tipo e contenuto di un'azione esistente mantenendo la stessa chiave di idempotenza. */
    override suspend fun replace(key: String, kind: ActionKind, payloadJson: String) {
        rows.value[key]?.let { rows.value += key to it.copy(kind = kind, payloadJson = payloadJson) }
    }
}
