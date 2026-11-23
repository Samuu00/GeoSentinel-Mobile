// Funzioni di supporto per i test di UI di Compose che girano sulla JVM con Robolectric: creazione di sessione e
// client sul backend finto, tema dell'app con la scala del testo regolabile e attese che fanno avanzare l'orologio
// del main looper.
package it.unical.geosentinel_mobile.ui

import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.network.HttpClientFactory
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.FakeSecureTokenStore
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.ui.theme.GeoSentinelTheme
import it.unical.geosentinel_mobile.core.ui.theme.ThemeMode
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * Crea SessionManager e HttpClient collegati tra loro come avviene in Koin, usando il backend finto dei test comuni.
 * A differenza dei test comuni qui non c'è il tempo virtuale: i test di UI girano con il main looper di Robolectric
 * e la rete finta usa i dispatcher veri.
 *
 * @return la coppia sessione e client autenticato.
 */
fun uiSession(backend: FakeBackend): Pair<SessionManager, HttpClient> {
    // Il client serve alla sessione (per il refresh) e la sessione al client: si risolve con un riferimento tardivo.
    lateinit var client: HttpClient
    val manager = SessionManager(FakeSecureTokenStore(), { client }, TEST_BASE_URL, { null })
    val engine = MockEngine { request -> with(backend) { handle(request) } }
    client = HttpClientFactory.createHttpClient(AppConfig(TEST_BASE_URL, isDebug = false), manager, engine)
    return manager to client
}

/**
 * Applica il tema chiaro dell'app al contenuto. Con [fontScale] uguale a 2 il testo viene mostrato al 200%, per
 * verificare che le schermate restino usabili con il testo ingrandito dalle impostazioni di accessibilità.
 */
@Composable
fun TestTheme(fontScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        GeoSentinelTheme(ThemeMode.LIGHT, content)
    }
}

/**
 * Aspetta che [condition] diventi vera, facendo avanzare a ogni giro anche l'orologio del main looper di
 * Robolectric. In modalità PAUSED l'orologio non avanza da solo, e un `delay` sul main thread (timeout,
 * animazioni) non scadrebbe mai.
 */
fun ComposeContentTestRule.waitFor(timeoutMillis: Long = 5_000, condition: () -> Boolean) =
    waitUntil(timeoutMillis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        condition()
    }

/** Aspetta che nella schermata compaia almeno un nodo con il testo indicato. */
fun ComposeContentTestRule.waitForText(text: String) =
    waitFor { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
