package it.unical.geosentinel_mobile.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import it.unical.geosentinel_mobile.auth.ui.LoginScreen
import it.unical.geosentinel_mobile.auth.ui.LoginViewModel
import it.unical.geosentinel_mobile.core.session.FakeBackend
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Accesso (M9.1): il form vero con il ViewModel vero, il server finto
@RunWith(RobolectricTestRunner::class)
class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val backend = FakeBackend()
    private lateinit var session: SessionManager
    private var loggedIn = false

    @Before
    fun setUp() {
        session = uiSession(backend).first
        runBlocking { session.restore() }
        startKoin { modules(module { viewModel { LoginViewModel(session) } }) }
    }

    @After
    fun tearDown() = stopKoin()

    private fun show() = compose.setContent {
        TestTheme { LoginScreen(onBack = {}, onRegister = {}, onLoggedIn = { loggedIn = true }) }
    }

    private fun email() = compose.onNode(hasSetTextAction() and hasText("Email"))
    private fun password() = compose.onNode(hasSetTextAction() and hasText("Password"))
    private fun submit() = compose.onNode(hasClickAction() and hasText("Accedi"))

    @Test
    fun `accesso riuscito porta avanti`() {
        show()
        submit().assertIsNotEnabled() // campi vuoti
        email().performTextInput("hiker@test.dev")
        password().performTextInput("password-giusta-1")
        submit().assertIsEnabled().performClick()

        compose.waitFor { loggedIn }
        assertTrue(backend.requests.any { it.startsWith("POST /api/v1/auth/login") })
    }

    @Test
    fun `password sbagliata mostra l'errore e resta sul form`() {
        show()
        email().performTextInput("hiker@test.dev")
        password().performTextInput("sbagliata-123")
        submit().performClick()

        compose.waitForText("Email o password non corretti.")
        assertFalse(loggedIn)
    }
}
