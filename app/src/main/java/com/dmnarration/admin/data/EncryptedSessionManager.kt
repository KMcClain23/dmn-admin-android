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
    fun hasStoredSession(): Boolean =
        runCatching { prefs?.contains(KEY_SESSION) == true }.getOrDefault(false)

    override suspend fun deleteSession() {
        val store = prefs ?: return
        runCatching { store.edit().remove(KEY_SESSION).apply() }
    }

    private companion object {
        const val TAG = "SessionStore"
        const val PREFS_NAME = "dmn_session"
        const val KEY_SESSION = "session"
    }
}
