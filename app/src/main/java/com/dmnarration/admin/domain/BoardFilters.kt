package com.dmnarration.admin.domain

import kotlinx.datetime.LocalDate

/**
 * Bucketing, sorting and the due-soon chips, ported from narration-site's
 * `src/components/board/board-filters.ts`.
 *
 * Client-side, exactly as on the web: the query fetches the same twenty rows
 * either way, and doing this here means the chips respond without a round trip.
 */

/**
 * Which Pipeline section a card belongs in.
 *
 * Note what `days <= 7` does to an overdue card: a negative number satisfies
 * it, so an overdue book lands in *This Week*, not *Later*. That is deliberate
 * — an overdue book is the most urgent thing on the board, not the least — and
 * it reads as a bug on the device if you are not expecting it. Only a card with
 * no deadline at all goes to *Later*.
 */
fun pipelineBucketFor(card: BoardCard, today: LocalDate): PipelineBucket {
    val deadline = card.deadline ?: return PipelineBucket.LATER
    val days = daysUntil(deadline, today)
    return when {
        days <= 7 -> PipelineBucket.THIS_WEEK
        days <= 30 -> PipelineBucket.THIS_MONTH
        else -> PipelineBucket.LATER
    }
}

/**
 * Deadline ascending, undated last, ties broken by newest first.
 *
 * "Undated last" is why this cannot just compare nullable dates: a null has to
 * sort as `+∞` rather than as absent, or every card without a deadline would
 * lead the column.
 */
fun compareCards(a: BoardCard, b: BoardCard): Int {
    val ad = a.deadline
    val bd = b.deadline
    if (ad != bd) {
        if (ad == null) return 1
        if (bd == null) return -1
        val byDeadline = ad.compareTo(bd)
        if (byDeadline != 0) return byDeadline
    }
    // Newest first on a tie, so the thing most recently taken on leads.
    return b.createdAt.compareTo(a.createdAt)
}

/**
 * The due-soon chips answer "what needs MY attention".
 *
 * Once a book moves to editing the remaining deadline is the editor's
 * responsibility, not the narrator's, so an editing card never matches either
 * chip — it still renders normally in its own subgroup, it just cannot be
 * highlighted as due soon.
 */
private val ATTENTION_STATUSES = setOf("contracted", "prepping", "recording")

fun passesDateFilter(card: BoardCard, filter: DateFilter?, today: LocalDate): Boolean {
    if (filter == null) return true
    val deadline = card.deadline ?: return false
    if (card.status !in ATTENTION_STATUSES) return false
    val days = daysUntil(deadline, today)
    return if (filter == DateFilter.WEEK) days <= 7 else days <= 30
}

/** Pipeline cards grouped and sorted, every bucket present even when empty. */
fun bucketPipeline(cards: List<BoardCard>, today: LocalDate): Map<PipelineBucket, List<BoardCard>> =
    PipelineBucket.entries.associateWith { bucket ->
        cards.filter { pipelineBucketFor(it, today) == bucket }.sortedWith(::compareCards)
    }

/** In-Production cards grouped by status and sorted, every subgroup present. */
fun bucketProduction(cards: List<BoardCard>): Map<ProductionSubgroup, List<BoardCard>> =
    ProductionSubgroup.entries.associateWith { subgroup ->
        cards.filter { it.status == subgroup.status }.sortedWith(::compareCards)
    }

/**
 * The statuses the board shows, matching `board_for_session()`'s where clause.
 *
 * Needed because `isPipeline` is defined as "not production", so a card moved to
 * 'released' would otherwise fall through to the Pipeline tab rather than
 * leaving the board. The read never returns one; an optimistic Mark as Released
 * creates one locally, which is the only way this list is ever exercised.
 */
val ACTIVE_STATUSES = setOf("contracted", "prepping", "recording", "editing")

fun isActive(card: BoardCard): Boolean = card.status in ACTIVE_STATUSES

/** The three statuses that mean a book is in production. */
private val PRODUCTION_STATUSES = setOf("prepping", "recording", "editing")

/**
 * Which tab a card belongs to.
 *
 * Deliberately a negative test, copied from the web rather than simplified to
 * `status == "contracted"`. The two are equivalent for the four statuses the
 * board queries today, but they diverge the moment a fifth is added: the
 * negative form puts an unrecognised status in Pipeline, where it is at least
 * visible, while the positive form would drop it from both tabs and leave a
 * card that exists in the query and appears nowhere on screen.
 */
fun isProduction(card: BoardCard): Boolean = card.status in PRODUCTION_STATUSES

fun isPipeline(card: BoardCard): Boolean = !isProduction(card)
