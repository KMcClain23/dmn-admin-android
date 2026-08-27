package com.dmnarration.admin.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dmnarration.admin.domain.ReleasedBook
import com.dmnarration.admin.domain.ratingLabel
import com.dmnarration.admin.domain.reviewCountLabel
import com.dmnarration.admin.ui.board.PullToRefreshSurface
import com.dmnarration.admin.ui.board.ScrollableContent
import com.dmnarration.admin.ui.theme.Background
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * Everything Dean has released, newest first.
 *
 * The ordering is the database's — `released_for_session()` orders by
 * `released_at desc nulls last, title asc`, matching the web's route including
 * its tiebreak — and this screen renders the rows in the order they arrive
 * rather than re-sorting them into a second opinion.
 *
 * The header states which count it is showing. There are two honest answers to
 * "how many has he released" and they differ the moment a released book is
 * archived; saying "12 released" without saying which population it counts is
 * how the two numbers on the web came to disagree by design and look like a bug.
 */
@Composable
fun ReleasedScreen(
    state: ShelfState,
    onRefresh: () -> Unit,
    onOpenCard: (String) -> Unit,
) {
    val c = DmnTheme.colors

    Column {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)) {
            Text("Released", style = DmnType.TitleLg, color = c.textPrimary)
            if (!state.releasedLoading && state.releasedError == null) {
                Text(
                    countLabel(state),
                    style = DmnType.Small,
                    color = c.textMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (state.releasedError != null) {
            Text(
                state.releasedError,
                style = DmnType.Body,
                color = c.alertRed,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        PullToRefreshSurface(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            content = ScrollableContent.list(
                contentPadding = 16.dp,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.released.isEmpty()) {
                    // An empty list is still a lazy list, so the screen scrolls
                    // and pulls with nothing in it. Returning early with nothing
                    // rendered is what killed pull-to-refresh on the board.
                    item {
                        Text(
                            when {
                                state.releasedLoading -> "Loading…"
                                // The error is already on screen above; saying
                                // "nothing released" underneath it would be the
                                // app asserting a fact it does not have.
                                state.releasedError != null -> ""
                                else -> "Nothing released yet."
                            },
                            style = DmnType.Body,
                            color = c.textMuted,
                        )
                    }
                    return@list
                }
                items(state.released, onOpenCard)
            },
        )
    }
}

/**
 * Which population this number counts, said out loud.
 *
 * When the two agree there is nothing to disambiguate and the extra clause would
 * be noise; when they differ, the screen has to say which one it means.
 */
private fun countLabel(state: ShelfState): String {
    val counts = state.counts
    return if (counts.agree) {
        "${counts.visible} released"
    } else {
        "${counts.visible} released · ${counts.allTime} all-time, including archived"
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(
    books: List<ReleasedBook>,
    onOpenCard: (String) -> Unit,
) {
    items(count = books.size, key = { books[it].id }) { i ->
        ReleasedRow(books[i], onOpenCard)
    }
}

@Composable
private fun ReleasedRow(book: ReleasedBook, onOpenCard: (String) -> Unit) {
    val c = DmnTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.surfaceRaised)
            .clickable { onOpenCard(book.id) }
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .width(64.dp)
                .height(96.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Background),
        ) {
            if (book.coverUrl != null) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                style = DmnType.Title,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                book.author,
                style = DmnType.Small,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                releaseDateLabel(book.releasedAt),
                style = DmnType.Small,
                color = c.textDim,
                modifier = Modifier.padding(top = 6.dp),
            )
            Amazon(book)
        }
    }
}

/**
 * The rating and the review count, each rendered only if it is known.
 *
 * A missing rating leaves the star off entirely rather than showing "0.0", and
 * a missing review count says nothing rather than "No reviews yet" — because
 * zero reviews is something Amazon reported and unknown is not.
 */
@Composable
private fun Amazon(book: ReleasedBook) {
    val c = DmnTheme.colors
    val rating = ratingLabel(book.amazonRating)
    val reviews = reviewCountLabel(book.amazonReviewCount)
    if (rating == null && reviews == null) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        if (rating != null) {
            Text("★", style = DmnType.Small, color = c.accentAmber)
            Text(
                rating,
                style = DmnType.Numeric,
                color = c.textPrimary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (reviews != null) {
            Text(
                reviews,
                style = DmnType.Small,
                color = c.textMuted,
                modifier = Modifier.padding(start = if (rating != null) 8.dp else 0.dp),
            )
        }
    }
}

/**
 * `released_at` is a real instant, so it is converted in the device's zone.
 *
 * Unlike `deadline`, which is a Postgres `date` and must never go near a
 * timezone — the distinction the board's comment is about.
 */
private fun releaseDateLabel(at: Instant?): String {
    if (at == null) return "Release date not recorded"
    val d = at.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${d.day} ${MONTHS[d.month.number - 1]} ${d.year}"
}

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
