package com.dmnarration.admin.domain

/**
 * What a write actually did, with the three outcomes kept apart.
 *
 * The middle one is the whole reason this type exists. The two write gates fail
 * differently: an ungranted column raises `permission denied`, but a row RLS
 * refuses comes back as a perfectly successful statement that affected nothing
 * — PostgREST answers 200 with an empty array. A client that reads "no
 * exception" as "saved" shows every optimistic update sticking forever for
 * someone whose access was revoked.
 *
 * That is the same family as Stage 1's bugs 3 to 5, inverted: there, a
 * transport-shaped answer was read as an authorization one; here, an
 * authorization answer arrives wearing success. So a write reports what came
 * back, never whether it threw.
 */
sealed interface WriteOutcome<out T> {
    /** The server returned the row. Its copy is the truth, not the optimistic guess. */
    data class Saved<T>(val row: T) : WriteOutcome<T>

    /** Success, zero rows: RLS refused this row. A write that returns no row has not happened. */
    data object Refused : WriteOutcome<Nothing>

    /** The request failed outright — network, permission denied, anything thrown. */
    data class Failed(val message: String) : WriteOutcome<Nothing>
}

/**
 * Anything this file can reconcile: a row the server can return by id.
 *
 * The row type is a parameter rather than `BoardCard` because Stage 6's archive
 * screen performs a write with exactly these three outcomes over a different
 * row. 6C.2 requires "full Stage 2 write discipline… not negotiable", and the
 * only way to require it is to reuse it — a second copy of this reducer would
 * be a second thing to keep correct, which is what 6A was about.
 *
 * `id` is a supertype rather than a selector parameter so every existing call
 * site compiles unchanged; the compiler still checks that a new row type
 * actually has one.
 */
interface Identified {
    val id: String
}

/**
 * One optimistic mutation in flight.
 *
 * `previous` is the card exactly as it was before the optimistic apply, kept so
 * a rollback restores it verbatim rather than recomputing a "default". A toggle
 * that was already complete must roll back to complete.
 */
data class PendingWrite<out T>(
    val cardId: String,
    val previous: T,
)

/** What the reducer decided: the new list, anything to say, and whether to re-fetch. */
data class WriteReduction<T>(
    val cards: List<T>,
    val error: String? = null,
    val refresh: Boolean = false,
)

/**
 * Apply a change locally before the server has agreed to it.
 *
 * Returns the updated list and the token needed to undo it. A null token means
 * the card was not present, in which case nothing was applied and there is
 * nothing to reconcile.
 */
fun <T : Identified> applyOptimistic(
    cards: List<T>,
    cardId: String,
    edit: (T) -> T,
): Pair<List<T>, PendingWrite<T>?> {
    val existing = cards.firstOrNull { it.id == cardId }
        ?: return cards to null
    val updated = cards.map { if (it.id == cardId) edit(it) else it }
    return updated to PendingWrite(cardId, existing)
}

/**
 * Reconcile the optimistic guess with what the server said.
 *
 * Saved replaces the local row with the server's, because a trigger may have
 * changed more than the client asked for — `released_at` gets stamped and
 * `updated_at` moves, and neither is something the app computes. Trusting the
 * optimistic guess here would leave the screen disagreeing with the database in
 * exactly the fields the database owns.
 */
fun <T : Identified> reconcileWrite(
    cards: List<T>,
    pending: PendingWrite<T>,
    outcome: WriteOutcome<T>,
): WriteReduction<T> = when (outcome) {
    is WriteOutcome.Saved -> WriteReduction(
        cards = cards.map { if (it.id == pending.cardId) outcome.row else it },
    )

    is WriteOutcome.Refused -> WriteReduction(
        cards = rollback(cards, pending),
        error = "You no longer have permission to make that change.",
        // The board is re-fetched because a refusal means this session's view of
        // what it may see has changed, not just this one row.
        refresh = true,
    )

    is WriteOutcome.Failed -> WriteReduction(
        cards = rollback(cards, pending),
        error = outcome.message,
        // Deliberately not refreshing: the request never landed, so the cards
        // already loaded remain the best information available.
        refresh = false,
    )
}

/** Restores the captured card verbatim — never a recomputed default. */
private fun <T : Identified> rollback(cards: List<T>, pending: PendingWrite<T>): List<T> =
    cards.map { if (it.id == pending.cardId) pending.previous else it }
