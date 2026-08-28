package com.dmnarration.admin.domain

/**
 * Everything `site_settings` holds — the five studio numbers plus the two that
 * describe whether Dean is taking work at all.
 *
 * Read-only, and structurally so: `site_settings` carries a `Role read` policy and
 * **no update policy of any kind**. A write would come back as zero rows rather than
 * an error, which is the ambiguity this project has spent two stages removing. There
 * is deliberately no write path here, and adding one later needs a migration that
 * makes the refusal visible first.
 */
data class SiteSettings(
    val acceptingProjects: Boolean?,
    /** Null when the stored value could not be read as months. See [availableMonthsLabel]. */
    val availableMonths: List<Int>?,
    /** The raw stored value, so an unreadable one can be shown rather than hidden. */
    val availableMonthsRaw: String?,
    val acceptingProjectsRaw: String?,
    /** The studio numbers AND what could not be read, per key. */
    val studio: StudioSettingsRead,
)

/**
 * The booking window, in the order it is stored.
 *
 * Stored as `[11, 12, 1, 2]`. That order is CLICK ORDER — the web's
 * BookingWindowPicker appends each month as it is tapped and never sorts — and
 * NOT, as this comment previously claimed, a deliberate expression of a window
 * that crosses the year. The corrected reason still says keep it: it is data the
 * user produced, and reordering it here would be a presentation habit quietly
 * rewriting what was entered. `[11,12,1,2]` sorted to `[1,2,11,12]` would also
 * render "January, February, November, December", turning one window into two —
 * so the effect the old comment described is real even though its account of the
 * cause was invented.
 *
 * A GAP IS LEGITIMATE. The picker is a free toggle grid over twelve rolling
 * months, so two clicks produce one; `formatBookingWindow` on the web collapses
 * any selection to a range without complaint, this returns null and the label
 * lists the months instead, and the database accepts it.
 *
 * Returns null when the months do not form a single contiguous run, because then
 * "start" and "end" are not answerable and a range label would be a fabrication.
 */
fun monthWindow(months: List<Int>): Pair<Int, Int>? {
    if (months.isEmpty()) return null
    if (months.size == 1) return months[0] to months[0]
    if (months.size > 12) return null
    // Each step must advance exactly one month, wrapping December to January.
    for (i in 1 until months.size) {
        val expected = if (months[i - 1] == 12) 1 else months[i - 1] + 1
        if (months[i] != expected) return null
    }
    return months.first() to months.last()
}

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

fun monthName(month: Int): String =
    MONTH_NAMES.getOrNull(month - 1) ?: month.toString()

/**
 * "November – February" for a contiguous run, otherwise the months as stored.
 *
 * Never sorted. A non-contiguous list is listed in its own order rather than being
 * forced into a range that would misdescribe it.
 *
 * A null list means the stored value could not be read as months at all, and that is
 * NOT the same as an empty booking window. Both used to render "None": one says Dean
 * takes no work, the other says nobody knows. The raw value is shown instead, because
 * a value that cannot be parsed is still evidence.
 */
fun availableMonthsLabel(months: List<Int>?, raw: String? = null): String = when {
    months == null -> raw?.let { "Unreadable: $it" } ?: "Could not be read"
    months.isEmpty() -> "None"
    else -> {
        val window = monthWindow(months)
        if (window != null && months.size > 1) {
            "${monthName(window.first)} – ${monthName(window.second)}"
        } else {
            months.joinToString(", ") { monthName(it) }
        }
    }
}

/**
 * A state, not a boolean. `true` on a screen is a value; this is an answer.
 *
 * The unreadable case is distinct from the unset one. `toBooleanStrictOrNull()`
 * answers null for "TRUE" and for "1" as readily as for a missing key, and rendering
 * both as "Not set" says the question was never answered when in fact the answer was
 * not understood.
 */
fun acceptingProjectsLabel(accepting: Boolean?, raw: String? = null): String = when {
    accepting == true -> "Open to new projects"
    accepting == false -> "Not taking new projects"
    raw != null -> "Unreadable: $raw"
    else -> "Not set"
}
