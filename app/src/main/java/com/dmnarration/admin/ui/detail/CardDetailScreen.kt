package com.dmnarration.admin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dmnarration.admin.domain.CHAPTER_STATUS_ORDER
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.Chapter
import com.dmnarration.admin.domain.chapterStatusLabel
import com.dmnarration.admin.domain.parseCoNarrators
import com.dmnarration.admin.domain.pageLine
import com.dmnarration.admin.domain.progressFraction
import com.dmnarration.admin.domain.recordedFraction
import com.dmnarration.admin.ui.board.PullToRefreshSurface
import com.dmnarration.admin.ui.board.ScrollableContent
import com.dmnarration.admin.ui.theme.AlertRed
import com.dmnarration.admin.ui.theme.Background
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.ui.theme.Surface
import com.dmnarration.admin.ui.theme.SurfaceBorder
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * One card in full.
 *
 * Every column `card_detail()` returns is rendered here or named in the omission
 * list in the migration. Financial fields go through [Capabilities], never a role
 * comparison.
 */
@Composable
fun CardDetailScreen(
    state: CardDetailUiState,
    capabilities: Capabilities,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSaveField: (String, String) -> Unit,
    onEditField: (String) -> Unit,
) {
    val c = DmnTheme.colors
    val detail = state.detail

    Column(Modifier.background(Background)) {
        Row(
            Modifier.fillMaxWidth().padding(end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.textMuted)
            }
            Text(
                detail?.title ?: "Card",
                style = DmnType.Title,
                color = c.textPrimary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (state.error != null) {
            Text(
                state.error,
                style = DmnType.Body,
                color = c.alertRed,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface)
                    .padding(12.dp),
            )
        }

        PullToRefreshSurface(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            content = ScrollableContent.list(
                contentPadding = 16.dp,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    // A refusal renders nothing but the banner above. The card the
                    // session last read is not shown, because the server has said it
                    // may not be.
                    state.refused -> Unit
                    state.missing -> item {
                        Text("That card no longer exists.", style = DmnType.Body, color = c.textMuted)
                    }
                    detail != null -> detailBody(detail, capabilities, state, onSaveField, onEditField)
                    state.loading -> item {
                        Text("Loading…", style = DmnType.Body, color = c.textFaint)
                    }
                }
            },
        )
    }
}

private fun LazyListScope.detailBody(
    d: CardDetail,
    capabilities: Capabilities,
    state: CardDetailUiState,
    onSaveField: (String, String) -> Unit,
    onEditField: (String) -> Unit,
) {
    item { Header(d, capabilities) }

    // ONE row per field, in the groups the web modal decided. There is no
    // separate Edit section any more: these rows are both the summary and the
    // editor, which is what stops a value having two representations.
    for (group in CardFieldGroup.entries) {
        // Money carries rates and shares, behind the same capability that used
        // to hide the Money summary rather than a second rule about who sees what.
        if (group == CardFieldGroup.Money && !capabilities.canViewFinancials) continue
        val fields = CARD_FIELDS.filter { it.group == group }
        if (fields.isEmpty()) continue
        item {
            Section(group.title) {
                Column {
                    for (field in fields) {
                        CardFieldRow(
                            field = field,
                            detail = d,
                            canEdit = capabilities.canEdit,
                            write = state.writeFor(field.column),
                            onSave = { onSaveField(field.column, it) },
                            onEdit = { onEditField(field.column) },
                        )
                    }
                    // Facts that belong beside these fields and that nobody
                    // edits here. They take no chevron, which is what makes
                    // them legible as "not editable" rather than unresponsive.
                    if (group == CardFieldGroup.Money) {
                        ReadOnlyFieldRow(
                            "Words recorded",
                            d.wordsRecorded?.let { "%,d".format(it) },
                            reason = "Derived from pages, or entered on the web.",
                        )
                    }
                    if (group == CardFieldGroup.Timing) {
                        ReadOnlyFieldRow("Added", d.createdAt?.let(::instantDate))
                        if (d.recordingDates.isNotEmpty()) {
                            ReadOnlyFieldRow(
                                "Recording days",
                                "${d.recordingDates.size}",
                                reason = d.recordingDates.sorted().joinToString(", ") { shortDate(it) },
                            )
                        }
                    }
                }
            }
        }
    }

    d.notes?.let { item { Section("Notes") { Body(it) } } }

    if (d.tags.isNotEmpty()) item { Section("Tags") { Chips(d.tags) } }
    if (d.triggerWarnings.isNotEmpty()) {
        item { Section("Trigger warnings") { Chips(d.triggerWarnings) } }
    }
    if (d.chapters.isNotEmpty()) item { Section("Chapters (${d.chapters.size})") { Chapters(d.chapters) } }

    // `links` is not null on the table and empty on every row today. Rendered
    // only when it holds something, rather than a section for data that does
    // not exist. The four named links are editable fields above.
    if (d.links.isNotEmpty()) {
        item { Section("Other links") { Links(d.links.mapIndexed { i, l -> "Link ${i + 1}" to l }) } }
    }

    item {
        Section("Not editable on the phone yet") {
            Column {
                for (shape in DEFERRED_SHAPES) ReadOnlyFieldRow(shape.label, null, shape.reason)
            }
        }
    }
    item {
        Section("Written elsewhere") {
            Column {
                ReadOnlyFieldRow(
                    "Amazon rating",
                    d.amazonRating?.let { "$it ★" + (d.amazonReviewCount?.let { n -> " ($n)" } ?: "") },
                    reason = "Written by the nightly job.",
                )
            }
        }
    }
}

@Composable
private fun Header(d: CardDetail, capabilities: Capabilities) {
    val c = DmnTheme.colors
    Row {
        Box(
            Modifier
                .width(96.dp)
                .height(144.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Surface),
        ) {
            // Confidential covers are withheld from a session that may not see them,
            // exactly as on the board.
            if (d.coverUrl != null && (!d.isConfidential || capabilities.canViewConfidentialCovers)) {
                AsyncImage(model = d.coverUrl, contentDescription = null, modifier = Modifier.fillMaxWidth())
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            d.subtitle?.let { Text(it, style = DmnType.Small, color = c.textMuted) }
            Text(d.author, style = DmnType.BodyMedium, color = c.accentAmber)
            parseCoNarrators(d.coNarrator).takeIf { it.isNotEmpty() }?.let {
                Text("with ${it.joinToString(", ")}", style = DmnType.Small, color = c.textMuted)
            }
            Text(
                d.status.replaceFirstChar { it.uppercase() },
                style = DmnType.Pill,
                color = c.textBody,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            if (d.isConfidential) {
                Text("Confidential", style = DmnType.Small, color = c.accentAmberDim, modifier = Modifier.padding(top = 6.dp))
            }
            Progress(d)
        }
    }
}

@Composable
private fun Progress(d: CardDetail) {
    val fraction = d.progressFraction() ?: return
    val c = DmnTheme.colors
    Column(Modifier.padding(top = 10.dp)) {
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(SurfaceBorder),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.toFloat())
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.accentAmber),
            )
        }
        // Percentage always; the page line additionally when the book has one.
        Text(
            buildString {
                append("${(fraction * 100).roundToInt()}% recorded")
                d.pageLine()?.let { append(" · ").append(it) }
            },
            style = DmnType.Small,
            color = c.textDim,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = DmnType.Label,
            color = DmnTheme.colors.textFaint,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = DmnType.Body, color = DmnTheme.colors.textBody)
}

@Composable
private fun Chips(values: List<String>) {
    val c = DmnTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (v in values) {
            Text(
                v,
                style = DmnType.Pill,
                color = c.pillNeutralText,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.pillNeutralBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Chapters, summarised by status rather than listed one by one.
 *
 * A chapter with a null `number` renders as "—". Every book with chapters has two or
 * three of them, so this is the ordinary case, not a defensive flourish. Statuses not
 * in the known order keep their own name and sort last, so a value the web adds later
 * appears rather than vanishing into a default.
 */
@Composable
private fun Chapters(chapters: List<Chapter>) {
    val c = DmnTheme.colors
    val byStatus = chapters.groupBy { it.status }
    val ordered = byStatus.keys.sortedWith(
        compareBy(
            { CHAPTER_STATUS_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } },
            { it ?: "" },
        ),
    )
    Column {
        for (status in ordered) {
            val group = byStatus.getValue(status)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(chapterStatusLabel(status), style = DmnType.Body, color = c.textMuted)
                Text("${group.size}", style = DmnType.Body, color = c.textPrimary)
            }
        }
        val unnumbered = chapters.count { it.number == null }
        if (unnumbered > 0) {
            Text(
                "$unnumbered without a chapter number",
                style = DmnType.Small,
                color = c.textDim,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Links(links: List<Pair<String, String>>) {
    val c = DmnTheme.colors
    Column {
        for ((label, url) in links) {
            Text(
                label,
                style = DmnType.Body,
                color = c.accentAmber,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { }
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/**
 * The web's own labels, not a title-cased enum value.
 *
 * `replaceFirstChar { uppercase() }` rendered "Pfh" one line above "PFH rate" — the
 * same acronym in two casings, adjacent. An unknown value falls through as itself
 * rather than being prettified into something that looks official.
 */
private fun paymentLabel(raw: String?): String? = when (raw) {
    null -> null
    "pfh" -> "PFH (Per Finished Hour)"
    "rs" -> "Royalty Share (RS)"
    "rs_plus" -> "Royalty Share Plus (RS+)"
    else -> raw
}

// MONTH_ABBR and longDate live in CardFields, which needs them to format the
// value a row DISPLAYS. One copy, so a date cannot read one way in a row and
// another in a summary — the divergence this screen was just rebuilt to remove.
private fun shortDate(d: LocalDate) = "${MONTH_ABBR[d.month.number - 1]} ${d.day}"
private fun instantDate(i: Instant) = longDate(i.toLocalDateTime(TimeZone.currentSystemDefault()).date)
