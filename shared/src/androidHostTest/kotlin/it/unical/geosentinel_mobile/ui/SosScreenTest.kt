// Test di UI della schermata SOS, sulla JVM con Robolectric, compresi i controlli di accessibilità: il pulsante
// "Chiama 112" è sempre presente, l'SOS si può attivare anche con l'azione "tieni premuto" di TalkBack e con il
// testo al 200% tutti i controlli restano raggiungibili.
package it.unical.geosentinel_mobile.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import it.unical.geosentinel_mobile.core.db.FakeOutboxDao
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.sos.data.SosRepository
import it.unical.geosentinel_mobile.sos.ui.SosController
import it.unical.geosentinel_mobile.sos.ui.SosScreen
import it.unical.geosentinel_mobile.sos.ui.SosViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

/** Verifica la schermata SOS da ospite e da utente autenticato, anche con il testo ingrandito. */
@RunWith(RobolectricTestRunner::class)
class SosScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Backend finto che risponde alle richieste HTTP del client. */
    private val backend = FakeBackend()
    /** Scope in cui gira SosController durante il test, chiuso alla fine. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Trova il pulsante SOS tramite la descrizione letta dal lettore di schermo. */
    private val holdButton = hasContentDescription("Invia SOS. Tieni premuto 3 secondi")
    /** Trova il pulsante "Chiama 112". */
    private val call112 = hasClickAction() and hasText("Chiama 112")

    /**
     * Prepara la sessione (da ospite o autenticata, secondo [loggedIn]), crea un SosController vero con un'outbox in
     * memoria, avvia Koin con il ViewModel e mostra la schermata con la scala del testo [fontScale].
     */
    private fun show(loggedIn: Boolean, fontScale: Float = 1f) {
        val (session, client) = uiSession(backend)
        runBlocking {
            session.restore()
            if (loggedIn) session.login("hiker@test.dev", "password-giusta-1").getOrThrow()
        }
        val controller = SosController(SosRepository({ client }, TEST_BASE_URL), FakeOutboxDao(), { session.currentUserId() }, scope)
        startKoin { modules(module { viewModel { SosViewModel(controller, session.state) } }) }
        compose.setContent { TestTheme(fontScale) { SosScreen(onBack = {}, onLogin = {}) } }
    }

    /** Ferma Koin e chiude lo scope del controller. */
    @After
    fun tearDown() {
        stopKoin()
        scope.cancel()
    }

    /**
     * Da ospite la schermata spiega che per l'SOS serve l'accesso, mostra "Chiama 112" e il pulsante di accesso, ma
     * non il pulsante SOS (l'allerta richiede un utente autenticato sul server).
     */
    @Test
    fun `da ospite c'è Chiama 112 e l'accesso ma non il pulsante SOS`() {
        show(loggedIn = false)
        compose.onNode(hasText("Per inviare un SOS", substring = true)).assertIsDisplayed()
        compose.onNode(call112).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Accedi")).assertExists()
        compose.onNode(holdButton).assertDoesNotExist()
    }

    /** Toccare "Chiama 112" apre il compositore del telefono con il numero già inserito, senza avviare la chiamata. */
    @Test
    fun `Chiama 112 apre il compositore con il numero`() {
        show(loggedIn = false)
        compose.onNode(call112).performClick()

        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:112", intent.data.toString())
    }

    /**
     * Da utente autenticato il pulsante SOS è visibile, ha il ruolo di pulsante ed espone l'azione di pressione lunga,
     * così chi usa un lettore di schermo può attivarlo senza tenere premuto il dito per 3 secondi.
     */
    @Test
    fun `da loggato il pulsante SOS si attiva con l'azione tieni premuto del lettore di schermo`() {
        show(loggedIn = true)
        compose.onNode(holdButton)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        compose.onNode(call112).assertExists()
    }

    /**
     * Con il testo al 200% il pulsante SOS, il campo delle note mediche e "Chiama 112" si raggiungono scorrendo, e
     * "Chiama 112" mantiene l'altezza minima di 72 dp prevista per le azioni d'emergenza.
     */
    @Test
    fun `con il testo al 200 percento SOS e Chiama 112 restano raggiungibili`() {
        show(loggedIn = true, fontScale = 2f)
        compose.onNode(holdButton).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Note mediche (facoltative)")).performScrollTo().assertIsDisplayed()
        compose.onNode(call112).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(72.dp)
    }
}
