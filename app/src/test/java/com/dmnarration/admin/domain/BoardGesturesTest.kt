package com.dmnarration.admin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The swipe thresholds, which were ported rather than designed.
 *
 * The numbers are the web's and must stay the web's: a gesture that archives at
 * 60dp on one platform and 90dp on the other is not the same gesture, and the
 * one that fires early destroys work. The velocity unit is the trap — the web
 * measures per millisecond, Compose per second — so the boundary cases here are
 * the point, not the obvious ones.
 */
class SwipeToArchiveTest {

    // ─── displacement ───────────────────────────────────────────────────────

    @Test fun `a drag past the threshold archives`() {
        assertTrue(SwipeToArchive.shouldArchive(-91f, 0f))
    }

    @Test fun `the threshold itself does not archive`() {
        // The web is `mx < ARCHIVE_THRESHOLD`, strictly. Exactly -90 is a
        // release that has not gone far enough.
        assertFalse(SwipeToArchive.shouldArchive(-90f, 0f))
    }

    @Test fun `a short slow drag springs back`() {
        for (dp in listOf(0f, -5f, -30f, -89f)) {
            assertFalse("$dp dp must not archive", SwipeToArchive.shouldArchive(dp, 0f))
        }
    }

    // ─── velocity, and the unit that would double it ────────────────────────

    /**
     * 0.5 per millisecond is 500 per second. If the constant were compared
     * against a per-second velocity directly, every drag over 0.5 dp/s — which
     * is to say every drag at all — would archive.
     */
    @Test fun `a fast leftward flick archives even when short`() {
        assertTrue(SwipeToArchive.shouldArchive(-20f, -501f))
    }

    @Test fun `just under the flick speed does not archive`() {
        assertFalse(SwipeToArchive.shouldArchive(-20f, -499f))
    }

    @Test fun `a slow leftward drift is not a flick`() {
        // The value that would fire if ms and s were confused.
        assertFalse(SwipeToArchive.shouldArchive(-20f, -0.6f))
    }

    /** Rightward is never an archive, however fast. */
    @Test fun `a rightward flick never archives`() {
        for (v in listOf(1f, 500f, 5000f)) {
            assertFalse("velocity $v must not archive", SwipeToArchive.shouldArchive(-20f, v))
        }
    }

    /** Past the threshold counts even with no velocity at all — a slow deliberate drag. */
    @Test fun `displacement and velocity are independent routes`() {
        assertTrue("slow but far", SwipeToArchive.shouldArchive(-95f, 0f))
        assertTrue("fast but near", SwipeToArchive.shouldArchive(-10f, -900f))
        assertFalse("neither", SwipeToArchive.shouldArchive(-10f, -100f))
    }

    // ─── the clamp ──────────────────────────────────────────────────────────

    @Test fun `the card cannot be dragged past the clamp`() {
        assertEquals(-110f, SwipeToArchive.clampOffset(-200f), 0.001f)
        assertEquals(-110f, SwipeToArchive.clampOffset(-110f), 0.001f)
    }

    @Test fun `the card cannot be dragged rightward`() {
        assertEquals(0f, SwipeToArchive.clampOffset(50f), 0.001f)
        assertEquals(0f, SwipeToArchive.clampOffset(0f), 0.001f)
    }

    @Test fun `within range the offset is untouched`() {
        assertEquals(-45f, SwipeToArchive.clampOffset(-45f), 0.001f)
    }

    /** The clamp is past the threshold, so a fully-dragged card always archives. */
    @Test fun `a fully dragged card is past the threshold`() {
        assertTrue(SwipeToArchive.shouldArchive(SwipeToArchive.clampOffset(-999f), 0f))
    }
}

/**
 * The long-press menu's contents.
 *
 * The card's own status is withheld: a move to where the card already is spends
 * a write to change nothing, and reads as the gesture having failed.
 */
class BoardActionsTest {

    private fun labels(status: String) = actionsFor(card(status = status)).map { it.label }

    @Test fun `the current status is not offered`() {
        assertFalse(labels("prepping").contains("Move to Prepping"))
        assertFalse(labels("recording").contains("Move to Recording"))
        assertFalse(labels("editing").contains("Move to Editing"))
        assertFalse(labels("contracted").contains("Move to Pipeline"))
    }

    @Test fun `the other statuses are`() {
        val l = labels("prepping")
        assertTrue(l.contains("Move to Pipeline"))
        assertTrue(l.contains("Move to Recording"))
        assertTrue(l.contains("Move to Editing"))
    }

    @Test fun `release and archive are always offered`() {
        for (s in listOf("contracted", "prepping", "recording", "editing")) {
            assertTrue("$s must offer release", labels(s).contains("Mark as Released"))
            assertTrue("$s must offer archive", labels(s).contains("Archive"))
        }
    }

    @Test fun `archive is the only action with no status`() {
        val actions = actionsFor(card(status = "recording"))
        assertEquals(1, actions.count { it.isArchive })
        assertEquals("Archive", actions.single { it.isArchive }.label)
    }

    @Test fun `every move carries the status it writes`() {
        val actions = actionsFor(card(status = "contracted"))
        assertEquals("prepping", actions.single { it.label == "Move to Prepping" }.status)
        assertEquals("released", actions.single { it.label == "Mark as Released" }.status)
    }

    /** Four moves minus the current one, plus release, plus archive. */
    @Test fun `the menu is the size it should be`() {
        assertEquals(5, actionsFor(card(status = "recording")).size)
    }
}
