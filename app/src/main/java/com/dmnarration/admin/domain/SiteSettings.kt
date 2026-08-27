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
    val availableMonths: List<Int>,
    val studio: StudioSettings,
)

/**
 * The booking window, in the order it actually runs.
 *
 * Stored as `[11, 12, 1, 2]` — November through February, a contiguous window that
 * crosses the year. **Sorting it numerically renders "January, February, November,
 * December", which turns one window into two and is wrong.** The stored order already
 * expresses the run, so it is preserved rather than tidied; sorting here would be a
 * presentation habit quietly changing the meaning.
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
 */
fun availableMonthsLabel(months: List<Int>): String {
    if (months.isEmpty()) return "None"
    val window = monthWindow(months)
    return if (window != null && months.size > 1) {
        "${monthName(window.first)} – ${monthName(window.second)}"
    } else {
        months.joinToString(", ") { monthName(it) }
    }
}

/** A state, not a boolean. `true` on a screen is a value; this is an answer. */
fun acceptingProjectsLabel(accepting: Boolean?): String = when (accepting) {
    true -> "Open to new projects"
    false -> "Not taking new projects"
    null -> "Not set"
}
