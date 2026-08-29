package com.dmnarration.admin.ui.shelf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.dmnarration.admin.ui.components.Section
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.domain.CareerTotals
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
        // Above both lists: a record of completed work heading the screen that
        // holds completed work. It is not a property of the Released list, so
        // it sits above the tabs rather than inside one.
        state.career?.let { CareerLine(it) }

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


/**
 * Words narrated, and what the figure does not include.
 *
 * THREE LINES, NOT ONE. A single total that silently omits books looks
 * answered, and looking answered is the failure this project has found
 * repeatedly. The third line names the count so it can be acted on: nine of the
 * uncounted are released books with no word count, and entering them is what
 * makes the headline real.
 *
 * Renders nothing at all if the partition does not hold. The database asserts
 * it too and refuses; this is the client refusing to DISPLAY a short total, so
 * a stale build cannot show a number the server would have rejected.
 */
@Composable
private fun CareerLine(career: CareerTotals) {
    if (!career.partitionHolds) return
    val c = DmnTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("WORDS NARRATED", style = DmnType.Label, color = c.textDim)
        Text(
            "%,d".format(career.countedWords),
            style = DmnType.TitleLg,
            color = c.textPrimary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            buildString {
                append("%,d exact across %d book".format(career.exactWords, career.exactBooks))
                if (career.exactBooks != 1) append("s")
                if (career.estimatedBooks > 0) {
                    append(" · %,d estimated from pages across %d book".format(
                        career.estimatedWords, career.estimatedBooks))
                    if (career.estimatedBooks != 1) append("s")
                }
            },
            style = DmnType.Small,
            color = c.textMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (career.notCountedBooks > 0) {
            Text(
                "%d book%s not counted — no word count recorded".format(
                    career.notCountedBooks, if (career.notCountedBooks == 1) "" else "s"),
                style = DmnType.Small,
                color = c.accentAmberDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
