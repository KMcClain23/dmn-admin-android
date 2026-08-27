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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dmnarration.admin.domain.ArchivedCard
import com.dmnarration.admin.domain.archiveReasonLabel
import com.dmnarration.admin.ui.board.PullToRefreshSurface
import com.dmnarration.admin.ui.board.ScrollableContent
import com.dmnarration.admin.ui.theme.Background
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.ui.theme.Surface
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * The archive: a recovery screen, not a browsing one.
 *
 * There is no search. One row exists today and scanning it is not the problem;
 * a search box over a list this size is a control that can only fail to earn
 * itself. If Dean later archives enough that scanning fails, that is when it
 * gets built.
 *
 * The empty state reads as reassurance rather than as absence — and it is only
 * reachable from a read that actually succeeded. "Nothing is archived" is a
 * claim about the data, and a screen that makes it after a failed read is
 * telling Dean something nobody checked.
 */
@Composable
fun ArchiveScreen(
    state: ShelfState,
    onRefresh: () -> Unit,
    onUnarchive: (String) -> Unit,
    onOpenCard: (String) -> Unit,
) {
    val c = DmnTheme.colors
    var restoring by remember { mutableStateOf<ArchivedCard?>(null) }

    Column {
        // A failed read and a refused write say different things and are shown
        // separately. Folding them into one line would let "could not restore
        // that card" look like the list itself is wrong.
        state.archivedError?.let {
            Text(
                it,
                style = DmnType.Body,
                color = c.alertRed,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        state.writeError?.let {
            Text(
                it,
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
                if (state.archived.isEmpty()) {
                    item { EmptyLine(state) }
                    return@list
                }
                rows(
                    cards = state.archived,
                    canEdit = state.capabilities.canEdit,
                    onOpenCard = onOpenCard,
                    onRestore = { restoring = it },
                )
            },
        )
    }

    restoring?.let { card ->
        RestoreConfirmDialog(
            card = card,
            onConfirm = {
                restoring = null
                onUnarchive(card.id)
            },
            onDismiss = { restoring = null },
        )
    }
}

/**
 * Three different empty screens, kept apart.
 *
 * Loading, failed, and genuinely empty all render no rows, and until they are
 * distinguished they are one screen wearing three meanings — the shape of every
 * bug this project has spent five stages on.
 */
@Composable
private fun EmptyLine(state: ShelfState) {
    val c = DmnTheme.colors
    when {
        state.archivedLoading -> Text("Loading…", style = DmnType.Body, color = c.textMuted)
        // The error is already above. Adding "nothing is archived" underneath it
        // would contradict it.
        state.archivedError != null -> Unit
        else -> Text(
            "Nothing is archived.",
            style = DmnType.Body,
            color = c.textMuted,
        )
    }
}

private fun LazyListScope.rows(
    cards: List<ArchivedCard>,
    canEdit: Boolean,
    onOpenCard: (String) -> Unit,
    onRestore: (ArchivedCard) -> Unit,
) {
    items(count = cards.size, key = { cards[it].id }) { i ->
        ArchivedRow(cards[i], canEdit, onOpenCard, onRestore)
    }
}

@Composable
private fun ArchivedRow(
    card: ArchivedCard,
    canEdit: Boolean,
    onOpenCard: (String) -> Unit,
    onRestore: (ArchivedCard) -> Unit,
) {
    val c = DmnTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.surfaceRaised)
            .clickable { onOpenCard(card.id) }
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(56.dp)
                    .height(84.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Background),
            ) {
                if (card.coverUrl != null) {
                    AsyncImage(
                        model = card.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    card.title,
                    style = DmnType.Title,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.author,
                    style = DmnType.Small,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    archivedLine(card),
                    style = DmnType.Small,
                    color = c.textDim,
                    modifier = Modifier.padding(top = 6.dp),
                )
                // Notes are shown in full rather than as a first line: this is a
                // recovery screen, and the note is usually the reason Dean is
                // looking. An empty note renders nothing at all, which is also
                // what a null one does — the two are indistinguishable here, and
                // the app and the web disagree about which one they write.
                card.archivedNotes?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = DmnType.Small,
                        color = c.textMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (canEdit) {
            TextButton(
                onClick = { onRestore(card) },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Restore to board", style = DmnType.BodyMedium, color = c.accentAmber)
            }
        }
    }
}

/** "Archived 14 Aug 2026 · Recasted", with each half omitted when it is not known. */
private fun archivedLine(card: ArchivedCard): String {
    val date = card.archivedAt?.let { at ->
        val d = at.toLocalDateTime(TimeZone.currentSystemDefault()).date
        "Archived ${d.day} ${MONTHS[d.month.number - 1]} ${d.year}"
    }
    val reason = archiveReasonLabel(card.archivedReason)
    return listOfNotNull(date, reason).joinToString(" · ").ifEmpty { "Archived" }
}

/**
 * The dialog names the status the card is going back to.
 *
 * Un-archiving does not change the status — the archive did not change it
 * either — so a card archived while recording returns as a recording card. That
 * is easy to forget and easy to "helpfully" correct, so the screen states it
 * before Dean commits rather than leaving him to discover it on the board.
 */
@Composable
private fun RestoreConfirmDialog(
    card: ArchivedCard,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Text("Restore to board?", style = DmnType.Title, color = DmnTheme.colors.textPrimary)
        },
        text = {
            Text(
                "${card.title} returns to the board as a ${card.status} card, the status it " +
                    "had when it was archived. Its archive reason and notes are cleared.",
                style = DmnType.Body,
                color = DmnTheme.colors.textBody,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Restore", style = DmnType.BodyMedium, color = DmnTheme.colors.accentAmber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = DmnType.Body, color = DmnTheme.colors.textMuted)
            }
        },
    )
}

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
