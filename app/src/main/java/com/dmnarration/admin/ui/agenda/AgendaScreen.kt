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
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.DUE_SOON_DAYS
import com.dmnarration.admin.domain.daysUntil
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
    onRefresh: () -> Unit,
    onOpenCard: (BoardCard) -> Unit,
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
                if (agenda.isEmpty) {
                    // A message rather than blank(): an agenda with nothing in it is
                    // a real answer — "nothing is asked of you today" — and rendering
                    // literally nothing would read as a screen that failed to load.
                    // Still a lazy list, so it still scrolls and still pulls.
                    item { NothingToday() }
                    return@list
                }

                group("Late", agenda.late.size) {
                    items(agenda.late) { LateRow(it, agenda.today, onOpenCard) }
                }
                group("Recording today", agenda.recordingToday.size) {
                    items(agenda.recordingToday) { RecordingRow(it, onOpenCard) }
                }
                group("Due within $DUE_SOON_DAYS days", agenda.dueSoon.size) {
                    items(agenda.dueSoon) { DueRow(it, agenda.today, onOpenCard) }
                }
            },
        )
    }
}

/** A heading plus its rows, emitted only when the group has something in it. */
private fun LazyListScope.group(label: String, count: Int, rows: LazyListScope.() -> Unit) {
    if (count == 0) return
    item(key = "h_$label") {
        Text(
            label.uppercase(),
            style = DmnType.Label,
            color = DmnTheme.colors.textFaint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    rows()
}

private fun <T> LazyListScope.items(list: List<T>, row: @Composable (T) -> Unit) {
    items(count = list.size) { row(list[it]) }
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
    val fraction = recordedFraction(card) ?: return
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
        Text(
            "${(fraction * 100).roundToInt()}% recorded",
            style = DmnType.Small,
            color = c.textDim,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LateRow(card: BoardCard, today: LocalDate, onOpenCard: (BoardCard) -> Unit) {
    AgendaCard(card, onOpenCard) {
        val days = card.deadline?.let { daysBetween(it, today) } ?: 0
        Text(
            if (days == 1) "1 day late" else "$days days late",
            style = DmnType.Small,
            color = AlertRed,
        )
    }
}

@Composable
private fun RecordingRow(item: AgendaItem, onOpenCard: (BoardCard) -> Unit) {
    AgendaCard(item.card, onOpenCard) {
        Text(
            item.hours?.let { "%.1f hrs".format(it) } ?: "scheduled",
            style = DmnType.Small,
            color = DmnTheme.colors.accentAmberBright,
        )
    }
}

@Composable
private fun DueRow(card: BoardCard, today: LocalDate, onOpenCard: (BoardCard) -> Unit) {
    AgendaCard(card, onOpenCard) {
        val days = card.deadline?.let { daysBetween(today, it) } ?: 0
        Text(
            when (days) {
                0 -> "due today"
                1 -> "due tomorrow"
                else -> "in $days days"
            },
            style = DmnType.Small,
            color = DmnTheme.colors.textMuted,
        )
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
