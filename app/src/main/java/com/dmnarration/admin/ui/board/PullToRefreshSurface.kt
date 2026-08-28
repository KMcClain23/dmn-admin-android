package com.dmnarration.admin.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Content that cannot fail to participate in nested scroll.
 *
 * Pull-to-refresh is driven by nested scroll, so content that does not scroll
 * cannot be pulled. That is not a hypothesis here: `state.isEmpty -> Unit`
 * rendered nothing inside a `PullToRefreshBox` and stranded a refused session on
 * a screen it could not leave without restarting the app — which was Stage 1's
 * DoD 19 bug, reintroduced by a different route, in the same state, despite
 * being documented. **Any branch that renders nothing kills the gesture**, and
 * the property is invisible when lost: nothing throws, nothing looks wrong, the
 * pull simply does nothing.
 *
 * Vigilance had already failed once, so this is a type instead. The constructor
 * is private and every factory below *builds the scrolling container itself* —
 * the caller supplies only what goes inside it. There is deliberately no
 * `ScrollableContent { ... }` taking an arbitrary composable: that would be a
 * promise rather than a proof, and a promise is what we had.
 *
 * The illegal construction — passing a bare lambda to [PullToRefreshSurface] —
 * does not compile.
 */
@Immutable
class ScrollableContent private constructor(
    internal val render: @Composable () -> Unit,
) {
    companion object {
        /** A lazy list. The scroll container is built here, not by the caller. */
        fun list(
            contentPadding: Dp = 0.dp,
            /**
             * Extra room under the last row, for content the navigation bar
             * would otherwise overlap. Reinstated for the money lists, whose
             * final row was being sliced mid-value.
             */
            extraBottomPadding: Dp = 0.dp,
            verticalArrangement: Arrangement.Vertical = Arrangement.Top,
            content: LazyListScope.() -> Unit,
        ) = ScrollableContent {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = contentPadding,
                    top = contentPadding,
                    end = contentPadding,
                    bottom = contentPadding + extraBottomPadding,
                ),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }

        /** A scrolling column, for content that is not list-shaped. */
        fun column(
            verticalArrangement: Arrangement.Vertical = Arrangement.Top,
            content: @Composable ColumnScope.() -> Unit,
        ) = ScrollableContent {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }

        /** Paged content. Each page is itself inside the pager's scroll. */
        fun pager(
            state: PagerState,
            page: @Composable (Int) -> Unit,
        ) = ScrollableContent {
            HorizontalPager(state = state, modifier = Modifier.fillMaxSize()) { page(it) }
        }

        /**
         * Nothing to show, and still pullable.
         *
         * This is the case that caused the bug. Rendering nothing at all is not
         * available as an option — the only way to say "nothing" is this, and it
         * scrolls.
         */
        fun blank() = list {}
    }
}

/**
 * Pull-to-refresh over content that is scrollable by construction.
 *
 * Takes a [ScrollableContent] rather than a composable lambda, which is the
 * whole point: `PullToRefreshSurface(...) { }` is a type error, not a subtle
 * runtime failure discovered on a device by someone stuck on a screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshSurface(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: ScrollableContent,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = rememberPullToRefreshState(),
        modifier = modifier,
    ) {
        content.render()
    }
}
