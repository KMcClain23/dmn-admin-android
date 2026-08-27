package com.dmnarration.admin.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json

/**
 * Whether a credential is on this device — including "cannot tell".
 *
 * A missing store and a store that refuses to be read are different facts, and only
 * one of them means a fresh install. Collapsing them sends someone whose keystore is
 * temporarily unavailable to the sign-in screen as though they had never signed in.
 */
enum class StoredSession { PRESENT, ABSENT, UNKNOWN }

/**
 * Where the signed-in session is kept between launches.
 *
 * supabase-kt's default writes the session to plain SharedPreferences. That is
 * a refresh token sitting in cleartext in app-private storage — unreadable on a
 * healthy device, readable on a rooted or backed-up one. This wraps the same
 * idea in EncryptedSharedPreferences so the file on disk is ciphertext, keyed
 * by the platform keystore.
 *
 * Every failure path returns null or does nothing rather than throwing. A
 * session store that cannot be read means "not signed in", which the app
 * already knows how to handle; a store that throws would take the launch down
 * with it, and a keystore can genuinely fail — a restored backup carries the
 * encrypted file to a device whose keystore cannot decrypt it. In that case the
 * right answer is to drop the file and ask the user to sign in again.
 */
class EncryptedSessionManager(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionManager {

    private val prefs: SharedPreferences? by lazy { openPrefs() }

    /**
     * Jetpack Security deprecated `MasterKey` and `EncryptedSharedPreferences`
     * in 1.1.0 without shipping a replacement — the guidance is to use platform
     * primitives directly, which for one string is more custom crypto than the
     * problem deserves. Suppressed rather than abandoned: a deprecated
     * encrypted store still beats an undeprecated plaintext one, and this is
     * the only place in the app that has to change when a successor exists.
     */
    @Suppress("DEPRECATION")
    private fun openPrefs(): SharedPreferences? = try {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Most likely a keystore that cannot open a file written by a different
        // install. Start clean rather than refusing to launch.
        Log.w(TAG, "session store unavailable, continuing signed out", e)
        runCatching { context.deleteSharedPreferences(PREFS_NAME) }
        null
    }

    override suspend fun saveSession(session: UserSession) {
        val store = prefs ?: return
        runCatching { store.edit().putString(KEY_SESSION, json.encodeToString(session)).apply() }
            .onFailure { Log.w(TAG, "could not persist session", it) }
    }

    /**
     * Throws when there is nothing to load, which is this interface's contract:
     * `loadSessionOrNull` is the wrapper that turns that into null, and it is
     * what supabase-kt calls when restoring at launch. Returning null here does
     * not compile — the signature is non-null on purpose.
     */
    override suspend fun loadSession(): UserSession {
        val store = prefs ?: error("session store unavailable")
        val raw = store.getString(KEY_SESSION, null) ?: error("no stored session")
        return try {
            json.decodeFromString<UserSession>(raw)
        } catch (e: Exception) {
            // A session we cannot read is a session we do not have. Drop it so
            // the next sign-in starts from something valid.
            Log.w(TAG, "stored session unreadable, discarding", e)
            runCatching { store.edit().remove(KEY_SESSION).apply() }
            throw e
        }
    }

    /**
     * Whether anything is stored, without decoding it.
     *
     * Used to tell a fresh install from a session that exists but cannot
     * currently be validated — the difference between "sign in" and "you are
     * signed in, the network is down".
     */
    fun storedSession(): StoredSession {
        val store = prefs ?: return StoredSession.UNKNOWN
        return runCatching {
            if (store.contains(KEY_SESSION)) StoredSession.PRESENT else StoredSession.ABSENT
        }.getOrElse {
            // NOT ABSENT. This function exists to tell a fresh install from a session
            // that cannot currently be validated, and answering "absent" when the
            // question could not be asked discards exactly that distinction — which
            // the doc comment above states as the whole point.
            Log.w(TAG, "could not read the session store", it)
            StoredSession.UNKNOWN
        }
    }

    /**
     * Whether the last [deleteSession] actually removed anything.
     *
     * Read once and reset by [takeDeleteFailure]. This exists because the
     * SessionManager contract returns Unit, so a failed delete had no way to reach
     * the caller and was swallowed entirely — not even logged. Sign-out then reported
     * success while the token stayed on disk, which is the one outcome a sign-out
     * must never get wrong.
     */
    @Volatile
    private var deleteFailure: Throwable? = null

    /** Takes the pending failure, if any, and clears it. */
    fun takeDeleteFailure(): Throwable? = deleteFailure.also { deleteFailure = null }

    override suspend fun deleteSession() {
        deleteFailure = null
        val store = prefs
        if (store == null) {
            // No store means nothing is persisted, which is the desired end state.
            return
        }
        runCatching {
            store.edit().remove(KEY_SESSION).commit().also { committed ->
                // commit() reports, apply() does not. A sign-out is the one write
                // worth waiting for an answer on.
                if (!committed) error("the session store refused the write")
            }
        }.onFailure {
            Log.e(TAG, "could not remove the stored session", it)
            deleteFailure = it
        }
    }

    private companion object {
        const val TAG = "SessionStore"
        const val PREFS_NAME = "dmn_session"
        const val KEY_SESSION = "session"
    }
}
