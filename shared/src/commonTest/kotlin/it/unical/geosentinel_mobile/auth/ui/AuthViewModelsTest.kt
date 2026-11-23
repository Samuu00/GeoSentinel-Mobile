// Test dei ViewModel di autenticazione: accesso con email e password, registrazione, accesso con Google, recupero
// della password con codice via email e passo del codice per gli account con la verifica in due passaggi.
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

/** Verifica LoginViewModel, RegisterViewModel e PasswordResetViewModel contro il backend finto. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelsTest {

    private val backend = FakeBackend()

    /**
     * Crea una sessione sul backend finto. viewModelScope usa Dispatchers.Main, che qui viene sostituito con il
     * dispatcher del test, così tutto gira nel tempo virtuale.
     */
    private fun TestScope.session(): SessionManager {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return sessionWith(backend, FakeSecureTokenStore()).first
    }

    /** Ripristina il dispatcher principale dopo ogni test. */
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()


    /** Dopo un accesso riuscito la password viene tolta dallo stato e la sessione riporta il ruolo dell'utente. */
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

    /** Una password sbagliata mostra il messaggio di credenziali errate, non quello di sessione scaduta. */
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

    /**
     * Con troppi tentativi il server risponde 429 con un'attesa di 60 secondi, oltre il limite del retry automatico:
     * il messaggio mostra all'utente quanti secondi aspettare.
     */
    @Test
    fun `troppi tentativi mostra il tempo d'attesa`() = runTest {
        backend.loginStatus = HttpStatusCode.TooManyRequests
        val viewModel = LoginViewModel(session())

        viewModel.onEmailChange("hiker@test.dev")
        viewModel.onPasswordChange("qualsiasi")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(UiMessage(Res.string.error_rate_limited_seconds, 60L), viewModel.state.value.error)
    }

    /** Senza email o senza password l'invio non produce nessuna richiesta. */
    @Test
    fun `senza email o password non parte nessuna richiesta`() = runTest {
        val viewModel = LoginViewModel(session())

        viewModel.onEmailChange("hiker@test.dev")
        viewModel.submit()
        advanceUntilIdle()

        assertTrue(backend.requests.isEmpty())
    }


    /** Compila il form di registrazione con dati validi, permettendo di cambiare password e data di nascita. */
    private fun RegisterViewModel.fill(password: String = "password-lunga-123", birthDate: LocalDate? = LocalDate(1990, 5, 1)) {
        onDisplayNameChange("Nuovo Escursionista")
        onEmailChange("nuovo@test.dev")
        onPasswordChange(password)
        birthDate?.let(::onBirthDateChange)
    }

    /** Una registrazione riuscita apre la sessione con il ruolo HIKER. */
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

    /** Password troppo corta e data di nascita mancante producono errori sui campi senza chiamare il server. */
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

    /** Una password di 40 lettere accentate supera i 72 byte e viene bloccata in locale con l'errore "troppo lunga". */
    @Test
    fun `una password oltre i 72 byte si blocca in locale`() = runTest {
        val viewModel = RegisterViewModel(session())

        viewModel.fill(password = "è".repeat(40))
        viewModel.submit()

        assertEquals(UiMessage(Res.string.error_field_password_long), viewModel.state.value.fieldErrors[RegisterField.PASSWORD])
    }

    /** Gli errori di validazione per campo restituiti dal server arrivano sui campi del form, senza un errore generale. */
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

    /** Se il server rifiuta la registrazione per una regola di business, compare il messaggio generale della registrazione. */
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

    /** Modificare un campo toglie l'errore mostrato su quel campo. */
    @Test
    fun `modificare un campo toglie il suo errore`() = runTest {
        val viewModel = RegisterViewModel(session())
        viewModel.fill(password = "corta")
        viewModel.submit()

        viewModel.onPasswordChange("password-lunga-123")

        assertNull(viewModel.state.value.fieldErrors[RegisterField.PASSWORD])
    }


    /**
     * Al primo accesso con Google il server chiede la data di nascita: il form la richiede e poi completa l'accesso
     * riusando lo stesso ID token di Google.
     */
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

    /**
     * Se l'utente annulla l'accesso con Google non compare nessun errore; un account del personale, che non può
     * usare Google, riceve un messaggio dedicato.
     */
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

    /**
     * Il recupero della password chiede il codice via email; un codice sbagliato mostra l'errore sul campo, quello
     * giusto completa il recupero e la nuova password viene tolta dallo stato.
     */
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

    /** Senza un'email valida la richiesta del codice non parte e l'errore compare sul campo. */
    @Test
    fun `password dimenticata - controlli locali senza richieste`() = runTest {
        val session = session()
        val viewModel = PasswordResetViewModel(session, "")

        viewModel.requestCode()
        assertEquals(UiMessage(Res.string.error_field_email), viewModel.state.value.fieldErrors[ResetField.EMAIL])
        assertTrue(backend.requests.isEmpty())
    }


    /** Attiva la 2FA per l'utente di prova, compila il login e lo invia: il server risponde chiedendo il codice. */
    private fun TestScope.loginWithMfa(session: SessionManager): LoginViewModel {
        backend.mfaTotp["hiker@test.dev"] = "654321"
        val viewModel = LoginViewModel(session)
        viewModel.onEmailChange("hiker@test.dev")
        viewModel.onPasswordChange("password-giusta-1")
        viewModel.submit()
        advanceUntilIdle()
        return viewModel
    }

    /**
     * La risposta "mfa-required" apre il passo del codice senza mostrare errori; la password non è più nello stato
     * visibile ma resta nel ViewModel per la seconda richiesta. Il codice accetta solo cifre, al massimo 6, e con il
     * codice giusto l'accesso riesce. Il 401 del login non fa partire il refresh, perché il login è escluso dal bearer.
     */
    @Test
    fun `2FA - mfa-required apre il passo del codice e la seconda richiesta lo manda`() = runTest {
        val session = session()
        session.restore()
        val viewModel = loginWithMfa(session)

        assertTrue(viewModel.state.value.mfaStep)
        assertEquals("", viewModel.state.value.password)
        assertNull(viewModel.state.value.error)
        assertEquals(SessionState.Anonymous, session.state.value)

        viewModel.onMfaCodeChange("65a43 21")
        assertEquals("654321", viewModel.state.value.mfaCode)
        viewModel.submitCode()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loggedIn)
        assertEquals(UserRole.HIKER, session.state.value.role)
        assertTrue(backend.requests.none { it.startsWith("POST /api/v1/auth/refresh") })
    }

    /**
     * Un codice sbagliato mostra l'errore sul campo; anche un codice valido ma già usato da un altro accesso viene
     * rifiutato, come protezione contro il riuso.
     */
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

        assertTrue(session.login("hiker@test.dev", "password-giusta-1", "654321").isSuccess)
        session.logout()
        viewModel.onMfaCodeChange("654321")
        viewModel.submitCode()
        advanceUntilIdle()
        assertEquals(UiMessage(Res.string.error_mfa_code_wrong), viewModel.state.value.mfaCodeError)
        assertFalse(viewModel.state.value.loggedIn)
    }

    /**
     * Al posto del codice TOTP si può usare un codice di recupero, scritto come testo libero. Tornando indietro dal
     * passo del codice si ritorna alla password e la richiesta con il codice non parte più.
     */
    @Test
    fun `2FA - codice di recupero e Indietro che dimentica la password`() = runTest {
        val session = session()
        session.restore()
        val viewModel = loginWithMfa(session)
        backend.mfaRecovery["hiker@test.dev"] = mutableSetOf("ABCD-EFGH")

        viewModel.onToggleRecoveryCode()
        viewModel.onMfaCodeChange("ABCD-EFGH")
        assertEquals("ABCD-EFGH", viewModel.state.value.mfaCode)
        viewModel.submitCode()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loggedIn)

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

    /** Con Google la seconda richiesta riusa lo stesso ID token aggiungendo il codice della 2FA. */
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

    /** Il toString dello stato non contiene la password, il codice né l'email dell'utente. */
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
