package com.dmnarration.admin.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.domain.ArchiveReason
import com.dmnarration.admin.domain.BoardAction
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.actionsFor
import com.dmnarration.admin.ui.components.DmnTextField
import com.dmnarration.admin.ui.theme.AlertRed
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.ui.theme.Surface

/**
 * The long-press menu.
 *
 * Every entry is one write through the same path as the First-15 toggle, so
 * each inherits the refusal, offline and rollback behaviour rather than
 * restating it. Archive is the exception that opens a second dialog first,
 * because it collects a reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardActionSheet(
    card: BoardCard,
    onDismiss: () -> Unit,
    onAction: (BoardAction) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Surface,
    ) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Text(
                card.title,
                style = DmnType.Title,
                color = DmnTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            for (action in actionsFor(card)) {
                Text(
                    action.label,
                    style = DmnType.Body,
                    color = if (action.isArchive) AlertRed else DmnTheme.colors.textBody,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(action) }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
        }
    }
}

/**
 * Mark as Released.
 *
 * A confirmation because the card leaves the board: 'released' is outside the
 * active statuses, so the only way back is the web. `released_at` is not
 * mentioned anywhere here — a trigger stamps it.
 */
@Composable
fun ReleaseConfirmDialog(
    card: BoardCard,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Mark as released?", style = DmnType.Title, color = DmnTheme.colors.textPrimary) },
        text = {
            Text(
                "${card.title} will leave the board and appear in Released.",
                style = DmnType.Body,
                color = DmnTheme.colors.textBody,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Mark as released", style = DmnType.BodyMedium, color = DmnTheme.colors.accentAmber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = DmnType.Body, color = DmnTheme.colors.textMuted)
            }
        },
    )
}

/**
 * Archive, with the reason and notes the web collects.
 *
 * Reason defaults to "recasted" as it does on the web. Notes are optional and
 * sent as an empty string rather than omitted, so the column is cleared rather
 * than left holding a previous archive's note.
 */
@Composable
fun ArchiveConfirmDialog(
    card: BoardCard,
    onConfirm: (ArchiveReason, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf(ArchiveReason.RECASTED) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Archive ${card.title}?", style = DmnType.Title, color = DmnTheme.colors.textPrimary) },
        text = {
            Column {
                for (r in ArchiveReason.entries) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { reason = r }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = reason == r,
                            onClick = { reason = r },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = DmnTheme.colors.accentAmber,
                                unselectedColor = DmnTheme.colors.textMuted,
                            ),
                        )
                        Text(r.label, style = DmnType.Body, color = DmnTheme.colors.textBody)
                    }
                }
                DmnTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "Notes (optional)…",
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason, notes) }) {
                Text("Archive", style = DmnType.BodyMedium, color = AlertRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = DmnType.Body, color = DmnTheme.colors.textMuted)
            }
        },
    )
}

/** The affordance revealed behind a card as it swipes left. */
