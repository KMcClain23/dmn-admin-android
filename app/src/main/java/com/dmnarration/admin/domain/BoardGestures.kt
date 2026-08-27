package com.dmnarration.admin.domain

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
