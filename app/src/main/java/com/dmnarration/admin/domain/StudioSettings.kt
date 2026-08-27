package com.dmnarration.admin.domain

/**
 * The numbers the app does arithmetic with, ported from
 * narration-site's `src/lib/studio-settings.ts`.
 *
 * Two of these are near-identical numbers meaning entirely different things,
 * and they have drifted apart before:
 *
 *  - `wordsPerNarrationHour` — TIME. Manuscript words got through in an hour at
 *    the mic. Drives every booth figure.
 *  - `wordsPerFinishedHour` — MONEY. Words that make one hour of finished
 *    audio. Drives every earnings figure.
 *
 * Both must be read from `site_settings`. **Every field is nullable, and null means
 * "not known" rather than "use a default".**
 *
 * Falling back is not free, and the narration rate is where it hurts: the live value
 * is 5,000 against a 9,200 default, so a silent fallback under-reports every booth
 * figure by roughly 46% with numbers that look entirely plausible. That is not a
 * hypothetical — it is what this type did until 27 August 2026, and the comment here
 * claimed the consumers took these as required parameters "rather than reading a
 * default of their own" while `narrationPlan` read exactly such a default.
 *
 * The rule now: a figure that cannot be computed is ABSENT, not defaulted — the same
 * rule multicast already follows, where an unknown narrator share renders no
 * percentage rather than a guessed one.
 *
 * The fields fail INDEPENDENTLY. A malformed finished-hour value blanks the money
 * figures and leaves the booth figures alone, because the booth figures are still
 * fully known. Collapsing them into one "rates unknown" flag would hide working
 * information, which is the same disease pointing the other way.
 */
data class StudioSettings(
    val wordsPerNarrationHour: Int?,
    val wordsPerFinishedHour: Int?,
    val dailyCapacityHours: Double?,
    val maxBooksPerDay: Int?,
    val heavyDayHours: Double?,
)

/**
 * Why one setting could not be used.
 *
 * Carried per key so the Settings screen can show the rejection against the offending
 * value. An out-of-range number silently becoming 9,200 is precisely the disease W1
 * documents: a Settings page displaying a number the app does not use.
 */
sealed interface SettingIssue {
    val key: String

    data class Missing(override val key: String) : SettingIssue
    data class Unreadable(override val key: String, val raw: String) : SettingIssue
    data class OutOfRange(override val key: String, val raw: String, val allowed: String) : SettingIssue
}

/** What a read of the studio settings produced, including what it could not use. */
data class StudioSettingsRead(
    val settings: StudioSettings,
    val issues: List<SettingIssue>,
) {
    fun issueFor(key: String): SettingIssue? = issues.firstOrNull { it.key == key }
}

/**
 * Kept for tests and for a genuinely unconfigured install.
 *
 * NOT a runtime fallback any more. All seven keys exist in `site_settings`, so a
 * missing one means drift and never a fresh start, and substituting 9,200 for a live
 * 5,000 is never the right answer.
 */
val DEFAULT_STUDIO_SETTINGS = StudioSettings(
    wordsPerNarrationHour = 9200,
    wordsPerFinishedHour = 9400,
    dailyCapacityHours = 6.0,
    maxBooksPerDay = 2,
    heavyDayHours = 4.0,
)

/** `site_settings` keys, kept beside the type so the two cannot drift apart. */
object SettingKeys {
    const val WORDS_PER_NARRATION_HOUR = "studio_words_per_narration_hour"
    const val WORDS_PER_FINISHED_HOUR = "studio_words_per_finished_hour"
    const val DAILY_CAPACITY_HOURS = "studio_daily_capacity_hours"
    const val MAX_BOOKS_PER_DAY = "studio_max_books_per_day"
    const val HEAVY_DAY_HOURS = "studio_heavy_day_hours"

    /** Not a studio number — whether Dean is taking work at all. */
    const val ACCEPTING_PROJECTS = "accepting_projects"

    /** The booking window, stored in run order and NOT sorted. See [monthWindow]. */
    const val AVAILABLE_MONTHS = "available_months"

    /** Every key the table holds. Settings renders all of them. */
    val ALL = listOf(
        WORDS_PER_NARRATION_HOUR, WORDS_PER_FINISHED_HOUR, DAILY_CAPACITY_HOURS,
        MAX_BOOKS_PER_DAY, HEAVY_DAY_HOURS, ACCEPTING_PROJECTS, AVAILABLE_MONTHS,
    )
}

/**
 * Bounds, so a typo cannot quietly break every figure in the app.
 *
 * A words-per-hour of 0 divides by zero; one of 5 says a novel takes two years.
 * Deliberately wide — they catch a slipped keystroke, they do not have an
 * opinion about how fast anyone reads. Out-of-range values fall back to the
 * default for that field rather than being clamped, matching `parseSetting` on
 * the web: a number outside these bounds is not a number that was meant.
 */
object SettingLimits {
    val wordsPerNarrationHour = 1000..30000
    val wordsPerFinishedHour = 1000..30000
    val dailyCapacityHours = 1.0..16.0
    val maxBooksPerDay = 1..5
    val heavyDayHours = 1.0..16.0
}

/**
 * Parse the studio settings, reporting per key what could not be used.
 *
 * No defaulting. A key that is missing, unreadable or out of range yields null for
 * that field plus an issue naming it, so the consumer can blank exactly the figures
 * that depend on it and the Settings screen can show the rejection beside the value
 * that caused it.
 */
fun studioSettingsFrom(rows: Map<String, String>): StudioSettingsRead {
    val issues = mutableListOf<SettingIssue>()

    fun raw(key: String): String? {
        val v = rows[key]?.trim()
        if (v.isNullOrEmpty()) {
            issues += SettingIssue.Missing(key)
            return null
        }
        return v
    }

    fun int(key: String, range: IntRange): Int? {
        val v = raw(key) ?: return null
        val n = v.toIntOrNull()
        if (n == null) {
            issues += SettingIssue.Unreadable(key, v)
            return null
        }
        if (n !in range) {
            issues += SettingIssue.OutOfRange(key, v, "${range.first}–${range.last}")
            return null
        }
        return n
    }

    fun double(key: String, range: ClosedFloatingPointRange<Double>): Double? {
        val v = raw(key) ?: return null
        val n = v.toDoubleOrNull()
        if (n == null) {
            issues += SettingIssue.Unreadable(key, v)
            return null
        }
        if (n !in range) {
            issues += SettingIssue.OutOfRange(key, v, "${range.start}–${range.endInclusive}")
            return null
        }
        return n
    }

    val settings = StudioSettings(
        wordsPerNarrationHour = int(SettingKeys.WORDS_PER_NARRATION_HOUR, SettingLimits.wordsPerNarrationHour),
        wordsPerFinishedHour = int(SettingKeys.WORDS_PER_FINISHED_HOUR, SettingLimits.wordsPerFinishedHour),
        dailyCapacityHours = double(SettingKeys.DAILY_CAPACITY_HOURS, SettingLimits.dailyCapacityHours),
        maxBooksPerDay = int(SettingKeys.MAX_BOOKS_PER_DAY, SettingLimits.maxBooksPerDay),
        heavyDayHours = double(SettingKeys.HEAVY_DAY_HOURS, SettingLimits.heavyDayHours),
    )
    return StudioSettingsRead(settings, issues)
}
