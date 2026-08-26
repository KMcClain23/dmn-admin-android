package com.dmnarration.admin.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/**
 * The optimistic-update reducer, and specifically the outcome that does not
 * look like a failure.
 *
 * An RLS-refused write is success with zero rows, not an exception. Everything
 * below asserts on what came back rather than on whether anything threw,
 * because a reducer that cannot tell Refused from Saved will show a revoked
 * user their change sticking forever.
 */
class BoardWriteTest {

    private val incomplete = card(id = "a").copy(first15Complete = false)
    private val complete = card(id = "b").copy(first15Complete = true)
    private val other = card(id = "z", status = "editing")
    private val board = listOf(incomplete, complete, other)

    private fun toggleFirst15(c: BoardCard) = c.copy(first15Complete = !c.first15Complete)

    // ─── applying ───────────────────────────────────────────────────────────

    @Test fun `an optimistic apply changes only the target card`() {
        val (after, pending) = applyOptimistic(board, "a", ::toggleFirst15)
        assertNotNull(pending)
        assertTrue(after.single { it.id == "a" }.first15Complete)
        assertEquals("the others must be untouched", complete, after.single { it.id == "b" })
        assertEquals(other, after.single { it.id == "z" })
    }

    @Test fun `the pending token captures the card as it was, not as it became`() {
        val (_, pending) = applyOptimistic(board, "b", ::toggleFirst15)
        assertEquals(
            "previous must be the pre-apply value — this is what rollback restores",
            true,
            pending!!.previous.first15Complete,
        )
    }

    @Test fun `an unknown card applies nothing and yields no token`() {
        val (after, pending) = applyOptimistic(board, "nope", ::toggleFirst15)
        assertEquals(board, after)
        assertNull("nothing was applied, so there is nothing to reconcile", pending)
    }

    // ─── saved ──────────────────────────────────────────────────────────────

    /**
     * The server's row wins, not the optimistic guess. A trigger may have
     * stamped released_at or moved updated_at, and the app computes neither —
     * keeping the guess would leave the screen disagreeing with the database in
     * exactly the fields the database owns.
     */
    @Test fun `saved replaces the local row with the server's copy`() {
        val (after, pending) = applyOptimistic(board, "a", ::toggleFirst15)
        val serverRow = after.single { it.id == "a" }.copy(
            status = "released",
            // the client never asked for either of these
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        val result = reconcileWrite(after, pending!!, WriteOutcome.Saved(serverRow))

        assertEquals(serverRow, result.cards.single { it.id == "a" })
        assertNull(result.error)
        assertFalse(result.refresh)
    }

    @Test fun `saved does not disturb the other cards`() {
        val (after, pending) = applyOptimistic(board, "a", ::toggleFirst15)
        val row = after.single { it.id == "a" }
        val result = reconcileWrite(after, pending!!, WriteOutcome.Saved(row))
        assertEquals(3, result.cards.size)
        assertEquals(complete, result.cards.single { it.id == "b" })
    }

    // ─── refused: success with zero rows ────────────────────────────────────

    @Test fun `refused rolls back, explains, and asks for a refresh`() {
        val (after, pending) = applyOptimistic(board, "a", ::toggleFirst15)
        assertTrue("precondition: the optimistic value is applied", after.single { it.id == "a" }.first15Complete)

        val result = reconcileWrite(after, pending!!, WriteOutcome.Refused)

        assertFalse(
            "a write that returned no row has not happened — it must not stick",
            result.cards.single { it.id == "a" }.first15Complete,
        )
        assertEquals(incomplete, result.cards.single { it.id == "a" })
        assertEquals("You no longer have permission to make that change.", result.error)
        assertTrue("a refusal means this session's view has changed, so re-fetch", result.refresh)
    }

    /**
     * DoD 14. The rollback of a non-default prior value.
     *
     * Card "b" starts complete. Toggling it optimistically makes it incomplete;
     * a refusal must restore *complete*, not the false a naive reset would give.
     * A reducer that recomputed a default instead of restoring the captured card
     * would pass every other test here and silently wipe this one.
     */
    @Test fun `refused restores a non-default prior value`() {
        val (after, pending) = applyOptimistic(board, "b", ::toggleFirst15)
        assertFalse("precondition: optimistically un-completed", after.single { it.id == "b" }.first15Complete)

        val result = reconcileWrite(after, pending!!, WriteOutcome.Refused)

        assertTrue(
            "must roll back to complete, which is not the default",
            result.cards.single { it.id == "b" }.first15Complete,
        )
        assertEquals(complete, result.cards.single { it.id == "b" })
    }

    @Test fun `refused restores every field, not just the toggled one`() {
        val edited = { c: BoardCard -> c.copy(status = "released", deadline = LocalDate.parse("2030-01-01")) }
        val (after, pending) = applyOptimistic(board, "z", edited)
        val result = reconcileWrite(after, pending!!, WriteOutcome.Refused)
        assertEquals(other, result.cards.single { it.id == "z" })
    }

    // ─── failed ─────────────────────────────────────────────────────────────

    @Test fun `failed rolls back and keeps the cards without re-fetching`() {
        val (after, pending) = applyOptimistic(board, "a", ::toggleFirst15)
        val result = reconcileWrite(after, pending!!, WriteOutcome.Failed("No connection."))

        assertEquals(incomplete, result.cards.single { it.id == "a" })
        assertEquals("No connection.", result.error)
        assertFalse(
            "the request never landed, so the loaded cards are still the best information",
            result.refresh,
        )
        assertEquals(3, result.cards.size)
    }

    /**
     * The distinction the whole type exists for. Same starting board, same
     * optimistic apply — only the outcome differs, and Saved and Refused must
     * not converge.
     */
    @Test fun `refused and saved are not interchangeable`() {
        val (after, pending) = applyOptimistic(board, "a", ::toggleFirst15)
        val saved = reconcileWrite(after, pending!!, WriteOutcome.Saved(after.single { it.id == "a" }))
        val refused = reconcileWrite(after, pending, WriteOutcome.Refused)

        assertTrue(saved.cards.single { it.id == "a" }.first15Complete)
        assertFalse(refused.cards.single { it.id == "a" }.first15Complete)
        assertNull(saved.error)
        assertNotNull(refused.error)
    }
}
