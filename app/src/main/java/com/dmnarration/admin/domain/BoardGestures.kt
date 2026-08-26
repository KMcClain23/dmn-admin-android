package com.dmnarration.admin.domain

/**
 * The swipe-to-archive thresholds, ported from the web verbatim.
 *
 * Kept as arithmetic rather than gesture-modifier configuration so the numbers
 * can be tested without a device. The web's `useDrag` reports velocity in units
 * per millisecond and Compose reports it per second, which is exactly the sort
 * of conversion that silently makes a gesture twice as eager if it is done
 * inline and never checked.
 */
object SwipeToArchive {
    /** Past this displacement, releasing archives. Negative is leftward. */
    const val THRESHOLD_DP = -90f

    /** The card cannot be dragged further than this, in either direction. */
    const val MAX_SWIPE_DP = -110f

    /** The web's `vx > 0.5`, in units per millisecond. */
    const val FLICK_PER_MS = 0.5f

    /** Movement beyond this counts as a drag rather than a tap. */
    const val DRAG_SLOP_DP = 5f

    /** Leftward only, and never past the clamp. */
    fun clampOffset(offsetDp: Float): Float = offsetDp.coerceIn(MAX_SWIPE_DP, 0f)

    /**
     * Whether releasing here archives the card.
     *
     * Either a slow drag past the threshold or a fast leftward flick, matching
     * `pastThreshold || fastFlick`. A flick counts however short it was, which
     * is the point of having it: the gesture should not require the full 90dp
     * when the intent is unambiguous from the speed.
     *
     * `velocityDpPerSecond` is negative leftward, so the flick test is a lower
     * bound rather than the web's magnitude comparison against a direction flag.
     */
    fun shouldArchive(offsetDp: Float, velocityDpPerSecond: Float): Boolean {
        val pastThreshold = offsetDp < THRESHOLD_DP
        val fastFlick = velocityDpPerSecond < -(FLICK_PER_MS * 1000f)
        return pastThreshold || fastFlick
    }
}

/**
 * Why a card was archived.
 *
 * "Recasted" here and the `recast` status are sequential, not two names for one
 * thing: the status is set while a partial fee is still owed so the card stays
 * on Payments where it can be invoiced and chased, and the card is archived only
 * once the money has landed. Archiving first hides it from Payments entirely and
 * takes the unraised invoice with it.
 */
enum class ArchiveReason(val stored: String, val label: String) {
    RECASTED("recasted", "Recasted"),
    CANCELED("canceled", "Canceled"),
    OTHER("other", "Other"),
}

/**
 * What the long-press menu offers for a card in a given status.
 *
 * The card's current status is never offered — moving a card to where it
 * already is would spend a write to change nothing, and would look like the
 * gesture had failed.
 */
data class BoardAction(val label: String, val status: String?) {
    val isArchive: Boolean get() = status == null
}

const val STATUS_RELEASED = "released"

private val MOVES = listOf(
    "contracted" to "Move to Pipeline",
    "prepping" to "Move to Prepping",
    "recording" to "Move to Recording",
    "editing" to "Move to Editing",
)

fun actionsFor(card: BoardCard): List<BoardAction> = buildList {
    MOVES.filter { it.first != card.status }
        .forEach { (status, label) -> add(BoardAction(label, status)) }
    add(BoardAction("Mark as Released", STATUS_RELEASED))
    add(BoardAction("Archive", null))
}
