// ViewModel della scheda Profilo e delle schermate dell'account: uscita, cancellazione, foto profilo, cambio del
// nome visualizzato e cambio password. Le operazioni sulla sessione passano tutte da SessionManager.
package it.unical.geosentinel_mobile.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_current_password_wrong
import geosentinel_mobile.shared.generated.resources.error_field_current_password
import geosentinel_mobile.shared.generated.resources.error_field_display_name
import geosentinel_mobile.shared.generated.resources.error_field_mfa_code
import geosentinel_mobile.shared.generated.resources.error_mfa_code_wrong
import geosentinel_mobile.shared.generated.resources.error_field_password_long
import geosentinel_mobile.shared.generated.resources.error_field_password_short
import geosentinel_mobile.shared.generated.resources.error_generic
import geosentinel_mobile.shared.generated.resources.error_media_unsupported_type
import geosentinel_mobile.shared.generated.resources.report_photo_unreadable
import geosentinel_mobile.shared.generated.resources.error_validation
import it.unical.geosentinel_mobile.auth.data.PasswordProblem
import it.unical.geosentinel_mobile.auth.data.passwordProblem
import it.unical.geosentinel_mobile.api.model.InitUploadRequest
import it.unical.geosentinel_mobile.api.model.MediaResponse
import it.unical.geosentinel_mobile.core.network.ApiError
import it.unical.geosentinel_mobile.core.network.ApiException
import it.unical.geosentinel_mobile.core.network.safely
import it.unical.geosentinel_mobile.core.session.SessionManager
import it.unical.geosentinel_mobile.core.session.SessionState
import it.unical.geosentinel_mobile.core.ui.UiMessage
import it.unical.geosentinel_mobile.core.ui.uiMessage
import it.unical.geosentinel_mobile.media.data.MediaRepository
import it.unical.geosentinel_mobile.media.data.MediaTarget
import it.unical.geosentinel_mobile.media.data.MediaUploader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Messaggio per un errore che il form non sa attribuire a un campo: quello del server o quello generico. */
private fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)

// Profilo: uscita, cancellazione dell'account e foto profilo.

/**
 * Stato della scheda Profilo: [deleting] durante la cancellazione dell'account, [avatarBusy] durante il caricamento o
 * la rimozione della foto, [error] per l'ultimo errore da mostrare.
 */
data class ProfileUiState(val deleting: Boolean = false, val error: UiMessage? = null, val avatarBusy: Boolean = false)

/** ViewModel della scheda Profilo: logout, cancellazione dell'account e gestione della foto profilo. */
class ProfileViewModel(
    private val session: SessionManager,
    private val uploader: MediaUploader,
    private val media: MediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    /**
     * Esce dall'account. La pulizia dei dati locali avviene in ogni caso, anche se l'utente lascia la schermata,
     * perché SessionManager la esegue in un blocco non cancellabile.
     */
    fun logout() {
        viewModelScope.launch { session.logout() }
    }

    /**
     * Cancella l'account, dopo la doppia conferma della schermata. Se riesce la sessione torna anonima e il Profilo
     * diventa quello dell'ospite. Per un account del personale il server risponde 409 `account-managed`, che diventa
     * il messaggio "si cancellano tramite l'amministratore".
     */
    fun deleteAccount() {
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            val result = session.deleteAccount()
            _state.update { it.copy(deleting = false, error = result.exceptionOrNull()?.toUiMessage()) }
        }
    }

    /**
     * Carica una nuova foto profilo. [encode] ricodifica la foto scelta in un JPEG quadrato senza metadati EXIF; poi
     * si usa lo stesso caricamento delle foto delle segnalazioni, con destinazione USER. Il server cancella da solo
     * la foto precedente quando conferma la nuova; il profilo si rilegge per avere il nuovo `avatarMediaId`.
     */
    fun uploadAvatar(encode: suspend () -> ByteArray) {
        val userId = (session.state.value as? SessionState.LoggedIn)?.profile?.id ?: return
        if (_state.value.avatarBusy) return
        _state.update { it.copy(avatarBusy = true, error = null) }
        viewModelScope.launch {
            val jpeg = try {
                encode()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(avatarBusy = false, error = UiMessage(Res.string.report_photo_unreadable)) }
                return@launch
            }
            val result = safely { uploader.upload(jpeg, MediaTarget(InitUploadRequest.EntityType.USER, userId)) }
            if (result.isSuccess) session.reloadProfile()
            _state.update { state ->
                state.copy(
                    avatarBusy = false,
                    error = result.fold(
                        // Se il file non risulta un'immagine il server lo mette in quarantena invece di renderlo READY:
                        // la foto precedente resta e si mostra un errore.
                        onSuccess = { if (it.status == MediaResponse.Status.READY) null else UiMessage(Res.string.error_media_unsupported_type) },
                        onFailure = { it.toUiMessage() },
                    ),
                )
            }
        }
    }

    /** Cancella la foto profilo corrente sul server e rilegge il profilo, così torna l'iniziale del nome. */
    fun removeAvatar() {
        val mediaId = (session.state.value as? SessionState.LoggedIn)?.profile?.avatarMediaId ?: return
        if (_state.value.avatarBusy) return
        _state.update { it.copy(avatarBusy = true, error = null) }
        viewModelScope.launch {
            val result = safely { media.delete(mediaId) }
            // Un 404 vuol dire che la foto è già stata tolta (per esempio da un altro dispositivo): non è un errore e
            // il profilo riletto mostra lo stato giusto.
            val notFound = (result.exceptionOrNull() as? ApiException)?.error is ApiError.NotFound
            if (result.isSuccess || notFound) session.reloadProfile()
            _state.update { it.copy(avatarBusy = false, error = result.exceptionOrNull()?.takeUnless { notFound }?.toUiMessage()) }
        }
    }
}

// Nome visualizzato.

/** Stato del form del nome: [original] è il nome attuale, per capire se l'utente l'ha cambiato. */
data class EditNameUiState(
    val displayName: String,
    val original: String,
    val fieldError: UiMessage? = null,
    val error: UiMessage? = null,
    val submitting: Boolean = false,
    val saved: Boolean = false,
) {
    /** Si può salvare solo se il nome è cambiato e non c'è un invio in corso. */
    val canSubmit: Boolean get() = displayName.trim() != original.trim() && !submitting

    // toString scritto a mano: il nome è un dato personale e non deve finire nei log.
    override fun toString() = "EditNameUiState(submitting=$submitting, saved=$saved, error=$error)"
}

/** ViewModel del cambio del nome; parte dal nome del profilo della sessione. */
class EditNameViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(
        ((session.state.value as? SessionState.LoggedIn)?.profile?.displayName.orEmpty()).let { EditNameUiState(it, it) },
    )
    val state: StateFlow<EditNameUiState> = _state.asStateFlow()

    /** Aggiorna il nome e toglie gli errori. */
    fun onDisplayNameChange(value: String) = _state.update { it.copy(displayName = value, fieldError = null, error = null) }

    /** Valida il nome e lo invia al server. */
    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        // Stessa regola del server: da 2 a 100 caratteri, esclusi gli spazi ai bordi.
        if (current.displayName.trim().length !in 2..100) {
            _state.update { it.copy(fieldError = UiMessage(Res.string.error_field_display_name)) }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = session.updateDisplayName(current.displayName)
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(submitting = false, saved = true) },
                    onFailure = { failure ->
                        if ((failure as? ApiException)?.error is ApiError.Validation) {
                            state.copy(submitting = false, fieldError = UiMessage(Res.string.error_field_display_name))
                        } else {
                            state.copy(submitting = false, error = failure.toUiMessage())
                        }
                    },
                )
            }
        }
    }
}

// Cambio password.

/** Campi del form del cambio password: password attuale, nuova password, codice della verifica in due passaggi. */
enum class PasswordField { CURRENT, NEW, MFA }

/** Stato del form del cambio password. */
data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    /** Vero se l'account ha la verifica in due passaggi: allora il server chiede anche il codice. */
    val mfaRequired: Boolean = false,
    val mfaCode: String = "",
    val fieldErrors: Map<PasswordField, UiMessage> = emptyMap(),
    val error: UiMessage? = null,
    val submitting: Boolean = false,
    val changed: Boolean = false,
) {
    // toString scritto a mano: password e codice non devono finire nei log.
    override fun toString() = "ChangePasswordUiState(submitting=$submitting, changed=$changed, fieldErrors=${fieldErrors.keys}, error=$error)"
}

/** ViewModel del cambio password. Se il profilo dice che la verifica è attiva, il campo del codice compare subito. */
class ChangePasswordViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(
        ChangePasswordUiState(mfaRequired = (session.state.value as? SessionState.LoggedIn)?.profile?.mfaEnabled == true),
    )
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    /** Aggiorna la password attuale. */
    fun onCurrentChange(value: String) = edit(PasswordField.CURRENT) { copy(currentPassword = value) }
    /** Aggiorna la nuova password. */
    fun onNewChange(value: String) = edit(PasswordField.NEW) { copy(newPassword = value) }
    /** Aggiorna il codice: vale sia quello dell'app sia un codice di recupero, quindi è testo libero fino a 32 caratteri. */
    fun onMfaCodeChange(value: String) = edit(PasswordField.MFA) { copy(mfaCode = value.take(32)) }

    /** Applica [change] allo stato: modificare un campo toglie il suo errore e quello generale. */
    private fun edit(field: PasswordField, change: ChangePasswordUiState.() -> ChangePasswordUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - field, error = null) }

    /** Controlla i campi localmente e, se vanno bene, invia il cambio password. */
    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val localErrors = buildMap {
            if (current.currentPassword.isEmpty()) put(PasswordField.CURRENT, UiMessage(Res.string.error_field_current_password))
            when (passwordProblem(current.newPassword)) {
                PasswordProblem.TOO_SHORT -> put(PasswordField.NEW, UiMessage(Res.string.error_field_password_short))
                PasswordProblem.TOO_LONG -> put(PasswordField.NEW, UiMessage(Res.string.error_field_password_long))
                null -> Unit
            }
            if (current.mfaRequired && current.mfaCode.isBlank()) put(PasswordField.MFA, UiMessage(Res.string.error_field_mfa_code))
        }
        if (localErrors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = localErrors) }
            return
        }
        _state.update { it.copy(submitting = true, error = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val result = session.changePassword(current.currentPassword, current.newPassword, current.mfaCode.takeIf { current.mfaRequired })
            _state.update { state ->
                result.fold(
                    // Password e codice si cancellano dallo stato appena il cambio è riuscito.
                    onSuccess = { state.copy(submitting = false, currentPassword = "", newPassword = "", mfaCode = "", changed = true) },
                    onFailure = { state.withServerError(it).copy(submitting = false) },
                )
            }
        }
    }
}

/** Restituisce lo stato con l'errore del server attaccato al campo giusto, o come errore generale. */
private fun ChangePasswordUiState.withServerError(failure: Throwable): ChangePasswordUiState =
    when (val error = (failure as? ApiException)?.error) {
        // Secondo la guida all'API del backend, qui un 422 business-rule vuol dire che la password attuale è sbagliata.
        is ApiError.BusinessRule if error.slug == "business-rule" ->
            copy(fieldErrors = mapOf(PasswordField.CURRENT to UiMessage(Res.string.error_current_password_wrong)))
        // Codice della verifica assente, sbagliato o già usato. Succede anche se la verifica è stata attivata da un
        // altro dispositivo dopo l'apertura del form: per questo il campo del codice si mostra comunque.
        is ApiError.BusinessRule if error.slug == "invalid-mfa-code" ->
            copy(mfaRequired = true, fieldErrors = mapOf(PasswordField.MFA to UiMessage(Res.string.error_mfa_code_wrong)))
        is ApiError.Validation -> {
            val fields = buildMap {
                if ("currentPassword" in error.fieldErrors) put(PasswordField.CURRENT, UiMessage(Res.string.error_field_current_password))
                if ("newPassword" in error.fieldErrors) {
                    val tooLong = passwordProblem(newPassword) == PasswordProblem.TOO_LONG
                    put(PasswordField.NEW, UiMessage(if (tooLong) Res.string.error_field_password_long else Res.string.error_field_password_short))
                }
            }
            copy(fieldErrors = fields, error = if (fields.isEmpty()) UiMessage(Res.string.error_validation) else null)
        }
        else -> copy(error = failure.toUiMessage())
    }
