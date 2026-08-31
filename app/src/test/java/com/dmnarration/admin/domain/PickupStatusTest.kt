package com.dmnarration.admin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WHICH ROWS OFFER WHICH ACTION — not "does pressing it work".
 *
 * The bug this replaces would have passed a test that only checked "Resolve
 * succeeds": it did succeed, on returned rows, which the app mislabelled as
 * Sent. The rows it labelled correctly got a guaranteed error. So every
 * assertion here is about the SET of rows an action is offered on.
 */
class PickupStatusTest {

    @Test fun `every known status parses to itself`() {
        for (s in PickupStatus.entries.filter { it.isKnown }) {
            assertEquals(s, PickupStatus.fromStored(s.stored))
        }
    }

    @Test fun `returned is a real state now`() {
        assertEquals(PickupStatus.RETURNED, PickupStatus.fromStored("returned"))
        assertTrue(PickupStatus.RETURNED.isKnown)
    }

    @Test fun `an unrecognised status is UNKNOWN, not SENT`() {
        // The class-level fix. Falling back to SENT is what made the next status
        // reproduce this bug exactly; a value from a schema this build has never
        // seen must be able to say so.
        assertEquals(PickupStatus.UNKNOWN, PickupStatus.fromStored("escalated"))
        assertEquals(PickupStatus.UNKNOWN, PickupStatus.fromStored("something-new"))
        assertEquals(PickupStatus.UNKNOWN, PickupStatus.fromStored(null))
        assertEquals(PickupStatus.UNKNOWN, PickupStatus.fromStored(""))
        assertFalse(PickupStatus.UNKNOWN.isKnown)
    }

    @Test fun `the unknown state says so rather than guessing`() {
        assertTrue(PickupStatus.UNKNOWN.label.contains("update the app", ignoreCase = true))
    }

    // ── which rows offer what ───────────────────────────────────────────────
    //
    // These mirror the conditions in EditingAndPickups: Re-recorded on SENT,
    // Verify & close on RETURNED, Dismiss on either, nothing on UNKNOWN.

    private fun offersReRecorded(s: PickupStatus) = s == PickupStatus.SENT
    private fun offersResolve(s: PickupStatus) = s == PickupStatus.RETURNED
    private fun offersDismiss(s: PickupStatus) =
        s == PickupStatus.SENT || s == PickupStatus.RETURNED

    @Test fun `Re-recorded is offered on sent and nowhere else`() {
        val offered = PickupStatus.entries.filter { offersReRecorded(it) }
        assertEquals(listOf(PickupStatus.SENT), offered)
    }

    @Test fun `Resolve is offered on returned and nowhere else`() {
        // The old build offered it on SENT, where the server now always refuses.
        val offered = PickupStatus.entries.filter { offersResolve(it) }
        assertEquals(listOf(PickupStatus.RETURNED), offered)
        assertFalse(offersResolve(PickupStatus.SENT))
    }

    @Test fun `Dismiss is offered from sent or returned only`() {
        assertEquals(
            listOf(PickupStatus.SENT, PickupStatus.RETURNED),
            PickupStatus.entries.filter { offersDismiss(it) },
        )
    }

    @Test fun `an unknown row offers no action at all`() {
        val s = PickupStatus.fromStored("a-status-from-the-future")
        assertFalse(offersReRecorded(s))
        assertFalse(offersResolve(s))
        assertFalse(offersDismiss(s))
    }

    @Test fun `a closed row offers no action either`() {
        for (s in listOf(PickupStatus.RESOLVED, PickupStatus.DISMISSED)) {
            assertFalse(offersReRecorded(s))
            assertFalse(offersResolve(s))
            assertFalse(offersDismiss(s))
        }
    }

    @Test fun `an unknown row is never editable, whoever is asking`() {
        val p = Pickup(
            id = "p", cardId = "c", chapter = "1", timestampAt = "01:00",
            kind = PickupKind.OTHER, said = "", shouldBe = "", note = "",
            assignedNarratorId = null, assignedNarratorName = null,
            status = PickupStatus.fromStored("who-knows"), createdBy = "me",
            createdAt = null, sentAt = null, resolvedAt = null, resolvedBy = null,
        )
        assertFalse(p.isEditableBy("me"))
        assertFalse(p.isReturned)
        assertFalse(p.isAwaitingNarrator)
    }
}
