package com.dmnarration.admin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The booking window, and the trap in rendering it.
 *
 * `available_months` is stored `[11, 12, 1, 2]` — November through February, one
 * contiguous run that crosses the year. Sorting it numerically produces "January,
 * February, November, December", which turns one window into two and describes a
 * narrator who takes work in winter as one who takes work twice a year. The live data
 * wraps, so this is not a hypothetical case.
 */
class SiteSettingsTest {

    @Test fun `the live window is contiguous across the year boundary`() {
        assertEquals(11 to 2, monthWindow(listOf(11, 12, 1, 2)))
        assertEquals("November – February", availableMonthsLabel(listOf(11, 12, 1, 2)))
    }

    /** The failure being guarded against, stated as the thing that must NOT happen. */
    @Test fun `a wrapping window is never rendered as two separate runs`() {
        val label = availableMonthsLabel(listOf(11, 12, 1, 2))
        assertEquals(
            "sorting would have produced January, February, November, December",
            "November – February",
            label,
        )
    }

    @Test fun `a window inside one year works the same way`() {
        assertEquals(3 to 6, monthWindow(listOf(3, 4, 5, 6)))
        assertEquals("March – June", availableMonthsLabel(listOf(3, 4, 5, 6)))
    }

    @Test fun `a single month is its own window`() {
        assertEquals(9 to 9, monthWindow(listOf(9)))
        assertEquals("September", availableMonthsLabel(listOf(9)))
    }

    /**
     * A gap means "start" and "end" are not answerable, so no range is claimed. The
     * months are listed in their stored order rather than being forced into a label
     * that would misdescribe them.
     */
    @Test fun `a non-contiguous list is not turned into a range`() {
        assertNull(monthWindow(listOf(1, 3, 5)))
        assertEquals("January, March, May", availableMonthsLabel(listOf(1, 3, 5)))
    }

    @Test fun `an out-of-order list is not silently sorted into a range`() {
        assertNull(monthWindow(listOf(2, 1, 12, 11)))
        assertEquals("February, January, December, November", availableMonthsLabel(listOf(2, 1, 12, 11)))
    }

    @Test fun `no months at all says so`() {
        assertNull(monthWindow(emptyList()))
        assertEquals("None", availableMonthsLabel(emptyList()))
    }

    @Test fun `a full year is contiguous`() {
        assertEquals(1 to 12, monthWindow((1..12).toList()))
    }

    // ─── accepting_projects is a state, not a boolean ───────────────────────

    @Test fun `accepting projects never renders as true or false`() {
        assertEquals("Open to new projects", acceptingProjectsLabel(true))
        assertEquals("Not taking new projects", acceptingProjectsLabel(false))
        assertEquals("Not set", acceptingProjectsLabel(null))

        for (label in listOf(true, false, null).map(::acceptingProjectsLabel)) {
            assertEquals("no raw boolean reaches the screen", false, label == "true" || label == "false")
        }
    }

    @Test fun `month names are one-based`() {
        assertEquals("January", monthName(1))
        assertEquals("December", monthName(12))
        assertEquals("13", monthName(13))
    }
}
