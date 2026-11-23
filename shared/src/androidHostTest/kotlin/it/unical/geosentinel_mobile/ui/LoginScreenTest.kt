// Test di UI della schermata di accesso, sulla JVM con Robolectric: usa il form e il ViewModel veri, collegati al
// backend finto dei test comuni.
package it.unical.geosentinel_mobile.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import it.unical.geosentinel_mobile.auth.ui.LoginScreen
import it.unical.geosentinel_mobile.auth.ui.LoginViewModel
import it.unical.geosentinel_mobile.core.config.AppConfig
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifica il comportamento del form di accesso nei casi di successo, errore e password dimenticata. */
@RunWith(RobolectricTestRunner::class)
class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    /** Backend finto che risponde alle richieste HTTP del client. */
    private val backend = FakeBackend()
    private lateinit var session: SessionManager
    /** Diventa vero quando la schermata segnala che l'accesso è riuscito. */
    private var loggedIn = false
    /** Email passata alla schermata di recupero della password, se l'utente ci è andato. */
    private var forgotFor: String? = null

    /**
     * Crea la sessione sul backend finto, la ripristina (all'inizio l'utente è ospite) e avvia Koin con il ViewModel
     * di accesso. La configurazione non contiene il client ID di Google, quindi il pulsante "Continua con Google" non
     * viene mostrato.
     */
    @Before
    fun setUp() {
        session = uiSession(backend).first
        runBlocking { session.restore() }
        startKoin { modules(module { viewModel { LoginViewModel(session) }; single { AppConfig(TEST_BASE_URL, isDebug = false) } }) }
    }

    /** Ferma Koin, così il test successivo può avviarlo di nuovo. */
    @After
    fun tearDown() = stopKoin()

    /** Mostra la schermata di accesso registrando nelle proprietà del test le navigazioni richieste. */
    private fun show() = compose.setContent {
        TestTheme { LoginScreen(onBack = {}, onRegister = {}, onForgotPassword = { forgotFor = it }, onLoggedIn = { loggedIn = true }) }
    }

    /** Il campo di testo dell'email. */
    private fun email() = compose.onNode(hasSetTextAction() and hasText("Email"))
    /** Il campo di testo della password. */
    private fun password() = compose.onNode(hasSetTextAction() and hasText("Password"))
    /** Il pulsante "Accedi". */
    private fun submit() = compose.onNode(hasClickAction() and hasText("Accedi"))

    /**
     * Con i campi vuoti il pulsante è disabilitato; con credenziali corrette l'invio chiama il login sul server e la
     * schermata segnala l'accesso riuscito.
     */
    @Test
    fun `accesso riuscito porta avanti`() {
        show()
        submit().assertIsNotEnabled()
        email().performTextInput("hiker@test.dev")
        password().performTextInput("password-giusta-1")
        submit().assertIsEnabled().performClick()

        compose.waitFor { loggedIn }
        assertTrue(backend.requests.any { it.startsWith("POST /api/v1/auth/login") })
    }

    /** Con una password errata compare il messaggio d'errore e l'utente resta sul form. */
    @Test
    fun `password sbagliata mostra l'errore e resta sul form`() {
        show()
        email().performTextInput("hiker@test.dev")
        password().performTextInput("sbagliata-123")
        submit().performClick()

        compose.waitForText("Email o password non corretti.")
        assertFalse(loggedIn)
    }

    /** Il link "Password dimenticata?" porta al recupero passando l'email già scritta, senza gli spazi ai lati. */
    @Test
    fun `password dimenticata porta al reset con l'email gia scritta`() {
        show()
        email().performTextInput(" hiker@test.dev ")
        compose.onNode(hasClickAction() and hasText("Password dimenticata?")).performScrollTo().performClick()

        compose.waitFor { forgotFor != null }
        assertEquals("hiker@test.dev", forgotFor)
    }
}
