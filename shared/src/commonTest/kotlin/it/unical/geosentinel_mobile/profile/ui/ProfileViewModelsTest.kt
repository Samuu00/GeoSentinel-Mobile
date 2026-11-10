package it.unical.geosentinel_mobile.profile.ui

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_account_managed
import geosentinel_mobile.shared.generated.resources.error_current_password_wrong
import geosentinel_mobile.shared.generated.resources.error_field_current_password
import geosentinel_mobile.shared.generated.resources.error_field_display_name
import geosentinel_mobile.shared.generated.resources.error_field_mfa_code
import geosentinel_mobile.shared.generated.resources.error_mfa_code_wrong
import geosentinel_mobile.shared.generated.resources.error_field_password_short
import geosentinel_mobile.shared.generated.resources.report_photo_unreadable
import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.core.session.TEST_BASE_URL
import it.unical.geosentinel_mobile.media.data.FakeMedia
import it.unical.geosentinel_mobile.media.data.MediaRepository
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
    private val media = FakeMedia(backend)
    private lateinit var client: HttpClient

    // viewModelScope usa Dispatchers.Main: lo si sostituisce con quello del test (tempo virtuale)
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

    private fun TestScope.profileViewModel(session: SessionManager) =
        ProfileViewModel(session, with(media) { uploader(client) }, MediaRepository({ client }, TEST_BASE_URL))

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

    // ADR-0026 del backend: con la verifica in due passaggi attiva il cambio password chiede anche il codice
    @Test
    fun `con la 2FA attiva serve il codice - assente sul campo poi sbagliato poi giusto`() = runTest {
        backend.mfaTotp["hiker@test.dev"] = "777888"
        val session = loggedIn(mfaCode = "777888")
        backend.mfaTotp["hiker@test.dev"] = "111222" // il codice del login è consumato: ne vale uno nuovo
        val viewModel = ChangePasswordViewModel(session)
        assertTrue(viewModel.state.value.mfaRequired)

        viewModel.onCurrentChange("password-giusta-1")
        viewModel.onNewChange("nuova-password-123")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.error_field_mfa_code), viewModel.state.value.fieldErrors[PasswordField.MFA])
        assertEquals(0, backend.requests.count { it.startsWith("POST /api/v1/users/me/password") })

        viewModel.onMfaCodeChange("000000")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.error_mfa_code_wrong), viewModel.state.value.fieldErrors[PasswordField.MFA])
        assertFalse(viewModel.state.value.changed)

        viewModel.onMfaCodeChange("111222")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.changed)
        assertEquals("", viewModel.state.value.mfaCode)
        assertEquals("nuova-password-123", backend.users["hiker@test.dev"])
    }

    // ---- cancellazione --------------------------------------------------------------------------------------

    @Test
    fun `cancellazione riuscita - si torna ospiti`() = runTest {
        val session = loggedIn()
        val viewModel = profileViewModel(session)

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(SessionState.Anonymous, session.state.value)
        assertFalse(viewModel.state.value.deleting)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `cancellazione del personale - messaggio account-managed e sessione intatta`() = runTest {
        val session = loggedIn("rescuer@test.dev", "password-giusta-2")
        val viewModel = profileViewModel(session)

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(UiMessage(Res.string.error_account_managed), viewModel.state.value.error)
        assertIs<SessionState.LoggedIn>(session.state.value)
    }

    // ---- foto profilo (M11) ---------------------------------------------------------------------------------

    @Test
    fun `foto profilo - upload con target USER e l'id dell'utente poi profilo riletto`() = runTest {
        val session = loggedIn()
        val viewModel = profileViewModel(session)
        val profileReads = requests("GET")

        viewModel.uploadAvatar { byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()) }
        assertTrue(viewModel.state.value.avatarBusy)
        backend.avatars["hiker@test.dev"] = "m1" // il server la collega al confirm
        advanceUntilIdle()

        assertTrue(media.uploadRequests.single().contains("\"entityType\":\"USER\""))
        assertTrue(media.uploadRequests.single().contains("\"entityId\":\"id-hiker@test.dev\""))
        assertEquals(1, media.confirms)
        assertEquals(profileReads + 1, requests("GET"))
        assertEquals("m1", (session.state.value as SessionState.LoggedIn).profile?.avatarMediaId)
        assertFalse(viewModel.state.value.avatarBusy)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `foto profilo - rimozione con DELETE del media corrente e profilo riletto`() = runTest {
        backend.avatars["hiker@test.dev"] = "m7"
        val session = loggedIn()
        val viewModel = profileViewModel(session)

        viewModel.removeAvatar()
        backend.avatars.remove("hiker@test.dev")
        advanceUntilIdle()

        assertEquals(listOf("m7"), media.deleted)
        assertNull((session.state.value as SessionState.LoggedIn).profile?.avatarMediaId)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `foto profilo illeggibile - messaggio e nessun upload`() = runTest {
        val viewModel = profileViewModel(loggedIn())

        viewModel.uploadAvatar { error("formato non supportato") }
        advanceUntilIdle()

        assertEquals(UiMessage(Res.string.report_photo_unreadable), viewModel.state.value.error)
        assertTrue(media.uploadRequests.isEmpty())
        assertFalse(viewModel.state.value.avatarBusy)
    }
}
