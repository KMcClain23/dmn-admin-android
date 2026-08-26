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
 * Swipe-to-archive lives inside a HorizontalPager that claims horizontal drags
 * of its own. In practice the card wins, and probably by rule rather than luck —
 * a descendant sees the Main pass before its ancestor. But nothing in the source
 * said so and nothing failed if it stopped being true, which is the shape of
 * every bug this project has found. This is the thing that fails.
 *
 * BOTH HALVES ARE ASSERTED, and the second is the one that matters. A "fix" that
 * consumes every horizontal drag in the subtree silently kills paging, and a test
 * that only checked the card could not tell a working arbitration from a broken
 * pager.
 *
 * WHAT THE MUTATIONS ESTABLISHED, recorded because two of them were surprises:
 *
 *   - Deleting the explicit `change.consume()` in BoardCardItem: BOTH HALVES
 *     STAYED GREEN. That line is not what makes the card win — the detector
 *     consumes the slop crossing itself. It is labelled as insurance there
 *     rather than as the mechanism, because a comment claiming otherwise is a
 *     false landmark for whoever reads it next.
 *   - Removing the card's swipe handling entirely: half one goes red, half two
 *     stays green. So half one is not vacuous, and half two is correctly
 *     indifferent to whether the card has a gesture at all.
 *   - Consuming horizontal drags one level up, on the list: BOTH go red, half
 *     two reporting "the pager must have paged". That is the over-broad fix,
 *     and it is the failure this test exists to produce.
 *
 * Half one's "the pager did not page" also held when the card consumed nothing,
 * so that clause is not by itself discriminating. It is kept because it is the
 * behaviour we actually require, and it is what would catch a Compose upgrade
 * that reversed dispatch order — the scenario none of the mutations can simulate.
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

    private var archiveCalls = 0
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
                                    onSwipeArchive = { archiveCalls++ },
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
        archiveCalls = 0
        compose.setContent { Harness() }
        compose.waitForIdle()
    }

    // ─── half one: the card owns a drag that starts on the card ─────────────

    @Test
    fun aSwipeOnTheCardArchivesAndDoesNotChangeThePage() {
        setUp()

        compose.onNodeWithTag("card").performTouchInput {
            // Past the -90dp threshold, well inside the card.
            swipe(
                start = Offset(width * 0.9f, height / 2f),
                end = Offset(width * 0.1f, height / 2f),
                durationMillis = 300,
            )
        }
        compose.waitForIdle()

        assertEquals("the swipe must reach the card's archive path", 1, archiveCalls)
        assertEquals(
            "the pager must not have paged — the card consumed the drag",
            0,
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
            "a drag that never touched the card must not archive anything",
            0,
            archiveCalls,
        )
        assertEquals("the pager must have paged", 1, pageAfter)
    }
}
