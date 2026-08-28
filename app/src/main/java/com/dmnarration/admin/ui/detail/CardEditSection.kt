package com.dmnarration.admin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.FieldWrite
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType

/**
 * The editable half of the card.
 *
 * PER-FIELD, not a form. Tapping a value opens that one field; saving writes
 * that one column. The web PUTs the entire form on every save, and this is a
 * deliberate divergence from it — see CardDetailViewModel.save for why the
 * ambiguity a whole-form write creates is the thing being avoided.
 *
 * Every granted scalar appears whether it holds a value or not. The read-only
 * summary above hides empty rows, which is right for reading and wrong for
 * editing: a field Dean cannot see is a field he cannot fill in, and "correct
 * the missing word count" is the errand this stage exists for.
 */
@Composable
fun CardEditSection(
    detail: CardDetail,
    capabilities: Capabilities,
    writeFor: (String) -> FieldWrite<String>,
    onSave: (String, String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val c = DmnTheme.colors

    // Money fields carry rates and shares. The same capability that hides the
    // Money summary hides its editors, rather than a second rule about who may
    // see what.
    val groups = CardFieldGroup.entries.filter { group ->
        group != CardFieldGroup.Money || capabilities.canViewFinancials
    }

    Column {
        for (group in groups) {
            val fields = CARD_FIELDS.filter { it.group == group }
            if (fields.isEmpty()) continue

            Text(
                group.title.uppercase(),
                style = DmnType.Label,
                color = c.textDim,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
            )
            for (field in fields) {
                EditableField(
                    field = field,
                    current = field.read(detail),
                    canEdit = capabilities.canEdit,
                    write = writeFor(field.column),
                    onSave = { onSave(field.column, it) },
                    onEdit = { onEdit(field.column) },
                )
            }
        }

        DeferredBlock(
            title = "Not editable on the phone yet",
            // Shown rather than omitted. A field that vanishes reads as data that
            // does not exist, and all four of these hold real values on real cards.
            shapes = DEFERRED_SHAPES,
        )
        DeferredBlock(title = "Written elsewhere", shapes = CRON_OWNED_SHAPES)
    }
}

@Composable
private fun DeferredBlock(title: String, shapes: List<DeferredShape>) {
    val c = DmnTheme.colors
    Text(
        title.uppercase(),
        style = DmnType.Label,
        color = c.textDim,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
    )
    for (shape in shapes) {
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(shape.label, style = DmnType.Body, color = c.textMuted)
            Text(shape.reason, style = DmnType.Small, color = c.textDim)
        }
    }
}

@Composable
private fun EditableField(
    field: CardField,
    current: String,
    canEdit: Boolean,
    write: FieldWrite<String>,
    onSave: (String) -> Unit,
    onEdit: () -> Unit,
) {
    val c = DmnTheme.colors
    var open by remember(field.column) { mutableStateOf(false) }
    var draft by remember(field.column, current) { mutableStateOf(current) }

    // A boolean has no meaningful "open the editor" step — the switch IS the
    // edit, and making it a two-tap affair would be worse than the toggle.
    if (field.kind == CardFieldKind.Bool) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(field.label, style = DmnType.Body, color = c.textMuted)
                FieldOutcome(write)
            }
            Switch(
                checked = current == "true",
                enabled = canEdit && write !is FieldWrite.Saving,
                onCheckedChange = { onSave(if (it) "true" else "false") },
            )
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .let { m -> if (canEdit) m.clickable { onEdit(); draft = current; open = !open } else m },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(field.label, style = DmnType.Body, color = c.textMuted)
            Text(
                // "Not set" rather than an empty row: the gap is the thing being
                // offered for editing, so it has to be visible and tappable.
                current.ifBlank { "Not set" },
                style = DmnType.Body,
                color = if (current.isBlank()) c.textDim else c.textPrimary,
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
                                color = if (choice.value == current) c.accentAmber else c.textBody,
                                modifier = Modifier.weight(1f),
                            )
                            if (choice.value == current) {
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
                    TextButton(onClick = { draft = current; open = false }) {
                        Text("Cancel", style = DmnType.Small, color = c.textMuted)
                    }
                    TextButton(
                        onClick = { onSave(draft); open = false },
                        enabled = draft != current,
                    ) {
                        Text("Save", style = DmnType.Small, color = c.accentAmber)
                    }
                }
            }
        }

        field.help?.let {
            if (open) Text(it, style = DmnType.Small, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * What happened to this field's last write.
 *
 * Four outcomes, not two. A refusal arrives wearing HTTP 200 with zero rows and
 * is a different thing from a failure; and a sentence the DATABASE raised is
 * shown verbatim, so the phone and the web refuse in the same words rather than
 * two people keeping two strings in step.
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
    CardFieldKind.Integer, CardFieldKind.Decimal -> "Empty to clear"
    else -> "Empty to clear"
}
