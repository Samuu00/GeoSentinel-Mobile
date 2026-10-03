package it.unical.geosentinel_mobile.trail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Gli stili sono generati da tools/map-styles e committati: qui si controlla che restino gratuiti e senza chiave.
// Sulla JVM i test girano dalla cartella del modulo shared.
class MapStylesTest {

    private fun style(name: String) = File("src/commonMain/composeResources/files/map/$name.json").readText()

    @Test
    fun `il satellite usa il livello EOX 2016 - CC BY 4_0 - non gli anni dopo solo non commerciali`() {
        val satellite = style("satellite")
        assertTrue("/s2cloudless_3857/" in satellite)
        assertFalse(Regex("""s2cloudless-20[0-9][0-9]""").containsMatchIn(satellite))
        assertTrue("Sentinel 2016" in satellite) // attribuzione richiesta dalla licenza
    }

    @Test
    fun `nessuno stile usa chiavi o host a pagamento`() {
        for (name in listOf("streets_light", "streets_dark", "satellite")) {
            val json = style(name)
            assertFalse(Regex("(?i)(api_?key|access_token|key=)").containsMatchIn(json), name)
            assertFalse("arcgisonline" in json || "mapbox.com" in json, name)
        }
    }
}
