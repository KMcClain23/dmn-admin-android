package com.dmnarration.admin.domain

/**
 * The part of a failure a person should read.
 *
 * ── WHY THIS EXISTS ────────────────────────────────────────────────────────
 *
 * supabase-kt puts the whole request diagnostic into `Throwable.message`: the
 * database's sentence, then the SQLSTATE, the hint, the URL, the HTTP method,
 * and the request HEADERS — which include an `Authorization: Bearer ey…` prefix
 * and the apikey's length.
 *
 * Rendered as-is, a refusal that should read
 *
 *     This book tracks chapters individually and 8 is marked done out of order.
 *     Use the website to change it.
 *
 * arrives as that sentence followed by six lines of transport detail with a
 * token fragment in the middle of it. It is unreadable, and it puts a piece of
 * a credential on a screen that has no reason to show one.
 *
 * The database's message is always the FIRST line, because that is how Postgres
 * composes it. Everything after the first newline is diagnostics for a log.
 */
fun humanMessage(t: Throwable?, fallback: String): String {
    val raw = t?.message?.trim().orEmpty()
    if (raw.isEmpty()) return fallback
    // "Code:" appears on its own line when there is no newline before it in
    // some builds; both separators are cut so neither shape leaks through.
    val firstLine = raw.lineSequence().firstOrNull()?.trim().orEmpty()
    val cut = firstLine.substringBefore("Code:").trim()
    return cut.ifEmpty { fallback }
}
