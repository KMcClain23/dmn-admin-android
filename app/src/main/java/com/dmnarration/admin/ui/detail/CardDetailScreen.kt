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
                    detail != null -> detailBody(detail, capabilities)
                    state.loading -> item {
                        Text("Loading…", style = DmnType.Body, color = c.textFaint)
                    }
                }
            },
        )
    }
}

private fun LazyListScope.detailBody(d: CardDetail, capabilities: Capabilities) {
    item { Header(d, capabilities) }

    d.description?.let { item { Section("Description") { Body(it) } } }
    d.notes?.let { item { Section("Notes") { Body(it) } } }

    item { Section("Dates") { Dates(d) } }
    item { Section("Production") { Production(d, capabilities) } }

    if (capabilities.canViewFinancials) item { Section("Money") { Money(d) } }

    if (d.tags.isNotEmpty()) item { Section("Tags") { Chips(d.tags) } }
    if (d.triggerWarnings.isNotEmpty()) {
        item { Section("Trigger warnings") { Chips(d.triggerWarnings) } }
    }
    if (d.chapters.isNotEmpty()) item { Section("Chapters (${d.chapters.size})") { Chapters(d.chapters) } }

    val links = buildList {
        d.audibleLink?.let { add("Audible" to it) }
        d.arLink?.let { add("AR" to it) }
        d.spotifyLink?.let { add("Spotify" to it) }
        d.scriptUrl?.let { add("Script" to it) }
        // `links` is not null on the table and empty on every row today. Rendered
        // only when it holds something, rather than a section for data that does
        // not exist.
        d.links.forEachIndexed { i, l -> add("Link ${i + 1}" to l) }
    }
    if (links.isNotEmpty()) item { Section("Links") { Links(links) } }
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
    val fraction = d.recordedFraction() ?: return
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
        Text(
            "${(fraction * 100).roundToInt()}% recorded",
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
private fun Field(label: String, value: String?, struck: Boolean = false) {
    if (value.isNullOrBlank()) return
    val c = DmnTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = DmnType.Body, color = c.textMuted)
        Text(
            value,
            style = DmnType.Body,
            color = c.textPrimary,
            textDecoration = if (struck) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun Dates(d: CardDetail) {
    Column {
        Field("Deadline", d.deadline?.let(::longDate))
        Field("First 15 due", d.first15Due?.let(::longDate), struck = d.first15Complete)
        Field("First 15", if (d.first15Complete) "Complete" else "Outstanding")
        // "Released" beside a status chip reading Editing asserts the book is out
        // when it is not. The date is a fact — it was released and came back — so the
        // label carries the tense rather than the row being hidden.
        Field(
            if (d.status == "released") "Released" else "Previously released",
            d.releasedAt?.let(::instantDate),
        )
        Field("Added", d.createdAt?.let(::instantDate))
        if (d.recordingDates.isNotEmpty()) {
            Field("Recording days", "${d.recordingDates.size}")
            Text(
                d.recordingDates.sorted().joinToString(", ") { shortDate(it) },
                style = DmnType.Small,
                color = DmnTheme.colors.textDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun Production(d: CardDetail, capabilities: Capabilities) {
    Column {
        Field("Format", d.narrationFormat?.replaceFirstChar { it.uppercase() })
        Field("Type", d.productionType?.replaceFirstChar { it.uppercase() })
        Field("Company", d.productionCompany)
        Field("Word count", d.wordCount?.let { "%,d".format(it) })
        Field("Words recorded", d.wordsRecorded?.let { "%,d".format(it) })
        if (d.amazonRating != null) {
            Field("Amazon", "${d.amazonRating} ★" + (d.amazonReviewCount?.let { " ($it)" } ?: ""))
        }
    }
}

@Composable
private fun Money(d: CardDetail) {
    Column {
        Field("Payment", paymentLabel(d.paymentType))
        Field("PFH rate", d.pfhRate?.let { "$%.2f".format(it) })
        Field("Narrator share", d.narratorSharePercent?.let { "$it%" })
        Field("Royalty split", d.royaltySplitPercent?.let { "$it%" })
    }
}

/**
 * Chips that wrap by WIDTH, not by count.
 *
 * This was `values.chunked(3)` inside a `Column` of `Row`s — three chips per line
 * whatever their length. A Row does not wrap, so when the three did not fit, the
 * first child took the full width and the later ones were measured against nothing
 * left: their text wrapped to roughly one character per line, producing a tall,
 * nearly-invisible column that read as a third of a screen of blank space, with the
 * following row of chips stranded beneath it looking like an orphaned section.
 *
 * `How an Angel Dies: Wrath` is the worst board-reachable case — nine warnings, the
 * longest 49 characters, and its middle chunk was 122 characters asked to share one
 * line.
 *
 * FlowRow is the component that actually expresses the intent, and it removes the
 * fixed count entirely rather than tuning it.
 */
@OptIn(ExperimentalLayoutApi::class)
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

private val MONTH_ABBR = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private fun shortDate(d: LocalDate) = "${MONTH_ABBR[d.month.number - 1]} ${d.day}"
private fun longDate(d: LocalDate) = "${MONTH_ABBR[d.month.number - 1]} ${d.day}, ${d.year}"
private fun instantDate(i: Instant) = longDate(i.toLocalDateTime(TimeZone.currentSystemDefault()).date)
