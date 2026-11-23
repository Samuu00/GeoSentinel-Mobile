// Test sugli stili della mappa in composeResources/files/map. Gli stili sono file JSON generati dallo script
// tools/map-styles e salvati nel repository: questi test controllano che usino solo sorgenti gratuite, senza chiavi,
// e che non carichino livelli troppo pesanti per il telefono.
package it.unical.geosentinel_mobile.trail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Controlli statici sul contenuto degli stili della mappa. Sulla JVM i test girano dalla cartella del modulo
 * shared, per questo i percorsi dei file sono relativi a quella cartella.
 */
class MapStylesTest {

    /** Legge il file JSON dello stile con il nome indicato. */
    private fun style(name: String) = File("src/commonMain/composeResources/files/map/$name.json").readText()

    /**
     * Lo stile satellitare usa il livello EOX Sentinel-2 del 2016, rilasciato con licenza CC BY 4.0, e non quelli
     * degli anni successivi, che sono solo per uso non commerciale. La licenza richiede anche l'attribuzione.
     */
    @Test
    fun `il satellite usa il livello EOX 2016 - CC BY 4_0 - non gli anni dopo solo non commerciali`() {
        val satellite = style("satellite")
        assertTrue("/s2cloudless_3857/" in satellite)
        assertFalse(Regex("""s2cloudless-20[0-9][0-9]""").containsMatchIn(satellite))
        // L'attribuzione è obbligatoria per la licenza CC BY.
        assertTrue("Sentinel 2016" in satellite)
    }

    /** Nessuno stile contiene chiavi di accesso né host di servizi a pagamento (ArcGIS, Mapbox). */
    @Test
    fun `nessuno stile usa chiavi o host a pagamento`() {
        for (name in listOf("streets_light", "streets_dark", "satellite")) {
            val json = style(name)
            assertFalse(Regex("(?i)(api_?key|access_token|key=)").containsMatchIn(json), name)
            assertFalse("arcgisonline" in json || "mapbox.com" in json, name)
        }
    }

    /**
     * Nessuno stile contiene il rilievo ombreggiato o un modello del terreno: sull'iPhone questi livelli rendevano
     * lenta la mappa durante lo zoom.
     */
    @Test
    fun `nessuno stile carica il rilievo - zoomando faceva laggare l'iPhone`() {
        for (name in listOf("streets_light", "streets_dark", "satellite")) {
            val json = style(name)
            assertFalse("hillshade" in json || "raster-dem" in json || "elevation-tiles" in json, name)
        }
    }
}
