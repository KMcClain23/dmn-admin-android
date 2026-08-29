package com.dmnarration.admin.domain

import kotlin.time.Instant

/**
 * A released book, in the shape the list renders.
 *
 * Lean by design: tapping a row opens `card_detail()`, exactly as the board
 * does, so this carries what a row shows and nothing else.
 *
 * `archivedAt` is here even though the Released screen never shows an archived
 * book, and that is the point. `released_for_session()` returns released cards
 * INCLUDING archived ones, and both questions the app can ask — "how many books
 * has Dean released" and "which released books are visible" — are answered from
 * these same rows by applying a predicate at the point of use. The web keeps two
 * routes with two different archived predicates that agree today only because no
 * released book has ever been archived; there is deliberately no second query
 * here that could drift from this one.
 */
data class ReleasedBook(
    override val id: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val releasedAt: Instant?,
    /** Null means nobody knows, which is not the same as a rating of zero. */
    val amazonRating: Double?,
    /** Null means unknown; 0 means Amazon says there are none yet. */
    val amazonReviewCount: Int?,
    val audibleLink: String?,
    val archivedAt: Instant?,
) : Identified

/**
 * What the Released list shows: released and not archived.
 *
 * The same predicate the web's `/api/released` route uses, written once.
 */
fun visibleReleased(all: List<ReleasedBook>): List<ReleasedBook> =
    all.filter { it.archivedAt == null }

/**
 * The two counts, from one list, each named for the question it answers.
 *
 * They differ the moment a released book is archived. Returning both together
 * from the same rows means a caller has to say which one it means, rather than
 * picking up whichever number a nearby query happened to produce.
 */
data class ReleasedCounts(val allTime: Int, val visible: Int) {
    val agree: Boolean get() = allTime == visible
}

fun releasedCounts(all: List<ReleasedBook>): ReleasedCounts =
    ReleasedCounts(allTime = all.size, visible = visibleReleased(all).size)

/**
 * The rating, or nothing.
 *
 * Null renders as absent rather than as "0.0". A book nobody has rated and a
 * book rated zero are different facts, and the column is nullable with no
 * default precisely so they can be told apart.
 */
fun ratingLabel(rating: Double?): String? {
    if (rating == null) return null
    // One decimal, matching how Amazon prints it. 5.0 stays "5.0", not "5".
    val rounded = kotlin.math.round(rating * 10.0) / 10.0
    return rounded.toString()
}

/**
 * "14 reviews", "1 review", "No reviews yet" — or nothing at all.
 *
 * Zero is a fact Amazon reported and is stated. Null is the absence of a fact
 * and is not stated, because "No reviews yet" would be an assertion nobody made.
 */
fun reviewCountLabel(count: Int?): String? = when {
    count == null -> null
    count <= 0 -> "No reviews yet"
    count == 1 -> "1 review"
    else -> "$count reviews"
}

/**
 * Words narrated across the career, in three categories.
 *
 * Computed by career_totals_for_session(), NOT here. The function is the one
 * place the categories are decided; this type carries its answer. A second
 * implementation in Kotlin would be free to filter differently from the
 * per-book display, which is the released-count divergence again.
 *
 * The third category is the point. [notCountedBooks] is named on screen so the
 * number can be acted on rather than silently lowering the total — nine of the
 * twenty-three today are released books with no word count, and entering them
 * would make the figure real.
 */
data class CareerTotals(
    val exactWords: Int,
    val exactBooks: Int,
    val estimatedWords: Int,
    val estimatedBooks: Int,
    val notCountedBooks: Int,
    val notCountedTitles: List<String>,
    val totalBooks: Int,
) {
    /** Everything the app is willing to claim was narrated. */
    val countedWords: Int get() = exactWords + estimatedWords

    /**
     * The categories must account for every non-archived book. The database
     * asserts this too and refuses rather than returning a short total; this is
     * the client refusing to DISPLAY one, so a stale build cannot show a figure
     * the server would have rejected.
     */
    val partitionHolds: Boolean
        get() = exactBooks + estimatedBooks + notCountedBooks == totalBooks
}
