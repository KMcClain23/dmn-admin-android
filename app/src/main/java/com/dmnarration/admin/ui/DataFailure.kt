package com.dmnarration.admin.ui

import android.util.Log
import com.dmnarration.admin.data.BoardAccessNotEnabledException

/**
 * One sentence for the person; the detail goes to the log.
 *
 * The classification lives here once and the wording is passed in, because
 * Stage 6 adds two more screens that have to tell the same four situations
 * apart — refused, offline, revoked, and everything else — while saying
 * different things about different data. Copying the `when` chain per screen is
 * how one of them ends up telling a signed-out user to check their network.
 *
 * The throwable itself never reaches the screen. supabase-kt's message carries
 * the request that failed — full URL with query string, and the headers,
 * including Authorization and apikey.
 *
 * `offline` is not a parameter: "No connection. Pull down to try again." is
 * true of every screen in this app, and a caller cannot get it wrong if it
 * cannot supply it.
 */
internal fun describeDataFailure(
    t: Throwable,
    tag: String,
    logMessage: String,
    refused: String,
    revoked: String,
    generic: String,
): String {
    Log.w(tag, logMessage, t)
    val message = t.message.orEmpty()
    return when {
        t is BoardAccessNotEnabledException -> refused
        message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("Failed to connect", ignoreCase = true) ||
            t is java.io.IOException ->
            "No connection. Pull down to try again."
        message.contains("permission denied", ignoreCase = true) ||
            message.contains("JWT", ignoreCase = true) -> revoked
        else -> generic
    }
}
