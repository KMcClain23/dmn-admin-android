package com.dmnarration.admin.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.max
import kotlin.math.min

/**
 * The derived-value rules, ported from narration-site's
 * `src/components/admin/board-card-utils.ts`.
 *
 * Pure and role-unaware by design. Capability gating happens at the call site:
 * a function that silently returns null because of ambient permissions is
 * untestable and surprising, and the caller is the only place that knows
 * whether a row it is holding came from a narrowed source.
 *
 * `today` is a parameter everywhere it is needed rather than being read from
 * the clock. The web reads `new Date()` inside `daysUntil`, which is fine for a
 * page that re-renders constantly and impossible to write a stable test
 * against. This is the one deliberate signature change from the source.
 */

// ─── dates and urgency ──────────────────────────────────────────────────────

/** Whole days from `today` to `date`. Negative when the date has passed. */
fun daysUntil(date: LocalDate, today: LocalDate): Int = today.daysUntil(date)

/**
 * Deadline colouring: red at a week out, amber at a month.
 *
 * A negative number satisfies `<= 7`, so an overdue deadline is red — which is
 * both correct and the reason there is no separate overdue branch here.
 */
fun completionUrgency(days: Int): Urgency = when {
    days <= 7 -> Urgency.RED
    days <= 30 -> Urgency.YELLOW
    else -> Urgency.DEFAULT
}

/**
 * First-15 colouring, which differs from the deadline rule in exactly one
 * branch: overdue goes red, but merely-soon goes amber rather than red.
 *
 * Lives in `BoardCardContent.tsx` on the web, not in board-card-utils, but it
 * belongs beside its peer.
 */
fun first15Urgency(days: Int): Urgency = when {
    days < 0 -> Urgency.RED
    days <= 7 -> Urgency.YELLOW
    else -> Urgency.DEFAULT
}

// ─── share and earnings ─────────────────────────────────────────────────────

/**
 * The fraction of a manuscript this narrator actually reads.
 *
 * Null means genuinely unknown rather than "all of it": multicast has no
 * default split, so guessing 100% would double every figure derived from it.
 * An explicit per-card percentage answers that question and therefore wins for
 * *any* format, multicast included.
 */
fun narratorShareOf(narrationFormat: String?, narratorSharePercent: Int?): Double? {
    if (narratorSharePercent != null) return narratorSharePercent / 100.0
    if (narrationFormat == "multicast") return null
    return if (narrationFormat == "duet" || narrationFormat == "dual") 0.5 else 1.0
}

/** Payment types that produce a per-finished-hour figure at all. */
private val EARNING_PAYMENT_TYPES = setOf("pfh", "rs_plus")

/**
 * The board card's earnings estimate.
 *
 * `wordsPerFinishedHour` is required and has no default, exactly as
 * `narrationPlan` requires its rate — see the comment at
 * `board-card-utils.ts:158`, which records three separate surfaces forgetting
 * to pass the equivalent argument and each then answering at a built-in rate
 * while everything else used the rate from Settings. A missing rate is a
 * compile error here rather than a number quietly wrong by a factor of two.
 *
 * Note that the web does *not* read this setting at all — five files hold their
 * own copy of 9,400. The stored value was changed to match rather than the code
 * rewired, so the two agree by value and not by wiring. Reading the setting is
 * what keeps this side correct if it is ever changed again. See
 * PROJECT_ROADMAP.md, Web Fix W1.
 *
 * Returns null whenever anything required is missing — callers hide the figure
 * rather than showing a zero.
 */
fun estimatedEarnings(
    wordCount: Int?,
    pfhRate: Double?,
    paymentType: String?,
    narrationFormat: String?,
    narratorSharePercent: Int?,
    wordsPerFinishedHour: Int,
): Double? {
    if (paymentType !in EARNING_PAYMENT_TYPES) return null
    // Zero is absent, not zero: both columns are NOT NULL DEFAULT 0 in Postgres,
    // so an unset rate arrives as 0 and "~$0" would be a lie rather than a fact.
    if (wordCount == null || wordCount <= 0) return null
    if (pfhRate == null || pfhRate <= 0.0) return null
    val share = narratorShareOf(narrationFormat, narratorSharePercent) ?: return null
    if (wordsPerFinishedHour <= 0) return null
    val hours = wordCount.toDouble() / wordsPerFinishedHour
    return hours * pfhRate * share
}

// ─── booth load ─────────────────────────────────────────────────────────────

/**
 * Statuses where the narrating is still ahead of you.
 *
 * Everything after Recording is post-production: editing, released and recast
 * all mean the mic work is finished or is not yours. Booth figures for those
 * are not merely useless but alarming — a book in Editing once showed "no
 * recording days left" in red, which reads as a missed deadline rather than a
 * job done.
 */
private val AT_MIC_STATUSES = setOf("contracted", "prepping", "recording")

fun stillAtMic(status: String?): Boolean = (status ?: "").trim() in AT_MIC_STATUSES

/** Monday to Friday, used when no recording days have been chosen. */
val DEFAULT_RECORDING_DAYS = setOf(1, 2, 3, 4, 5)

/**
 * Days in `from`..`to` inclusive that fall on a recording day.
 *
 * `days` holds JavaScript `Date.getDay()` numbers, 0 for Sunday, because that
 * is what the web stores. kotlinx-datetime counts Monday as 1 through Sunday as
 * 7, so Sunday is the one day where the two disagree — hence the `% 7`, which
 * maps 7 back to 0 and leaves Monday-to-Saturday alone.
 */
fun recordingDaysBetween(
    from: LocalDate,
    to: LocalDate,
    days: Set<Int> = DEFAULT_RECORDING_DAYS,
): Int {
    val wanted = days.ifEmpty { DEFAULT_RECORDING_DAYS }
    if (to < from) return 0
    var count = 0
    var d = from
    while (d <= to) {
        if (d.dayOfWeek.isoDayNumber % 7 in wanted) count++
        d = d.plus(1, DateTimeUnit.DAY)
    }
    return count
}

data class RecordingSchedule(
    /** Specific chosen days. When present these beat the weekday pattern. */
    val dates: List<LocalDate> = emptyList(),
    /** Weekday pattern in JS getDay() numbering. */
    val pattern: Set<Int> = DEFAULT_RECORDING_DAYS,
)

data class NarrationInput(
    val wordCount: Int?,
    val narrationFormat: String?,
    val narratorSharePercent: Int?,
    val deadline: LocalDate?,
    /**
     * Required, and required on purpose — see the note on estimatedEarnings.
     * This is the TIME rate (words per hour at the mic), not the money one.
     */
    val wordsPerNarrationHour: Int,
    val wordsRecorded: Int = 0,
    val schedule: RecordingSchedule = RecordingSchedule(),
    val today: LocalDate,
)

data class NarrationPlan(
    /**
     * Hours still to record. Remaining, not total: once recording has started
     * that is the only figure that answers anything.
     */
    val hours: Double,
    /** The whole job, ignoring progress. */
    val totalHours: Double,
    /** 0 to 1. Zero when nothing is recorded or nothing is known. */
    val fractionDone: Double,
    /** Recording days left including today. Null when there is no deadline. */
    val daysLeft: Int?,
    /** hours ÷ daysLeft. Null with no deadline, or when no day is left. */
    val hoursPerDay: Double?,
    /** The deadline has passed, or no recording day remains before it. */
    val overdue: Boolean,
)

/**
 * How long a book takes to narrate, and what that means per working day.
 *
 * Deliberately not gated on payment type the way estimatedEarnings is: a flat
 * fee book occupies exactly as much of the week as a per-finished-hour one.
 * Today counts as available, since it is a day you can still record in.
 */
fun narrationPlan(input: NarrationInput): NarrationPlan? {
    val wordCount = input.wordCount
    if (wordCount == null || wordCount <= 0) return null
    val share = narratorShareOf(input.narrationFormat, input.narratorSharePercent) ?: return null

    val rate = if (input.wordsPerNarrationHour > 0) {
        input.wordsPerNarrationHour
    } else {
        DEFAULT_STUDIO_SETTINGS.wordsPerNarrationHour
    }

    val shareWords = wordCount * share
    // Clamped at both ends: a recorded figure larger than the share would
    // produce negative hours left, which reads as time owed back.
    val done = min(max(input.wordsRecorded.toDouble(), 0.0), shareWords)
    val totalHours = shareWords / rate
    val fractionDone = if (shareWords > 0) done / shareWords else 0.0
    val hours = (shareWords - done) / rate

    val chosen = input.schedule.dates
    if (chosen.isNotEmpty()) {
        // Days that have not happened yet. A day already recorded is not a day
        // the remaining work can be spread over, whether or not it was used.
        val ahead = chosen.filter { it >= input.today && (input.deadline == null || it <= input.deadline) }
        return if (ahead.isEmpty()) {
            NarrationPlan(hours, totalHours, fractionDone, daysLeft = 0, hoursPerDay = null, overdue = true)
        } else {
            NarrationPlan(hours, totalHours, fractionDone, ahead.size, hours / ahead.size, overdue = false)
        }
    }

    val deadline = input.deadline
        ?: return NarrationPlan(hours, totalHours, fractionDone, daysLeft = null, hoursPerDay = null, overdue = false)

    if (deadline < input.today) {
        return NarrationPlan(hours, totalHours, fractionDone, daysLeft = 0, hoursPerDay = null, overdue = true)
    }

    val daysLeft = recordingDaysBetween(input.today, deadline, input.schedule.pattern)
    // A deadline can fall inside a stretch with no recording day in it at all —
    // a Sunday deadline for someone who records weekdays only. Dividing by zero
    // there would read as Infinity hours a day.
    if (daysLeft == 0) {
        return NarrationPlan(hours, totalHours, fractionDone, daysLeft = 0, hoursPerDay = null, overdue = true)
    }

    return NarrationPlan(hours, totalHours, fractionDone, daysLeft, hours / daysLeft, overdue = false)
}

// ─── co-narrators ───────────────────────────────────────────────────────────

// Strict, deliberately. Lenient parsing accepts unquoted tokens, which would
// swallow a bare name like `Zach Hoffman` as valid JSON instead of letting it
// fall through to the catch that handles exactly that storage shape.
private val strictJson = Json

/**
 * `board_cards.co_narrator` is a `text` column, not a Postgres array.
 *
 * Most rows hold a JSON-encoded array string; five live rows hold a bare,
 * non-JSON name. Both must work and neither may crash — there is no
 * Postgres-level array operator usable against this column as stored.
 */
fun parseCoNarrators(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        when (val parsed = strictJson.parseToJsonElement(raw)) {
            is JsonArray -> parsed.mapNotNull { el ->
                (el as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            }
            JsonNull -> emptyList()
            is JsonPrimitive -> listOf(parsed.content).filter { it.isNotBlank() }
            else -> emptyList()
        }
    } catch (_: Exception) {
        // A bare name is not malformed data, it is the other storage shape.
        listOf(raw)
    }
}
