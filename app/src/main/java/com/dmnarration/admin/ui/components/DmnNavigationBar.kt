package com.dmnarration.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.ui.theme.Surface
import com.dmnarration.admin.ui.theme.SurfaceRaised

/** One destination in the bar. A label, an icon, and what tapping it does. */
data class NavItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * The narrowest screen this app is expected to render on.
 *
 * 320dp is the smallest width Android phones ship at, and the figure the bar's
 * test measures against. It is a constant rather than a number typed into the
 * test so that the claim "the labels fit" is anchored to a stated assumption
 * about hardware rather than to whatever width someone happened to try.
 */
val NARROWEST_SUPPORTED_WIDTH: Dp = 320.dp

/**
 * The bottom bar, extracted from MainActivity so it can be measured.
 *
 * It exists as its own composable for one reason: a claim about whether labels
 * wrap is a claim about text layout, and text layout cannot be asserted on a
 * composable that only exists inside an Activity's private route.
 *
 * Two things this fixes, both visible on Dean's phone:
 *
 * 1. **Every item now has an icon.** `NavigationBarItem` draws its selection
 *    indicator BEHIND the icon slot. With `icon = {}` the indicator rendered as
 *    a grey capsule floating above the label with nothing in it — an artefact
 *    that read as a rendering bug rather than as "this tab is selected".
 * 2. **Four destinations, not seven.** Material's guidance is three to five, and
 *    seven left so little width that "Released" broke mid-word as "Release / d"
 *    and "Settings" as "Setting / s".
 *
 * `onLabelLayout` is a measurement hook with a no-op default. It is the seam the
 * wrapping test observes through — `lineCount` is only knowable from a
 * `TextLayoutResult`, and there is no way to read one back out of a rendered
 * tree. Nothing in the app passes it.
 */
@Composable
fun DmnNavigationBar(
    items: List<NavItem>,
    onSettings: (() -> Unit)? = null,
    onLabelLayout: (String, TextLayoutResult) -> Unit = { _, _ -> },
) {
    Row(
        Modifier.background(Surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
    NavigationBar(containerColor = Surface, modifier = Modifier.weight(1f)) {
        for (item in items) {
            NavigationBarItem(
                selected = item.selected,
                onClick = item.onClick,
                icon = { Icon(item.icon, contentDescription = null) },
                label = {
                    Text(
                        item.label,
                        style = DmnType.BodyMedium,
                        // NOT `maxLines = 1`. Clamping would hide a wrap instead
                        // of preventing one, and the test would then pass on a
                        // label that is silently truncated — a fact about the
                        // clamp rather than about the bar fitting.
                        onTextLayout = { onLabelLayout(item.label, it) },
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = DmnTheme.colors.accentAmber,
                    selectedIconColor = DmnTheme.colors.accentAmber,
                    unselectedTextColor = DmnTheme.colors.textMuted,
                    unselectedIconColor = DmnTheme.colors.textMuted,
                    indicatorColor = SurfaceRaised,
                ),
            )
        }
    }

        /*
         * Settings sits BESIDE the bar, not floating over the content.
         *
         * It was a floating button first, which is what Dean asked for and what
         * this replaced. On the emulator it landed squarely on a payment row and
         * covered the amount — "$1,59" with the rest of the figure behind the
         * gear. A control that hides money on the screen whose only job is
         * showing money is worse than one that is slightly less elegant, and no
         * amount of bottom padding fixes it: the padding clears the END of the
         * list, while the overlap happens in the middle of a scroll.
         *
         * Still its own button rather than a fifth tab: it is not a destination,
         * it does not take a label, and it does not compete for the width the
         * four labels need.
         */
        if (onSettings != null) {
            IconButton(onClick = onSettings, modifier = Modifier.padding(end = 4.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = DmnTheme.colors.textMuted,
                )
            }
        }
    }
}
