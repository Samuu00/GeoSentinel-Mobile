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
 * Sessione e client collegati come in Koin, sul backend finto dei test comuni. Qui niente tempo virtuale: i test di UI
 * (M9.1) girano con il main looper di Robolectric e la rete finta sui dispatcher veri.
 */
fun uiSession(backend: FakeBackend): Pair<SessionManager, HttpClient> {
    lateinit var client: HttpClient
    val manager = SessionManager(FakeSecureTokenStore(), { client }, TEST_BASE_URL, { null })
    val engine = MockEngine { request -> with(backend) { handle(request) } }
    client = HttpClientFactory.createHttpClient(AppConfig(TEST_BASE_URL, isDebug = false), manager, engine)
    return manager to client
}

/** Il tema dell'app; [fontScale] 2 = testo al 200% (M9.2). */
@Composable
fun TestTheme(fontScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        GeoSentinelTheme(ThemeMode.LIGHT, content)
    }
}

/**
 * Aspetta [condition] facendo avanzare anche l'orologio di Robolectric: in modalità PAUSED non va avanti da solo, e un
 * `delay` sul main thread (timeout, animazioni) non scadrebbe mai.
 */
fun ComposeContentTestRule.waitFor(timeoutMillis: Long = 5_000, condition: () -> Boolean) =
    waitUntil(timeoutMillis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        condition()
    }

/** Aspetta che compaia un testo. */
fun ComposeContentTestRule.waitForText(text: String) =
    waitFor { onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
