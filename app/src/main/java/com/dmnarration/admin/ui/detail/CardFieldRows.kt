package com.dmnarration.admin.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.FieldWrite
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType

/**
 * One row per field, and ONE ROW ONLY.
 *
 * The card previously showed each value twice: a formatted read-only row in the
 * summary, and a raw editable row in a separate Edit section below a
 * full-length book description. Dean asked for editing, got it, and could not
 * find it — he tapped the formatted rows, which did nothing, and never reached
 * the editable copies. The only controls he found were the two switches, whose
 * TYPE carries an affordance that text rows do not.
 *
 * Two representations of one field is two sources for one figure, in the UI
 * layer, and it fails the same way: one of them is better-looking and wrong.
 * The raw forms — ["Ann Dahlia"], duet, contracted, 2026-09-24 — were nobody's
 * decision. They were an artefact of the second section existing.
 *
 * So: the summary row IS the editable row. It keeps the formatting it always
 * had, and gains a mark saying it can be changed. The mark does two jobs, which
 * is why it is worth designing rather than sprinkling: the same thing that says
 * "you can change this" says "and not those" about the deferred shapes and the
 * ones written elsewhere.
 */
@Composable
fun CardFieldRow(
    field: CardField,
    detail: CardDetail,
    canEdit: Boolean,
    write: FieldWrite<String>,
    onSave: (String) -> Unit,
    onEdit: () -> Unit,
) {
    val c = DmnTheme.colors
    val shown = field.display(detail)
    val raw = field.read(detail)
    var open by remember(field.column) { mutableStateOf(false) }
    var draft by remember(field.column, raw) { mutableStateOf(raw) }
    // Free text of unbounded length, and anything that has grown long in
    // practice — a URL is one line but a very wide one.
    val stacked = field.kind == CardFieldKind.MultilineText || shown.length > 34

    // A switch is already its own affordance; making it a two-tap affair would
    // be worse than the toggle, and these are the two controls Dean DID find.
    if (field.kind == CardFieldKind.Bool) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(field.label, style = DmnType.Body, color = c.textMuted)
                FieldOutcome(write)
            }
            Switch(
                checked = raw == "true",
                enabled = canEdit && write !is FieldWrite.Saving,
                onCheckedChange = { onSave(if (it) "true" else "false") },
            )
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .let { m -> if (canEdit) m.clickable { onEdit(); draft = raw; open = !open } else m },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                field.label,
                style = DmnType.Body,
                color = c.textMuted,
                modifier = Modifier.padding(end = 12.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                if (!stacked) {
                    Text(
                        // "Not set" rather than a hidden row. The summary used
                        // to omit empty values, which is right for reading and
                        // wrong here: the errand this exists for is filling in
                        // a word count that is MISSING, and a row Dean cannot
                        // see is one he cannot fill in.
                        shown.ifBlank { "Not set" },
                        style = DmnType.Body,
                        color = if (shown.isBlank()) c.textDim else c.textPrimary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (canEdit) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Edit ${field.label}",
                        tint = c.accentAmberDim,
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                    )
                }
            }
        }

        // A book description and a full Amazon URL do not belong in the right
        // half of a label/value row: the label ends up centred against a wall
        // of text. Long values get the full width under their label, which is
        // how the description always rendered before the merge.
        if (stacked) {
            Text(
                shown.ifBlank { "Not set" },
                style = DmnType.Body,
                color = if (shown.isBlank()) c.textDim else c.textBody,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }

        FieldOutcome(write)

        if (open && canEdit) {
            if (field.kind == CardFieldKind.Choice) {
                Column(Modifier.padding(top = 6.dp)) {
                    for (choice in field.choices) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSave(choice.value); open = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                choice.label,
                                style = DmnType.Body,
                                color = if (choice.value == raw) c.accentAmber else c.textBody,
                                modifier = Modifier.weight(1f),
                            )
                            if (choice.value == raw) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = c.accentAmber)
                            }
                        }
                    }
                    TextButton(onClick = { onSave(""); open = false }) {
                        Text("Clear", style = DmnType.Small, color = c.textMuted)
                    }
                }
            } else {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = field.kind != CardFieldKind.MultilineText,
                    placeholder = { Text(placeholderFor(field), style = DmnType.Small, color = c.textDim) },
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardFor(field.kind)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = c.surfaceRaised,
                        unfocusedContainerColor = c.surfaceRaised,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { draft = raw; open = false }) {
                        Text("Cancel", style = DmnType.Small, color = c.textMuted)
                    }
                    TextButton(onClick = { onSave(draft); open = false }, enabled = draft != raw) {
                        Text("Save", style = DmnType.Small, color = c.accentAmber)
                    }
                }
            }
            field.help?.let {
                Text(it, style = DmnType.Small, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/**
 * A row that carries a value nobody can change here, and says why.
 *
 * Takes NO chevron, deliberately. The absence is the second half of the mark's
 * job: with every editable row carrying one, a row without one is legible as
 * "not here" rather than as a control that failed to respond.
 */
@Composable
fun ReadOnlyFieldRow(label: String, value: String?, reason: String? = null) {
    val c = DmnTheme.colors
    if (value.isNullOrBlank() && reason == null) return
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = DmnType.Body, color = c.textMuted)
            value?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = DmnType.Body, color = c.textPrimary)
            }
        }
        reason?.let { Text(it, style = DmnType.Small, color = c.textDim) }
    }
}

/**
 * What happened to this field's last write.
 *
 * Four outcomes, not two. A refusal arrives wearing HTTP 200 with zero rows and
 * is a different thing from a failure; and a sentence the DATABASE raised is
 * shown verbatim, so the phone and the web refuse in the same words.
 */
@Composable
private fun FieldOutcome(write: FieldWrite<String>) {
    val c = DmnTheme.colors
    when (write) {
        is FieldWrite.Idle -> Unit
        is FieldWrite.Saving -> Text("Saving…", style = DmnType.Small, color = c.textDim)
        is FieldWrite.Saved -> Text("Saved", style = DmnType.Small, color = c.capacityLight)
        is FieldWrite.Refused -> Text(
            "You no longer have permission to make that change.",
            style = DmnType.Small,
            color = c.alertRed,
        )
        is FieldWrite.Failed -> Text(write.message, style = DmnType.Small, color = c.alertRed)
    }
}

private fun keyboardFor(kind: CardFieldKind): KeyboardType = when (kind) {
    CardFieldKind.Integer -> KeyboardType.Number
    CardFieldKind.Decimal -> KeyboardType.Decimal
    else -> KeyboardType.Text
}

private fun placeholderFor(field: CardField): String = when (field.kind) {
    CardFieldKind.Date -> "YYYY-MM-DD"
    else -> "Empty to clear"
}
