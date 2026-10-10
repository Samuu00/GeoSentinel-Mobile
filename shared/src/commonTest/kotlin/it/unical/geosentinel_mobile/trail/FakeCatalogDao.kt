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
 * CatalogDao in memoria per i test sulla JVM: sull'host Android Room vuole un Context. Le stesse condizioni delle
 * query SQL, scritte in Kotlin; la SQL vera si prova sull'emulatore (CatalogDaoDeviceTest).
 */
class FakeCatalogDao : CatalogDao() {
    val details = MutableStateFlow<Map<String, TrailDetailEntity>>(emptyMap())
    val summaries = MutableStateFlow<Map<String, TrailSummaryEntity>>(emptyMap())
    val pois = MutableStateFlow<Map<String, PoiEntity>>(emptyMap())
    val etags = mutableMapOf<String, String>()
    var failWrites = false

    override fun observeTrailDetail(id: String): Flow<TrailDetailEntity?> = details.map { it[id] }
    override suspend fun getTrailDetail(id: String): TrailDetailEntity? = details.value[id]
    override suspend fun upsertTrailDetail(entity: TrailDetailEntity) {
        if (failWrites) throw IllegalStateException("database or disk is full")
        details.value += entity.id to entity
    }
    override suspend fun deleteTrailDetail(id: String) { details.value -= id }
    override suspend fun deleteEtag(key: String) { etags.remove(key) }
    override suspend fun updateTrailDetailTimestamp(id: String, updatedAt: Instant) {
        details.value[id]?.let { details.value += id to it.copy(updatedAt = updatedAt) }
    }
    override suspend fun updateSummaryStatus(id: String, status: String) {
        summaries.value[id]?.let { summaries.value += id to it.copy(status = status) }
    }

    // come la WHERE della query: il riquadro del sentiero tocca quello chiesto
    private fun TrailSummaryEntity.touches(west: Double, south: Double, east: Double, north: Double) =
        this.maxLon >= west && this.minLon <= east && this.maxLat >= south && this.minLat <= north

    override fun observeSummariesInBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double): Flow<List<TrailSummaryEntity>> =
        summaries.map { rows -> rows.values.filter { it.touches(minLon, minLat, maxLon, maxLat) } }
    override suspend fun upsertSummaries(entities: List<TrailSummaryEntity>) { summaries.value += entities.associateBy { it.id } }
    override suspend fun deleteSummariesInsideBoxExcept(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, keep: List<String>) {
        // come la query: solo i riquadri tutti dentro l'area
        summaries.value = summaries.value.filterValues {
            val inside = it.minLon >= minLon && it.maxLon <= maxLon && it.minLat >= minLat && it.maxLat <= maxLat
            !inside || it.id in keep
        }
    }

    override fun observePoiByTrail(trailId: String): Flow<List<PoiEntity>> = pois.map { rows -> rows.values.filter { it.trailId == trailId } }
    override fun observePoisInBox(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double): Flow<List<PoiEntity>> =
        pois.map { rows -> rows.values.filter { it.lon in minLon..maxLon && it.lat in minLat..maxLat } }
    override suspend fun upsertPois(pois: List<PoiEntity>) { this.pois.value += pois.associateBy { it.id } }
    override suspend fun deletePoisInBoxExcept(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double, keep: List<String>) {
        pois.value = pois.value.filterValues { !(it.lon in minLon..maxLon && it.lat in minLat..maxLat) || it.id in keep }
    }
    override suspend fun deletePoisOfTrail(trailId: String) { pois.value = pois.value.filterValues { it.trailId != trailId } }

    override suspend fun etagOf(key: String): String? = etags[key]
    override suspend fun saveEtag(entity: CatalogEtagEntity) { etags[entity.key] = entity.etag }
}
