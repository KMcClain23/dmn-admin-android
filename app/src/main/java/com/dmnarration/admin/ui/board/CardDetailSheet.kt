package com.dmnarration.admin.ui.board

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.NarrationInput
import com.dmnarration.admin.domain.RecordingSchedule
import com.dmnarration.admin.domain.StudioSettings
import com.dmnarration.admin.domain.estimatedEarnings
import com.dmnarration.admin.domain.narrationPlan
import com.dmnarration.admin.domain.parseCoNarrators
import com.dmnarration.admin.ui.theme.Background
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import com.dmnarration.admin.ui.theme.Surface
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.math.roundToLong

/**
 * Read-only detail. Nothing here writes, and nothing here decides for itself
 * what counts as financial — it asks the same Capabilities the card face does,
 * so there is exactly one definition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailSheet(
    card: BoardCard,
    capabilities: Capabilities,
    settings: StudioSettings,
    today: LocalDate,
    onDismiss: () -> Unit,
) {
    val c = DmnTheme.colors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val coNarrators = parseCoNarrators(card.coNarrator)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row {
                Box(
                    Modifier
                        .width(120.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Background)
                ) {
                    if (card.coverUrl != null) {
                        AsyncImage(card.coverUrl, null, Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.title, style = DmnType.TitleLg, color = c.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(card.author, style = DmnType.BodyMedium, color = c.accentAmber)
                    if (coNarrators.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("with ${coNarrators.joinToString(", ")}", style = DmnType.Small, color = c.textMuted)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        card.status.replaceFirstChar { it.uppercase() },
                        style = DmnType.Pill,
                        color = c.textBody,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Dates")
            DetailRow("Deadline", card.deadline?.let(::detailDate) ?: "—")
            DetailRow("First 15 due", card.first15Due?.let(::detailDate) ?: "—")
            DetailRow("First 15", if (card.first15Complete) "Complete" else "Outstanding")

            Spacer(Modifier.height(20.dp))
            SectionLabel("Production")
            DetailRow("Format", card.narrationFormat?.replaceFirstChar { it.uppercase() } ?: "Solo")
            DetailRow("Word count", card.wordCount?.let { "%,d".format(it) } ?: "—")
            card.narratorSharePercent?.let { DetailRow("Narrator share", "$it%") }

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
            if (plan != null) {
                DetailRow("Total hours", "%.1f".format(plan.totalHours))
                DetailRow("Hours remaining", "%.1f".format(plan.hours))
                DetailRow("Percent done", "${(plan.fractionDone * 100).roundToLong()}%")
                DetailRow("Recording days left", plan.daysLeft?.toString() ?: "—")
                DetailRow("Hours per day", plan.hoursPerDay?.let { "%.1f".format(it) } ?: "—")
            } else {
                // A multicast book with no explicit share has no knowable split,
                // so there is no plan to show — not a plan full of zeroes.
                DetailRow("Narration plan", "Not computable for this card")
            }

            if (capabilities.canViewFinancials) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("Earnings")
                DetailRow("Payment type", card.paymentType ?: "—")
                DetailRow("Rate", card.pfhRate?.let { "$%,.2f".format(it) } ?: "—")
                val earnings = estimatedEarnings(
                    wordCount = card.wordCount,
                    pfhRate = card.pfhRate,
                    paymentType = card.paymentType,
                    narrationFormat = card.narrationFormat,
                    narratorSharePercent = card.narratorSharePercent,
                    wordsPerFinishedHour = settings.wordsPerFinishedHour,
                )
                DetailRow("Estimated", earnings?.let { "~$${"%,d".format(it.roundToLong())}" } ?: "—")
            }

            // Stage 1 writes nothing, so the escape hatch is the web. Gated on
            // being able to use that web admin at all, which is its own
            // question — not borrowed from whether earnings are visible.
            if (capabilities.canUseWebAdmin) {
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = {
                        val url = "https://dmnarration.com/board?editCard=${card.id}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Edit on web", style = DmnType.BodyMedium, color = c.accentAmber)
                }
            }
        }
    }
}

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private fun detailDate(d: LocalDate) = "${MONTHS[d.month.number - 1]} ${d.day}, ${d.year}"

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = DmnType.Label,
        color = DmnTheme.colors.textFaint,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = DmnType.Small, color = DmnTheme.colors.textMuted)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            style = DmnType.Numeric,
            color = DmnTheme.colors.textBody,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
