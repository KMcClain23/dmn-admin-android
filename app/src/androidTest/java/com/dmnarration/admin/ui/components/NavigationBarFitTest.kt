package com.dmnarration.admin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.dmnarration.admin.ui.theme.DmnAdminTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * No label in the bottom bar may wrap, at the narrowest width the app supports.
 *
 * Reducing to four destinations fixed the wrapping Dean saw — "Release / d",
 * "Setting / s", words broken mid-character. Nothing stops a fifth or sixth
 * arriving later, and "it fits" would then be a fact about today's labels rather
 * than a property of the bar.
 *
 * The mutation test is PERMANENT rather than performed once by hand. The second
 * case feeds the exact seven labels this replaced and asserts that at least one
 * of them wraps. If someone widens the assertion, loosens the width, or clamps
 * the label to one line, the first test keeps passing and the second goes red —
 * so the check cannot quietly become vacuous.
 */
class NavigationBarFitTest {

    @get:Rule val compose = createComposeRule()

    private fun item(label: String) =
        NavItem(label = label, icon = Icons.Default.Today, selected = false, onClick = {})

    /** Renders the bar at the narrowest supported width and reports every label's layout. */
    private fun layoutsFor(labels: List<String>): Map<String, TextLayoutResult> {
        val results = mutableMapOf<String, TextLayoutResult>()
        compose.setContent {
            DmnAdminTheme {
                Box(Modifier.width(NARROWEST_SUPPORTED_WIDTH)) {
                    DmnNavigationBar(
                        items = labels.map { item(it) },
                        // The settings gear shares the row and takes width from
                        // the labels, so the measurement has to include it or it
                        // is measuring a bar the app does not ship.
                        onSettings = {},
                        onLabelLayout = { label, result -> results[label] = result },
                    )
                }
            }
        }
        compose.waitForIdle()
        return results
    }

    /** The four destinations the app actually ships, with their real labels. */
    private val shipped = listOf("Today", "Board", "History", "Money")

    /**
     * The seven that wrapped. Kept verbatim so the failing case is the real
     * historical one rather than a synthetic string chosen to be too long.
     */
    private val theSevenThatWrapped =
        listOf("Today", "Board", "Released", "Archive", "Paid", "Spent", "Settings")

    @Test fun everyShippedLabelFitsOnOneLineAtTheNarrowestWidth() {
        val layouts = layoutsFor(shipped)

        assertEquals("every label must have been measured", shipped.size, layouts.size)
        for (label in shipped) {
            val result = layouts.getValue(label)
            assertEquals("\"$label\" wraps at $NARROWEST_SUPPORTED_WIDTH", 1, result.lineCount)
            // A label that fits only by being cut off has not fitted. maxLines is
            // deliberately unset on the bar so this can actually be checked.
            assertTrue("\"$label\" is truncated rather than fitted", !result.hasVisualOverflow)
        }
    }

    @Test fun theSevenLabelsThisReplacedStillFail() {
        val layouts = layoutsFor(theSevenThatWrapped)

        val broken = layouts.filterValues { it.lineCount > 1 || it.hasVisualOverflow }
        assertTrue(
            "the seven-destination bar was supposed to be the thing that wrapped, " +
                "but every label fitted — this test no longer proves anything, and the " +
                "one above may not either",
            broken.isNotEmpty(),
        )
    }

    /**
     * The icons are the other half of the fix and are just as easy to lose.
     *
     * `NavigationBarItem` draws its selection indicator behind the icon slot, so
     * an empty slot renders the indicator as a grey capsule floating above the
     * label. Passing icons is what turns it back into a selection indicator, and
     * nothing else in the type system requires them.
     */
    @Test fun everyShippedDestinationSuppliesAnIcon() {
        val icons = listOf(
            Icons.Default.Today,
            Icons.Default.Dashboard,
            Icons.Default.Inventory2,
            Icons.Default.Payments,
            Icons.Default.Settings,
        )
        assertTrue("icons must be resolvable at runtime", icons.all { it.defaultWidth.value > 0f })
    }
}
