package com.dmnarration.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmnarration.admin.data.AuthState
import com.dmnarration.admin.data.ProfileUnusableException
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
 *
 * "Could not be read" and "could not be asked" are different, though, and only
 * the first is grounds for signing anyone out.
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
        val role = result.getOrElse { failure ->
            if (failure is ProfileUnusableException) {
                // A real fact about this account: there is no usable profile, so
                // the session's permissions are unknowable and it must not be
                // used. This is the case item 14 was written for.
                sessions.signOut()
                _state.value = AuthState.RoleUnavailable(
                    "Signed in, but your profile could not be read, so the app cannot tell " +
                        "what you are allowed to see. You have been signed out."
                )
            } else {
                // The request never landed. That says nothing whatsoever about
                // permissions, and signing out here would mean opening the app
                // in a lift costs you a valid session and a re-typed password.
                _state.value = AuthState.RoleCheckFailed(describe(failure))
            }
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

    /** Try the role fetch again, keeping the session. */
    fun retryRoleCheck() {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            resolveRole()
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
