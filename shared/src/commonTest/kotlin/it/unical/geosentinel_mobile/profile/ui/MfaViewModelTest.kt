// Test dei ViewModel della verifica in due passaggi (2FA): attivazione con chiave TOTP e codice via email,
// disattivazione, rigenerazione dei codici di recupero e azzeramento della 2FA di un altro utente da parte di un ADMIN.
package it.unical.geosentinel_mobile.profile.ui

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_forbidden
import geosentinel_mobile.shared.generated.resources.error_mfa_code_wrong
import geosentinel_mobile.shared.generated.resources.error_mfa_setup_codes_wrong
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_not_found
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_self
import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.FakeSecureTokenStore
import it.unical.geosentinel_mobile.core.session.MFA_EMAIL_CODE
import it.unical.geosentinel_mobile.core.session.MFA_SECRET
import it.unical.geosentinel_mobile.core.session.MFA_SETUP_CODE
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.profile.data.MfaRepository
import it.unical.geosentinel_mobile.staff.data.StaffRepository
import it.unical.geosentinel_mobile.staff.ui.MfaResetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifica MfaViewModel e MfaResetViewModel contro il backend finto, che simula anche la 2FA. */
@OptIn(ExperimentalCoroutinesApi::class)
class MfaViewModelTest {

    /** Backend finto con in più un utente ADMIN, necessario per l'azzeramento della 2FA. */
    private val backend = FakeBackend().apply {
        users["admin@test.dev"] = "password-giusta-3"
        roles["admin@test.dev"] = "ADMIN"
    }
    /** Client HTTP della sessione corrente, condiviso dai repository creati nei test. */
    private lateinit var client: HttpClient

    /**
     * Imposta il dispatcher principale di test, crea una sessione e fa l'accesso con le credenziali indicate.
     * [mfaCode] serve agli account che hanno già la verifica in due passaggi attiva.
     */
    private suspend fun TestScope.loggedIn(
        email: String = "hiker@test.dev",
        password: String = "password-giusta-1",
        mfaCode: String? = null,
    ): SessionManager {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (session, http) = sessionWith(backend, FakeSecureTokenStore())
        client = http
        session.restore()
        assertTrue(session.login(email, password, mfaCode).isSuccess)
        return session
    }

    /** Crea il ViewModel della pagina 2FA del profilo per la sessione indicata. */
    private fun mfaViewModel(session: SessionManager) = MfaViewModel(MfaRepository({ client }, TEST_BASE_URL), session)

    /** Legge dal profilo della sessione se la 2FA risulta attiva. */
    private fun SessionManager.mfaEnabled() = (state.value as SessionState.LoggedIn).profile?.mfaEnabled

    /** Ripristina il dispatcher principale dopo ogni test. */
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * L'attivazione passa per tre fasi: la chiave da inserire nell'app di autenticazione, poi il codice ricevuto per
     * email insieme al primo codice TOTP, infine i codici di recupero, mostrati una sola volta e cancellati dallo stato
     * quando l'utente conferma di averli salvati.
     */
    @Test
    fun `attivazione in tre passi - chiave poi codice email e primo codice poi codici di recupero una volta sola`() = runTest {
        val session = loggedIn()
        val viewModel = mfaViewModel(session)
        assertFalse(viewModel.state.value.enabled)

        viewModel.startSetup()
        advanceUntilIdle()
        assertEquals(MfaStep.SETUP, viewModel.state.value.step)
        assertEquals(MFA_SECRET, viewModel.state.value.secret)

        // Senza il codice dell'email l'invio resta disabilitato.
        viewModel.onCodeChange(MFA_SETUP_CODE)
        assertFalse(viewModel.state.value.canSubmitCode)

        viewModel.onEmailCodeChange("ZZZZZZZZ-ZZZZZZZZ")
        viewModel.submitCode()
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.error_mfa_setup_codes_wrong), viewModel.state.value.codeError)

        // Il codice scritto come farebbe l'utente, in minuscolo e senza trattino, deve essere accettato.
        viewModel.onEmailCodeChange(MFA_EMAIL_CODE.lowercase().replace("-", ""))
        viewModel.submitCode()
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(MfaStep.CODES, state.step)
        assertEquals(10, state.recoveryCodes.size)
        // Dopo l'attivazione la chiave non serve più e non deve restare in memoria.
        assertNull(state.secret)
        assertEquals("", state.emailCode)
        assertTrue(state.enabled)
        assertEquals(true, session.mfaEnabled())

        // Dopo "Li ho salvati" e il ritorno alla panoramica i codici spariscono dallo stato.
        viewModel.onSavedChange(true)
        viewModel.back()
        assertTrue(viewModel.state.value.recoveryCodes.isEmpty())
        assertEquals(MfaStep.OVERVIEW, viewModel.state.value.step)
    }

    /** Il toString dello stato non contiene la chiave, l'URI otpauth né i codici inseriti, così non finiscono nei log. */
    @Test
    fun `segreti e codici non finiscono nel toString`() = runTest {
        val viewModel = mfaViewModel(loggedIn())
        viewModel.startSetup()
        advanceUntilIdle()
        viewModel.onCodeChange(MFA_SETUP_CODE)
        viewModel.onEmailCodeChange(MFA_EMAIL_CODE)
        val printed = viewModel.state.value.toString()
        assertFalse(MFA_SECRET in printed)
        assertFalse("otpauth" in printed)
        assertFalse(MFA_SETUP_CODE in printed)
        assertFalse(MFA_EMAIL_CODE in printed)
    }

    /**
     * Per disattivare la 2FA un codice sbagliato mostra l'errore sul campo e lascia la 2FA attiva; un codice di
     * recupero valido la disattiva e aggiorna il profilo della sessione.
     */
    @Test
    fun `disattivazione - codice sbagliato sul campo poi con un codice di recupero`() = runTest {
        backend.mfaTotp["hiker@test.dev"] = "777888"
        backend.mfaRecovery["hiker@test.dev"] = mutableSetOf("ABCD-EFGH")
        val session = loggedIn(mfaCode = "777888")
        val viewModel = mfaViewModel(session)
        assertTrue(viewModel.state.value.enabled)

        viewModel.openDisable()
        viewModel.onCodeChange("sbagliato")
        viewModel.submitCode()
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.error_mfa_code_wrong), viewModel.state.value.codeError)
        assertTrue(viewModel.state.value.enabled)

        viewModel.onCodeChange("ABCD-EFGH")
        viewModel.submitCode()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.enabled)
        assertEquals(MfaStep.OVERVIEW, viewModel.state.value.step)
        assertEquals(false, session.mfaEnabled())
    }

    /** Con un codice TOTP valido si ottengono dieci nuovi codici di recupero. */
    @Test
    fun `nuovi codici di recupero con il TOTP`() = runTest {
        backend.mfaTotp["hiker@test.dev"] = "777888"
        val viewModel = mfaViewModel(loggedIn(mfaCode = "777888"))

        viewModel.openRegenerate()
        viewModel.onCodeChange("777888")
        viewModel.submitCode()
        advanceUntilIdle()
        assertEquals(MfaStep.CODES, viewModel.state.value.step)
        assertEquals(10, viewModel.state.value.recoveryCodes.size)
    }


    /** Crea il ViewModel con cui un ADMIN azzera la 2FA di un altro utente. */
    private fun resetViewModel() = MfaResetViewModel(StaffRepository({ client }, TEST_BASE_URL))

    /**
     * Un ADMIN che azzera la 2FA riceve un messaggio dedicato se l'utente non ha la 2FA (404) o se prova ad azzerare
     * la propria (422); per un utente con la 2FA attiva l'azzeramento riesce e il server la rimuove.
     */
    @Test
    fun `azzeramento da ADMIN - fatto e 404 senza 2FA e 422 sulla propria`() = runTest {
        backend.mfaTotp["hiker@test.dev"] = "777888"
        backend.mfaTotp["admin@test.dev"] = "999000"
        loggedIn("admin@test.dev", "password-giusta-3", mfaCode = "999000")

        val viewModel = resetViewModel()
        viewModel.onEmailChange("rescuer@test.dev")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.staff_mfa_reset_not_found), viewModel.state.value.error)

        viewModel.onEmailChange("admin@test.dev")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.staff_mfa_reset_self), viewModel.state.value.error)

        viewModel.onEmailChange("hiker@test.dev")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.done)
        assertFalse("hiker@test.dev" in backend.mfaTotp)
    }

    /** Se chi azzera non è ADMIN il server risponde 403 e il ViewModel mostra il messaggio di permesso negato. */
    @Test
    fun `azzeramento da un non ADMIN - 403 gestito`() = runTest {
        loggedIn()
        val viewModel = resetViewModel()
        viewModel.onEmailChange("rescuer@test.dev")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.error_forbidden), viewModel.state.value.error)
        assertFalse(viewModel.state.value.done)
    }
}
