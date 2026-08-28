package com.dmnarration.admin.domain

/**
 * One editable field's write, as a mechanism rather than a screen.
 *
 * Settings is the first caller. Card fields are next, then money records, and
 * each of those is dozens of fields — every one needing a grant, an input,
 * validation, an optimistic apply, a rollback to its OWN prior value, and a
 * refusal distinguishable from success. Thirty hand-built copies of that is
 * thirty chances to get one wrong, and repeated discipline is the thing this
 * project has watched fail in every form it takes.
 *
 * So the four outcomes live here once. This is deliberately NOT a generic
 * framework — it does not know about forms, focus, or which screen it is on. It
 * knows what a single field write can do and insists the caller handle each
 * case.
 */
sealed interface FieldWrite<out T> {
    /** Nothing in flight. */
    data object Idle : FieldWrite<Nothing>

    /** Sent, not yet answered. The optimistic value is already on screen. */
    data class Saving<T>(val optimistic: T) : FieldWrite<T>

    /**
     * The server returned the row. Its value is the truth, not the guess —
     * a trigger may have changed more than the client asked for.
     */
    data class Saved<T>(val stored: T) : FieldWrite<T>

    /**
     * Success, zero rows: RLS refused this row. It arrives wearing HTTP 200 and
     * nothing is thrown, which is why this is a case rather than an error.
     */
    data object Refused : FieldWrite<Nothing>

    /**
     * The write was rejected or failed, with a sentence for the person.
     *
     * `fromServer` distinguishes a rule the database stated — which is the exact
     * text every client shows, so the phone and the web read identically — from
     * a transport failure this app worded itself.
     */
    data class Failed(val message: String, val fromServer: Boolean) : FieldWrite<Nothing>
}

/** What a refused write says. One sentence, so two screens cannot word it differently. */
const val WRITE_REFUSED_MESSAGE: String =
    "You no longer have permission to make that change."

/**
 * The rule the database raises, as it reaches the client.
 *
 * `check_site_setting()` raises SQLSTATE 22023 with the sentence the Settings
 * screen already displays for a stored-but-unusable value. PostgREST returns it
 * as a JSON body and supabase-kt wraps that in an exception message, so the
 * sentence has to be dug back out — the alternative is the client composing its
 * own wording, which is exactly the drift that put "1,000–30,000" on one client
 * and "1000–30000" on the other.
 *
 * Returns null when this is not a validation refusal, so a caller cannot mistake
 * a dropped connection for a rule.
 */
fun serverRefusalMessage(t: Throwable): String? {
    var cur: Throwable? = t
    while (cur != null) {
        val text = cur.message.orEmpty()
        if (VALIDATION_SQLSTATE in text) {
            MESSAGE_FIELD.find(text)?.groupValues?.getOrNull(1)?.let { raw ->
                return raw.unescapeJsonString()
            }
        }
        cur = cur.cause
    }
    return null
}

private const val VALIDATION_SQLSTATE = "22023"

/**
 * The `message` member of the PostgREST error body.
 *
 * Non-greedy and escape-aware: the sentence itself contains escaped quotes —
 * `Stored value \"abc\" is not a number.` — so a naive match to the next quote
 * stops in the middle of the value being complained about.
 */
private val MESSAGE_FIELD = Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

private fun String.unescapeJsonString(): String =
    replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")
