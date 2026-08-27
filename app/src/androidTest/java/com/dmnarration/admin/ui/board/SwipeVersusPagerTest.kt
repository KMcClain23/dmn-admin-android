package com.dmnarration.admin.ui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.StudioSettings
import com.dmnarration.admin.domain.UserRole
import com.dmnarration.admin.ui.theme.DmnAdminTheme
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/**
 * Who owns a horizontal drag: the card, or the pager underneath it.
 *
 * THE NAME OUTLIVED THE GESTURE, DELIBERATELY. There is no swipe-to-archive any
 * more — it was removed because it took horizontal drags away from the pager, so
 * paging between Pipeline and In Production only worked from the gaps between
 * cards. Archiving moved to the long-press action sheet, where it already
 * existed as a menu item; the swipe had only ever been a second, hidden route to
 * the same confirmation dialog.
 *
 * The file keeps its name because the QUESTION is unchanged and only the ANSWER
 * flipped, and that flip is the thing worth recording. A deleted test would
 * leave no trace that the opposite was once true and deliberate; this one fails
 * if anyone reintroduces a horizontal gesture on a card. If you came here
 * looking for the swipe, this paragraph is why you will not find it.
 *
 * BOTH HALVES ARE ASSERTED. Half one now says a drag starting ON A CARD reaches
 * the pager and fires no card action — the inverse of what it asserted through
 * Stages 2 to 8. Half two is unchanged: a drag starting on bare list surface
 * pages and archives nothing.
 *
 * WHAT THE MUTATIONS ESTABLISHED WHEN THE SWIPE STILL EXISTED, kept because the
 * findings outlast the gesture and two of them were surprises:
 *
 *   - Deleting the explicit `change.consume()` in BoardCardItem: BOTH HALVES
 *     STAYED GREEN. That line was never what made the card win — the detector
 *     consumed the slop crossing itself.
 *   - Removing the card's swipe handling entirely: half one went red, half two
 *     stayed green. That mutation is now the shipped state, which is exactly why
 *     half one had to be inverted rather than deleted.
 *   - Consuming horizontal drags one level up, on the list: BOTH went red. That
 *     is the over-broad fix, and half two still exists to produce that failure.
 */
class SwipeVersusPagerTest {

    @get:Rule val compose = createComposeRule()

    private val settings = StudioSettings(
        wordsPerNarrationHour = 5000,
        wordsPerFinishedHour = 9400,
        dailyCapacityHours = 6.0,
        maxBooksPerDay = 2,
        heavyDayHours = 4.0,
    )

    private val today = LocalDate.parse("2026-08-25")

    private val card = BoardCard(
        id = "card-1",
        title = "Whiskey and Lies",
        author = "E.A. Harper",
        coNarrator = null,
        coverUrl = null,
        status = "recording",
        deadline = LocalDate.parse("2026-09-30"),
        first15Due = LocalDate.parse("2026-09-11"),
        first15Complete = false,
        wordCount = 96_000,
        pfhRate = 240.0,
        paymentType = "pfh",
        isConfidential = false,
        narrationFormat = "solo",
        narratorSharePercent = null,
        recordingDates = emptyList(),
        wordsRecorded = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    /** Any card-level action firing from a drag. Must stay at zero throughout. */
    private var cardActionCalls = 0
    private var pageAfter = -1

    /**
     * The real nesting, minimally: a pager whose page is a scrolling list with a
     * card at the top and empty list surface below it. That surface is a genuine
     * gutter — in the running app it is the space under the last card — and it is
     * where the pager is supposed to own the gesture.
     */
    @Composable
    private fun Harness() {
        val pagerState = rememberPagerState(pageCount = { 2 })
        pageAfter = pagerState.currentPage
        DmnAdminTheme {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().testTag("pager"),
            ) { page ->
                if (page == 0) {
                    LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                        item {
                            Box(Modifier.padding(16.dp)) {
                                BoardCardItem(
                                    card = card,
                                    capabilities = Capabilities.of(UserRole.ADMIN),
                                    settings = settings,
                                    today = today,
                                    onClick = {},
                                    // Every card action now goes through the
                                    // long-press sheet. If a horizontal drag
                                    // ever fires one of these again, half one
                                    // catches it.
                                    onLongPress = { cardActionCalls++ },
                                    onToggleFirst15 = { cardActionCalls++ },
                                    modifier = Modifier.testTag("card"),
                                )
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize().testTag("page2"))
                }
            }
        }
    }

    private fun setUp() {
        cardActionCalls = 0
        compose.setContent { Harness() }
        compose.waitForIdle()
    }

    // ─── half one: the PAGER owns a drag that starts on the card ────────────

    /**
     * The inverse of what this asserted through Stages 2 to 8.
     *
     * It used to require that a swipe on a card archived it and the pager did
     * not move. Both clauses are now wrong by design: a horizontal drag belongs
     * to the pager wherever it starts, including on top of a card, which is the
     * whole point of removing the swipe. Dean could not page between tabs
     * without finding a gap between cards.
     */
    @Test
    fun aDragStartingOnTheCardPagesAndFiresNoCardAction() {
        setUp()

        compose.onNodeWithTag("card").performTouchInput {
            // The same gesture the old test used, from the same place on the
            // card. Only the expected outcome changed.
            swipe(
                start = Offset(width * 0.9f, height / 2f),
                end = Offset(width * 0.1f, height / 2f),
                durationMillis = 300,
            )
        }
        compose.waitForIdle()

        assertEquals(
            "a horizontal drag must not fire any card action — there is no card gesture",
            0,
            cardActionCalls,
        )
        assertEquals(
            "the pager must have paged: a drag starting on a card belongs to it now",
            1,
            pageAfter,
        )
    }

    // ─── half two: the pager owns a drag that starts off the card ───────────

    /**
     * Not optional. This is the half that distinguishes a working arbitration
     * from a subtree that swallows every horizontal drag, and the latter passes
     * half one without complaint.
     */
    @Test
    fun aSwipeBelowTheCardPagesAndFiresNoCardAction() {
        setUp()

        val cardBottom = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.bottom

        compose.onNodeWithTag("pager").performTouchInput {
            // Comfortably below the card, on bare list surface inside the pager.
            val y = cardBottom + 200f
            assertTrue("the gutter must be inside the pager", y < height)
            swipe(
                start = Offset(width * 0.9f, y),
                end = Offset(width * 0.1f, y),
                durationMillis = 300,
            )
        }
        compose.waitForIdle()

        assertEquals(
            "a drag that never touched the card must not fire a card action",
            0,
            cardActionCalls,
        )
        assertEquals("the pager must have paged", 1, pageAfter)
    }
}
