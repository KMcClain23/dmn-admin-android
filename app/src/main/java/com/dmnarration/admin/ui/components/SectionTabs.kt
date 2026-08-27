package com.dmnarration.admin.ui.components

import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.dmnarration.admin.ui.theme.Background
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType

/** One section of a screen: a label, and how many things are in it. */
data class Section(val label: String, val count: Int)

/**
 * The two-tab row the Board has used since Stage 2, extracted so the screens
 * that group two independent lists can use the same one.
 *
 * Extracted rather than copied: Shelf and Money each hold two lists that load
 * separately and fail separately, which is exactly the shape Pipeline / In
 * Production already has. A second implementation of this row would be a second
 * place for the selected-tab colours and the count format to drift.
 *
 * The count is part of the label rather than a badge, matching the board. It is
 * also the reason these rows are worth having at all: "Released (12) / Archive
 * (1)" says more at a glance than a screen title can.
 */
@Composable
fun SectionTabRow(
    sections: List<Section>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val c = DmnTheme.colors
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Background,
        contentColor = c.accentAmber,
    ) {
        sections.forEachIndexed { i, section ->
            Tab(
                selected = selectedIndex == i,
                onClick = { onSelect(i) },
                text = {
                    Text(
                        "${section.label} (${section.count})",
                        style = DmnType.BodyMedium,
                        color = if (selectedIndex == i) c.accentAmber else c.textMuted,
                    )
                },
            )
        }
    }
}
