package it.unical.geosentinel_mobile.auth.ui

import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_field_birth_date
import geosentinel_mobile.shared.generated.resources.error_field_email
import geosentinel_mobile.shared.generated.resources.error_field_password_long
import geosentinel_mobile.shared.generated.resources.error_field_password_short
import geosentinel_mobile.shared.generated.resources.error_login_failed
import geosentinel_mobile.shared.generated.resources.error_rate_limited_seconds
import geosentinel_mobile.shared.generated.resources.error_register_failed
import io.ktor.http.HttpStatusCode
import it.unical.geosentinel_mobile.core.session.FakeBackend
import it.unical.geosentinel_mobile.core.session.FakeSecureTokenStore
import it.unical.geosentinel_mobile.core.session.SessionManager
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
    fun `accesso riuscito, la password non resta nello stato`() = runTest {
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
    fun `password sbagliata mostra credenziali errate, non sessione scaduta`() = runTest {
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
}
