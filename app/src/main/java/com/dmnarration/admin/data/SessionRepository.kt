package com.dmnarration.admin.data

import android.util.Log
import com.dmnarration.admin.domain.UserRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The profile row is missing, or its role is a string this build does not know.
 *
 * A fact about the account, and the only thing this type may mean. It
 * deliberately does NOT cover "there is no signed-in user right now" — that is
 * a statement about authentication, not entitlement, and conflating the two is
 * what let a dropped network destroy a session.
 */
class ProfileUnusableException(message: String) : Exception(message)

/** What a session status means, once interpreted. */
sealed interface SessionState {
    /** Authenticated, with a user to look up. */
    data class Ready(val userId: String, val email: String?) : SessionState

    /** Genuinely signed out: deliberate, revoked, or never signed in here. */
    data object NeedsSignIn : SessionState

    /**
     * There is a session; it just cannot be validated right now. Retryable, and
     * never a reason to touch what is stored.
     */
    data class Unreachable(val message: String) : SessionState
}

/**
 * Sign-in, sign-out, and the role that governs everything else.
 *
 * ─── the rule this file exists to enforce ───────────────────────────────────
 *
 * CREDENTIAL DESTRUCTION IS REACHABLE ONLY FROM AN EXPLICIT USER ACTION.
 *
 * `signOutByUser()` is the single function in the app that clears stored
 * credentials, and the Sign out button is the only thing that calls it. No
 * automatic path — session restore, role loading, refresh failure, or anything
 * added later — may destroy a credential. The worst an automatic path may do is
 * refuse to show data.
 *
 * Three separate bugs in this stage were the same shape: an automatic path
 * deciding, on incomplete information, to throw away a working session. Getting
 * the classification right a fourth time would not prevent a fifth. Making the
 * destructive call unreachable does — a future misclassification now costs a
 * wrong screen instead of a lost session.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val client: SupabaseClient,
    private val sessionStore: EncryptedSessionManager,
) {

    /**
     * The library's status, observed continuously rather than sampled once.
     *
     * Sampling at launch was why a token expiring under a running app went
     * unnoticed, and why recovery needed a restart: supabase-kt retries a failed
     * refresh on its own, and nothing was listening for it to succeed.
     */
    val sessionStatus: Flow<SessionStatus> get() = client.auth.sessionStatus

    /**
     * Interpret a status. The whole point is that four states get four answers.
     *
     * A 4xx from the refresh endpoint — a revoked or expired refresh token — is
     * handled inside the library: it clears the session itself and reports
     * NotAuthenticated(isSignOut = true). So "asked and was refused" still ends
     * at the sign-in screen, which is what stops the careful handling below from
     * becoming a zombie that shows "No connection" forever at a server that is
     * actually saying no. Only "could not ask" is held open.
     *
     * Null means the status is still settling and the caller should keep waiting.
     */
    fun interpret(status: SessionStatus): SessionState? = when (status) {
        is SessionStatus.Initializing -> null

        is SessionStatus.Authenticated ->
            SessionState.Ready(status.session.user?.id.orEmpty(), status.session.user?.email)

        // The cause — NetworkError or InternalServerError — is deliberately not
        // read: the property is deprecated, and both causes mean the same thing
        // here. Could not ask, versus asked and refused, is the distinction that
        // matters, and a 5xx is on the "could not ask" side of it.
        is SessionStatus.RefreshFailure -> SessionState.Unreachable(
            "Could not confirm your session — no connection, or the server is not answering."
        )

        is SessionStatus.NotAuthenticated ->
            if (status.isSignOut || !sessionStore.hasStoredSession()) {
                // Deliberate, revoked, or a fresh install — all genuinely signed out.
                SessionState.NeedsSignIn
            } else {
                // Not authenticated, yet something is still stored. Treat that as
                // unvalidatable rather than as a decision, and leave it alone.
                SessionState.Unreachable("Could not confirm your session. Showing the last board loaded.")
            }
    }

    val currentEmail: String? get() = client.auth.currentUserOrNull()?.email

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /**
     * The one credential-destroying function in the app.
     *
     * Called from the Sign out button and nowhere else — see the class comment.
     * Revocation needs the network and may fail; discarding the local copy
     * cannot and must not, so the server call is best effort and clearSession()
     * is unconditional.
     */
    suspend fun signOutByUser() {
        runCatching { client.auth.signOut() }
            .onFailure { Log.w(TAG, "could not revoke the session server-side", it) }
        runCatching { client.auth.clearSession() }
            .onFailure { Log.e(TAG, "could not clear the local session", it) }
    }

    /**
     * The role for an already-authenticated user.
     *
     * Takes the id rather than discovering it. This used to call
     * currentUserOrNull() and treat null as "this account has no usable
     * profile", so an expired token — a network condition — was read as a
     * disqualifying fact and cost the user their session. Authentication is
     * decided upstream now, and this cannot be reached without it.
     *
     * The profiles policy allows a user to select their own row and nothing
     * else, so this returns exactly one row or none. None means the row is
     * genuinely missing, which happened for real in Stage 0 when the admin user
     * was created before the provisioning trigger existed.
     */
    suspend fun loadRole(userId: String): Result<UserRole> = runCatching {
        val rows = client.from("profiles")
            .select(Columns.raw("id, role, display_name")) {
                filter { eq("id", userId) }
            }
            .decodeList<ProfileDto>()

        val row = rows.firstOrNull()
            ?: throw ProfileUnusableException("no profile row for this user")
        UserRole.fromStored(row.role)
    }

    private companion object {
        const val TAG = "SessionRepository"
    }
}
