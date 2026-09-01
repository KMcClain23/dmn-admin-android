package com.dmnarration.admin.ui.editing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.CastMember
import com.dmnarration.admin.domain.EditingState
import com.dmnarration.admin.domain.Pickup
import com.dmnarration.admin.domain.PickupKind
import com.dmnarration.admin.domain.PickupStatus
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType

/**
 * Editing progress and pickups — the only parts of a card an EDITOR can change.
 *
 * Kept in one file so "what may she write" is answerable by reading one thing.
 * Every action is a call to a SECURITY DEFINER function with a role gate: these
 * composables decide which controls to DRAW, and the server decides what
 * happens. Where the two disagree the server wins and the refusal is shown.
 */

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = DmnType.Label,
        color = DmnTheme.colors.textFaint,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    numeric: Boolean = false,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = DmnType.Small) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = DmnTheme.colors.surfaceRaised,
            unfocusedContainerColor = DmnTheme.colors.surfaceRaised,
            focusedTextColor = DmnTheme.colors.textPrimary,
            unfocusedTextColor = DmnTheme.colors.textPrimary,
        ),
        modifier = modifier,
    )
}

/**
 * Chapters edited, chapters total, and done.
 *
 * The state label is DERIVED from those three. There is no status field to read
 * and none to write, which is why "done" cannot appear beside 4 of 12: the label
 * and the count are the same fact.
 */
@Composable
fun EditingSection(
    detail: CardDetail,
    canEdit: Boolean,
    onSetProgress: (Int?, Int?) -> Unit,
    onMarkComplete: (Boolean) -> Unit,
) {
    var edited by remember(detail.id, detail.chaptersEdited) {
        mutableStateOf(detail.chaptersEdited?.toString() ?: "")
    }
    var total by remember(detail.id, detail.chaptersTotal) {
        mutableStateOf(detail.chaptersTotal?.toString() ?: "")
    }

    Column {
        SectionTitle("Editing")

        val label = when (detail.editingState) {
            EditingState.DONE -> "Editing complete"
            EditingState.IN_PROGRESS ->
                "${detail.chaptersEdited ?: 0} of ${detail.chaptersTotal?.toString() ?: "?"} chapters"
            EditingState.NOT_STARTED -> "Not started"
        }
        Text(label, style = DmnType.Body, color = DmnTheme.colors.textBody)

        if (!canEdit) return@Column

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
        ) {
            field(
                value = edited,
                onChange = { edited = it.filter(Char::isDigit) },
                label = "Edited",
                numeric = true,
                modifier = Modifier.width(110.dp),
            )
            field(
                value = total,
                onChange = { total = it.filter(Char::isDigit) },
                label = "Of",
                numeric = true,
                modifier = Modifier.width(110.dp),
            )
            TextButton(onClick = { onSetProgress(edited.toIntOrNull(), total.toIntOrNull()) }) {
                Text("Save", style = DmnType.Body, color = DmnTheme.colors.accentAmber)
            }
        }

        // A toggle, not a one-way button: marking complete by mistake must be
        // undoable, and the column is a nullable timestamp precisely so it can
        // be unset.
        TextButton(onClick = { onMarkComplete(detail.editingState != EditingState.DONE) }) {
            Text(
                if (detail.editingState == EditingState.DONE) "Reopen editing" else "Mark editing complete",
                style = DmnType.Body,
                color = DmnTheme.colors.accentAmber,
            )
        }
    }
}

/** Chapters sort numerically where they are numbers, alphabetically otherwise. */
private val chapterOrder = Comparator<String> { a, b ->
    val na = a.trim().toIntOrNull()
    val nb = b.trim().toIntOrNull()
    when {
        na != null && nb != null -> na.compareTo(nb)
        na != null -> -1
        nb != null -> 1
        else -> a.compareTo(b)
    }
}

/**
 * Pickups on this card, grouped by chapter.
 *
 * THE FORM CHANGES SHAPE BY KIND, because the kinds are not the same question. A
 * misread needs the pair — what was said, what it should be — and the database
 * refuses one without the other; noise and sentence need a timestamp and a note.
 * Showing every field for every kind would ask for information that does not
 * exist and bury the two that must.
 *
 * SEND IS PER CHAPTER, matching send_chapter_pickups. Nothing is emailed here:
 * that transition is the seam E3 hangs the email on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickupsSection(
    pickups: List<Pickup>,
    userId: String?,
    cast: List<CastMember>,
    castError: String?,
    canRaise: Boolean,
    canResolve: Boolean,
    canDelete: Boolean,
    error: String?,
    /** What the last send reported, including narrators it could NOT reach. */
    report: String?,
    onRaise: (String, String, PickupKind, String, String, String, String?) -> Unit,
    onDelete: (String) -> Unit,
    onSendChapter: (String) -> Unit,
    onResolve: (String, PickupStatus) -> Unit,
    onMarkReturned: (String) -> Unit,
    onAdminDelete: (String) -> Unit,
) {
    var chapter by remember { mutableStateOf("") }
    var timestampAt by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(PickupKind.MISREAD) }
    var said by remember { mutableStateOf("") }
    var shouldBe by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    // The narrator ID, not a name: create_pickup takes a real reference now.
    var assignedId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    // A solo book has one possible answer, so it is filled in rather than asked.
    // A two-hander defaults to the CO-NARRATOR: a pickup is usually about the
    // other person's read, and correcting it is one tap.
    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove this pickup?") },
            text = {
                Text(
                    "This cannot be undone. To close it and keep the record, use Dismiss.",
                    style = DmnType.Small,
                )
            },
            confirmButton = {
                TextButton(onClick = { onAdminDelete(id); confirmDelete = null }) {
                    Text("Remove", color = DmnTheme.colors.alertRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }

    LaunchedEffect(cast) {
        if (assignedId == null && cast.isNotEmpty()) {
            assignedId = (cast.firstOrNull { !it.isOwner } ?: cast.first()).narratorId
        }
    }
    var raising by remember { mutableStateOf(false) }

    Column {
        val awaiting = pickups.count { it.status == PickupStatus.SENT }
        SectionTitle(if (awaiting > 0) "Pickups ($awaiting awaiting)" else "Pickups")

        if (pickups.isEmpty()) {
            Text("None raised.", style = DmnType.Body, color = DmnTheme.colors.textFaint)
        }

        // Grouped by chapter, the same grouping the read functions order by, so
        // what arrives together reads together.
        for ((chap, rows) in pickups.groupBy { it.chapter }.toSortedMap(chapterOrder)) {
            Text(
                "Chapter $chap",
                style = DmnType.Label,
                color = DmnTheme.colors.textFaint,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            for (p in rows) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DmnTheme.colors.surfaceRaised)
                        .padding(10.dp),
                ) {
                    val head = listOfNotNull(
                        p.timestampAt.takeIf { it.isNotBlank() },
                        p.kind.label,
                        p.assignedNarratorName?.takeIf { it.isNotBlank() }?.let { "for $it" },
                    ).joinToString(" · ")
                    Text(head, style = DmnType.Small, color = DmnTheme.colors.textFaint)
                    Text(p.summary, style = DmnType.Body, color = DmnTheme.colors.textPrimary)
                    if (p.note.isNotBlank() && p.kind == PickupKind.MISREAD) {
                        Text(p.note, style = DmnType.Body, color = DmnTheme.colors.textBody)
                    }
                    if (p.status != PickupStatus.DRAFT) {
                        Text(p.status.label, style = DmnType.Small, color = DmnTheme.colors.textFaint)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Only what the server would allow: hers, and still draft.
                        if (p.isEditableBy(userId)) {
                            TextButton(onClick = { onDelete(p.id) }) {
                                Text("Delete", style = DmnType.Small, color = DmnTheme.colors.accentAmber)
                            }
                        }
                        // AN UNKNOWN STATUS OFFERS NOTHING. This build cannot
                        // know which transitions the server accepts from a state
                        // it has never heard of, and guessing is what produced a
                        // Resolve button that only worked where the label lied.
                        if (!p.status.isKnown) {
                            Text(
                                p.status.label,
                                style = DmnType.Small,
                                color = DmnTheme.colors.textFaint,
                            )
                        }

                        // SENT is out with the narrator. Resolve from sent is
                        // refused by the server since P1, so that button could
                        // only ever produce an error — "Re-recorded" is the
                        // transition that actually exists.
                        if (p.status == PickupStatus.SENT) {
                            TextButton(onClick = { onMarkReturned(p.id) }) {
                                Text("Re-recorded", style = DmnType.Small, color = DmnTheme.colors.accentAmber)
                            }
                        }

                        if (canResolve && p.status == PickupStatus.RETURNED) {
                            TextButton(onClick = { onResolve(p.id, PickupStatus.RESOLVED) }) {
                                Text("Verify & close", style = DmnType.Small, color = DmnTheme.colors.accentAmber)
                            }
                        }
                        if (canResolve &&
                            (p.status == PickupStatus.SENT || p.status == PickupStatus.RETURNED)
                        ) {
                            TextButton(onClick = { onResolve(p.id, PickupStatus.DISMISSED) }) {
                                Text("Dismiss", style = DmnType.Small, color = DmnTheme.colors.accentAmber)
                            }
                        }
                        // REMOVE, and deliberately not Dismiss. Dismiss closes
                        // something real and keeps the history true; this is for
                        // rows that should never have existed. Irreversible, so
                        // it confirms, and it is the quietest control on the row.
                        if (canDelete) {
                            TextButton(onClick = { confirmDelete = p.id }) {
                                Text("Remove", style = DmnType.Small, color = DmnTheme.colors.textFaint)
                            }
                        }
                    }
                }
            }
            // One Send per chapter, offered only when this session has something
            // to send. The server refuses an empty send and says so.
            if (canRaise && rows.any { it.isEditableBy(userId) }) {
                TextButton(onClick = { onSendChapter(chap) }) {
                    Text("Send chapter $chap", style = DmnType.Body, color = DmnTheme.colors.accentAmber)
                }
            }
        }

        if (canRaise) {
            if (!raising) {
                TextButton(onClick = { raising = true }) {
                    Text("Raise a pickup", style = DmnType.Body, color = DmnTheme.colors.accentAmber)
                }
            } else {
                Column(Modifier.padding(top = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        field(chapter, { chapter = it }, "Chapter", modifier = Modifier.width(110.dp))
                        field(timestampAt, { timestampAt = it }, "04:32.1", modifier = Modifier.width(130.dp))
                    }
                    Row(
                        Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (k in PickupKind.entries) {
                            TextButton(onClick = { kind = k }) {
                                Text(
                                    k.label,
                                    style = DmnType.Small,
                                    color = if (k == kind) {
                                        DmnTheme.colors.accentAmber
                                    } else {
                                        DmnTheme.colors.textFaint
                                    },
                                )
                            }
                        }
                    }
                    // THE SHAPE CHANGE. The database refuses a misread without
                    // both halves, so the form asks for exactly what this kind
                    // needs and nothing else.
                    if (kind.needsSaidPair) {
                        field(said, { said = it }, "She said", modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        field(shouldBe, { shouldBe = it }, "Should be", modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    }
                    field(note, { note = it }, "Note", modifier = Modifier.fillMaxWidth().padding(top = 6.dp))

                    // WHOSE READ — sized to THIS BOOK's cast, never the roster.
                    //
                    //   1   a solo book has one possible answer: assign it and
                    //       draw no control at all.
                    //   2   27 of 33 books. Two named buttons, co-narrator first.
                    //   3+  chips, for this card's cast only.
                    //
                    // isOwner marks whose book it is and is NOT viewer-aware, so
                    // it is never rendered as "you".
                    if (castError != null) {
                        Text(
                            castError,
                            style = DmnType.Small,
                            color = DmnTheme.colors.alertRed,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (cast.size == 1) {
                        Text(
                            "For ${cast[0].displayName} — the only narrator on this book.",
                            style = DmnType.Small,
                            color = DmnTheme.colors.textFaint,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    } else if (cast.isNotEmpty()) {
                        FlowRow(
                            Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (n in cast) {
                                TextButton(onClick = {
                                    assignedId = if (assignedId == n.narratorId) null else n.narratorId
                                }) {
                                    Text(
                                        if (n.isOwner) n.displayName else "${n.displayName} · co-narrator",
                                        style = DmnType.Small,
                                        color = if (assignedId == n.narratorId) {
                                            DmnTheme.colors.accentAmber
                                        } else {
                                            DmnTheme.colors.textFaint
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Row {
                        TextButton(
                            // Mirrors the server's rules, so the button is not
                            // offered in a state it would refuse.
                            enabled = chapter.isNotBlank() &&
                                (!kind.needsSaidPair || (said.isNotBlank() && shouldBe.isNotBlank())),
                            onClick = {
                                onRaise(chapter, timestampAt, kind, said, shouldBe, note, assignedId)
                                timestampAt = ""
                                said = ""
                                shouldBe = ""
                                note = ""
                                raising = false
                            },
                        ) { Text("Add", style = DmnType.Body, color = DmnTheme.colors.accentAmber) }
                        TextButton(onClick = { raising = false }) {
                            Text("Cancel", style = DmnType.Body, color = DmnTheme.colors.textFaint)
                        }
                    }
                }
            }
        }

        // A refused write says so, and is never folded into the card's own
        // error: "your pickup did not save" and "the card would not load" are
        // different things to have gone wrong.
        error?.let {
            Text(
                it,
                style = DmnType.Small,
                color = DmnTheme.colors.alertRed,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        // Not an error: a send that reached some narrators and not others is the
        // normal case, and saying only "sent" would hide the ones it missed.
        report?.let {
            Text(
                it,
                style = DmnType.Small,
                color = DmnTheme.colors.textBody,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
