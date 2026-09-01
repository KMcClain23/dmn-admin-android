package com.dmnarration.admin.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.domain.Agenda
import com.dmnarration.admin.domain.AgendaItem
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.dmnarration.admin.domain.AgendaTier
import com.dmnarration.admin.domain.dateFor
import com.dmnarration.admin.domain.AgendaReason
import com.dmnarration.admin.domain.relativeDeadline
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontFamily
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.NeedsMe
import com.dmnarration.admin.domain.DUE_SOON_DAYS
import com.dmnarration.admin.domain.daysUntil
import com.dmnarration.admin.domain.pageLine
import com.dmnarration.admin.domain.progressFraction
import com.dmnarration.admin.domain.recordedFraction
import com.dmnarration.admin.ui.board.PullToRefreshSurface
import com.dmnarration.admin.ui.board.ScrollableContent
import com.dmnarration.admin.ui.theme.AlertRed
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.ui.theme.Surface
import com.dmnarration.admin.ui.theme.SurfaceBorder
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.math.roundToInt

/**
 * Today, ported from `api/agenda/route.ts`.
 *
 * Three groups in priority order: what has already slipped, what is being recorded
 * today, and what falls due inside the next week. The route has the last two; the
 * first is the one addition, and it exists because the route's `dueSoon` filters
 * `deadline >= today`, so a slipped book silently leaves the agenda altogether.
 */
@Composable
fun AgendaScreen(
    agenda: Agenda,
    refreshing: Boolean,
    error: String?,
    /**
     * Pickups waiting on the signed-in person, from `pickups_needing_me()`.
     *
     * NOT MERGED INTO THE AGENDA, and that is the whole design of it. Everything
     * else on this screen is scheduled work with a date; a pickup has only a
     * sent_at, so it cannot be "due today" and must not be counted into a
     * due-today total. Adding it there would make "Nothing due today" false with
     * nothing on the screen able to explain why.
     *
     * So it renders as its own block, and "Nothing due today" and "3 pickups to
     * re-record" are both allowed to be true at once — which today they are.
     */
    needsMe: List<NeedsMe>,
    onRefresh: () -> Unit,
    onOpenCard: (BoardCard) -> Unit,
    /** Tapping a pickup goes to the Editing tab, where the actions live. */
    onOpenPickup: (String) -> Unit,
) {
    val c = DmnTheme.colors

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Today", style = DmnType.TitleLg, color = c.textPrimary)
            Text(
                formatDay(agenda.today),
                style = DmnType.Small,
                color = c.textMuted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        HoursSummary(agenda)

        if (error != null) {
            Text(
                error,
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
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            content = ScrollableContent.list(
                contentPadding = 16.dp,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                /*
                  PINNED ABOVE THE DAY, and rendered whether or not the day has
                  anything in it — including when it does not, which is the case
                  this exists for.

                  NO WRITE ACTIONS. Re-recorded, Resolve, Dismiss and Remove all
                  live on the Editing tab; a second screen that writes pickups is
                  how one rule becomes two implementations. Tapping goes there.
                */
                if (needsMe.isNotEmpty()) {
                    item(key = "pickups_h") { GroupHeading("To re-record") }
                    items(count = needsMe.size, key = { needsMe[it].id }) {
                        PickupRow(needsMe[it], onOpenPickup)
                    }
                }

                if (agenda.isEmpty) {
                    if (needsMe.isNotEmpty()) return@list
                    // A message rather than blank(): an agenda with nothing in it is
                    // a real answer — "nothing is asked of you today" — and rendering
                    // literally nothing would read as a screen that failed to load.
                    // Still a lazy list, so it still scrolls and still pulls.
                    item { NothingToday() }
                    return@list
                }

                for (tier in AgendaTier.entries) {
                    val group = agenda.grouped(tier)
                    if (group.isEmpty()) continue
                    item(key = "h_$tier") { GroupHeading(heading(tier)) }
                    items(count = group.size) { AgendaRow(group[it], agenda.today, onOpenCard) }
                }
            },
        )
    }
}

@Composable
private fun GroupHeading(label: String) {
    Text(
        label.uppercase(),
        style = DmnType.Label,
        color = DmnTheme.colors.textFaint,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun HoursSummary(agenda: Agenda) {
    val c = DmnTheme.colors
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Stat("This week", agenda.weekHours)
        Stat("This month", agenda.monthHours)
    }
}

@Composable
private fun Stat(label: String, hours: Double) {
    val c = DmnTheme.colors
    Column {
        Text(label.uppercase(), style = DmnType.Label, color = c.textFaint)
        Text(
            if (hours <= 0.0) "—" else "%.1f hrs".format(hours),
            style = DmnType.Numeric,
            color = c.textPrimary,
        )
    }
}

/**
 * One line to re-record.
 *
 * THE CORRECTION IS THE LARGEST THING AND IS NEVER TRUNCATED — no maxLines, no
 * ellipsis. A line you have to open a card to read has not been shown to you,
 * and reading it is the entire reason the row is here. The timestamp is
 * monospaced so a column of them scans as times.
 */
@Composable
private fun PickupRow(p: NeedsMe, onOpen: (String) -> Unit) {
    val c = DmnTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable { onOpen(p.cardId) }
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
private fun NothingToday() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing due today", style = DmnType.Body, color = DmnTheme.colors.textMuted)
        Text(
            "No recording scheduled, nothing late, nothing due this week.",
            style = DmnType.Small,
            color = DmnTheme.colors.textFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
        )
    }
}

@Composable
private fun AgendaCard(
    card: BoardCard,
    onOpenCard: (BoardCard) -> Unit,
    trailing: @Composable () -> Unit,
    body: @Composable () -> Unit = {},
) {
    val c = DmnTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .clickable { onOpenCard(card) }
            .padding(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.padding(end = 12.dp)) {
                Text(card.title, style = DmnType.BodyMedium, color = c.textPrimary)
                Text(card.author, style = DmnType.Small, color = c.accentAmber)
            }
            trailing()
        }
        ProgressLine(card)
        body()
    }
}

/**
 * Recorded progress, or nothing at all.
 *
 * `recordedFraction` returns null when the word count is missing or zero, and this
 * renders nothing in that case rather than a confident 0% for a book whose size
 * nobody has entered.
 */
@Composable
private fun ProgressLine(card: BoardCard) {
    // Pages where there are pages, words otherwise — see progressFraction. A
    // book with neither shows no bar rather than 0%, which would assert that
    // nothing has been recorded rather than that nobody knows.
    val fraction = progressFraction(card) ?: return
    val c = DmnTheme.colors
    Column(Modifier.padding(top = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SurfaceBorder),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.toFloat())
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.accentAmber),
            )
        }
        // The percentage always. The page line ADDITIONALLY, when the book has
        // pages — context on top rather than a rival measure, and no empty row
        // for the books that have none.
        Text(
            buildString {
                append("${(fraction * 100).roundToInt()}% recorded")
                pageLine(card)?.let { append(" · ").append(it) }
            },
            style = DmnType.Small,
            color = c.textDim,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * One book, once.
 *
 * The card sits under its highest tier and carries its other reasons as chips. Every
 * relative date — the trailing figure and the chips alike — goes through
 * `relativeDeadline`, because two formatters would disagree on a boundary day and
 * only one of them would be right.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgendaRow(item: AgendaItem, today: LocalDate, onOpenCard: (BoardCard) -> Unit) {
    val c = DmnTheme.colors
    AgendaCard(item.card, onOpenCard, trailing = {
        Text(
            reasonLabel(item.primary, item, today),
            style = DmnType.Small,
            color = when (item.primary.tier) {
                AgendaTier.LATE -> AlertRed
                AgendaTier.TODAY -> c.accentAmberBright
                AgendaTier.UPCOMING -> c.textMuted
            },
        )
    }) {
        if (item.secondary.isEmpty()) return@AgendaCard
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (reason in item.secondary) {
                Text(
                    reasonLabel(reason, item, today),
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
}

private fun heading(tier: AgendaTier): String = when (tier) {
    AgendaTier.LATE -> "Late"
    AgendaTier.TODAY -> "Recording today"
    AgendaTier.UPCOMING -> "Due within $DUE_SOON_DAYS days"
}

/**
 * One label per reason, used for the trailing figure AND the chips.
 *
 * The first-15 labels name themselves because a bare "3 days late" beside a delivery
 * deadline would not say which commitment slipped.
 */
private fun reasonLabel(reason: AgendaReason, item: AgendaItem, today: LocalDate): String {
    val date = reason.dateFor(item.card, today)
    return when (reason) {
        AgendaReason.LATE -> date?.let { relativeDeadline(it, today) } ?: "late"
        AgendaReason.DUE_SOON -> date?.let { relativeDeadline(it, today) } ?: "due"
        AgendaReason.RECORDING_TODAY ->
            item.hours?.let { "%.1f hrs".format(it) } ?: "recording today"
        AgendaReason.FIRST15_OVERDUE ->
            "first 15 " + (date?.let { relativeDeadline(it, today) } ?: "late")
        AgendaReason.FIRST15_DUE_SOON ->
            "first 15 " + (date?.let { relativeDeadline(it, today) } ?: "due")
    }
}

private val MONTH_ABBR = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "Wed 26 Aug" — field by field, never through an Instant. */
private fun formatDay(d: LocalDate): String {
    val dow = d.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$dow ${d.day} ${MONTH_ABBR[d.month.number - 1]}"
}

/** Whole days from [from] to [to]; negative if [to] is behind. */
private fun daysBetween(from: LocalDate, to: LocalDate): Int = daysUntil(to, from)
