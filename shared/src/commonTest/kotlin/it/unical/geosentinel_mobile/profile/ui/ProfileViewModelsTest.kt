package it.unical.geosentinel_mobile.profile.ui

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_account_managed
import geosentinel_mobile.shared.generated.resources.error_current_password_wrong
import geosentinel_mobile.shared.generated.resources.error_field_current_password
import geosentinel_mobile.shared.generated.resources.error_field_display_name
import geosentinel_mobile.shared.generated.resources.error_field_password_short
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.FakeSecureTokenStore
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.session.sessionWith
import it.unical.geosentinel_mobile.core.ui.UiMessage
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelsTest {

    private val backend = FakeBackend()

    // viewModelScope usa Dispatchers.Main: lo si sostituisce con quello del test (tempo virtuale)
    private suspend fun TestScope.loggedIn(email: String = "hiker@test.dev", password: String = "password-giusta-1"): SessionManager {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val session = sessionWith(backend, FakeSecureTokenStore()).first
        session.restore()
        assertTrue(session.login(email, password).isSuccess)
        return session
    }

    private fun requests(method: String) = backend.requests.count { it.startsWith("$method /api/v1/users/me") }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // ---- nome -----------------------------------------------------------------------------------------------

    @Test
    fun `il nome parte da quello attuale e se non cambia non si salva`() = runTest {
        val viewModel = EditNameViewModel(loggedIn())

        assertEquals("Nome", viewModel.state.value.displayName)
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.onDisplayNameChange(" Nome ")
        assertFalse(viewModel.state.value.canSubmit, "solo spazi in più non è un nome nuovo")
    }

    @Test
    fun `nome troppo corto - errore sul campo e nessuna richiesta`() = runTest {
        val viewModel = EditNameViewModel(loggedIn())

        viewModel.onDisplayNameChange(" A ")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(UiMessage(Res.string.error_field_display_name), viewModel.state.value.fieldError)
        assertEquals(0, requests("PATCH"))
    }

    @Test
    fun `nome salvato - stato saved e profilo della sessione aggiornato`() = runTest {
        val session = loggedIn()
        val viewModel = EditNameViewModel(session)

        viewModel.onDisplayNameChange("Anna")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.saved)
        assertEquals("Anna", assertIs<SessionState.LoggedIn>(session.state.value).profile?.displayName)
    }

    // ---- password -------------------------------------------------------------------------------------------

    @Test
    fun `controlli locali - password attuale vuota e nuova corta senza richiesta`() = runTest {
        val viewModel = ChangePasswordViewModel(loggedIn())

        viewModel.onNewChange("corta")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(UiMessage(Res.string.error_field_current_password), viewModel.state.value.fieldErrors[PasswordField.CURRENT])
        assertEquals(UiMessage(Res.string.error_field_password_short), viewModel.state.value.fieldErrors[PasswordField.NEW])
        assertEquals(0, backend.requests.count { it.startsWith("POST /api/v1/users/me/password") })
    }

    @Test
    fun `password attuale sbagliata - errore sul campo della password attuale`() = runTest {
        val viewModel = ChangePasswordViewModel(loggedIn())

        viewModel.onCurrentChange("password-sbagliata")
        viewModel.onNewChange("nuova-password-123")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(UiMessage(Res.string.error_current_password_wrong), viewModel.state.value.fieldErrors[PasswordField.CURRENT])
        assertFalse(viewModel.state.value.changed)
        assertFalse(viewModel.state.value.submitting)
    }

    @Test
    fun `password cambiata - stato changed e password tolte dallo stato`() = runTest {
        val viewModel = ChangePasswordViewModel(loggedIn())

        viewModel.onCurrentChange("password-giusta-1")
        viewModel.onNewChange("nuova-password-123")
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.changed)
        assertEquals("", state.currentPassword)
        assertEquals("", state.newPassword)
        assertEquals("nuova-password-123", backend.users["hiker@test.dev"])
    }

    // ---- cancellazione --------------------------------------------------------------------------------------

    @Test
    fun `cancellazione riuscita - si torna ospiti`() = runTest {
        val session = loggedIn()
        val viewModel = ProfileViewModel(session)

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(SessionState.Anonymous, session.state.value)
        assertFalse(viewModel.state.value.deleting)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `cancellazione del personale - messaggio account-managed e sessione intatta`() = runTest {
        val session = loggedIn("rescuer@test.dev", "password-giusta-2")
        val viewModel = ProfileViewModel(session)

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(UiMessage(Res.string.error_account_managed), viewModel.state.value.error)
        assertIs<SessionState.LoggedIn>(session.state.value)
    }
}
