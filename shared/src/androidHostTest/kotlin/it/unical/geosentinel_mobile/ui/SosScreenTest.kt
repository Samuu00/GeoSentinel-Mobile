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

/**
 * Schermata SOS (M9.1) e la sua accessibilità (M9.2): "Chiama 112" c'è sempre, il pulsante si attiva anche con l'azione
 * "tieni premuto" di TalkBack, e con il testo al 200% tutto resta raggiungibile.
 */
@RunWith(RobolectricTestRunner::class)
class SosScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val backend = FakeBackend()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val holdButton = hasContentDescription("Invia SOS. Tieni premuto 3 secondi")
    private val call112 = hasClickAction() and hasText("Chiama 112")

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

    @After
    fun tearDown() {
        stopKoin()
        scope.cancel()
    }

    @Test
    fun `da ospite c'è Chiama 112 e l'accesso ma non il pulsante SOS`() {
        show(loggedIn = false)
        compose.onNode(hasText("Per inviare un SOS", substring = true)).assertIsDisplayed()
        compose.onNode(call112).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Accedi")).assertExists()
        compose.onNode(holdButton).assertDoesNotExist()
    }

    @Test
    fun `Chiama 112 apre il compositore con il numero`() {
        show(loggedIn = false)
        compose.onNode(call112).performClick()

        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:112", intent.data.toString())
    }

    @Test
    fun `da loggato il pulsante SOS si attiva con l'azione tieni premuto del lettore di schermo`() {
        show(loggedIn = true)
        compose.onNode(holdButton)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        compose.onNode(call112).assertExists()
    }

    @Test
    fun `con il testo al 200 percento SOS e Chiama 112 restano raggiungibili`() {
        show(loggedIn = true, fontScale = 2f)
        compose.onNode(holdButton).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Note mediche (facoltative)")).performScrollTo().assertIsDisplayed()
        compose.onNode(call112).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(72.dp)
    }
}
