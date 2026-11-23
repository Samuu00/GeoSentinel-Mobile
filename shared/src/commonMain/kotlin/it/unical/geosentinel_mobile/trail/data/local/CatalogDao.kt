// DAO di Room per la cache del catalogo: letture osservabili (Flow) per la UI e scritture transazionali che
// sostituiscono i dati di un'area o di un sentiero con l'ultima risposta del server.
package it.unical.geosentinel_mobile.trail.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Accesso alle tabelle della cache del catalogo (sentieri, dettagli, POI, ETag). È una classe astratta e non
 * un'interfaccia perché alcune operazioni composte da più query sono metodi concreti marcati @Transaction. Usato da
 * TrailRepository.
 */
@Dao
abstract class CatalogDao {

    // Dettaglio dei sentieri.

    /** Osserva il dettaglio del sentiero [id]; emette null se non è in cache. */
    @Query("SELECT * FROM trail_detail WHERE id = :id")
    abstract fun observeTrailDetail(id: String): Flow<TrailDetailEntity?>

    /** Legge una volta il dettaglio del sentiero [id], oppure null. */
    @Query("SELECT * FROM trail_detail WHERE id = :id")
    abstract suspend fun getTrailDetail(id: String): TrailDetailEntity?

    /** Inserisce o sostituisce il dettaglio di un sentiero. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTrailDetail(entity: TrailDetailEntity)

    /** Cancella il dettaglio di un sentiero che il server non ha più (risposta 404). */
    @Query("DELETE FROM trail_detail WHERE id = :id")
    abstract suspend fun deleteTrailDetail(id: String)

    /** Cancella l'ETag salvato per la rotta [key]. */
    @Query("DELETE FROM catalog_etag WHERE `key` = :key")
    abstract suspend fun deleteEtag(key: String)

    /** Dopo un 304 il server ha confermato la copia in cache: si aggiorna solo l'orario di "aggiornato alle". */
    @Query("UPDATE trail_detail SET updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun updateTrailDetailTimestamp(id: String, updatedAt: Instant)

    /**
     * Aggiorna lo stato di un sentiero nel riassunto, dopo che un gestore lo ha cambiato: la mappa lo mostra subito,
     * senza attendere il prossimo caricamento dell'area.
     */
    @Query("UPDATE trail_summary SET status = :status WHERE id = :id")
    abstract suspend fun updateSummaryStatus(id: String, status: String)

    // Riassunti dei sentieri, usati dalla mappa.

    /**
     * Osserva i sentieri il cui riquadro tocca la vista. È l'equivalente di ST_Intersects usato dal server, ma calcolato
     * sul riquadro invece che sul percorso: per decidere cosa disegnare sulla mappa è sufficiente.
     */
    @Query(
        """
        SELECT * FROM trail_summary
        WHERE maxLon >= :minLon AND minLon <= :maxLon
          AND maxLat >= :minLat AND minLat <= :maxLat
        """
    )
    abstract fun observeSummariesInBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double): Flow<List<TrailSummaryEntity>>

    /** Inserisce o sostituisce i riassunti dei sentieri. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSummaries(entities: List<TrailSummaryEntity>)

    /**
     * Cancella i sentieri il cui riquadro sta tutto dentro l'area, tranne quelli in [keep]. Si considerano solo questi
     * perché un percorso tutto dentro l'area la interseca di sicuro, quindi il server (che usa ST_Intersects sul
     * percorso vero) lo avrebbe restituito: se manca, è stato tolto. Un riquadro che si limita a toccare l'area non
     * basta: un sentiero a forma di L può toccarla con il riquadro ma non con il percorso.
     */
    @Query(
        """
        DELETE FROM trail_summary
        WHERE minLon >= :minLon AND maxLon <= :maxLon
          AND minLat >= :minLat AND maxLat <= :maxLat
          AND id NOT IN (:keep)
        """
    )
    protected abstract suspend fun deleteSummariesInsideBoxExcept(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, keep: List<String>)

    /**
     * Sostituisce i sentieri dell'area con la risposta completa del server: quelli tutti dentro l'area che il server
     * non ha più restituito sono stati cancellati (o spostati) e spariscono anche dalla cache. Va chiamata solo con una
     * risposta completa, cioè con meno risultati del massimo che il server restituisce (TrailRepository.MAX_BBOX_RESULTS).
     */
    @Transaction
    open suspend fun replaceSummariesInBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, entities: List<TrailSummaryEntity>) {
        // Con una lista vuota "NOT IN ()" non sarebbe SQL valido: si passa un id che non esiste.
        deleteSummariesInsideBoxExcept(minLon, minLat, maxLon, maxLat, entities.map { it.id }.ifEmpty { listOf("") })
        upsertSummaries(entities)
    }

    // Punti di interesse.

    /** Osserva i POI del sentiero [trailId]. */
    @Query("SELECT * FROM poi WHERE trailId = :trailId")
    abstract fun observePoiByTrail(trailId: String): Flow<List<PoiEntity>>

    /** Osserva i POI che cadono nel riquadro. */
    @Query("SELECT * FROM poi WHERE lon BETWEEN :minLon AND :maxLon AND lat BETWEEN :minLat AND :maxLat")
    abstract fun observePoisInBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double): Flow<List<PoiEntity>>

    /** Inserisce o sostituisce dei POI. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPois(pois: List<PoiEntity>)

    /** Cancella i POI del riquadro tranne quelli in [keep]. */
    @Query("DELETE FROM poi WHERE lon BETWEEN :minLon AND :maxLon AND lat BETWEEN :minLat AND :maxLat AND id NOT IN (:keep)")
    protected abstract suspend fun deletePoisInBoxExcept(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, keep: List<String>)

    /**
     * Sostituisce i POI del riquadro con la risposta completa del server: quelli dentro il riquadro che il server non ha
     * più restituito spariscono. Il chiamante deve aver chiesto un cerchio che contiene tutto il riquadro e ricevuto
     * meno risultati del massimo (TrailRepository.MAX_POI_RESULTS), altrimenti la risposta sarebbe parziale.
     */
    @Transaction
    open suspend fun replacePoisInBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, pois: List<PoiEntity>) {
        deletePoisInBoxExcept(minLon, minLat, maxLon, maxLat, pois.map { it.id }.ifEmpty { listOf("") })
        upsertPois(pois)
    }

    /** Cancella tutti i POI del sentiero [trailId]. */
    @Query("DELETE FROM poi WHERE trailId = :trailId")
    protected abstract suspend fun deletePoisOfTrail(trailId: String)

    /** Sostituisce i POI di un sentiero con quelli appena restituiti dal server: quelli rimossi spariscono anche qui. */
    @Transaction
    open suspend fun replacePoisOfTrail(trailId: String, pois: List<PoiEntity>) {
        deletePoisOfTrail(trailId)
        upsertPois(pois)
    }

    // Pulizia di ciò che sta fuori dal limite dei 50 km attorno all'utente.

    /** Cancella i sentieri il cui riquadro non tocca l'area. */
    @Query("DELETE FROM trail_summary WHERE maxLon < :minLon OR minLon > :maxLon OR maxLat < :minLat OR minLat > :maxLat")
    protected abstract suspend fun deleteSummariesOutsideBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double)

    /**
     * Cancella gli ETag "pois/<trailId>" dei sentieri che hanno POI fuori dall'area. Va eseguita prima di cancellare i
     * POI: se restasse l'ETag senza più le righe, il server risponderebbe 304 e il dettaglio rimarrebbe senza POI.
     */
    @Query(
        """
        DELETE FROM catalog_etag WHERE `key` IN (
            SELECT 'pois/' || trailId FROM poi
            WHERE trailId IS NOT NULL AND (lon < :minLon OR lon > :maxLon OR lat < :minLat OR lat > :maxLat)
        )
        """
    )
    protected abstract suspend fun deletePoiEtagsOutsideBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double)

    /** Cancella i POI fuori dall'area. */
    @Query("DELETE FROM poi WHERE lon < :minLon OR lon > :maxLon OR lat < :minLat OR lat > :maxLat")
    protected abstract suspend fun deletePoisOutsideBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double)

    /**
     * Cancella i sentieri il cui riquadro non tocca l'area e i POI che ne stanno fuori, insieme ai relativi ETag: la
     * cache conserva solo ciò che è vicino all'utente.
     */
    @Transaction
    open suspend fun deleteOutsideBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) {
        deleteSummariesOutsideBox(minLon, minLat, maxLon, maxLat)
        deletePoiEtagsOutsideBox(minLon, minLat, maxLon, maxLat)
        deletePoisOutsideBox(minLon, minLat, maxLon, maxLat)
    }

    // ETag per rotta.

    /** Restituisce l'ETag salvato per la rotta [key], oppure null. */
    @Query("SELECT etag FROM catalog_etag WHERE `key` = :key")
    abstract suspend fun etagOf(key: String): String?

    /** Salva o sostituisce l'ETag di una rotta. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveEtag(entity: CatalogEtagEntity)
}
