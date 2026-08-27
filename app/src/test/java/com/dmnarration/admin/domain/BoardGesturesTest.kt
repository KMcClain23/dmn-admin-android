package com.dmnarration.admin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
