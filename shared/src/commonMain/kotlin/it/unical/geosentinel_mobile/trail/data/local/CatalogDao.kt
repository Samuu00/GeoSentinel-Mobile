package it.unical.geosentinel_mobile.trail.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
abstract class CatalogDao {

    // ---- dettaglio ----

    @Query("SELECT * FROM trail_detail WHERE id = :id")
    abstract fun observeTrailDetail(id: String): Flow<TrailDetailEntity?>

    @Query("SELECT * FROM trail_detail WHERE id = :id")
    abstract suspend fun getTrailDetail(id: String): TrailDetailEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTrailDetail(entity: TrailDetailEntity)

    // 404: il sentiero non esiste più sul server
    @Query("DELETE FROM trail_detail WHERE id = :id")
    abstract suspend fun deleteTrailDetail(id: String)

    @Query("DELETE FROM catalog_etag WHERE `key` = :key")
    abstract suspend fun deleteEtag(key: String)

    // 304: il server conferma la copia in cache, cambia solo la data
    @Query("UPDATE trail_detail SET updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun updateTrailDetailTimestamp(id: String, updatedAt: Instant)

    // stato cambiato da un gestore (M8.1): la mappa lo mostra subito, senza aspettare il prossimo giro sulla bbox
    @Query("UPDATE trail_summary SET status = :status WHERE id = :id")
    abstract suspend fun updateSummaryStatus(id: String, status: String)

    // ---- riepiloghi (mappa) ----

    // I sentieri il cui riquadro tocca la vista: come ST_Intersects sul server, ma sul riquadro (basta per la mappa)
    @Query(
        """
        SELECT * FROM trail_summary
        WHERE maxLon >= :minLon AND minLon <= :maxLon
          AND maxLat >= :minLat AND minLat <= :maxLat
        """
    )
    abstract fun observeSummariesInBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double): Flow<List<TrailSummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSummaries(entities: List<TrailSummaryEntity>)

    // Solo le righe il cui riquadro sta tutto dentro l'area: un percorso tutto dentro interseca di sicuro l'area, quindi
    // il server (ST_Intersects sul percorso vero) l'avrebbe restituito. Un riquadro che tocca soltanto non basta: un
    // sentiero a L può toccare l'area con il riquadro e non con il percorso.
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
     * La risposta completa del server per un'area: quelli tutti dentro l'area che il server non ha più restituito sono
     * stati tolti (o spostati) e spariscono anche dalla cache. Solo con una risposta completa (meno di 200 risultati).
     */
    @Transaction
    open suspend fun replaceSummariesInBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, entities: List<TrailSummaryEntity>) {
        // con una lista vuota "NOT IN ()" non è SQL valido: un id che non esiste
        deleteSummariesInsideBoxExcept(minLon, minLat, maxLon, maxLat, entities.map { it.id }.ifEmpty { listOf("") })
        upsertSummaries(entities)
    }

    // ---- POI ----

    @Query("SELECT * FROM poi WHERE trailId = :trailId")
    abstract fun observePoiByTrail(trailId: String): Flow<List<PoiEntity>>

    @Query("SELECT * FROM poi WHERE lon BETWEEN :minLon AND :maxLon AND lat BETWEEN :minLat AND :maxLat")
    abstract fun observePoisInBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double): Flow<List<PoiEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertPois(pois: List<PoiEntity>)

    @Query("DELETE FROM poi WHERE lon BETWEEN :minLon AND :maxLon AND lat BETWEEN :minLat AND :maxLat AND id NOT IN (:keep)")
    protected abstract suspend fun deletePoisInBoxExcept(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, keep: List<String>)

    /**
     * La risposta completa del server per un riquadro: i POI dentro che il server non ha più spariscono. Il chiamante
     * deve aver chiesto un cerchio che contiene tutto il riquadro, con meno di 50 risultati.
     */
    @Transaction
    open suspend fun replacePoisInBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, pois: List<PoiEntity>) {
        deletePoisInBoxExcept(minLon, minLat, maxLon, maxLat, pois.map { it.id }.ifEmpty { listOf("") })
        upsertPois(pois)
    }

    @Query("DELETE FROM poi WHERE trailId = :trailId")
    protected abstract suspend fun deletePoisOfTrail(trailId: String)

    /** I POI di un sentiero come li ha appena dati il server: quelli tolti spariscono anche qui. */
    @Transaction
    open suspend fun replacePoisOfTrail(trailId: String, pois: List<PoiEntity>) {
        deletePoisOfTrail(trailId)
        upsertPois(pois)
    }

    // ---- ETag per rotta ----

    @Query("SELECT etag FROM catalog_etag WHERE `key` = :key")
    abstract suspend fun etagOf(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveEtag(entity: CatalogEtagEntity)
}
