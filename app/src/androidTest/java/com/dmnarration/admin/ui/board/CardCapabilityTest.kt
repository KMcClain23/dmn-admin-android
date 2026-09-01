package com.dmnarration.admin.ui.board

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.StudioSettings
import com.dmnarration.admin.domain.UserRole
import com.dmnarration.admin.ui.theme.DmnAdminTheme
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/**
 * The proof that the capability seam works, long before an editor exists.
 *
 * Two renders of the same card, differing only in `canViewFinancials`. The
 * earnings suffix must vanish, the word count and booth-load line must stay —
 * words and hours are production information, not money — and the card must be
 * exactly the same height either way. That last one matters more than it looks:
 * if hiding earnings changed the height, an editor's board would be laid out
 * differently from an admin's for no reason a viewer could see.
 */
class CardCapabilityTest {

    @get:Rule val compose = createComposeRule()

    // Live rates, so the arithmetic below is the arithmetic the app does.
    private val settings = StudioSettings(
        wordsPerNarrationHour = 5000,
        wordsPerFinishedHour = 9400,
        dailyCapacityHours = 6.0,
        maxBooksPerDay = 2,
        heavyDayHours = 4.0,
    )

    private val today = LocalDate.parse("2026-08-25")

    // Duet, 112,880 words -> 56,440 share. Earnings: 112,880 / 9,400 = 12.0085
    // finished hours x $240 x 0.5 = $1,441.
    private val card = BoardCard(
        id = "card-1",
        title = "Whiskey and Lies",
        author = "E.A. Harper",
        coNarrator = """["Lucy Vale"]""",
        coverUrl = null,
        status = "recording",
        deadline = LocalDate.parse("2026-09-14"),
        first15Due = LocalDate.parse("2026-09-01"),
        first15Complete = false,
        wordCount = 112_880,
        pfhRate = 240.0,
        paymentType = "pfh",
        isConfidential = false,
        narrationFormat = "duet",
        narratorSharePercent = null,
        recordingDates = emptyList(),
        wordsRecorded = 0,
        // Added when page progress shipped; these fixtures were never updated,
        // which broke the whole androidTest compile and took every instrumented
        // test down with it — including the bottom bar's width measurement.
        totalPages = null,
        currentPage = null,
        createdAt = Instant.parse("2026-08-01T00:00:00Z"),
    )

    private var toggles = 0

    private fun render(capabilities: Capabilities) {
        toggles = 0
        compose.setContent {
            DmnAdminTheme {
                BoardCardItem(
                    card = card,
                    capabilities = capabilities,
                    settings = settings,
                    today = today,
                    onClick = {},
                    onToggleFirst15 = { toggles++ },
                    modifier = Modifier.testTag("card"),
                )
            }
        }
    }

    /**
     * The First-15 row is a target only for a session that may write.
     *
     * Both renders show the same row — a read-only viewer sees an identical
     * card, it simply does not respond. Asserting the callback count rather
     * than appearance is the point: a disabled-looking control that still fires
     * would pass any visual check.
     */
    @Test
    fun adminCanToggleFirst15() {
        render(Capabilities.of(UserRole.ADMIN))
        compose.onNodeWithText("Sep 1").performClick()
        assertEquals("an admin's tap must reach the ViewModel", 1, toggles)
    }

    @Test
    fun aReadOnlySessionCannotToggleFirst15() {
        render(Capabilities.of(UserRole.EDITOR))
        compose.onNodeWithText("Sep 1").performClick()
        assertEquals("a read-only session must not be able to fire a write", 0, toggles)
        // and the row is still rendered, not hidden
        compose.onNodeWithText("Sep 1").assertIsDisplayed()
    }

    @Test
    fun adminSeesEarningsAndTheCardIsTheStandardHeight() {
        render(Capabilities.of(UserRole.ADMIN))
        compose.onNodeWithText("112,880 words · ~$1,441").assertIsDisplayed()
        compose.onNodeWithTag("card").assertHeightIsEqualTo(CARD_HEIGHT)
    }

    @Test
    fun withoutFinancialsTheEarningsGoButNothingElseMoves() {
        render(Capabilities.of(UserRole.EDITOR))

        // The suffix is gone entirely — not zeroed, gone.
        compose.onNodeWithText("112,880 words · ~$1,441").assertDoesNotExist()
        // The word count stays: words are production information.
        compose.onNodeWithText("112,880 words").assertIsDisplayed()
        // So does the booth load. 56,440 / 5,000 = 11.288 -> "11.3 hrs at the mic".
        compose.onNodeWithText("11.3 hrs at the mic").assertIsDisplayed()
        // And the card is the same height it was for an admin.
        compose.onNodeWithTag("card").assertHeightIsEqualTo(CARD_HEIGHT)
    }

    /**
     * Finding 3, on the rendering side: a card with a word count but no
     * computable earnings must print the words and stop. "~$0" is the classic
     * failure and it is plausible enough to survive review — a multicast book
     * that appears to earn nothing reads as a data problem and gets
     * investigated in the wrong place. Live data has three of these.
     */
    @Test
    fun aMulticastCardShowsWordsAndNoDollarFigureAtAll() {
        compose.setContent {
            DmnAdminTheme {
                BoardCardItem(
                    card = card.copy(narrationFormat = "multicast", title = "Multicast Book"),
                    capabilities = Capabilities.of(UserRole.ADMIN),
                    settings = settings,
                    today = today,
                    onClick = {},
                    modifier = Modifier.testTag("card"),
                )
            }
        }
        compose.onNodeWithText("112,880 words").assertIsDisplayed()
        compose.onNodeWithText("112,880 words · ~$0").assertDoesNotExist()
        compose.onNodeWithTag("card").assertHeightIsEqualTo(CARD_HEIGHT)
    }
}
