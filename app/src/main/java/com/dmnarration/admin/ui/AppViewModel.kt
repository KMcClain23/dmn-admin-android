package com.dmnarration.admin.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmnarration.admin.data.ProfileUnusableException
import com.dmnarration.admin.data.SessionRepository
import com.dmnarration.admin.data.SignOutOutcome
import com.dmnarration.admin.data.signOutMessage
import com.dmnarration.admin.data.SessionState
import com.dmnarration.admin.domain.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** What the app shows, and why. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val role: UserRole, val email: String?) : AuthState

    /**
     * The account's role is missing or unrecognised, so its permissions cannot
     * be established and no data may be shown.
     *
     * The session is deliberately NOT destroyed. Refusing to show data is the
     * most an automatic path may do; only the Sign out button discards a
     * credential.
     */
    data class RoleUnavailable(val reason: String) : AuthState

    /**
     * Signed in, session intact, just not confirmable right now — almost always
     * the network. Retryable, and never a sign-out.
     */
    data class Unreachable(val reason: String) : AuthState
}

/**
 * Who is signed in, observed rather than sampled.
 *
 * The session status is collected for the whole lifetime of the ViewModel, not
 * read once at launch. Reading once was why an access token expiring under a
 * running app went unnoticed until something rebuilt the screen, and why
 * recovery took a restart: supabase-kt retries a failed refresh by itself, and
 * nothing here was listening for it to succeed.
 *
 * Nothing in this class destroys a credential. `signOut()` exists, is wired to
 * the Sign out button, and is the only route to the repository's single
 * credential-clearing function.
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

    /** Whose role is already resolved, so a token refresh does not re-fetch it. */
    private var resolvedUserId: String? = null
    private var settleTimeout: Job? = null

    init {
        // If the status never settles, say so rather than spinning. Offline the
        // restore retries and can sit at Initializing far longer than anyone
        // waits — a network answer, not an authentication one.
        settleTimeout = viewModelScope.launch {
            delay(SETTLE_TIMEOUT)
            if (_state.value is AuthState.Loading) {
                _state.value = AuthState.Unreachable("No connection. Check your network and try again.")
            }
        }
        viewModelScope.launch {
            sessions.sessionStatus.collect { status ->
                val interpreted = sessions.interpret(status) ?: return@collect // still settling
                settleTimeout?.cancel()
                apply(interpreted)
            }
        }
    }

    private suspend fun apply(session: SessionState) {
        when (session) {
            is SessionState.NeedsSignIn -> {
                resolvedUserId = null
                _state.value = AuthState.SignedOut
            }

            is SessionState.Unreachable -> {
                // Keep what is on screen if the board is already up. A failed
                // refresh says nothing about entitlement, and the last board
                // loaded remains the best information available.
                if (_state.value !is AuthState.SignedIn) {
                    _state.value = AuthState.Unreachable(session.message)
                }
            }

            is SessionState.Ready -> {
                // A successful refresh re-emits Authenticated. Re-resolving the
                // role every time would rebuild the whole screen for nothing.
                if (resolvedUserId == session.userId && _state.value is AuthState.SignedIn) return
                resolveRole(session.userId, session.email)
            }
        }
    }

    /**
     * Establish the role for an authenticated user.
     *
     * Every outcome here is non-destructive. A missing or unrecognised role
     * refuses to show data and offers Sign out as a choice; it does not make
     * that choice on the user's behalf.
     */
    private suspend fun resolveRole(userId: String, email: String?) {
        val role = sessions.loadRole(userId).getOrElse { failure ->
            _state.value = if (failure is ProfileUnusableException) {
                AuthState.RoleUnavailable(
                    "Your profile could not be read, so the app cannot tell what you are " +
                        "allowed to see. Nothing is shown until that is fixed."
                )
            } else {
                AuthState.Unreachable(describe(failure))
            }
            return
        }
        if (!role.isRecognised) {
            _state.value = AuthState.RoleUnavailable(
                "Your account has a role this version of the app does not recognise, so " +
                    "nothing is shown."
            )
            return
        }
        resolvedUserId = userId
        _state.value = AuthState.SignedIn(role, email ?: sessions.currentEmail)
    }

    fun signIn(email: String, password: String) {
        if (_signingIn.value) return
        viewModelScope.launch {
            _signingIn.value = true
            _signInError.value = null
            // On success the status flow emits Authenticated and the collector
            // takes it from there — there is no second path to keep in step.
            sessions.signIn(email, password)
                .onFailure { _signInError.value = describe(it) }
            _signingIn.value = false
        }
    }

    /** The only route to credential destruction, wired to the Sign out button. */
    fun signOut() {
        viewModelScope.launch {
            resolvedUserId = null
            _signInError.value = null
            // A sign-out that could not discard the credential must say so. The user
            // believes they are off this device; being wrong about that silently is
            // the worst outcome this app has, and until now it was invisible — the
            // store's refusal was swallowed and sign-out reported success either way.
            val outcome = sessions.signOutByUser()
            if (outcome is SignOutOutcome.CredentialMayRemain) {
                Log.e(TAG, "sign-out could not remove the stored session", outcome.cause)
            }
            _signInError.value = signOutMessage(outcome)
            _state.value = AuthState.SignedOut
        }
    }

    /** Ask again, destroying nothing. */
    fun retry() {
        viewModelScope.launch {
            _state.value = AuthState.Loading
            val current = sessions.sessionStatus.first()
            val interpreted = sessions.interpret(current)
            if (interpreted != null) apply(interpreted) else _state.value = AuthState.Loading
        }
    }

    /**
     * Bad credentials and no network are different problems with different
     * fixes, and a single "sign-in failed" makes the user try the wrong one.
     */
    private fun describe(t: Throwable): String {
        Log.w("AppViewModel", "auth failure", t)
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
            // Same rule as the board: a sentence on screen, the object in the log.
            else -> "Could not sign in. Please try again."
        }
    }

    private companion object {
        const val TAG = "AppViewModel"
        val SETTLE_TIMEOUT = 10.seconds
    }
}
