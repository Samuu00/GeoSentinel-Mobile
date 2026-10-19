package it.unical.geosentinel_mobile.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import geosentinel_mobile.shared.generated.resources.Res
import geosentinel_mobile.shared.generated.resources.error_current_password_wrong
import geosentinel_mobile.shared.generated.resources.error_field_current_password
import geosentinel_mobile.shared.generated.resources.error_field_display_name
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

// Messaggio per un errore che il form non sa attribuire a un campo
private fun Throwable.toUiMessage(): UiMessage = (this as? ApiException)?.error?.uiMessage() ?: UiMessage(Res.string.error_generic)

// ---- Profilo: uscita, cancellazione dell'account e foto profilo -----------------------------------------------

data class ProfileUiState(val deleting: Boolean = false, val error: UiMessage? = null, val avatarBusy: Boolean = false)

class ProfileViewModel(
    private val session: SessionManager,
    private val uploader: MediaUploader,
    private val media: MediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    // La pulizia locale avviene comunque, anche se l'utente lascia la schermata (NonCancellable nel SessionManager)
    fun logout() {
        viewModelScope.launch { session.logout() }
    }

    // Dopo la doppia conferma. Riuscita: la sessione torna Anonymous e il Profilo diventa quello dell'ospite.
    // Personale: 409 account-managed, con il messaggio "si cancellano tramite l'amministratore".
    fun deleteAccount() {
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            val result = session.deleteAccount()
            _state.update { it.copy(deleting = false, error = result.exceptionOrNull()?.toUiMessage()) }
        }
    }

    /**
     * Foto profilo (M11): [encode] ricodifica la foto scelta (JPEG quadrato da 512 px, senza EXIF), poi lo stesso upload
     * delle segnalazioni con target USER. Il server cancella da solo la foto precedente al confirm; il profilo si
     * rilegge per avere il nuovo avatarMediaId.
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
                        // in quarantena (non un'immagine): la foto di prima resta
                        onSuccess = { if (it.status == MediaResponse.Status.READY) null else UiMessage(Res.string.error_media_unsupported_type) },
                        onFailure = { it.toUiMessage() },
                    ),
                )
            }
        }
    }

    // DELETE /media/{id} della foto corrente, poi il profilo si rilegge (torna l'iniziale)
    fun removeAvatar() {
        val mediaId = (session.state.value as? SessionState.LoggedIn)?.profile?.avatarMediaId ?: return
        if (_state.value.avatarBusy) return
        _state.update { it.copy(avatarBusy = true, error = null) }
        viewModelScope.launch {
            val result = safely { media.delete(mediaId) }
            // 404: già tolta (da un altro dispositivo); il profilo riletto lo dice
            val notFound = (result.exceptionOrNull() as? ApiException)?.error is ApiError.NotFound
            if (result.isSuccess || notFound) session.reloadProfile()
            _state.update { it.copy(avatarBusy = false, error = result.exceptionOrNull()?.takeUnless { notFound }?.toUiMessage()) }
        }
    }
}

// ---- Nome visualizzato ---------------------------------------------------------------------------------------

data class EditNameUiState(
    val displayName: String,
    val original: String,
    val fieldError: UiMessage? = null,
    val error: UiMessage? = null,
    val submitting: Boolean = false,
    val saved: Boolean = false,
) {
    // Salva solo se il nome è cambiato
    val canSubmit: Boolean get() = displayName.trim() != original.trim() && !submitting

    override fun toString() = "EditNameUiState(submitting=$submitting, saved=$saved, error=$error)" // niente dati personali nei log
}

class EditNameViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(
        ((session.state.value as? SessionState.LoggedIn)?.profile?.displayName.orEmpty()).let { EditNameUiState(it, it) },
    )
    val state: StateFlow<EditNameUiState> = _state.asStateFlow()

    fun onDisplayNameChange(value: String) = _state.update { it.copy(displayName = value, fieldError = null, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        // stessa regola del server (UpdateProfileRequest: da 2 a 100 caratteri)
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

// ---- Cambio password -----------------------------------------------------------------------------------------

enum class PasswordField { CURRENT, NEW }

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val fieldErrors: Map<PasswordField, UiMessage> = emptyMap(),
    val error: UiMessage? = null,
    val submitting: Boolean = false,
    val changed: Boolean = false,
) {
    // le password non finiscono nei log
    override fun toString() = "ChangePasswordUiState(submitting=$submitting, changed=$changed, fieldErrors=${fieldErrors.keys}, error=$error)"
}

class ChangePasswordViewModel(private val session: SessionManager) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    fun onCurrentChange(value: String) = edit(PasswordField.CURRENT) { copy(currentPassword = value) }
    fun onNewChange(value: String) = edit(PasswordField.NEW) { copy(newPassword = value) }

    // Modificare un campo toglie il suo errore
    private fun edit(field: PasswordField, change: ChangePasswordUiState.() -> ChangePasswordUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - field, error = null) }

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
        }
        if (localErrors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = localErrors) }
            return
        }
        _state.update { it.copy(submitting = true, error = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val result = session.changePassword(current.currentPassword, current.newPassword)
            _state.update { state ->
                result.fold(
                    // le password non restano in memoria più del necessario
                    onSuccess = { state.copy(submitting = false, currentPassword = "", newPassword = "", changed = true) },
                    onFailure = { state.withServerError(it).copy(submitting = false) },
                )
            }
        }
    }
}

private fun ChangePasswordUiState.withServerError(failure: Throwable): ChangePasswordUiState =
    when (val error = (failure as? ApiException)?.error) {
        // 422 business-rule: la password attuale è sbagliata (guida all'API)
        is ApiError.BusinessRule if error.slug == "business-rule" ->
            copy(fieldErrors = mapOf(PasswordField.CURRENT to UiMessage(Res.string.error_current_password_wrong)))
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
