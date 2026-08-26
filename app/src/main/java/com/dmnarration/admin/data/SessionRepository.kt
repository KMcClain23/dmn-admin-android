package com.dmnarration.admin.data

import android.util.Log
import com.dmnarration.admin.domain.UserRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** What the app knows about who is signed in. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val role: UserRole, val email: String?) : AuthState
    /** Signed in, but the role could not be established. Fails closed. */
    data class RoleUnavailable(val reason: String) : AuthState
}

/**
 * Sign-in, sign-out, and the role that governs everything else.
 *
 * The role is fetched from `profiles` on every cold start rather than cached
 * across launches. Stage 0 proved it is read fresh from the table on every
 * query rather than baked into the JWT — demoting a user made an already-issued
 * token return zero rows with no re-authentication — so a stale cached role
 * would be a client-side lie about a server-side fact.
 *
 * A profile that cannot be read signs the user out. Proceeding with a session
 * whose permissions are unknown is the one outcome worse than an error message:
 * it produces a UI that is guessing.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val client: SupabaseClient,
) {

    /** Waits for supabase-kt to finish restoring any stored session. */
    suspend fun awaitInitialised(): SessionStatus =
        client.auth.sessionStatus.first { it !is SessionStatus.Initializing }

    val currentEmail: String? get() = client.auth.currentUserOrNull()?.email

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        runCatching { client.auth.signOut() }
            .onFailure { Log.w(TAG, "sign-out failed locally", it) }
    }

    /**
     * The caller's role, straight from `profiles`.
     *
     * Its RLS policy allows a user to select their own row and nothing else, so
     * this either returns exactly one row or none. None means the profile is
     * missing — which happened for real during Stage 0, when the admin user was
     * created before the auto-provisioning trigger existed — and it must be a
     * failure rather than a default.
     */
    suspend fun loadRole(): Result<UserRole> = runCatching {
        val userId = client.auth.currentUserOrNull()?.id
            ?: error("no signed-in user")

        val rows = client.from("profiles")
            .select(Columns.raw("id, role, display_name")) {
                filter { eq("id", userId) }
            }
            .decodeList<ProfileDto>()

        val row = rows.firstOrNull() ?: error("no profile row for this user")
        UserRole.fromStored(row.role)
    }

    private companion object {
        const val TAG = "SessionRepository"
    }
}
