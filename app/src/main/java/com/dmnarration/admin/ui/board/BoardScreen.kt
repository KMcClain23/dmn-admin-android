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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
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
import com.dmnarration.admin.domain.ArchiveReason
import com.dmnarration.admin.domain.STATUS_RELEASED
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
    onMoveTo: (String, String) -> Unit,
    onArchive: (String, ArchiveReason, String) -> Unit,
    onSignOut: () -> Unit,
    // Hoisted so a trip to the Agenda and back returns to the same tab, scrolled
    // where it was. Remembered inside this composable they would reset on every
    // destination switch, which reads as the board having reloaded.
    pagerState: PagerState = rememberPagerState(pageCount = { 2 }),
    pipelineScroll: LazyListState = rememberLazyListState(),
    productionScroll: LazyListState = rememberLazyListState(),
) {
    val c = DmnTheme.colors
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    // Which card a gesture is currently about. One at a time by construction:
    // there is one board and one finger, and a second dialog stacked over the
    // first would leave the user confirming an action against a card they can
    // no longer see.
    var actionCard by remember { mutableStateOf<BoardCard?>(null) }
    var releaseCard by remember { mutableStateOf<BoardCard?>(null) }
    var archiveCard by remember { mutableStateOf<BoardCard?>(null) }

    actionCard?.let { card ->
        BoardActionSheet(
            card = card,
            onDismiss = { actionCard = null },
            onAction = { action ->
                actionCard = null
                when {
                    action.isArchive -> archiveCard = card
                    action.status == STATUS_RELEASED -> releaseCard = card
                    else -> onMoveTo(card.id, action.status!!)
                }
            },
        )
    }

    releaseCard?.let { card ->
        ReleaseConfirmDialog(
            card = card,
            onConfirm = { releaseCard = null; onMoveTo(card.id, STATUS_RELEASED) },
            onDismiss = { releaseCard = null },
        )
    }

    archiveCard?.let { card ->
        ArchiveConfirmDialog(
            card = card,
            onConfirm = { reason, notes -> archiveCard = null; onArchive(card.id, reason, notes) },
            onDismiss = { archiveCard = null },
        )
    }

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

        PullToRefreshSurface(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            content = when {
                state.loading -> shimmerContent()
                state.isEmpty && state.error == null -> emptyBoardContent()
                // Nothing to show and something to say: the banner is the whole
                // answer. Falling through to the pager here rendered "no books"
                // under every bucket heading, which reads as an empty board —
                // the same false impression EmptyBoard is withheld to avoid,
                // just spelled differently.
                //
                // `blank()` rather than nothing at all, and it is now the only
                // way to say "nothing": rendering nothing left PullToRefresh
                // with no nested-scroll participant and stranded a refused
                // session here with no way back short of restarting the app.
                // ScrollableContent exists so that mistake is a type error.
                state.isEmpty -> ScrollableContent.blank()
                else -> ScrollableContent.pager(pagerState) { page ->
                    val sections = if (page == 0) {
                        PipelineBucket.entries.map {
                            PIPELINE_LABELS.getValue(it) to state.pipeline[it].orEmpty()
                        }
                    } else {
                        ProductionSubgroup.entries.map {
                            PRODUCTION_LABELS.getValue(it) to state.production[it].orEmpty()
                        }
                    }
                    SectionList(
                        sections = sections,
                        listState = if (page == 0) pipelineScroll else productionScroll,
                        state = state,
                        onOpenCard = onOpenCard,
                        onToggleFirst15 = onToggleFirst15,
                        onLongPress = { actionCard = it },
                    )
                }
            },
        )
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
    listState: LazyListState,
    state: BoardUiState,
    onOpenCard: (BoardCard) -> Unit,
    onToggleFirst15: (String) -> Unit,
    onLongPress: (BoardCard) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
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
                        onLongPress = { onLongPress(cards[i]) },
                        awaitingPickups = state.awaitingPickups[cards[i].id] ?: 0,
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
 *
 * Returned as ScrollableContent rather than rendered directly. It scrolled
 * before too — but by the author remembering to, which is the dependency the
 * type removes.
 */
private fun shimmerContent() = ScrollableContent.column(
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    repeat(4) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(CARD_HEIGHT)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .alpha(alpha)
                .background(Surface)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
        )
    }
}

private fun emptyBoardContent() = ScrollableContent.list {
    item {
        Box(
            Modifier.fillParentMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No active projects", style = DmnType.Body, color = DmnTheme.colors.textMuted)
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
