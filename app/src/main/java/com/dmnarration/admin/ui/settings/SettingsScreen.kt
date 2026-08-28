package com.dmnarration.admin.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.domain.FieldWrite
import com.dmnarration.admin.domain.SettingIssue
import com.dmnarration.admin.domain.SettingKeys
import com.dmnarration.admin.domain.SiteSettings
import com.dmnarration.admin.domain.WRITE_REFUSED_MESSAGE
import com.dmnarration.admin.domain.acceptingProjectsLabel
import com.dmnarration.admin.domain.availableMonthsLabel
import com.dmnarration.admin.domain.monthName
import com.dmnarration.admin.ui.board.PullToRefreshSurface
import com.dmnarration.admin.ui.board.ScrollableContent
import com.dmnarration.admin.ui.components.DmnTextField
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.ui.theme.Surface

/**
 * The seven numbers the app does arithmetic with — now editable.
 *
 * Read-only was the schema's decision until Stage 9, not this screen's: there
 * was a `Role read` policy and no update policy at all, so a write returned zero
 * rows rather than an error. The migration added `grant update (value)`, a FOR
 * UPDATE policy, and — the part that matters — a trigger holding the rule, so
 * the phone cannot store a value the web would refuse.
 *
 * NOTHING HERE VALIDATES, deliberately. A client that checked the range itself
 * would be the second copy of a rule that has just been moved into one place.
 * The field sends what was typed and DISPLAYS WHAT THE DATABASE SAID, which is
 * why the phone and the web produce the same sentence without anyone keeping two
 * strings in step.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onRefresh: () -> Unit,
    onSave: (String, String) -> Unit,
    onEdited: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val c = DmnTheme.colors

    Column {
        Row(
            Modifier.padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = c.textMuted,
                    )
                }
            }
            Text(
                "Settings",
                style = DmnType.TitleLg,
                color = c.textPrimary,
                modifier = Modifier.padding(start = if (onBack != null) 0.dp else 12.dp),
            )
        }

        state.error?.let {
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
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val settings = state.settings
                if (settings == null) {
                    item {
                        Text(
                            when {
                                state.loading -> "Loading…"
                                state.error != null -> ""
                                else -> "Settings could not be read. Pull down to try again."
                            },
                            style = DmnType.Body,
                            color = c.textMuted,
                        )
                    }
                    return@list
                }
                body(settings, state, onSave, onEdited)
            },
        )
    }
}

private fun LazyListScope.body(
    s: SiteSettings,
    state: SettingsUiState,
    onSave: (String, String) -> Unit,
    onEdited: (String) -> Unit,
) {
    val editable = state.capabilities.canEdit

    item {
        Group("Availability") {
            BooleanSetting(
                label = "Status",
                summary = acceptingProjectsLabel(s.acceptingProjects, s.acceptingProjectsRaw),
                checked = s.acceptingProjects == true,
                // An unreadable stored value has no "current" state to toggle
                // from, so the switch would be guessing which way to start.
                enabled = editable && s.acceptingProjects != null,
                write = state.writeFor(SettingKeys.ACCEPTING_PROJECTS),
                onToggle = { onSave(SettingKeys.ACCEPTING_PROJECTS, it.toString()) },
            )
            MonthsSetting(
                summary = availableMonthsLabel(s.availableMonths, s.availableMonthsRaw),
                months = s.availableMonths,
                enabled = editable,
                write = state.writeFor(SettingKeys.AVAILABLE_MONTHS),
                onSave = { onSave(SettingKeys.AVAILABLE_MONTHS, it) },
            )
        }
    }

    item {
        Group("Rates") {
            NumberSetting(
                "Words per hour at the mic",
                SettingKeys.WORDS_PER_NARRATION_HOUR,
                s.studio.settings.wordsPerNarrationHour?.toString(),
                s.studio.issueFor(SettingKeys.WORDS_PER_NARRATION_HOUR),
                "Drives every TIME figure — hours at the mic, days needed, capacity.",
                editable, state, onSave, onEdited,
            )
            NumberSetting(
                "Words per finished hour",
                SettingKeys.WORDS_PER_FINISHED_HOUR,
                s.studio.settings.wordsPerFinishedHour?.toString(),
                s.studio.issueFor(SettingKeys.WORDS_PER_FINISHED_HOUR),
                "Drives every MONEY figure — earnings and PFH totals.",
                editable, state, onSave, onEdited,
            )
        }
    }

    item {
        Group("Capacity") {
            NumberSetting(
                "A full day at the mic", SettingKeys.DAILY_CAPACITY_HOURS,
                s.studio.settings.dailyCapacityHours?.toString(),
                s.studio.issueFor(SettingKeys.DAILY_CAPACITY_HOURS),
                null, editable, state, onSave, onEdited,
            )
            NumberSetting(
                "A heavy day starts at", SettingKeys.HEAVY_DAY_HOURS,
                s.studio.settings.heavyDayHours?.toString(),
                s.studio.issueFor(SettingKeys.HEAVY_DAY_HOURS),
                null, editable, state, onSave, onEdited,
            )
            NumberSetting(
                "Books at the mic in one day", SettingKeys.MAX_BOOKS_PER_DAY,
                s.studio.settings.maxBooksPerDay?.toString(),
                s.studio.issueFor(SettingKeys.MAX_BOOKS_PER_DAY),
                null, editable, state, onSave, onEdited,
            )
        }
    }
}

/**
 * A number, editable, with Save appearing only once it has been changed.
 *
 * The box is seeded from the STORED value and reseeded whenever that changes, so
 * a successful save leaves it showing what the database actually holds rather
 * than what was typed. Those differ the moment anything normalises on the way in.
 */
@Composable
private fun NumberSetting(
    label: String,
    key: String,
    stored: String?,
    issue: SettingIssue?,
    note: String?,
    editable: Boolean,
    state: SettingsUiState,
    onSave: (String, String) -> Unit,
    onEdited: (String) -> Unit,
) {
    val c = DmnTheme.colors
    val write = state.writeFor(key)
    var draft by remember(key, stored) { mutableStateOf(stored.orEmpty()) }
    val dirty = draft.trim() != stored.orEmpty().trim()

    Column(Modifier.padding(bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = DmnType.Body,
                color = c.textMuted,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            if (editable) {
                DmnTextField(
                    value = draft,
                    onValueChange = { draft = it; onEdited(key) },
                    modifier = Modifier.width(112.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = write !is FieldWrite.Saving,
                )
            } else {
                Text(
                    stored ?: "Not usable",
                    style = DmnType.Numeric,
                    color = if (stored == null) c.alertRed else c.textPrimary,
                )
            }
        }

        if (editable && dirty) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { draft = stored.orEmpty(); onEdited(key) }) {
                    Text("Cancel", style = DmnType.Small, color = c.textMuted)
                }
                TextButton(
                    onClick = { onSave(key, draft.trim()) },
                    enabled = write !is FieldWrite.Saving,
                ) {
                    Text(
                        if (write is FieldWrite.Saving) "Saving…" else "Save",
                        style = DmnType.BodyMedium,
                        color = c.accentAmber,
                    )
                }
            }
        }

        WriteOutcome(write)
        StoredIssue(issue)
        note?.let {
            Text(it, style = DmnType.Small, color = c.textDim, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun BooleanSetting(
    label: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    write: FieldWrite<String>,
    onToggle: (Boolean) -> Unit,
) {
    val c = DmnTheme.colors
    Column(Modifier.padding(bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = DmnType.Body, color = c.textMuted)
                Text(summary, style = DmnType.Small, color = c.textPrimary)
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                enabled = enabled && write !is FieldWrite.Saving,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.accentAmber,
                    checkedTrackColor = c.accentAmberDim,
                ),
            )
        }
        WriteOutcome(write)
    }
}

/**
 * The booking window, as twelve toggles.
 *
 * Order is CLICK ORDER, appended, exactly as the web's picker stores it. It is
 * not a deliberately-arranged run — that reading was invented and has been
 * corrected in SiteSettings.kt — but it is data the user produced, and sorting
 * it would rewrite what they entered. It would also turn `[11,12,1,2]` into
 * "January, February, November, December": one window rendered as two.
 *
 * Nothing here refuses a gap. The web's picker produces one in two clicks, its
 * formatter collapses any selection to a range without erroring, Android lists
 * the months instead, and the database accepts it. A rule against gaps would
 * break a picker that ships today.
 */
@Composable
private fun MonthsSetting(
    summary: String,
    months: List<Int>?,
    enabled: Boolean,
    write: FieldWrite<String>,
    onSave: (String) -> Unit,
) {
    val c = DmnTheme.colors
    var open by remember { mutableStateOf(false) }
    var draft by remember(months) { mutableStateOf(months.orEmpty()) }
    val dirty = draft != months.orEmpty()

    Column(Modifier.padding(bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Booking window",
                style = DmnType.Body,
                color = c.textMuted,
                modifier = Modifier.weight(1f),
            )
            Text(summary, style = DmnType.Numeric, color = c.textPrimary)
        }
        if (enabled) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { open = !open }) {
                    Text(if (open) "Done" else "Change", style = DmnType.Small, color = c.accentAmber)
                }
            }
        }

        if (open) {
            for (row in (1..12).chunked(3)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    for (m in row) {
                        val on = m in draft
                        Text(
                            monthName(m).take(3),
                            style = DmnType.Small,
                            color = if (on) c.accentAmber else c.textMuted,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (on) c.surfaceRaised else Surface)
                                .clickable { draft = if (on) draft - m else draft + m }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
            if (dirty) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { draft = months.orEmpty() }) {
                        Text("Cancel", style = DmnType.Small, color = c.textMuted)
                    }
                    TextButton(
                        onClick = { onSave(draft.joinToString(",", "[", "]")) },
                        enabled = write !is FieldWrite.Saving,
                    ) {
                        Text(
                            if (write is FieldWrite.Saving) "Saving…" else "Save",
                            style = DmnType.BodyMedium,
                            color = c.accentAmber,
                        )
                    }
                }
            }
        }
        WriteOutcome(write)
    }
}

/**
 * What the last write did, in the words of whoever decided it.
 *
 * A server refusal is shown VERBATIM — that sentence is the database's, and it
 * is the same one the web shows, which is the property this stage exists to
 * create. Anything this app worded itself was a transport failure, not a rule.
 */
@Composable
private fun WriteOutcome(write: FieldWrite<String>) {
    val c = DmnTheme.colors
    when (write) {
        is FieldWrite.Idle, is FieldWrite.Saving -> Unit
        is FieldWrite.Saved -> Text(
            "Saved.",
            style = DmnType.Small,
            color = c.accentAmberDim,
            modifier = Modifier.padding(top = 2.dp),
        )
        is FieldWrite.Refused -> Text(
            WRITE_REFUSED_MESSAGE,
            style = DmnType.Small,
            color = c.alertRed,
            modifier = Modifier.padding(top = 2.dp),
        )
        is FieldWrite.Failed -> Text(
            write.message,
            style = DmnType.Small,
            color = c.alertRed,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * A STORED value that cannot be used — now nearly unreachable, and kept anyway.
 *
 * Stage 7 built this and forced it with direct SQL. Stage 9's trigger fires for
 * every writer, so no NEW value can reach this state: it now covers only values
 * written before the trigger existed, or written with it disabled. Same shape as
 * the card sheet's empty-actions guard — deliberately unreachable, labelled as
 * such, and defence in depth rather than an affordance that can never fire.
 *
 * Stage 7's DoD 5 forcing now needs
 *   alter table site_settings disable trigger site_settings_validate;
 * and a re-enable afterwards.
 */
@Composable
private fun StoredIssue(issue: SettingIssue?) {
    val c = DmnTheme.colors
    issue?.let {
        Text(
            when (it) {
                is SettingIssue.Missing -> "Not set in site_settings."
                is SettingIssue.Unreadable -> "Stored value \"${it.raw}\" is not a number."
                is SettingIssue.OutOfRange ->
                    "Stored value \"${it.raw}\" is outside ${it.allowed} and is not being used."
            },
            style = DmnType.Small,
            color = c.alertRed,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = DmnType.Label,
            color = DmnTheme.colors.textFaint,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}
