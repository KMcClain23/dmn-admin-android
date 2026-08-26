package com.dmnarration.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmnarration.admin.data.AuthState
import com.dmnarration.admin.data.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Who is signed in, and what the launch decision is.
 *
 * The role is loaded here on every cold start rather than being remembered
 * between launches, and a failure to load it signs the user out. A session
 * whose permissions are unknown must not reach the board — `UserRole.UNKNOWN`
 * grants nothing and shows an error instead.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessions: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    init {
        viewModelScope.launch { restore() }
    }

    private suspend fun restore() {
        _state.value = AuthState.Loading
        val status = sessions.awaitInitialised()
        if (status !is SessionStatus.Authenticated) {
            _state.value = AuthState.SignedOut
            return
        }
        resolveRole()
    }

    /**
     * Turn an authenticated session into a usable one by establishing its role.
     *
     * Signing out on failure is the point. Stage 0 showed what a missing profile
     * row looks like from the outside — every symptom pointed somewhere other
     * than the cause — so this refuses to continue rather than guessing at a
     * default and producing a board that may be showing the wrong person the
     * wrong things.
     */
    private suspend fun resolveRole() {
        val result = sessions.loadRole()
        val role = result.getOrElse {
            sessions.signOut()
            _state.value = AuthState.RoleUnavailable(
                "Signed in, but your profile could not be read, so the app cannot tell " +
                    "what you are allowed to see. You have been signed out."
            )
            return
        }
        if (!role.isRecognised) {
            sessions.signOut()
            _state.value = AuthState.RoleUnavailable(
                "Your account has a role this version of the app does not recognise. " +
                    "You have been signed out."
            )
            return
        }
        _state.value = AuthState.SignedIn(role, sessions.currentEmail)
    }

    fun signIn(email: String, password: String) {
        if (_signingIn.value) return
        viewModelScope.launch {
            _signingIn.value = true
            _signInError.value = null
            sessions.signIn(email, password)
                .onSuccess { resolveRole() }
                .onFailure { _signInError.value = describe(it) }
            _signingIn.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            sessions.signOut()
            _signInError.value = null
            _state.value = AuthState.SignedOut
        }
    }

    fun dismissRoleError() {
        _state.value = AuthState.SignedOut
    }

    /**
     * Bad credentials and no network are different problems with different
     * fixes, and a single "sign-in failed" makes the user try the wrong one.
     */
    private fun describe(t: Throwable): String {
        val message = t.message.orEmpty()
        return when {
            message.contains("Invalid login", ignoreCase = true) ||
                message.contains("invalid_grant", ignoreCase = true) ||
                message.contains("invalid_credentials", ignoreCase = true) ->
                "That email and password do not match an account."
            message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("Failed to connect", ignoreCase = true) ||
                t is java.io.IOException ->
                "No connection. Check your network and try again."
            else -> "Could not sign in: ${message.ifBlank { "unknown error" }}"
        }
    }
}
