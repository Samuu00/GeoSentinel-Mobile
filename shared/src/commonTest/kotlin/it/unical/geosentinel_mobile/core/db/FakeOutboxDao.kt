package it.unical.geosentinel_mobile.core.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// L'outbox in memoria: la SQL vera di Room si prova sull'emulatore
class FakeOutboxDao : OutboxDao {
    val rows = MutableStateFlow<Map<String, PendingActionEntity>>(emptyMap())

    override suspend fun insert(action: PendingActionEntity) {
        check(action.idempotencyKey !in rows.value) { "chiave già presente" }
        rows.value += action.idempotencyKey to action
    }

    override suspend fun pending(kind: ActionKind, ownerId: String) =
        rows.value.values.filter { it.kind == kind && it.ownerId == ownerId }.sortedBy { it.createdAt }

    override fun observePending(kind: ActionKind, ownerId: String): Flow<List<PendingActionEntity>> =
        rows.map { all -> all.values.filter { it.kind == kind && it.ownerId == ownerId }.sortedBy { it.createdAt } }

    override suspend fun incrementAttempts(key: String) {
        rows.value[key]?.let { rows.value += key to it.copy(attempts = it.attempts + 1) }
    }

    override suspend fun delete(key: String) {
        rows.value -= key
    }

    override suspend fun deleteOwner(ownerId: String, kinds: List<ActionKind>) {
        rows.value = rows.value.filterValues { it.ownerId != ownerId || it.kind !in kinds }
    }

    override suspend fun replace(key: String, kind: ActionKind, payloadJson: String) {
        rows.value[key]?.let { rows.value += key to it.copy(kind = kind, payloadJson = payloadJson) }
    }
}
