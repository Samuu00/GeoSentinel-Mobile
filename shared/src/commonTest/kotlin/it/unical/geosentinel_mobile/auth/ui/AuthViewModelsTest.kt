package it.unical.geosentinel_mobile.auth.ui

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_field_birth_date
import geosentinel_mobile.shared.generated.resources.error_field_email
import geosentinel_mobile.shared.generated.resources.error_field_password_long
import geosentinel_mobile.shared.generated.resources.error_field_password_short
import geosentinel_mobile.shared.generated.resources.error_login_failed
import geosentinel_mobile.shared.generated.resources.error_mfa_code_wrong
import geosentinel_mobile.shared.generated.resources.error_invalid_reset_code
import geosentinel_mobile.shared.generated.resources.error_google_staff
import geosentinel_mobile.shared.generated.resources.error_rate_limited_seconds
import geosentinel_mobile.shared.generated.resources.error_register_failed
import io.ktor.http.HttpStatusCode
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.FakeSecureTokenStore
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.session.UserRole
import it.unical.geosentinel_mobile.core.session.role
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
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {

    private val backend = FakeBackend()

    // viewModelScope usa Dispatchers.Main: lo si sostituisce con quello del test (tempo virtuale)
    private fun TestScope.session(): SessionManager {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return sessionWith(backend, FakeSecureTokenStore()).first
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // ---- accesso --------------------------------------------------------------------------------------------

    @Test
    fun `accesso riuscito - la password non resta nello stato`() = runTest {
        val session = session()
        session.restore()
        val viewModel = LoginViewModel(session)

        viewModel.onEmailChange("rescuer@test.dev")
        viewModel.onPasswordChange("password-giusta-2")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loggedIn)
        assertEquals("", viewModel.state.value.password)
        assertEquals(UserRole.RESCUE_OPERATOR, session.state.value.role)
    }

    @Test
    fun `password sbagliata mostra credenziali errate - non sessione scaduta`() = runTest {
        val viewModel = LoginViewModel(session())

        viewModel.onEmailChange("hiker@test.dev")
        viewModel.onPasswordChange("sbagliata")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(UiMessage(Res.string.error_login_failed), viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.submitting)
    }

    @Test
    fun `troppi tentativi mostra il tempo d'attesa`() = runTest {
        backend.loginStatus = HttpStatusCode.TooManyRequests // Retry-After 60: oltre il retry automatico
        val viewModel = LoginViewModel(session())

        viewModel.onEmailChange("hiker@test.dev")
        viewModel.onPasswordChange("qualsiasi")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(UiMessage(Res.string.error_rate_limited_seconds, 60L), viewModel.state.value.error)
    }

    @Test
    fun `senza email o password non parte nessuna richiesta`() = runTest {
        val viewModel = LoginViewModel(session())

        viewModel.onEmailChange("hiker@test.dev")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(backend.requests.isEmpty())
    }

    // ---- registrazione --------------------------------------------------------------------------------------

    private fun RegisterViewModel.fill(password: String = "password-lunga-123", birthDate: LocalDate? = LocalDate(1990, 5, 1)) {
        onDisplayNameChange("Nuovo Escursionista")
        onEmailChange("nuovo@test.dev")
        onPasswordChange(password)
        birthDate?.let(::onBirthDateChange)
    }

    @Test
    fun `registrazione riuscita apre la sessione`() = runTest {
        val session = session()
        session.restore()
        val viewModel = RegisterViewModel(session)

        viewModel.fill()
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.registered)
        assertEquals(UserRole.HIKER, session.state.value.role)
    }

    @Test
    fun `i controlli locali fermano il form senza chiamare il server`() = runTest {
        val viewModel = RegisterViewModel(session())

        viewModel.fill(password = "corta", birthDate = null)
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(UiMessage(Res.string.error_field_password_short), viewModel.state.value.fieldErrors[RegisterField.PASSWORD])
        assertEquals(UiMessage(Res.string.error_field_birth_date), viewModel.state.value.fieldErrors[RegisterField.BIRTH_DATE])
        assertTrue(backend.requests.isEmpty())
    }

    @Test
    fun `una password oltre i 72 byte si blocca in locale`() = runTest {
        val viewModel = RegisterViewModel(session())

        viewModel.fill(password = "è".repeat(40))
        viewModel.submit()

        assertEquals(UiMessage(Res.string.error_field_password_long), viewModel.state.value.fieldErrors[RegisterField.PASSWORD])
    }

    @Test
    fun `gli errori per campo del server arrivano al form`() = runTest {
        backend.registerProblem = HttpStatusCode.BadRequest to
            """{"type":"https://geosentinel.dev/problems/validation-error","title":"Bad Request","status":400,
               "errors":[{"field":"email","message":"Email format is invalid"}]}"""
        val viewModel = RegisterViewModel(session())

        viewModel.fill()
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(mapOf(RegisterField.EMAIL to UiMessage(Res.string.error_field_email)), viewModel.state.value.fieldErrors)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `email già registrata mostra il messaggio della registrazione`() = runTest {
        backend.registerProblem = HttpStatusCode.UnprocessableEntity to
            """{"type":"https://geosentinel.dev/problems/business-rule","title":"x","status":422}"""
        val viewModel = RegisterViewModel(session())

        viewModel.fill()
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(UiMessage(Res.string.error_register_failed), viewModel.state.value.error)
    }

    @Test
    fun `modificare un campo toglie il suo errore`() = runTest {
        val viewModel = RegisterViewModel(session())
        viewModel.fill(password = "corta")
        viewModel.submit()

        viewModel.onPasswordChange("password-lunga-123")

        assertNull(viewModel.state.value.fieldErrors[RegisterField.PASSWORD])
    }

    // ---- Google e password dimenticata ----------------------------------------------------------------------

    @Test
    fun `primo accesso con Google - chiede la data e poi entra con lo stesso token`() = runTest {
        val session = session()
        session.restore()
        val viewModel = LoginViewModel(session)

        viewModel.onGoogleStarted()
        viewModel.onGoogleResult(GoogleSignInResult.Token("google:nuovo@gmail.com"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.askBirthDate)
        assertEquals(false, viewModel.state.value.submitting)

        viewModel.onGoogleBirthDate(LocalDate(1990, 5, 1))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loggedIn)
        assertEquals(UserRole.HIKER, session.state.value.role)
    }

    @Test
    fun `Google annullato non mostra errori e account del personale ha il suo messaggio`() = runTest {
        val session = session()
        session.restore()
        val viewModel = LoginViewModel(session)

        viewModel.onGoogleStarted()
        viewModel.onGoogleResult(GoogleSignInResult.Cancelled)
        assertNull(viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.submitting)

        viewModel.onGoogleResult(GoogleSignInResult.Token("google:rescuer@test.dev"))
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.error_google_staff), viewModel.state.value.error)
    }

    @Test
    fun `password dimenticata - codice sbagliato sul campo poi fatto`() = runTest {
        val session = session()
        session.restore()
        val viewModel = PasswordResetViewModel(session, "hiker@test.dev")

        viewModel.requestCode()
        advanceUntilIdle()
        assertEquals(ResetStep.CODE, viewModel.state.value.step)

        viewModel.onCodeChange("sbagliato")
        viewModel.onPasswordChange("nuova-password-lunga")
        viewModel.confirm()
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.error_invalid_reset_code), viewModel.state.value.fieldErrors[ResetField.CODE])

        viewModel.onCodeChange("ABCD1234EFGH5678")
        viewModel.confirm()
        advanceUntilIdle()
        assertEquals(ResetStep.DONE, viewModel.state.value.step)
        assertEquals("", viewModel.state.value.newPassword)
    }

    @Test
    fun `password dimenticata - controlli locali senza richieste`() = runTest {
        val session = session()
        val viewModel = PasswordResetViewModel(session, "")

        viewModel.requestCode()
        assertEquals(UiMessage(Res.string.error_field_email), viewModel.state.value.fieldErrors[ResetField.EMAIL])
        assertTrue(backend.requests.isEmpty())
    }

    // ---- verifica in due passaggi (M10.1) -------------------------------------------------------------------

    private fun TestScope.loginWithMfa(session: SessionManager): LoginViewModel {
        backend.mfaTotp["hiker@test.dev"] = "654321"
        val viewModel = LoginViewModel(session)
        viewModel.onEmailChange("hiker@test.dev")
        viewModel.onPasswordChange("password-giusta-1")
        viewModel.submit()
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `2FA - mfa-required apre il passo del codice e la seconda richiesta lo manda`() = runTest {
        val session = session()
        session.restore()
        val viewModel = loginWithMfa(session)

        assertTrue(viewModel.state.value.mfaStep)
        assertEquals("", viewModel.state.value.password) // la password resta solo nel ViewModel
        assertNull(viewModel.state.value.error)
        assertEquals(SessionState.Anonymous, session.state.value)

        viewModel.onMfaCodeChange("65a43 21") // solo cifre, al massimo 6
        assertEquals("654321", viewModel.state.value.mfaCode)
        viewModel.submitCode()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loggedIn)
        assertEquals(UserRole.HIKER, session.state.value.role)
        // nessun refresh partito dal 401 mfa-required: login e google sono fuori dal bearer
        assertTrue(backend.requests.none { it.startsWith("POST /api/v1/auth/refresh") })
    }

    @Test
    fun `2FA - codice sbagliato sul campo e stesso codice due volte rifiutato`() = runTest {
        val session = session()
        session.restore()
        val viewModel = loginWithMfa(session)

        viewModel.onMfaCodeChange("000000")
        viewModel.submitCode()
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.error_mfa_code_wrong), viewModel.state.value.mfaCodeError)
        assertTrue(viewModel.state.value.mfaStep)

        // usato una volta da un altro login: non vale più (replay)
        assertTrue(session.login("hiker@test.dev", "password-giusta-1", "654321").isSuccess)
        session.logout()
        viewModel.onMfaCodeChange("654321")
        viewModel.submitCode()
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.error_mfa_code_wrong), viewModel.state.value.mfaCodeError)
        assertFalse(viewModel.state.value.loggedIn)
    }

    @Test
    fun `2FA - codice di recupero e Indietro che dimentica la password`() = runTest {
        val session = session()
        session.restore()
        val viewModel = loginWithMfa(session)
        backend.mfaRecovery["hiker@test.dev"] = mutableSetOf("ABCD-EFGH")

        viewModel.onToggleRecoveryCode()
        viewModel.onMfaCodeChange("ABCD-EFGH")
        assertEquals("ABCD-EFGH", viewModel.state.value.mfaCode) // testo libero
        viewModel.submitCode()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loggedIn)

        // Indietro dal passo del codice: si torna alla password e la richiesta non parte più
        session.logout()
        val again = loginWithMfa(session)
        again.onMfaCancelled()
        assertFalse(again.state.value.mfaStep)
        val sent = backend.requests.size
        again.onMfaCodeChange("654321")
        again.submitCode()
        advanceUntilIdle()
        assertEquals(sent, backend.requests.size)
    }

    @Test
    fun `2FA - con Google lo stesso ID token e il codice`() = runTest {
        backend.mfaTotp["hiker@test.dev"] = "111222"
        val viewModel = LoginViewModel(session())

        viewModel.onGoogleResult(GoogleSignInResult.Token("google:hiker@test.dev"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.mfaStep)

        viewModel.onMfaCodeChange("111222")
        viewModel.submitCode()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loggedIn)
        assertEquals(2, backend.requests.count { it.startsWith("POST /api/v1/auth/google") })
    }

    @Test
    fun `2FA - password e codice non finiscono nel toString dello stato`() = runTest {
        val session = session()
        session.restore()
        val viewModel = loginWithMfa(session)
        viewModel.onMfaCodeChange("654321")
        val printed = viewModel.state.value.toString()
        assertFalse("654321" in printed)
        assertFalse("password-giusta-1" in printed)
        assertFalse("hiker@test.dev" in printed)
    }
}
