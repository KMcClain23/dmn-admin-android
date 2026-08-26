package com.dmnarration.admin.ui.board

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.dmnarration.admin.domain.SwipeToArchive
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.NarrationInput
import com.dmnarration.admin.domain.RecordingSchedule
import com.dmnarration.admin.domain.StudioSettings
import com.dmnarration.admin.domain.Urgency
import com.dmnarration.admin.domain.completionUrgency
import com.dmnarration.admin.domain.daysUntil
import com.dmnarration.admin.domain.estimatedEarnings
import com.dmnarration.admin.domain.first15Urgency
import com.dmnarration.admin.domain.narrationPlan
import com.dmnarration.admin.domain.parseCoNarrators
import com.dmnarration.admin.domain.stillAtMic
import com.dmnarration.admin.ui.theme.Background
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.ui.theme.Surface
import com.dmnarration.admin.ui.theme.SurfaceBorder
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.math.roundToLong

/** Fixed, matching the web. Every blank row below exists to hold this height. */
val CARD_HEIGHT = 176.dp

private val MONTH_ABBR = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "Aug 28" — formatted field by field, never through an Instant. */
private fun shortDate(d: LocalDate): String = "${MONTH_ABBR[d.month.number - 1]} ${d.day}"

private fun thousands(n: Long): String = "%,d".format(n)

@Composable
private fun urgencyColor(u: Urgency): Color = when (u) {
    Urgency.RED -> DmnTheme.colors.alertRed
    Urgency.YELLOW -> DmnTheme.colors.accentAmberBright
    Urgency.DEFAULT -> DmnTheme.colors.textBody
}

/**
 * The board card, row for row as `BoardCardContent.tsx` draws it.
 *
 * Several rows render a single space rather than being omitted when they have
 * nothing to say. That is not sloppiness — it is what keeps every card in a
 * section exactly the same height, and dropping any of them makes the column
 * ragged. It applies to the capability-gated content too: hiding earnings must
 * not change a card's height, or an editor's board would be laid out
 * differently from an admin's for no reason a viewer could see.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoardCardItem(
    card: BoardCard,
    capabilities: Capabilities,
    settings: StudioSettings,
    today: LocalDate,
    onClick: () -> Unit,
    onToggleFirst15: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onSwipeArchive: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = DmnTheme.colors
    val coNarrators = parseCoNarrators(card.coNarrator)
    val format = card.narrationFormat?.takeIf { it != "solo" }

    // Gestures only exist for a session that may write. Without this an editor
    // could long-press into a menu of things the server would refuse, or swipe a
    // card away and watch it come back.
    val gesturesEnabled = capabilities.canEdit
    val density = LocalDensity.current
    var offsetDp by remember(card.id) { mutableFloatStateOf(0f) }

    Box(modifier.fillMaxWidth().height(CARD_HEIGHT)) {
        if (offsetDp < 0f) ArchiveAffordance()

        Box(
            Modifier
                .offset { IntOffset(with(density) { offsetDp.dp.roundToPx() }, 0) }
                .fillMaxWidth()
                .height(CARD_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                .then(
                    if (gesturesEnabled) {
                        Modifier.pointerInput(card.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    // Velocity is not available from this
                                    // detector, so displacement is the only
                                    // route here; the flick is covered by the
                                    // threshold once the drag has travelled.
                                    if (SwipeToArchive.shouldArchive(offsetDp, 0f)) {
                                        offsetDp = SwipeToArchive.MAX_SWIPE_DP
                                        onSwipeArchive()
                                    } else {
                                        offsetDp = 0f
                                    }
                                },
                                onDragCancel = { offsetDp = 0f },
                            ) { change, drag ->
                                change.consume()
                                offsetDp = SwipeToArchive.clampOffset(
                                    offsetDp + with(density) { drag.toDp().value },
                                )
                            }
                        }
                    } else Modifier,
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (gesturesEnabled) onLongPress else null,
                )
                .padding(12.dp)
        ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(96.dp)
                    .height(144.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Background)
            ) {
                if (card.coverUrl != null) {
                    AsyncImage(
                        model = card.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                // 1. Title, plus the format pill. End padding when confidential
                // so the corner lock never lands on top of the pill.
                Row(
                    Modifier.padding(end = if (card.isConfidential) 18.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        card.title,
                        style = DmnType.Title,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (format != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(c.pillNeutralBg)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                format.replaceFirstChar { it.uppercase() },
                                style = DmnType.Pill,
                                color = c.pillNeutralText,
                            )
                        }
                    }
                }

                // 2. Author
                Spacer(Modifier.height(4.dp))
                Text(
                    card.author.ifBlank { " " },
                    style = DmnType.BodyMedium,
                    color = c.accentAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // 3. Co-narrators — blank but height-preserving when solo
                Spacer(Modifier.height(2.dp))
                Text(
                    if (coNarrators.isEmpty()) " " else "with ${coNarrators.joinToString(", ")}",
                    style = DmnType.Small,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // 4. Dates
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.defaultMinSize(minHeight = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    card.deadline?.let { deadline ->
                        val tint = urgencyColor(completionUrgency(daysUntil(deadline, today)))
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(tint.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.CalendarToday, null, tint = tint, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(shortDate(deadline), style = DmnType.Numeric, color = tint)
                        }
                    }

                    card.first15Due?.let { due ->
                        // Interactive only when this session may write. The row
                        // renders identically either way — a read-only viewer
                        // sees the same card, just without a target.
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .then(
                                    if (capabilities.canEdit) {
                                        Modifier.clickable(onClick = onToggleFirst15)
                                    } else Modifier
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (card.first15Complete) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                null,
                                tint = c.textMuted,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("15:", style = DmnType.Pill, color = c.textMuted)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                shortDate(due),
                                style = DmnType.Numeric,
                                color = if (card.first15Complete) c.textMuted
                                else urgencyColor(first15Urgency(daysUntil(due, today))),
                                textDecoration = if (card.first15Complete) TextDecoration.LineThrough else null,
                            )
                        }
                    }
                }

                // 5. Word count, with earnings appended only when there are any
                // AND this session may see them. Words are production
                // information, so the count itself is never gated.
                Spacer(Modifier.height(8.dp))
                Text(wordsLine(card, capabilities, settings), style = DmnType.Body, color = c.textDim)

                // 6. Booth load — production progress, not money, so it stays
                // visible without canViewFinancials.
                Spacer(Modifier.height(2.dp))
                BoothLoadRow(card, settings, today)
            }
        }

        if (card.isConfidential) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Confidential",
                tint = c.accentAmberDim,
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.TopEnd),
            )
        }
    }
    }
}

/**
 * "112,880 words" or "112,880 words · ~$1,441".
 *
 * The suffix appears only when `estimatedEarnings` returns something. A
 * multicast book, or one on a payment type that is not per-finished-hour, has
 * no computable figure — and "· ~$0" would be a plausible-looking lie that
 * reads as a data problem and gets investigated in entirely the wrong place.
 */
private fun wordsLine(card: BoardCard, capabilities: Capabilities, settings: StudioSettings): String {
    val words = card.wordCount ?: return " "
    val line = "${thousands(words.toLong())} words"
    if (!capabilities.canViewFinancials) return line
    val earnings = estimatedEarnings(
        wordCount = card.wordCount,
        pfhRate = card.pfhRate,
        paymentType = card.paymentType,
        narrationFormat = card.narrationFormat,
        narratorSharePercent = card.narratorSharePercent,
        wordsPerFinishedHour = settings.wordsPerFinishedHour,
    ) ?: return line
    return "$line · ~$${thousands(earnings.roundToLong())}"
}

@Composable
private fun BoothLoadRow(card: BoardCard, settings: StudioSettings, today: LocalDate) {
    val c = DmnTheme.colors

    // Nothing to say once the mic work is done — but the row keeps its height.
    if (!stillAtMic(card.status)) {
        Text(" ", style = DmnType.Small, color = c.textMuted)
        return
    }

    val plan = narrationPlan(
        NarrationInput(
            wordCount = card.wordCount,
            narrationFormat = card.narrationFormat,
            narratorSharePercent = card.narratorSharePercent,
            deadline = card.deadline,
            wordsPerNarrationHour = settings.wordsPerNarrationHour,
            wordsRecorded = card.wordsRecorded ?: 0,
            schedule = RecordingSchedule(dates = card.recordingDates),
            today = today,
        )
    )
    if (plan == null) {
        Text(" ", style = DmnType.Small, color = c.textMuted)
        return
    }

    if (plan.hours <= 0.005) {
        Text("Recording complete", style = DmnType.Small, color = c.capacityLight)
        return
    }

    val started = plan.fractionDone > 0.005
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "%.1f hrs %s".format(plan.hours, if (started) "left" else "at the mic"),
            style = DmnType.Small,
            color = c.textMuted,
        )
        // Only once there is progress to report: an untouched book saying
        // "0% done" is noise on every card in the column.
        if (started) {
            Text(
                " · ${(plan.fractionDone * 100).roundToLong()}% done",
                style = DmnType.Small,
                color = c.textDim,
            )
        }
        when {
            plan.overdue -> Text(" · no recording days left", style = DmnType.Small, color = c.alertRed)
            plan.hoursPerDay != null -> Text(
                " · %.1f hrs/day".format(plan.hoursPerDay),
                style = DmnType.Small,
                color = if (plan.hoursPerDay >= settings.heavyDayHours) c.accentAmberBright else c.textMuted,
            )
        }
    }
}
