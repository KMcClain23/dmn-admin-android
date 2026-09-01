package com.dmnarration.admin.ui.editing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.NeedsMe
import com.dmnarration.admin.domain.UserRole
import com.dmnarration.admin.ui.theme.Background
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.ui.theme.SurfaceRaised

/**
 * Editing, as a destination.
 *
 * ── ONE SHAPE, BOTH ROLES ──────────────────────────────────────────────────
 *
 * Dean and Marizete see the same two sections. What differs is what the DATA
 * says belongs in them, and the server decides that:
 *
 *   Needs you   `pickups_needing_me()` — his `sent` rows, her `returned` ones.
 *   Books       everything in editing, with the holder named. Hers are marked
 *               and unclaimed ones are claimable.
 *
 * Nothing is filtered again here. Two surfaces already show this idea (the web
 * hub and this) and a client-side rule would be a third answer to "which rows
 * are mine", free to disagree with the two that are enforced.
 *
 * NO WRITE ACTIONS AT THIS LEVEL beyond claiming. Re-recorded, Resolve, Dismiss
 * and Remove live on the book pane, which is the moved EditingSection and
 * PickupsSection — one implementation, reached from here and linked to from card
 * detail.
 */
@Composable
fun EditingScreen(
    state: EditingUiState,
    onRefresh: () -> Unit,
    onOpenBook: (String) -> Unit,
    onClaim: (String) -> Unit,
    onUnclaim: (String) -> Unit,
    onDismissClaimError: () -> Unit,
) {
    val c = DmnTheme.colors

    Column(Modifier.background(Background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Editing", style = DmnType.TitleLg, color = c.textPrimary, modifier = Modifier.weight(1f))
            if (state.refreshing) Text("…", style = DmnType.Body, color = c.textMuted)
            TextButton(onClick = onRefresh) {
                Text("Refresh", style = DmnType.Small, color = c.textMuted)
            }
        }

        state.error?.let {
            Text(
                it,
                style = DmnType.Small,
                color = c.alertRed,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        state.claimError?.let {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(it, style = DmnType.Small, color = c.alertRed, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissClaimError) {
                    Text("OK", style = DmnType.Small, color = c.textMuted)
                }
            }
        }

        if (state.loading) {
            Text(
                "Loading…",
                style = DmnType.Body,
                color = c.textFaint,
                modifier = Modifier.padding(16.dp),
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxWidth()) {
            /*
              PINNED, AND ABSENT WHEN EMPTY.

              A "Needs you (0)" heading is a heading over nothing, which reads as
              something failing to load rather than as good news. Nothing at all
              is the honest rendering of nothing outstanding.
            */
            if (state.needsMe.isNotEmpty()) {
                item { SectionHeading("Needs you", "${state.needsMe.size}") }
                items(state.needsMe, key = { it.id }) { p ->
                    NeedsMeRow(p, onClick = { onOpenBook(p.cardId) })
                }
            }

            item {
                SectionHeading(
                    if (state.role == UserRole.EDITOR) "Books" else "In editing",
                    "${state.books.size}",
                )
            }
            if (state.books.isEmpty()) {
                item {
                    Text(
                        "No books are in editing.",
                        style = DmnType.Small,
                        color = c.textFaint,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(state.books, key = { it.id }) { card ->
                BookRow(
                    card = card,
                    holder = state.holderOf(card.id)?.editorName,
                    mine = state.isMine(card.id),
                    // Claiming is the EDITOR's action. Dean assigns from the
                    // web; giving him a Claim button here would let him take a
                    // book from her by tapping the wrong row.
                    canClaim = state.role == UserRole.EDITOR,
                    onOpen = { onOpenBook(card.id) },
                    onClaim = { onClaim(card.id) },
                    onUnclaim = { onUnclaim(card.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, count: String) {
    val c = DmnTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = DmnType.Label, color = c.textMuted, modifier = Modifier.weight(1f))
        Text(count, style = DmnType.Label, color = c.textFaint)
    }
}

/**
 * One line waiting on you.
 *
 * THE CORRECTION IS THE POINT, so it is the largest thing on the row and it is
 * NEVER TRUNCATED. A pickup you have to open a card to read is a pickup this
 * screen has not actually shown you. The timestamp is monospaced so a column of
 * them scans as times rather than as prose.
 */
@Composable
private fun NeedsMeRow(p: NeedsMe, onClick: () -> Unit) {
    val c = DmnTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceRaised)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.bookTitle,
                style = DmnType.BodyMedium,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                p.timestampAt,
                style = DmnType.Small.copy(fontFamily = FontFamily.Monospace),
                color = c.accentAmber,
            )
        }
        Text("ch ${p.chapter}", style = DmnType.Small, color = c.textMuted)
        if (p.line.isNotBlank()) {
            Text(
                p.line,
                style = DmnType.Body,
                color = c.textPrimary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun BookRow(
    card: BoardCard,
    holder: String?,
    mine: Boolean,
    canClaim: Boolean,
    onOpen: () -> Unit,
    onClaim: () -> Unit,
    onUnclaim: () -> Unit,
) {
    val c = DmnTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(card.title, style = DmnType.Body, color = c.textPrimary)
            Text(
                buildString {
                    val edited = card.chaptersEdited ?: 0
                    val total = card.chaptersTotal
                    append(
                        when {
                            card.editingCompletedAt != null -> "Complete"
                            total != null && total > 0 -> "$edited of $total chapters"
                            edited > 0 -> "$edited chapter${if (edited == 1) "" else "s"} edited"
                            else -> "Not started"
                        },
                    )
                    // WHOSE IT IS, said plainly. "Unclaimed" is a real state and
                    // saying nothing would leave it looking like everybody's.
                    append(" · ")
                    append(if (mine) "yours" else holder?.let { "with $it" } ?: "unclaimed")
                },
                style = DmnType.Small,
                color = c.textMuted,
            )
        }
        if (canClaim) {
            when {
                mine -> TextButton(onClick = onUnclaim) {
                    // "Unclaim", never "Release": released already means
                    // published, and the two must not share a word on one screen.
                    Text("Unclaim", style = DmnType.Small, color = c.textMuted)
                }
                holder == null -> TextButton(onClick = onClaim) {
                    Text("Claim", style = DmnType.Small, color = c.accentAmber)
                }
            }
        }
    }
}
