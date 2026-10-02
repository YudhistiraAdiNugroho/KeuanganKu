package id.keuanganku.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.keuanganku.app.data.repo.AuthRepository
import id.keuanganku.app.data.repo.OpResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false
)

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private fun run(block: suspend () -> OpResult<Unit>) {
        viewModelScope.launch {
            _state.value = AuthUiState(loading = true)
            _state.value = when (val r = block()) {
                is OpResult.Ok -> AuthUiState(done = true)
                is OpResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    fun login(email: String, password: String) = run { repo.login(email, password) }
    fun requestRegisterOtp(email: String) = run { repo.requestRegisterOtp(email) }
    fun verifyRegister(email: String, code: String, password: String, name: String) =
        run { repo.verifyRegisterOtp(email, code, password, name) }
    fun requestRecovery(email: String) = run { repo.requestRecoveryOtp(email) }
    fun resetPassword(email: String, code: String, pw: String) = run { repo.resetPassword(email, code, pw) }
    fun logout() { viewModelScope.launch { repo.logout() } }
    fun reset() { _state.value = AuthUiState() }
}
