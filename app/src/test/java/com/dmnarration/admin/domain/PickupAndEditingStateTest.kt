package com.dmnarration.admin.domain

import com.dmnarration.admin.ui.detail.narratorOptions
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules this feature rests on that no type enforces.
 *
 * 1. EDITING STATE IS DERIVED, NEVER STORED. There is no editing_status column,
 *    because a stored status and a chapter count can disagree — "done" beside 4
 *    of 12 is a row that cannot be true and would still render.
 *
 * 2. A PICKUP IS EDITABLE ONLY BY ITS CREATOR AND ONLY WHILE DRAFT. The database
 *    enforces both in update_own_draft_pickup and delete_own_draft_pickup; this
 *    is the client not offering a button the server would refuse. If the two
 *    ever disagree, the server is right.
 *
 * 3. A MISREAD CARRIES A PAIR. The database refuses one half without the other,
 *    so the form must ask for both — and `needsSaidPair` is what the form reads.
 */
class PickupAndEditingStateTest {

    private val t = Instant.parse("2026-08-29T12:00:00Z")

    @Test fun `neither count nor completion means not started`() {
        assertEquals(EditingState.NOT_STARTED, editingStateOf(null, null))
        assertEquals(EditingState.NOT_STARTED, editingStateOf(0, null))
    }

    @Test fun `a count above zero means in progress`() {
        assertEquals(EditingState.IN_PROGRESS, editingStateOf(1, null))
    }

    @Test fun `completion wins over the count, in both directions`() {
        // The case a stored status would get wrong: finished early, or a count
        // never filled in. Completion answers it either way, and there is no
        // second field that could say otherwise.
        assertEquals(EditingState.DONE, editingStateOf(null, t))
        assertEquals(EditingState.DONE, editingStateOf(4, t))
        assertEquals(EditingState.DONE, editingStateOf(0, t))
    }

    private fun pickup(
        createdBy: String?,
        status: PickupStatus = PickupStatus.DRAFT,
        kind: PickupKind = PickupKind.OTHER,
        said: String = "",
        shouldBe: String = "",
        note: String = "",
    ) = Pickup(
        id = "p1", cardId = "c1", chapter = "12", timestampAt = "04:32.1",
        kind = kind, said = said, shouldBe = shouldBe, note = note,
        assignedTo = "", status = status, createdBy = createdBy,
        createdAt = t, sentAt = null, resolvedAt = null, resolvedBy = null,
    )

    @Test fun `her own draft is editable`() {
        assertTrue(pickup("editor-1").isEditableBy("editor-1"))
    }

    @Test fun `someone elses draft is not, even for the only editor`() {
        // "There is only one editor" is a fact about today's data, not a
        // property of the system.
        assertFalse(pickup("dean").isEditableBy("editor-1"))
    }

    @Test fun `a SENT pickup is not editable even by its author`() {
        // Once sent the email is out; changing the record would make it
        // disagree with what the narrator was actually asked to do.
        assertFalse(pickup("editor-1", PickupStatus.SENT).isEditableBy("editor-1"))
        assertFalse(pickup("editor-1", PickupStatus.RESOLVED).isEditableBy("editor-1"))
    }

    @Test fun `an unknown session edits nothing`() {
        // A null userId must not match a null created_by and quietly become
        // "yours".
        assertFalse(pickup("editor-1").isEditableBy(null))
        assertFalse(pickup(null).isEditableBy(null))
    }

    @Test fun `an unrecognised status reads as SENT, not DRAFT`() {
        // DRAFT would offer edit and delete for a row this build does not
        // understand, and the server would refuse them. SENT offers nothing and
        // hides nothing.
        assertEquals(PickupStatus.SENT, PickupStatus.fromStored("something-new"))
        assertEquals(PickupStatus.SENT, PickupStatus.fromStored(null))
        assertEquals(PickupStatus.DRAFT, PickupStatus.fromStored("draft"))
        assertEquals(PickupStatus.DISMISSED, PickupStatus.fromStored("DISMISSED"))
    }

    @Test fun `only a misread needs the said pair`() {
        assertTrue(PickupKind.MISREAD.needsSaidPair)
        assertFalse(PickupKind.NOISE.needsSaidPair)
        assertFalse(PickupKind.SENTENCE.needsSaidPair)
        assertFalse(PickupKind.OTHER.needsSaidPair)
        assertEquals(PickupKind.OTHER, PickupKind.fromStored("nonsense"))
    }

    @Test fun `the summary reads as the kind demands`() {
        val misread = pickup("e", kind = PickupKind.MISREAD, said = "Aiden", shouldBe = "Aidan")
        assertEquals("said \"Aiden\" — should be \"Aidan\"", misread.summary)
        // A non-misread falls back to the note, and to the kind when there is
        // none — never to an empty line.
        assertEquals("chair creak", pickup("e", kind = PickupKind.NOISE, note = "chair creak").summary)
        assertEquals("Noise", pickup("e", kind = PickupKind.NOISE).summary)
    }

    @Test fun `the narrator picker handles both co_narrator shapes and always offers Dean`() {
        // The column holds a JSON array in most rows and a bare name in a few.
        assertEquals(listOf("Veronica Moore", "Dean"), narratorOptions("[\"Veronica Moore\"]"))
        assertEquals(listOf("Ann Dahlia", "Dean"), narratorOptions("Ann Dahlia"))
        assertEquals(listOf("Dean"), narratorOptions(null))
        assertEquals(listOf("Dean"), narratorOptions(""))
        // Dean is not duplicated when he is already a co-narrator on the card.
        assertEquals(listOf("Dean"), narratorOptions("[\"Dean\"]"))
    }
}
