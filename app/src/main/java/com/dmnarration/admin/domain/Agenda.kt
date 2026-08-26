package com.dmnarration.admin.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The statuses an agenda considers, defined once.
 *
 * Carried across from `api/agenda/route.ts:16`, whose comment says it better than
 * any paraphrase and is therefore quoted rather than rewritten:
 *
 * > Editing is deliberately absent. A book past the mic still has work in it, but
 * > none of it happens in the booth, and an agenda that lists it is telling you to
 * > record something you have already recorded.
 *
 * Three independent definitions in the web admin already agree on this exact set —
 * `ACTIVE_STATUSES` in `api/agenda/route.ts`, `AT_MIC_STATUSES` in
 * `board-card-utils.ts`, and `ATTENTION_STATUSES` in `board-filters.ts`. That is as
 * canonical as the codebase gets, and it is a SET, not a ranking.
 *
 * There is deliberately no status ordering anywhere in this app. `CardEditModal`'s
 * `STATUSES` array looks like one but places `recast` after `released`, so ranking by
 * it would score a recast card as further along than a released one. `recast` is an
 * off-ramp, not a stage.
 */
val PRE_DELIVERY = setOf("contracted", "prepping", "recording")

/** How far ahead a deadline is worth seeing beside today's work. */
const val DUE_SOON_DAYS = 7

/** A book scheduled for today, with the hours its plan asks of today. */
data class AgendaItem(
    val card: BoardCard,
    val hours: Double?,
)

data class Agenda(
    val today: LocalDate,
    /** Books with today actually chosen as a recording day. */
    val recordingToday: List<AgendaItem>,
    /** Deadlines inside the next [DUE_SOON_DAYS], soonest first. */
    val dueSoon: List<BoardCard>,
    /** Past the delivery deadline and not yet delivered. See [isLate]. */
    val late: List<BoardCard>,
    val weekHours: Double,
    val monthHours: Double,
) {
    val isEmpty: Boolean
        get() = recordingToday.isEmpty() && dueSoon.isEmpty() && late.isEmpty()
}

/**
 * Monday-first, ending Sunday — matching the web's calendars.
 *
 * The route computes this as `(getDay() + 6) % 7` then adds `6 - dow`, which is the
 * same arithmetic expressed against JavaScript's Sunday-first week.
 */
fun endOfWeek(today: LocalDate): LocalDate =
    today.plus(7 - today.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)

fun endOfMonth(today: LocalDate): LocalDate =
    LocalDate(today.year, today.month, 1)
        .plus(1, DateTimeUnit.MONTH)
        .minus(1, DateTimeUnit.DAY)

/**
 * Past the audio delivery deadline, and not yet delivered.
 *
 * `deadline` is the **audio delivery** deadline, so reaching `editing` means it was
 * met. A card sitting in `editing` past its deadline is delivered and awaiting
 * release — it is not late, and rendering it as late is the specific defect this rule
 * exists to prevent. Six cards are in exactly that state today.
 *
 * This is the one thing added to the route rather than ported from it. The route's
 * `dueSoon` filters `deadline >= today`, so a card that slips while still
 * pre-delivery drops out of it and — unless it happens to be recording today —
 * appears nowhere at all. That is a dropped case, not a design choice.
 */
fun isLate(card: BoardCard, today: LocalDate): Boolean =
    card.status in PRE_DELIVERY && card.deadline != null && card.deadline < today

/**
 * Everything the agenda shows, from cards the board has already fetched.
 *
 * Deliberately takes the board's cards rather than issuing a query: every field an
 * agenda wants is already in `board_for_session()`'s eighteen columns. If this ever
 * appears to need a column the board does not fetch, that is a decision to widen the
 * fetch, not a step to take quietly.
 */
fun buildAgenda(
    cards: List<BoardCard>,
    settings: StudioSettings,
    today: LocalDate,
): Agenda {
    // board_for_session() already excludes archived rows and everything outside the
    // four active statuses; this narrows to the three the agenda is about.
    val considered = cards.filter { it.status in PRE_DELIVERY }

    val weekEnd = endOfWeek(today)
    val monthEnd = endOfMonth(today)

    var weekHours = 0.0
    var monthHours = 0.0

    val recordingToday = mutableListOf<AgendaItem>()

    for (card in considered) {
        val dates = card.recordingDates
        // A book whose hours are merely spread across every weekday was never
        // planned for today, and counting it would turn the agenda into a list of
        // everything, always.
        if (dates.isEmpty()) continue

        val plan = narrationPlan(
            NarrationInput(
                wordCount = card.wordCount,
                narrationFormat = card.narrationFormat,
                narratorSharePercent = card.narratorSharePercent,
                deadline = card.deadline,
                wordsPerNarrationHour = settings.wordsPerNarrationHour,
                wordsRecorded = card.wordsRecorded ?: 0,
                schedule = RecordingSchedule(dates = dates),
                today = today,
            ),
        )
        val perDay = plan?.hoursPerDay

        // The week and month totals are the same per-day figure counted across every
        // day the book occupies inside each span. Days already behind today are
        // excluded: what is already spent is not a decision anyone can still make.
        if (perDay != null) {
            for (date in dates) {
                if (date < today) continue
                if (date <= weekEnd) weekHours += perDay
                if (date <= monthEnd) monthHours += perDay
            }
        }

        if (today in dates) recordingToday += AgendaItem(card, perDay)
    }

    val horizon = today.plus(DUE_SOON_DAYS, DateTimeUnit.DAY)
    val dueSoon = considered
        .filter { it.deadline != null && it.deadline >= today && it.deadline <= horizon }
        .sortedBy { it.deadline }

    val late = considered
        .filter { isLate(it, today) }
        .sortedBy { it.deadline }

    return Agenda(
        today = today,
        recordingToday = recordingToday,
        dueSoon = dueSoon,
        late = late,
        weekHours = weekHours,
        monthHours = monthHours,
    )
}

/**
 * How much of a book is recorded, as a fraction in 0..1, or null when unknowable.
 *
 * Clamped at both ends deliberately. `word_count` can be zero or absent, which would
 * divide by zero and render as NaN; and `words_recorded` is about to be maintained by
 * hand, so a figure larger than the total is a matter of time. Over-100% clamps to
 * 1.0 rather than reading as more-than-finished, and a missing or zero word count
 * returns null so the caller renders nothing rather than a confident 0%.
 */
fun recordedFraction(card: BoardCard): Double? {
    val total = card.wordCount ?: return null
    if (total <= 0) return null
    val done = (card.wordsRecorded ?: 0).coerceAtLeast(0)
    return (done.toDouble() / total).coerceIn(0.0, 1.0)
}
