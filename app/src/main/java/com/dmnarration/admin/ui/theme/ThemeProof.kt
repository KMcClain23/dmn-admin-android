package com.dmnarration.admin.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A static rendering of the design system on card-shaped surfaces.
 *
 * This exists for the 1.3 review checkpoint and proves exactly one thing: that
 * the ported colours, the bundled Manrope and the row rhythm look like the web
 * admin. Every value below is hard-coded — there is no domain logic here, and
 * there deliberately is none, because none of it has been ported yet (1.7).
 * The real card composable replaces this in 1.6.
 */

private data class SampleCard(
    val title: String,
    val format: String?,
    val author: String,
    val coNarrators: String,
    val deadline: String,
    val deadlineColor: Color,
    val first15: String,
    val first15Complete: Boolean,
    val words: String,
    val booth: String,
    val boothAccent: Color,
    val confidential: Boolean,
)

@Composable
private fun SampleBoardCard(card: SampleCard) {
    val c = DmnTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            // Cover: 96x144 at 2:3. Background-coloured, standing in for a card
            // whose cover_url is empty — which is what the placeholder must
            // look like anyway.
            Box(
                Modifier
                    .width(96.dp)
                    .height(144.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Background)
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                // The lock sits in the card's top-right corner, and the format
                // pill is the last thing in the title row — so on a phone they
                // land on the same pixels. The web never hit this because a
                // desktop card is wide enough that the two never meet.
                Row(
                    Modifier.padding(end = if (card.confidential) 18.dp else 0.dp),
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
                    if (card.format != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(c.pillNeutralBg)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(card.format, style = DmnType.Pill, color = c.pillNeutralText)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(card.author, style = DmnType.BodyMedium, color = c.accentAmber, maxLines = 1, overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(2.dp))
                // Blank but height-preserving when solo — the reason every card
                // down the column lines up.
                Text(
                    card.coNarrators.ifEmpty { " " },
                    style = DmnType.Small,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.defaultMinSize(minHeight = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(card.deadlineColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CalendarToday, null, tint = card.deadlineColor, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(card.deadline, style = DmnType.Numeric, color = card.deadlineColor)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                            card.first15,
                            style = DmnType.Numeric,
                            color = if (card.first15Complete) c.textMuted else c.accentAmberBright,
                            textDecoration = if (card.first15Complete) TextDecoration.LineThrough else null,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(card.words.ifEmpty { " " }, style = DmnType.Body, color = c.textDim)

                Spacer(Modifier.height(2.dp))
                Text(card.booth.ifEmpty { " " }, style = DmnType.Small, color = card.boothAccent)
            }
        }

        if (card.confidential) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Confidential",
                tint = c.accentAmberDim,
                modifier = Modifier.size(14.dp).align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
fun ThemeProofScreen(modifier: Modifier = Modifier) {
    val c = DmnTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Board", style = DmnType.TitleLg, color = c.textPrimary)
        Spacer(Modifier.height(16.dp))

        Text("THIS WEEK", style = DmnType.Label, color = c.textFaint)
        Spacer(Modifier.height(8.dp))
        SampleBoardCard(
            SampleCard(
                title = "Blood on the Asphalt",
                format = null,
                author = "River Fox",
                coNarrators = "",
                deadline = "Aug 28",
                deadlineColor = AlertRed,
                first15 = "Aug 20",
                first15Complete = true,
                words = "94,300 words · ~\$1,204",
                booth = "6.4 hrs left · 41% done · 2.1 hrs/day",
                boothAccent = TextMuted,
                confidential = false,
            )
        )
        Spacer(Modifier.height(12.dp))
        SampleBoardCard(
            SampleCard(
                title = "A Very Long Title That Has To Ellipsize",
                format = "Duet",
                author = "E.A. Harper",
                coNarrators = "with Lucy Vale",
                deadline = "Sep 14",
                deadlineColor = AccentAmberBright,
                first15 = "Sep 1",
                first15Complete = false,
                words = "112,880 words · ~\$1,441",
                booth = "11.3 hrs at the mic · 4.6 hrs/day",
                boothAccent = AccentAmberBright,
                confidential = true,
            )
        )
        Spacer(Modifier.height(12.dp))
        SampleBoardCard(
            SampleCard(
                title = "Untitled Multicast",
                format = "Multicast",
                author = "K.E. Noel",
                coNarrators = "with Zach Hoffman, Lucy Vale",
                deadline = "Nov 3",
                deadlineColor = TextBody,
                first15 = "Oct 12",
                first15Complete = false,
                words = "",
                booth = "",
                boothAccent = CapacityLight,
                confidential = false,
            )
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1420, widthDp = 412, heightDp = 900)
@Composable
private fun ThemeProofPreview() {
    DmnAdminTheme { ThemeProofScreen() }
}
