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

/**
 * Why a book is on today's agenda. Declaration order IS priority order.
 *
 * Late first because it is the plan that already failed; recording-today is a plan
 * still on track. A book qualifying for several reasons is grouped under its highest
 * and carries the rest as chips — it is one book, and rendering it twice on a
 * two-item screen is how the first cut of this looked.
 */
enum class AgendaReason { LATE, RECORDING_TODAY, DUE_SOON }

/**
 * One BOOK on the agenda, with the set of reasons it qualified today.
 *
 * Deliberately a book plus reasons rather than a row in a filtered per-section list.
 * The filtered shape let A Cowboy's Runaway occupy two of the screen's two slots with
 * near-identical content, and it would have done the same again the moment
 * `first15_due` landed. As a reason set, a new commitment type is another member —
 * not another section that reintroduces the duplication.
 */
data class AgendaItem(
    val card: BoardCard,
    val reasons: Set<AgendaReason>,
    /** Hours today's plan asks of this book. Only meaningful when it is recording today. */
    val hours: Double?,
) {
    /** The group this card is rendered under: its highest-priority reason. */
    val primary: AgendaReason get() = AgendaReason.entries.first { it in reasons }

    /** Everything else it qualified for, rendered as chips on the same card. */
    val secondary: List<AgendaReason>
        get() = AgendaReason.entries.filter { it in reasons && it != primary }
}

data class Agenda(
    val today: LocalDate,
    /** One entry per distinct book. See [AgendaItem]. */
    val items: List<AgendaItem>,
    val weekHours: Double,
    val monthHours: Double,
) {
    fun grouped(reason: AgendaReason): List<AgendaItem> = items.filter { it.primary == reason }

    val isEmpty: Boolean get() = items.isEmpty()
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
    val horizon = today.plus(DUE_SOON_DAYS, DateTimeUnit.DAY)

    var weekHours = 0.0
    var monthHours = 0.0

    val items = mutableListOf<AgendaItem>()

    for (card in considered) {
        val dates = card.recordingDates
        var perDay: Double? = null

        // A book whose hours are merely spread across every weekday was never
        // planned for today, and counting it would turn the agenda into a list of
        // everything, always.
        if (dates.isNotEmpty()) {
            perDay = narrationPlan(
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
            )?.hoursPerDay

            // The week and month totals are the same per-day figure counted across
            // every day the book occupies inside each span. Days already behind
            // today are excluded: what is already spent is not a decision anyone can
            // still make.
            if (perDay != null) {
                for (date in dates) {
                    if (date < today) continue
                    if (date <= weekEnd) weekHours += perDay
                    if (date <= monthEnd) monthHours += perDay
                }
            }
        }

        // Every reason this book qualified, gathered before anything is grouped.
        val reasons = buildSet {
            if (isLate(card, today)) add(AgendaReason.LATE)
            if (today in dates) add(AgendaReason.RECORDING_TODAY)
            val deadline = card.deadline
            if (deadline != null && deadline >= today && deadline <= horizon) {
                add(AgendaReason.DUE_SOON)
            }
        }

        if (reasons.isNotEmpty()) items += AgendaItem(card, reasons, perDay)
    }

    return Agenda(
        today = today,
        // Soonest deadline first within a group; a book with no deadline sorts last.
        items = items.sortedWith(compareBy({ it.primary.ordinal }, { it.card.deadline })),
        weekHours = weekHours,
        monthHours = monthHours,
    )
}

/**
 * How a deadline reads relative to today.
 *
 * ONE formatter, used by the due-soon rows, the late rows and the chips alike. Two
 * would disagree on a boundary day — "due today" against "0 days late" — and only one
 * of them would be right.
 */
fun relativeDeadline(deadline: LocalDate, today: LocalDate): String {
    val days = daysUntil(deadline, today)
    return when {
        days < -1 -> "${-days} days late"
        days == -1 -> "1 day late"
        days == 0 -> "due today"
        days == 1 -> "due tomorrow"
        else -> "in $days days"
    }
}

/**
 * How much of a book is recorded, as a fraction in 0..1, or null when unknowable.
 *
 * THE DENOMINATOR IS THE SHARE, NOT THE MANUSCRIPT. `narrationPlan` subtracts
 * `words_recorded` from `wordCount × narratorShare`, so `words_recorded` means the
 * words THIS narrator recorded, not the book's total. Dividing by `word_count`
 * instead put two different percentages of the same book on one screen: A Cowboy's
 * Runaway read 20% on the progress bar and 40% by its remaining hours, because a
 * duet is half a manuscript.
 *
 * The share comes from [narratorShareOf] — the same function `narrationPlan` uses —
 * rather than being re-derived here. Two derivations of one rule is how the two
 * percentages came to disagree in the first place, and `narrator_share_percent` is
 * populated on 1 card in 33, so the inference from `narration_format` is the real
 * mechanism and must not be duplicated.
 *
 * Null when the share is unknown. Multicast has no default split, and guessing an
 * equal one would write a confident wrong number over a column Dean has just started
 * maintaining. Rendering nothing is the honest answer.
 *
 * Still clamped at both ends: `words_recorded` is maintained by hand, so a figure
 * above the share is a matter of time, and it must read as finished rather than as
 * more-than-finished.
 */
fun recordedFraction(card: BoardCard): Double? {
    val total = card.wordCount ?: return null
    if (total <= 0) return null
    val share = narratorShareOf(card.narrationFormat, card.narratorSharePercent) ?: return null
    val shareWords = total * share
    if (shareWords <= 0) return null
    val done = (card.wordsRecorded ?: 0).coerceAtLeast(0)
    return (done / shareWords).coerceIn(0.0, 1.0)
}
