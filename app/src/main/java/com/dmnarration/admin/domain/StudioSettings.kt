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
 * Both must be read from `site_settings`. The defaults below exist only for the
 * case in §1.5 where they are deliberately not fetched — an editor session must
 * never issue that query, because RLS would reject it and produce an error that
 * looks like a bug rather than a policy.
 *
 * Falling back is not free, and the narration rate is where it hurts: the live
 * value is 5,000 against a 9,200 default, so a silent fallback under-reports
 * every booth figure by roughly 46% with numbers that look entirely plausible.
 * Which is exactly why the functions that consume these take them as required
 * parameters rather than reading a default of their own.
 */
data class StudioSettings(
    val wordsPerNarrationHour: Int,
    val wordsPerFinishedHour: Int,
    val dailyCapacityHours: Double,
    val maxBooksPerDay: Int,
    val heavyDayHours: Double,
)

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

/** Build settings from whatever `site_settings` rows exist, defaults for the rest. */
fun studioSettingsFrom(rows: Map<String, String>): StudioSettings {
    fun int(key: String, range: IntRange, default: Int): Int {
        val n = rows[key]?.trim()?.toIntOrNull() ?: return default
        return if (n in range) n else default
    }
    fun double(key: String, range: ClosedFloatingPointRange<Double>, default: Double): Double {
        val n = rows[key]?.trim()?.toDoubleOrNull() ?: return default
        return if (n in range) n else default
    }
    return StudioSettings(
        wordsPerNarrationHour = int(
            SettingKeys.WORDS_PER_NARRATION_HOUR,
            SettingLimits.wordsPerNarrationHour,
            DEFAULT_STUDIO_SETTINGS.wordsPerNarrationHour,
        ),
        wordsPerFinishedHour = int(
            SettingKeys.WORDS_PER_FINISHED_HOUR,
            SettingLimits.wordsPerFinishedHour,
            DEFAULT_STUDIO_SETTINGS.wordsPerFinishedHour,
        ),
        dailyCapacityHours = double(
            SettingKeys.DAILY_CAPACITY_HOURS,
            SettingLimits.dailyCapacityHours,
            DEFAULT_STUDIO_SETTINGS.dailyCapacityHours,
        ),
        maxBooksPerDay = int(
            SettingKeys.MAX_BOOKS_PER_DAY,
            SettingLimits.maxBooksPerDay,
            DEFAULT_STUDIO_SETTINGS.maxBooksPerDay,
        ),
        heavyDayHours = double(
            SettingKeys.HEAVY_DAY_HOURS,
            SettingLimits.heavyDayHours,
            DEFAULT_STUDIO_SETTINGS.heavyDayHours,
        ),
    )
}
