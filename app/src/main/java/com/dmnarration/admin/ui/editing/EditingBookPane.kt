package com.dmnarration.admin.ui.editing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.PickupKind
import com.dmnarration.admin.domain.PickupStatus
import com.dmnarration.admin.ui.detail.CardDetailUiState
import com.dmnarration.admin.ui.theme.Background
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType

/**
 * One book's editing progress and pickups — and the ONLY place either is
 * written, on any screen.
 *
 * ── WHAT MOVED, AND WHAT DID NOT ───────────────────────────────────────────
 *
 * `EditingSection` and `PickupsSection` used to render inside CardDetailScreen.
 * They were MOVED here, not copied: card detail now carries a single
 * "Editing & pickups →" row and offers no pickup action at all. Two screens that
 * both write pickups is how one rule becomes two implementations, and the rules
 * involved — who may resolve, who may delete, which rows are editable — are
 * exactly the kind that drift quietly.
 *
 * The VIEW MODEL is reused too. This pane is driven by CardDetailViewModel,
 * keyed by card id, because that view model already owns every write these
 * sections make. A second view model with the same methods would be the same
 * duplication one layer down.
 */
@Composable
fun EditingBookPane(
    state: CardDetailUiState,
    capabilities: Capabilities,
    onBack: () -> Unit,
    onSetProgress: (Int?, Int?) -> Unit,
    onMarkComplete: (Boolean) -> Unit,
    onRaisePickup: (String, String, PickupKind, String, String, String, String?) -> Unit,
    onDeletePickup: (String) -> Unit,
    onSendChapter: (String) -> Unit,
    onResolvePickup: (String, PickupStatus) -> Unit,
    onMarkReturned: (String) -> Unit,
    onAdminDeletePickup: (String) -> Unit,
) {
    val c = DmnTheme.colors
    val detail = state.detail

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            Modifier.fillMaxWidth().padding(end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.textMuted)
            }
            Text(
                detail?.title ?: "Editing",
                style = DmnType.Title,
                color = c.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }

        when {
            // A refusal renders nothing but its message. What the session last
            // read is not shown, because the server has said it may not be.
            state.refused -> Message("This account may not read that book.", isError = true)
            state.missing -> Message("That book no longer exists.", isError = false)
            detail == null && state.loading -> Message("Loading…", isError = false)
            detail == null -> Message(state.error ?: "Could not load that book.", isError = true)
            else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                EditingSection(
                    detail = detail,
                    error = state.progressError,
                    // An editor may record progress; so may Dean, because the
                    // gate admits admin too. Anyone who can see the book can say
                    // how far through it is.
                    canEdit = true,
                    onSetProgress = onSetProgress,
                    onMarkComplete = onMarkComplete,
                )
                PickupsSection(
                    pickups = state.pickups,
                    userId = state.userId,
                    cast = state.cast,
                    castError = state.castError,
                    canRaise = true,
                    // VERIFICATION IS THE EDITOR'S, and canEdit is false for
                    // editors — reusing it would hide Resolve from the one
                    // person the step belongs to. canVerifyPickups says exactly
                    // this and nothing else.
                    canResolve = capabilities.canVerifyPickups,
                    // Removing a row outright stays Dean's.
                    canDelete = capabilities.canEdit,
                    error = state.pickupError,
                    report = state.sendReport,
                    onRaise = onRaisePickup,
                    onDelete = onDeletePickup,
                    onSendChapter = onSendChapter,
                    onResolve = onResolvePickup,
                    onMarkReturned = onMarkReturned,
                    onAdminDelete = onAdminDeletePickup,
                )
            }
        }
    }
}

@Composable
private fun Message(text: String, isError: Boolean) {
    val c = DmnTheme.colors
    Text(
        text,
        style = DmnType.Body,
        color = if (isError) c.alertRed else c.textMuted,
        modifier = Modifier.padding(16.dp),
    )
}
