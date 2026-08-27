package com.dmnarration.admin.ui.shelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.dmnarration.admin.ui.components.Section
import com.dmnarration.admin.ui.components.SectionTabRow
import kotlinx.coroutines.launch

/**
 * Released and Archive, as one destination with two sections.
 *
 * They were already one thing in the model: `ShelfViewModel` loads both, Stage 6
 * built them as a pair, and their two reads fail independently. The bottom bar
 * was simply more granular than the code behind it — seven destinations where
 * Material's guidance is three to five, which is why "Released" was rendering as
 * "Release / d" with the word broken mid-character.
 *
 * The tab row is the one the Board has used since Stage 2. Dean already knows
 * the pattern from Pipeline / In Production, and keeping two independently
 * failing loads as two visible tabs is the point: a broken archive read must not
 * be able to hide behind a working released list.
 */
@Composable
fun ShelfScreen(
    state: ShelfState,
    pagerState: PagerState,
    onRefresh: () -> Unit,
    onUnarchive: (String) -> Unit,
    onOpenCard: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        SectionTabRow(
            sections = listOf(
                // The visible count, not the all-time one. The Released screen
                // itself says which population it is showing when they differ.
                Section("Released", state.released.size),
                Section("Archive", state.archived.size),
            ),
            selectedIndex = pagerState.currentPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
        )

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ReleasedScreen(state = state, onRefresh = onRefresh, onOpenCard = onOpenCard)
                else -> ArchiveScreen(
                    state = state,
                    onRefresh = onRefresh,
                    onUnarchive = onUnarchive,
                    onOpenCard = onOpenCard,
                )
            }
        }
    }
}
