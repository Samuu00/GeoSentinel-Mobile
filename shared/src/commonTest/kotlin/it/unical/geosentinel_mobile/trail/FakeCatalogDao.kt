// Implementazione in memoria di CatalogDao, la DAO di Room che fa da cache locale del catalogo (sentieri, POI ed
// ETag). La usano i test comuni del repository dei sentieri; le query SQL vere si provano a parte sul database reale.
package it.unical.geosentinel_mobile.trail

import it.unical.geosentinel_mobile.trail.data.local.CatalogDao
import it.unical.geosentinel_mobile.trail.data.local.CatalogEtagEntity
import it.unical.geosentinel_mobile.trail.data.local.PoiEntity
import it.unical.geosentinel_mobile.trail.data.local.TrailDetailEntity
import it.unical.geosentinel_mobile.trail.data.local.TrailSummaryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * CatalogDao in memoria per i test che girano senza database: sulla JVM Room richiederebbe un Context Android.
 * Ogni metodo riproduce in Kotlin le stesse condizioni della query SQL corrispondente; le query vere sono verificate
 * da CatalogDaoTest e CatalogDaoDeviceTest.
 */
class FakeCatalogDao : CatalogDao() {
    /** Dettagli dei sentieri salvati, per id. */
    val details = MutableStateFlow<Map<String, TrailDetailEntity>>(emptyMap())
    /** Riassunti dei sentieri (quelli disegnati sulla mappa), per id. */
    val summaries = MutableStateFlow<Map<String, TrailSummaryEntity>>(emptyMap())
    /** Punti di interesse salvati, per id. */
    val pois = MutableStateFlow<Map<String, PoiEntity>>(emptyMap())
    /** ETag salvati per ogni rotta del catalogo (per esempio "trail/id" o "pois/id"). */
    val etags = mutableMapOf<String, String>()
    /** Se vero, la scrittura di un dettaglio fallisce come con il disco pieno, per provare la gestione dell'errore. */
    var failWrites = false

    /** Osserva il dettaglio di un sentiero; emette null finché non è in cache. */
    override fun observeTrailDetail(id: String): Flow<TrailDetailEntity?> = details.map { it[id] }
    /** Legge una volta il dettaglio di un sentiero. */
    override suspend fun getTrailDetail(id: String): TrailDetailEntity? = details.value[id]
    /** Inserisce o sostituisce un dettaglio; lancia un'eccezione se failWrites è attivo. */
    override suspend fun upsertTrailDetail(entity: TrailDetailEntity) {
        if (failWrites) throw IllegalStateException("database or disk is full")
        details.value += entity.id to entity
    }
    /** Cancella il dettaglio di un sentiero. */
    override suspend fun deleteTrailDetail(id: String) { details.value -= id }
    /** Cancella l'ETag di una rotta. */
    override suspend fun deleteEtag(key: String) { etags.remove(key) }
    /** Aggiorna solo la data di aggiornamento di un dettaglio (risposta 304: i dati sono ancora validi). */
    override suspend fun updateTrailDetailTimestamp(id: String, updatedAt: Instant) {
        details.value[id]?.let { details.value += id to it.copy(updatedAt = updatedAt) }
    }
    /** Cambia lo stato (aperto, chiuso, ...) del riassunto di un sentiero. */
    override suspend fun updateSummaryStatus(id: String, status: String) {
        summaries.value[id]?.let { summaries.value += id to it.copy(status = status) }
    }

    /** Vero se il riquadro del sentiero interseca quello indicato, come la clausola WHERE della query. */
    private fun TrailSummaryEntity.touches(west: Double, south: Double, east: Double, north: Double) =
        this.maxLon >= west && this.minLon <= east && this.maxLat >= south && this.minLat <= north

    /** Osserva i sentieri il cui riquadro tocca l'area visibile. */
    override fun observeSummariesInBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double): Flow<List<TrailSummaryEntity>> =
        summaries.map { rows -> rows.values.filter { it.touches(minLon, minLat, maxLon, maxLat) } }
    /** Inserisce o sostituisce una lista di riassunti. */
    override suspend fun upsertSummaries(entities: List<TrailSummaryEntity>) { summaries.value += entities.associateBy { it.id } }
    /**
     * Cancella i sentieri interamente contenuti nell'area tranne quelli in [keep]. Come la query vera, non tocca i
     * sentieri che escono dall'area, perché il server potrebbe non averli restituiti pur esistendo ancora.
     */
    override suspend fun deleteSummariesInsideBoxExcept(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, keep: List<String>) {
        summaries.value = summaries.value.filterValues {
            val inside = it.minLon >= minLon && it.maxLon <= maxLon && it.minLat >= minLat && it.maxLat <= maxLat
            !inside || it.id in keep
        }
    }

    /** Osserva i POI collegati a un sentiero. */
    override fun observePoiByTrail(trailId: String): Flow<List<PoiEntity>> = pois.map { rows -> rows.values.filter { it.trailId == trailId } }
    /** Osserva i POI che cadono nell'area indicata. */
    override fun observePoisInBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double): Flow<List<PoiEntity>> =
        pois.map { rows -> rows.values.filter { it.lon in minLon..maxLon && it.lat in minLat..maxLat } }
    /** Inserisce o sostituisce una lista di POI. */
    override suspend fun upsertPois(pois: List<PoiEntity>) { this.pois.value += pois.associateBy { it.id } }
    /** Cancella i POI dell'area tranne quelli in [keep]. */
    override suspend fun deletePoisInBoxExcept(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, keep: List<String>) {
        pois.value = pois.value.filterValues { !(it.lon in minLon..maxLon && it.lat in minLat..maxLat) || it.id in keep }
    }
    /** Cancella tutti i POI collegati a un sentiero. */
    override suspend fun deletePoisOfTrail(trailId: String) { pois.value = pois.value.filterValues { it.trailId != trailId } }

    /** Vero se il POI cade fuori dall'area, come nelle query usate per ripulire la cache lontano dall'utente. */
    private fun PoiEntity.outside(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) =
        lon !in minLon..maxLon || lat !in minLat..maxLat
    /** Tiene solo i sentieri che toccano l'area e cancella gli altri. */
    override suspend fun deleteSummariesOutsideBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) {
        summaries.value = summaries.value.filterValues { it.touches(minLon, minLat, maxLon, maxLat) }
    }
    /** Cancella gli ETag dei POI dei sentieri che hanno POI fuori dall'area, così verranno riscaricati per intero. */
    override suspend fun deletePoiEtagsOutsideBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) {
        pois.value.values.filter { it.trailId != null && it.outside(minLon, minLat, maxLon, maxLat) }.forEach { etags.remove("pois/${it.trailId}") }
    }
    /** Cancella i POI che cadono fuori dall'area. */
    override suspend fun deletePoisOutsideBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double) {
        pois.value = pois.value.filterValues { !it.outside(minLon, minLat, maxLon, maxLat) }
    }

    /** Restituisce l'ETag salvato per una rotta, o null. */
    override suspend fun etagOf(key: String): String? = etags[key]
    /** Salva o sostituisce l'ETag di una rotta. */
    override suspend fun saveEtag(entity: CatalogEtagEntity) { etags[entity.key] = entity.etag }
}
