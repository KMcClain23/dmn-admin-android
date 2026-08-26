package com.dmnarration.admin.ui.board

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.DateFilter
import com.dmnarration.admin.domain.PipelineBucket
import com.dmnarration.admin.domain.ProductionSubgroup
import com.dmnarration.admin.ui.theme.Background
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.ui.theme.Surface
import com.dmnarration.admin.ui.theme.SurfaceBorder
import kotlinx.coroutines.launch

private val PIPELINE_LABELS = mapOf(
    PipelineBucket.THIS_WEEK to "This Week",
    PipelineBucket.THIS_MONTH to "This Month",
    PipelineBucket.LATER to "Later",
)

private val PRODUCTION_LABELS = mapOf(
    ProductionSubgroup.PREPPING to "Prepping",
    ProductionSubgroup.RECORDING to "Recording",
    ProductionSubgroup.EDITING to "Editing",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    state: BoardUiState,
    onRefresh: () -> Unit,
    onToggleFilter: (DateFilter) -> Unit,
    onOpenCard: (BoardCard) -> Unit,
    onToggleFirst15: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val c = DmnTheme.colors
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TopAppBar(
            title = { Text("Board", style = DmnType.TitleLg, color = c.textPrimary) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            actions = {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = c.textMuted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Sign out") },
                        onClick = { menuOpen = false; onSignOut() },
                    )
                }
            },
        )

        // The mobile web's Pipeline / In Production chips are gone on purpose:
        // they existed only because both sections shared one scroll. The tabs
        // do that job now. The due-soon chips remain, and still toggle off when
        // tapped again.
        // Withheld entirely when the board is refused. A count of zero beside a
        // refusal implies there is a pipeline and it happens to be empty, and
        // "Due this week" is a filter over nothing. This is the one state where
        // less chrome is more truthful — the message is the whole answer.
        if (!state.refused) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DueChip("Due this week", state.dateFilter == DateFilter.WEEK) { onToggleFilter(DateFilter.WEEK) }
                DueChip("Due this month", state.dateFilter == DateFilter.MONTH) { onToggleFilter(DateFilter.MONTH) }
            }

            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Background,
                contentColor = c.accentAmber,
            ) {
                TabWithCount("Pipeline", state.pipelineCount, pagerState.currentPage == 0) {
                    scope.launch { pagerState.animateScrollToPage(0) }
                }
                TabWithCount("In Production", state.productionCount, pagerState.currentPage == 1) {
                    scope.launch { pagerState.animateScrollToPage(1) }
                }
            }
        }

        if (state.error != null) {
            ErrorBanner(state.error)
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            state = rememberPullToRefreshState(),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading -> ShimmerList()
                state.isEmpty && state.error == null -> EmptyBoard()
                // Nothing to show and something to say: the banner is the
                // whole answer. Falling through to the pager here rendered
                // "no books" under every bucket heading, which reads as an
                // empty board — the same false impression EmptyBoard is
                // withheld to avoid, just spelled differently.
                //
                // An empty LazyColumn rather than nothing at all. PullToRefresh
                // drives off nested scroll, so content that does not scroll
                // cannot be pulled: rendering nothing here stranded a refused
                // session on this screen with no way back short of restarting
                // the app. This scrolls, and says nothing.
                state.isEmpty -> LazyColumn(modifier = Modifier.fillMaxSize()) {}
                else -> HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    if (page == 0) {
                        SectionList(
                            sections = PipelineBucket.entries.map {
                                PIPELINE_LABELS.getValue(it) to state.pipeline[it].orEmpty()
                            },
                            state = state,
                            onOpenCard = onOpenCard,
                            onToggleFirst15 = onToggleFirst15,
                        )
                    } else {
                        SectionList(
                            sections = ProductionSubgroup.entries.map {
                                PRODUCTION_LABELS.getValue(it) to state.production[it].orEmpty()
                            },
                            state = state,
                            onOpenCard = onOpenCard,
                            onToggleFirst15 = onToggleFirst15,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabWithCount(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val c = DmnTheme.colors
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Text(
                "$label ($count)",
                style = DmnType.BodyMedium,
                color = if (selected) c.accentAmber else c.textMuted,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = DmnTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = DmnType.Small) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Surface,
            labelColor = c.textMuted,
            selectedContainerColor = c.accentAmber.copy(alpha = 0.18f),
            selectedLabelColor = c.accentAmber,
        ),
    )
}

@Composable
private fun SectionList(
    sections: List<Pair<String, List<BoardCard>>>,
    state: BoardUiState,
    onOpenCard: (BoardCard) -> Unit,
    onToggleFirst15: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        sections.forEach { (label, cards) ->
            stickyHeader(key = "h_$label") { SectionHeader(label) }
            if (cards.isEmpty()) {
                item(key = "e_$label") {
                    Text("— no books —", style = DmnType.Small, color = DmnTheme.colors.textFaint)
                }
            } else {
                items(count = cards.size, key = { cards[it].id }) { i ->
                    BoardCardItem(
                        card = cards[i],
                        capabilities = state.capabilities,
                        settings = state.settings,
                        today = state.today,
                        onClick = { onOpenCard(cards[i]) },
                        onToggleFirst15 = { onToggleFirst15(cards[i].id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label.uppercase(),
        style = DmnType.Label,
        color = DmnTheme.colors.textFaint,
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(vertical = 6.dp),
    )
}

/**
 * Card-shaped placeholders rather than a centred spinner.
 *
 * The web uses a spinner because it was cheap. On a phone the board is the
 * whole app, and a shape that matches what is coming reads as loading rather
 * than as waiting.
 */
@Composable
private fun ShimmerList() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(CARD_HEIGHT)
                    .clip(RoundedCornerShape(8.dp))
                    .alpha(alpha)
                    .background(Surface)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
            )
        }
    }
}

/**
 * A LazyColumn holding one full-height item, not a Box.
 *
 * PullToRefreshBox detects the gesture through nested scroll, so it needs a
 * scrollable descendant to hear it. With a plain Box here the empty board could
 * not be pulled at all — which is precisely the state you are in after a
 * permission change empties the list, and precisely when you most need to
 * retry. The only way out was to kill the app.
 */
@Composable
private fun EmptyBoard() {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(
                Modifier
                    .fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("No active projects", style = DmnType.Body, color = DmnTheme.colors.textMuted)
            }
        }
    }
}

/** Matches the web: AlertRed at 10% over a 30% border. */
@Composable
private fun ErrorBanner(message: String) {
    val c = DmnTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.alertRed.copy(alpha = 0.10f))
            .border(1.dp, c.alertRed.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, style = DmnType.Small, color = c.alertRed)
        Spacer(Modifier.width(4.dp))
    }
}
