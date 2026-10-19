package it.unical.geosentinel_mobile.profile.ui

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_forbidden
import geosentinel_mobile.shared.generated.resources.error_mfa_code_wrong
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_not_found
import geosentinel_mobile.shared.generated.resources.staff_mfa_reset_self
import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.FakeSecureTokenStore
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

@OptIn(ExperimentalCoroutinesApi::class)
class MfaViewModelTest {

    private val backend = FakeBackend().apply {
        users["admin@test.dev"] = "password-giusta-3"
        roles["admin@test.dev"] = "ADMIN"
    }
    private lateinit var client: HttpClient

    // mfaCode: per gli account che hanno già la verifica attiva
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

    private fun mfaViewModel(session: SessionManager) = MfaViewModel(MfaRepository({ client }, TEST_BASE_URL), session)

    private fun SessionManager.mfaEnabled() = (state.value as SessionState.LoggedIn).profile?.mfaEnabled

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `attivazione in tre passi - chiave, primo codice, codici di recupero una volta sola`() = runTest {
        val session = loggedIn()
        val viewModel = mfaViewModel(session)
        assertFalse(viewModel.state.value.enabled)

        viewModel.startSetup()
        advanceUntilIdle()
        assertEquals(MfaStep.SETUP, viewModel.state.value.step)
        assertEquals(MFA_SECRET, viewModel.state.value.secret)

        viewModel.onCodeChange("000000")
        viewModel.submitCode()
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.error_mfa_code_wrong), viewModel.state.value.codeError)

        viewModel.onCodeChange(MFA_SETUP_CODE)
        viewModel.submitCode()
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(MfaStep.CODES, state.step)
        assertEquals(10, state.recoveryCodes.size)
        assertNull(state.secret) // la chiave non serve più e non resta in memoria
        assertTrue(state.enabled)
        assertEquals(true, session.mfaEnabled())

        // Fine (dopo "Li ho salvati"): i codici spariscono dallo stato
        viewModel.onSavedChange(true)
        viewModel.back()
        assertTrue(viewModel.state.value.recoveryCodes.isEmpty())
        assertEquals(MfaStep.OVERVIEW, viewModel.state.value.step)
    }

    @Test
    fun `segreti e codici non finiscono nel toString`() = runTest {
        val viewModel = mfaViewModel(loggedIn())
        viewModel.startSetup()
        advanceUntilIdle()
        viewModel.onCodeChange(MFA_SETUP_CODE)
        val printed = viewModel.state.value.toString()
        assertFalse(MFA_SECRET in printed)
        assertFalse("otpauth" in printed)
        assertFalse(MFA_SETUP_CODE in printed)
    }

    @Test
    fun `disattivazione - codice sbagliato sul campo, poi con un codice di recupero`() = runTest {
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

    // ---- azzeramento da ADMIN (M10.3) -----------------------------------------------------------------------

    private fun resetViewModel() = MfaResetViewModel(StaffRepository({ client }, TEST_BASE_URL))

    @Test
    fun `azzeramento da ADMIN - fatto, 404 senza 2FA, 422 sulla propria`() = runTest {
        backend.mfaTotp["hiker@test.dev"] = "777888"
        backend.mfaTotp["admin@test.dev"] = "999000"
        loggedIn("admin@test.dev", "password-giusta-3", mfaCode = "999000")

        val viewModel = resetViewModel()
        viewModel.onEmailChange("rescuer@test.dev") // senza 2FA
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
