package com.dmnarration.admin.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/**
 * Every expectation here is hand-computed from the LIVE studio settings —
 * 5,000 words per narration hour, 9,400 per finished hour — and the arithmetic
 * is written out beside it rather than reduced to a literal.
 *
 * Deliberately not seeded from the 1.3 theme proof's sample strings. Those were
 * written to look plausible and one of them encoded roughly 8,700 words/hour,
 * near the old default. A wrong expectation in a passing test is worse than no
 * test: it never asks to be looked at again.
 */
class BoardMathTest {

    // Tuesday. Every date below is chosen relative to this one.
    private val today = LocalDate.parse("2026-08-25")

    private val narrationRate = 5000   // studio_words_per_narration_hour, live
    private val finishedRate = 9400    // studio_words_per_finished_hour, live

    // ─── daysUntil ──────────────────────────────────────────────────────────

    @Test fun `daysUntil is zero for today`() =
        assertEquals(0, daysUntil(today, today))

    @Test fun `daysUntil counts forward`() =
        assertEquals(7, daysUntil(LocalDate.parse("2026-09-01"), today))

    @Test fun `daysUntil goes negative for a date already passed`() =
        assertEquals(-5, daysUntil(LocalDate.parse("2026-08-20"), today))

    @Test fun `daysUntil crosses a year boundary`() =
        assertEquals(1, daysUntil(LocalDate.parse("2027-01-01"), LocalDate.parse("2026-12-31")))

    // ─── urgency: the two rules differ in exactly one branch ────────────────

    @Test fun `completionUrgency is red through a week, including overdue`() {
        assertEquals(Urgency.RED, completionUrgency(-5))
        assertEquals(Urgency.RED, completionUrgency(0))
        assertEquals(Urgency.RED, completionUrgency(7))
    }

    @Test fun `completionUrgency is amber to a month, then default`() {
        assertEquals(Urgency.YELLOW, completionUrgency(8))
        assertEquals(Urgency.YELLOW, completionUrgency(30))
        assertEquals(Urgency.DEFAULT, completionUrgency(31))
    }

    @Test fun `first15Urgency is red only when actually overdue`() {
        assertEquals(Urgency.RED, first15Urgency(-1))
        // The divergence: at 0-7 days a deadline is RED but a First-15 is amber.
        assertEquals(Urgency.YELLOW, first15Urgency(0))
        assertEquals(Urgency.YELLOW, first15Urgency(7))
        assertEquals(Urgency.DEFAULT, first15Urgency(8))
    }

    @Test fun `first15 48 days out is default, not amber`() {
        // The 1.3 screenshot showed Oct 12 amber at 48 days out. It was a
        // hardcoded colour in the proof, and this is the rule that replaces it.
        val days = daysUntil(LocalDate.parse("2026-10-12"), today)
        assertEquals(48, days)
        assertEquals(Urgency.DEFAULT, first15Urgency(days))
    }

    // ─── narratorShareOf ────────────────────────────────────────────────────

    @Test fun `share defaults to all of it when the format says nothing`() {
        assertEquals(1.0, narratorShareOf(null, null)!!, 1e-9)
        assertEquals(1.0, narratorShareOf("solo", null)!!, 1e-9)
    }

    @Test fun `duet and dual split in half`() {
        assertEquals(0.5, narratorShareOf("duet", null)!!, 1e-9)
        assertEquals(0.5, narratorShareOf("dual", null)!!, 1e-9)
    }

    @Test fun `multicast is unknown, not everything`() =
        assertNull(narratorShareOf("multicast", null))

    @Test fun `an explicit share overrides any format, multicast included`() {
        assertEquals(0.40, narratorShareOf("multicast", 40)!!, 1e-9)
        assertEquals(0.60, narratorShareOf("duet", 60)!!, 1e-9)
        assertEquals(0.99, narratorShareOf("solo", 99)!!, 1e-9)
    }

    // ─── estimatedEarnings ──────────────────────────────────────────────────

    private fun earnings(
        words: Int?, rate: Double?, type: String?, format: String?, pct: Int? = null,
    ) = estimatedEarnings(words, rate, type, format, pct, finishedRate)

    @Test fun `earnings apply only to pfh and rs_plus`() {
        // 94,300 / 9,400 = 10.0319148936 finished hours; x $120 x 1.0
        assertEquals(1203.8297872340, earnings(94_300, 120.0, "pfh", "solo")!!, 1e-6)
        assertEquals(1203.8297872340, earnings(94_300, 120.0, "rs_plus", "solo")!!, 1e-6)
        assertNull(earnings(94_300, 120.0, "flat", "solo"))
        assertNull(earnings(94_300, 120.0, null, "solo"))
    }

    @Test fun `earnings halve for a duet`() {
        // 112,880 / 9,400 = 12.0085106383 finished hours; x $240 x 0.5
        assertEquals(1441.0212765957, earnings(112_880, 240.0, "pfh", "duet")!!, 1e-6)
    }

    @Test fun `zero is absent, not zero — no card should ever read tilde-dollar-zero`() {
        // Both columns are NOT NULL DEFAULT 0 in Postgres, so unset arrives as 0.
        assertNull(earnings(0, 120.0, "pfh", "solo"))
        assertNull(earnings(null, 120.0, "pfh", "solo"))
        assertNull(earnings(94_300, 0.0, "pfh", "solo"))
        assertNull(earnings(94_300, null, "pfh", "solo"))
    }

    @Test fun `multicast earns nothing computable, but that is not zero`() {
        // Finding 3: a word count with no earnings. The row must print the words
        // and stop — the caller has to see null here, not 0.0.
        assertNull(earnings(112_880, 240.0, "pfh", "multicast"))
    }

    @Test fun `multicast with an explicit share does earn`() {
        // 100,000 / 9,400 = 10.6382978723; x $100 x 0.40
        assertEquals(425.5319148936, earnings(100_000, 100.0, "pfh", "multicast", 40)!!, 1e-6)
    }

    @Test fun `earnings use the passed rate, not a built-in one`() {
        // The whole reason the parameter is required. Same card, two rates.
        val at9400 = estimatedEarnings(94_000, 100.0, "pfh", "solo", null, 9400)!!
        val at5000 = estimatedEarnings(94_000, 100.0, "pfh", "solo", null, 5000)!!
        assertEquals(1000.0, at9400, 1e-9)   // 94,000 / 9,400 = 10.0 exactly
        assertEquals(1880.0, at5000, 1e-9)   // 94,000 / 5,000 = 18.8 exactly
    }

    // ─── stillAtMic ─────────────────────────────────────────────────────────

    @Test fun `mic work is ahead only up to and including recording`() {
        assertTrue(stillAtMic("contracted"))
        assertTrue(stillAtMic("prepping"))
        assertTrue(stillAtMic("recording"))
        assertFalse(stillAtMic("editing"))
        assertFalse(stillAtMic("released"))
        assertFalse(stillAtMic("recast"))
        assertFalse(stillAtMic(null))
        assertTrue("status is trimmed before comparison", stillAtMic("  recording  "))
    }

    // ─── recordingDaysBetween ───────────────────────────────────────────────

    @Test fun `weekdays are counted inclusive of both ends`() {
        // Tue 25 Aug .. Fri 28 Aug = Tue, Wed, Thu, Fri = 4.
        assertEquals(4, recordingDaysBetween(today, LocalDate.parse("2026-08-28")))
    }

    @Test fun `a weekend-only stretch has no recording days`() {
        // Sat 29 .. Sun 30 August, weekday pattern.
        assertEquals(0, recordingDaysBetween(LocalDate.parse("2026-08-29"), LocalDate.parse("2026-08-30")))
    }

    @Test fun `Sunday is day 0, not day 7`() {
        // The pattern is stored in JavaScript getDay() numbering, where Sunday
        // is 0; kotlinx-datetime calls it 7. Sunday is the only day where the
        // two disagree, so it is the only one worth a test.
        val sunday = LocalDate.parse("2026-08-30")
        assertEquals(1, recordingDaysBetween(sunday, sunday, setOf(0)))
        assertEquals(0, recordingDaysBetween(sunday, sunday, setOf(7)))
    }

    @Test fun `an empty pattern falls back to weekdays rather than counting nothing`() =
        assertEquals(4, recordingDaysBetween(today, LocalDate.parse("2026-08-28"), emptySet()))

    // ─── narrationPlan ──────────────────────────────────────────────────────

    private fun plan(
        words: Int?,
        format: String? = "solo",
        pct: Int? = null,
        deadline: String? = "2026-08-28",
        recorded: Int = 0,
        dates: List<String> = emptyList(),
        on: LocalDate = today,
    ) = narrationPlan(
        NarrationInput(
            wordCount = words,
            narrationFormat = format,
            narratorSharePercent = pct,
            deadline = deadline?.let(LocalDate::parse),
            wordsPerNarrationHour = narrationRate,
            wordsRecorded = recorded,
            schedule = RecordingSchedule(dates = dates.map(LocalDate::parse)),
            today = on,
        )
    )

    @Test fun `an untouched book spreads its whole self over the days left`() {
        // 100,000 words solo / 5,000 = 20.0 hours. Tue-Fri = 4 recording days.
        val p = plan(100_000)!!
        assertEquals(20.0, p.totalHours, 1e-9)
        assertEquals(20.0, p.hours, 1e-9)
        assertEquals(0.0, p.fractionDone, 1e-9)
        assertEquals(4, p.daysLeft)
        assertEquals(5.0, p.hoursPerDay!!, 1e-9)   // 20.0 / 4
        assertFalse(p.overdue)
    }

    @Test fun `progress reduces hours left but not total`() {
        // Duet: 112,880 x 0.5 = 56,440 share words. / 5,000 = 11.288 total.
        // 20,000 recorded -> 36,440 left / 5,000 = 7.288.
        val p = plan(112_880, format = "duet", recorded = 20_000)!!
        assertEquals(11.288, p.totalHours, 1e-9)
        assertEquals(7.288, p.hours, 1e-9)
        assertEquals(20_000.0 / 56_440.0, p.fractionDone, 1e-9)
    }

    @Test fun `an over-reported recorded count is clamped, never negative hours`() {
        // 50,000 share words, 999,999 claimed recorded. Clamped to 50,000.
        val p = plan(50_000, recorded = 999_999)!!
        assertEquals(0.0, p.hours, 1e-9)
        assertEquals(1.0, p.fractionDone, 1e-9)
        assertEquals(10.0, p.totalHours, 1e-9)     // 50,000 / 5,000
        // hours <= 0.005 is what the card renders as "Recording complete".
        assertTrue(p.hours <= 0.005)
    }

    @Test fun `a deadline in the past is overdue with no hours per day`() {
        val p = plan(100_000, deadline = "2026-08-20")!!
        assertTrue(p.overdue)
        assertEquals(0, p.daysLeft)
        assertNull(p.hoursPerDay)
        // The work itself does not vanish just because the date passed.
        assertEquals(20.0, p.hours, 1e-9)
    }

    @Test fun `a deadline with no recording day before it is overdue, not infinite`() {
        // Sat 29 Aug to Sun 30 Aug: no weekday in between. Dividing 20 hours by
        // zero days would render as Infinity hrs/day.
        val p = plan(100_000, deadline = "2026-08-30", on = LocalDate.parse("2026-08-29"))!!
        assertTrue(p.overdue)
        assertEquals(0, p.daysLeft)
        assertNull(p.hoursPerDay)
    }

    @Test fun `no deadline means no pace, but still a total`() {
        val p = plan(100_000, deadline = null)!!
        assertFalse(p.overdue)
        assertNull(p.daysLeft)
        assertNull(p.hoursPerDay)
        assertEquals(20.0, p.hours, 1e-9)
    }

    @Test fun `chosen dates beat the weekday pattern`() {
        // Mon 24th is behind us; Wed 26th and Thu 27th are ahead and inside the
        // deadline. Two days, not the four the weekday pattern would give.
        val p = plan(100_000, dates = listOf("2026-08-24", "2026-08-26", "2026-08-27"), deadline = "2026-08-31")!!
        assertEquals(2, p.daysLeft)
        assertEquals(10.0, p.hoursPerDay!!, 1e-9)  // 20.0 / 2
        assertFalse(p.overdue)
    }

    @Test fun `chosen dates all behind us is overdue`() {
        val p = plan(100_000, dates = listOf("2026-08-01", "2026-08-24"))!!
        assertTrue(p.overdue)
        assertEquals(0, p.daysLeft)
        assertNull(p.hoursPerDay)
    }

    @Test fun `multicast has no plan at all`() =
        assertNull(plan(112_880, format = "multicast"))

    @Test fun `no word count means no plan`() {
        assertNull(plan(0))
        assertNull(plan(null))
    }

    // ─── parseCoNarrators ───────────────────────────────────────────────────

    @Test fun `a JSON array is the common shape`() =
        assertEquals(listOf("Lucy Vale", "Zach Hoffman"), parseCoNarrators("""["Lucy Vale","Zach Hoffman"]"""))

    @Test fun `a bare non-JSON name is the other live shape and must not crash`() {
        // Five rows on the board look like this today.
        assertEquals(listOf("Zach Hoffman"), parseCoNarrators("Zach Hoffman"))
    }

    @Test fun `empty and absent values give nothing`() {
        assertEquals(emptyList<String>(), parseCoNarrators(null))
        assertEquals(emptyList<String>(), parseCoNarrators(""))
        assertEquals(emptyList<String>(), parseCoNarrators("[]"))
        assertEquals(emptyList<String>(), parseCoNarrators("null"))
    }

    @Test fun `blank entries inside an array are dropped`() =
        assertEquals(listOf("Lucy Vale"), parseCoNarrators("""["","Lucy Vale"]"""))

    // ─── studio settings ────────────────────────────────────────────────────

    /*
     * These three used to assert that a missing, unreadable or out-of-range setting
     * FELL BACK to a default. That is the behaviour removed on 27 August 2026: the
     * narration rate's default is 9,200 against a live 5,000, so falling back
     * under-reported every booth figure by roughly 46% with plausible-looking numbers
     * and no indication. They now assert the opposite contract — null, plus an issue
     * naming the key — so the old behaviour cannot return without one of them failing.
     */

    @Test fun `a good value is read and reported without issue`() {
        val read = studioSettingsFrom(
            mapOf(
                SettingKeys.WORDS_PER_NARRATION_HOUR to "5000",
                SettingKeys.WORDS_PER_FINISHED_HOUR to "9400",
            )
        )
        assertEquals(5000, read.settings.wordsPerNarrationHour)
        assertEquals(9400, read.settings.wordsPerFinishedHour)
        assertNull(read.issueFor(SettingKeys.WORDS_PER_NARRATION_HOUR))
    }

    @Test fun `a missing key is null and says which key`() {
        val read = studioSettingsFrom(mapOf(SettingKeys.WORDS_PER_NARRATION_HOUR to "5000"))
        assertNull("never a default", read.settings.heavyDayHours)
        val issue = read.issueFor(SettingKeys.HEAVY_DAY_HOURS)
        assertTrue(issue is SettingIssue.Missing)
    }

    @Test fun `an unreadable value is null and carries what was stored`() {
        val read = studioSettingsFrom(mapOf(SettingKeys.WORDS_PER_FINISHED_HOUR to "not a number"))
        assertNull(read.settings.wordsPerFinishedHour)
        val issue = read.issueFor(SettingKeys.WORDS_PER_FINISHED_HOUR)
        assertTrue(issue is SettingIssue.Unreadable)
        assertEquals("not a number", (issue as SettingIssue.Unreadable).raw)
    }

    /** The W1 disease exactly: a typo'd number silently becoming a different one. */
    @Test fun `an out-of-range value is null and reports the value and the bounds`() {
        val read = studioSettingsFrom(mapOf(SettingKeys.WORDS_PER_NARRATION_HOUR to "500000"))
        assertNull("500000 must not silently become 9200", read.settings.wordsPerNarrationHour)
        val issue = read.issueFor(SettingKeys.WORDS_PER_NARRATION_HOUR)
        assertTrue(issue is SettingIssue.OutOfRange)
        assertEquals("500000", (issue as SettingIssue.OutOfRange).raw)
        assertEquals("1000–30000", issue.allowed)
    }

    @Test fun `no rows at all yields all nulls and an issue for every key`() {
        val read = studioSettingsFrom(emptyMap())
        assertNull(read.settings.wordsPerNarrationHour)
        assertNull(read.settings.wordsPerFinishedHour)
        assertNull(read.settings.dailyCapacityHours)
        assertNull(read.settings.maxBooksPerDay)
        assertNull(read.settings.heavyDayHours)
        assertEquals(5, read.issues.size)
    }
}

/** Shared card builder for the filter tests. */
internal fun card(
    id: String = "1",
    status: String = "contracted",
    deadline: String? = null,
    createdAt: String = "2026-08-01T00:00:00Z",
    wordCount: Int? = null,
    format: String? = null,
) = BoardCard(
    id = id,
    title = "T$id",
    author = "A",
    coNarrator = null,
    coverUrl = null,
    status = status,
    deadline = deadline?.let(LocalDate::parse),
    first15Due = null,
    first15Complete = false,
    wordCount = wordCount,
    pfhRate = null,
    paymentType = null,
    isConfidential = false,
    narrationFormat = format,
    narratorSharePercent = null,
    recordingDates = emptyList(),
    wordsRecorded = null,
    createdAt = Instant.parse(createdAt),
)
