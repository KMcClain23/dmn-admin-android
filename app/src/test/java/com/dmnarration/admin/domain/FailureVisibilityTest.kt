package com.dmnarration.admin.domain

import com.dmnarration.admin.data.SessionState
import com.dmnarration.admin.data.SignOutOutcome
import com.dmnarration.admin.data.StoredSession
import com.dmnarration.admin.data.notAuthenticatedState
import com.dmnarration.admin.data.signOutMessage
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Two states that must never render identically.
 *
 * This whole defect class is "nothing to show" and "we could not find out" producing
 * the same pixels, so the only assertion worth making is that they differ. Each test
 * pairs the benign state with the failed one and asserts they are distinguishable —
 * not that either has a particular wording.
 */
class FailureVisibilityTest {

    // ─── available_months ───────────────────────────────────────────────────

    @Test fun `no booking window reads differently from an unreadable one`() {
        val none = availableMonthsLabel(emptyList(), null)
        val unreadable = availableMonthsLabel(null, "winter-ish")
        assertNotEquals("both used to be \"None\"", none, unreadable)
        assertEquals("None", none)
    }

    @Test fun `an unreadable window shows what was actually stored`() {
        assertEquals("Unreadable: winter-ish", availableMonthsLabel(null, "winter-ish"))
    }

    @Test fun `a readable window is unaffected`() {
        assertEquals("November – February", availableMonthsLabel(listOf(11, 12, 1, 2)))
    }

    // ─── accepting_projects ─────────────────────────────────────────────────

    @Test fun `unset reads differently from unreadable`() {
        val unset = acceptingProjectsLabel(null, null)
        val unreadable = acceptingProjectsLabel(null, "TRUE")
        assertNotEquals("both used to be \"Not set\"", unset, unreadable)
        assertEquals("Not set", unset)
        assertEquals("Unreadable: TRUE", unreadable)
    }

    /** The two values that actually produce this: strict parsing rejects both. */
    @Test fun `TRUE and 1 are reported rather than silently unset`() {
        for (raw in listOf("TRUE", "1", "yes")) {
            assertEquals("Unreadable: $raw", acceptingProjectsLabel(null, raw))
        }
    }

    @Test fun `a real boolean is unaffected`() {
        assertEquals("Open to new projects", acceptingProjectsLabel(true, null))
        assertEquals("Not taking new projects", acceptingProjectsLabel(false, null))
    }

    // ─── sign-out: did the credential actually go? ──────────────────────────

    /**
     * The worst outcome this app has, and it was silent. deleteSession() returns Unit
     * by contract, so a store refusing the write had no way to report it and sign-out
     * said "done" regardless.
     */
    @Test fun `a sign-out that could not clear the credential says so`() {
        val cleared = signOutMessage(SignOutOutcome.Cleared)
        val remains = signOutMessage(SignOutOutcome.CredentialMayRemain(IllegalStateException("denied")))
        assertNotEquals("both used to be silent", cleared, remains)
        assertNull("a clean sign-out needs no message", cleared)
        assertNotNull("a failed one must be visible", remains)
    }

    // ─── not authenticated: three states, not two ───────────────────────────

    /**
     * A store that cannot be read used to answer ABSENT and land here as a fresh
     * install — offering a sign-in screen as though "never signed in" were a fact.
     */
    @Test fun `cannot-read-the-store never reads as a fresh install`() {
        val fresh = notAuthenticatedState(isSignOut = false, stored = StoredSession.ABSENT)
        val unknown = notAuthenticatedState(isSignOut = false, stored = StoredSession.UNKNOWN)
        assertNotEquals("these used to be the same branch", fresh, unknown)
        assertEquals(SessionState.NeedsSignIn, fresh)
        assertTrue("cannot-tell keeps the session", unknown is SessionState.Unreachable)
    }

    @Test fun `a stored-but-unvalidatable session is distinct from both`() {
        val present = notAuthenticatedState(false, StoredSession.PRESENT)
        val unknown = notAuthenticatedState(false, StoredSession.UNKNOWN)
        assertTrue(present is SessionState.Unreachable)
        assertTrue(unknown is SessionState.Unreachable)
        assertNotEquals(
            "and they do not say the same thing",
            (present as SessionState.Unreachable).message,
            (unknown as SessionState.Unreachable).message,
        )
    }

    /** A deliberate sign-out is signed out whatever the store says. */
    @Test fun `an explicit sign-out always needs a sign-in`() {
        for (stored in StoredSession.entries) {
            assertEquals(SessionState.NeedsSignIn, notAuthenticatedState(isSignOut = true, stored = stored))
        }
    }


    // ─── the two rates fail independently ───────────────────────────────────

    private val liveRows = mapOf(
        SettingKeys.WORDS_PER_NARRATION_HOUR to "5000",
        SettingKeys.WORDS_PER_FINISHED_HOUR to "9400",
        SettingKeys.DAILY_CAPACITY_HOURS to "6",
        SettingKeys.MAX_BOOKS_PER_DAY to "2",
        SettingKeys.HEAVY_DAY_HOURS to "4",
    )

    private fun plan(settings: StudioSettings) = narrationPlan(
        NarrationInput(
            wordCount = 92_000,
            narrationFormat = "solo",
            narratorSharePercent = null,
            deadline = LocalDate.parse("2026-09-30"),
            wordsPerNarrationHour = settings.wordsPerNarrationHour,
            wordsRecorded = 0,
            today = LocalDate.parse("2026-08-26"),
        ),
    )

    private fun earnings(settings: StudioSettings) = settings.wordsPerFinishedHour?.let {
        estimatedEarnings(
            wordCount = 92_000,
            pfhRate = 240.0,
            paymentType = "pfh",
            narrationFormat = "solo",
            narratorSharePercent = null,
            wordsPerFinishedHour = it,
        )
    }

    /**
     * THREE states, not two. A whole-read failure blanks both; a bad narration rate
     * blanks the booth figures only; a bad finished rate blanks the money only.
     * Collapsing any pair hides information that is genuinely known.
     */
    @Test fun `the two rates blank independently`() {
        val benign = studioSettingsFrom(liveRows).settings
        val readFailed = StudioSettings(null, null, null, null, null)
        val narrationBad = studioSettingsFrom(liveRows + (SettingKeys.WORDS_PER_NARRATION_HOUR to "500000")).settings
        val finishedBad = studioSettingsFrom(liveRows + (SettingKeys.WORDS_PER_FINISHED_HOUR to "nonsense")).settings

        // benign: both figures available
        assertNotNull("benign hours", plan(benign))
        assertNotNull("benign earnings", earnings(benign))

        // whole read failed: neither
        assertNull("no rates, no hours", plan(readFailed))
        assertNull("no rates, no earnings", earnings(readFailed))

        // narration rate bad: hours gone, MONEY SURVIVES
        assertNull("a bad narration rate blanks the booth figures", plan(narrationBad))
        assertNotNull("but the money is still fully known", earnings(narrationBad))

        // finished rate bad: money gone, HOURS SURVIVE
        assertNotNull("a bad finished rate leaves the booth figures alone", plan(finishedBad))
        assertNull("but the money cannot be computed", earnings(finishedBad))
    }

    /** The three failures are distinguishable from one another, not just from benign. */
    @Test fun `the three failure states do not collapse into two`() {
        val readFailed = StudioSettings(null, null, null, null, null)
        val narrationBad = studioSettingsFrom(liveRows + (SettingKeys.WORDS_PER_NARRATION_HOUR to "500000")).settings
        val finishedBad = studioSettingsFrom(liveRows + (SettingKeys.WORDS_PER_FINISHED_HOUR to "nonsense")).settings

        fun shape(s: StudioSettings) = (plan(s) != null) to (earnings(s) != null)

        assertEquals(false to false, shape(readFailed))
        assertEquals(false to true, shape(narrationBad))
        assertEquals(true to false, shape(finishedBad))
        assertNotEquals(shape(narrationBad), shape(finishedBad))
        assertNotEquals(shape(narrationBad), shape(readFailed))
        assertNotEquals(shape(finishedBad), shape(readFailed))
    }

    /** An out-of-range value must be reported, not silently replaced. */
    @Test fun `a rejected value is visible against its key`() {
        val read = studioSettingsFrom(liveRows + (SettingKeys.WORDS_PER_NARRATION_HOUR to "500000"))
        val issue = read.issueFor(SettingKeys.WORDS_PER_NARRATION_HOUR)
        assertNotNull("the rejection must reach the screen", issue)
        assertTrue(issue is SettingIssue.OutOfRange)
        assertEquals("500000", (issue as SettingIssue.OutOfRange).raw)
    }

}
