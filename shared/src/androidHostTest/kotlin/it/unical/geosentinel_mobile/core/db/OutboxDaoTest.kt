package it.unical.geosentinel_mobile.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.unical.geosentinel_mobile.core.session.SessionEnd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Instant

// La SQL vera dell'outbox (pending_actions) sulla JVM, come CatalogDaoTest: Room in memoria sopra l'SQLite di Robolectric
@RunWith(RobolectricTestRunner::class)
class OutboxDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext()).build()
    private val dao = db.outboxDao()

    @After
    fun close() = db.close()

    private fun action(key: String, kind: ActionKind, owner: String = "anna", second: Long = 0) =
        PendingActionEntity(key, kind, owner, "{}", Instant.fromEpochSeconds(1_790_000_000 + second))

    private suspend fun keys(kind: ActionKind, owner: String = "anna") = dao.pending(kind, owner).map { it.idempotencyKey }

    @Test
    fun `pending filtra per tipo e utente in ordine di creazione`() = runBlocking {
        dao.insert(action("b2", ActionKind.BOOKING, second = 20))
        dao.insert(action("b1", ActionKind.BOOKING, second = 10))
        dao.insert(action("sos", ActionKind.SOS))
        dao.insert(action("altro", ActionKind.BOOKING, owner = "bruno"))

        assertEquals(listOf("b1", "b2"), keys(ActionKind.BOOKING))
        assertEquals(listOf("b1", "b2"), dao.observePending(ActionKind.BOOKING, "anna").first().map { it.idempotencyKey })
        assertEquals(listOf("altro"), keys(ActionKind.BOOKING, owner = "bruno"))
    }

    @Test
    fun `stessa chiave due volte e un errore non una sovrascrittura`() = runBlocking {
        dao.insert(action("k", ActionKind.SOS))
        assertFails { dao.insert(action("k", ActionKind.BOOKING)) }
        assertEquals(listOf("k"), keys(ActionKind.SOS))
    }

    @Test
    fun `tentativi tipo cambiato e cancellazione per chiave`() = runBlocking {
        dao.insert(action("k", ActionKind.BOOKING))
        dao.insert(action("resta", ActionKind.BOOKING, second = 1))
        dao.incrementAttempts("k")
        dao.incrementAttempts("k")
        assertEquals(2, dao.pending(ActionKind.BOOKING, "anna").first().attempts)

        dao.replace("k", ActionKind.BOOKING_REJECTED, "{\"r\":1}")
        assertEquals(listOf("resta"), keys(ActionKind.BOOKING))
        val rejected = dao.pending(ActionKind.BOOKING_REJECTED, "anna").single()
        assertEquals("{\"r\":1}", rejected.payloadJson)
        assertEquals(2, rejected.attempts)

        dao.delete("k")
        assertEquals(emptyList(), keys(ActionKind.BOOKING_REJECTED))
        assertEquals(listOf("resta"), keys(ActionKind.BOOKING))
    }

    @Test
    fun `fine sessione - logout tiene solo la verifica dell'SOS e la cancellazione toglie tutto dell'utente`() = runBlocking {
        for (kind in ActionKind.entries) dao.insert(action("anna-$kind", kind))
        dao.insert(action("bruno-sos", ActionKind.SOS, owner = "bruno"))

        dao.clearAfterSession("anna", SessionEnd.EXPIRED)
        assertEquals(ActionKind.entries.size, ActionKind.entries.sumOf { keys(it).size })

        dao.clearAfterSession("anna", SessionEnd.LOGOUT)
        assertEquals(mapOf(ActionKind.SOS_CANCEL to listOf("anna-SOS_CANCEL")), ActionKind.entries.associateWith { keys(it) }.filterValues { it.isNotEmpty() })

        dao.clearAfterSession("anna", SessionEnd.ACCOUNT_DELETED)
        assertEquals(0, ActionKind.entries.sumOf { keys(it).size })
        assertEquals(listOf("bruno-sos"), keys(ActionKind.SOS, owner = "bruno"))
    }
}
