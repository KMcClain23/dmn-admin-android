package com.dmnarration.admin.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The agenda, ported from `api/agenda/route.ts`.
 *
 * The route is the specification — running code that already encodes the workflow —
 * so these are written against its behaviour rather than a fresh reading of what an
 * agenda ought to be. Two things are not the route's: the late group (§3A.3, a
 * dropped case) and one-card-per-book (§3, because the route returns two independent
 * lists and a phone screen renders them as two cards for one book).
 */
class AgendaTest {

    private val today = LocalDate.parse("2026-08-26") // a Wednesday

    private val settings = StudioSettings(
        wordsPerNarrationHour = 5000,
        wordsPerFinishedHour = 9400,
        dailyCapacityHours = 6.0,
        maxBooksPerDay = 2,
        heavyDayHours = 4.0,
    )

    private fun agenda(cards: List<BoardCard>) = buildAgenda(cards, settings, today)

    private fun book(
        id: String,
        status: String,
        deadline: String? = null,
        dates: List<String> = emptyList(),
        wordCount: Int? = 90_000,
        recorded: Int? = 0,
    ) = card(id = id, status = status).copy(
        deadline = deadline?.let(LocalDate::parse),
        recordingDates = dates.map(LocalDate::parse),
        wordCount = wordCount,
        wordsRecorded = recorded,
        narrationFormat = "solo",
    )

    private fun Agenda.ids(reason: AgendaReason) = grouped(reason).map { it.card.id }

    // ─── the status set ─────────────────────────────────────────────────────

    @Test fun `the set is the three pre-delivery statuses`() {
        assertEquals(setOf("contracted", "prepping", "recording"), PRE_DELIVERY)
    }

    @Test fun `editing is not in the set`() {
        assertFalse("a book past the mic is not booth work", "editing" in PRE_DELIVERY)
    }

    @Test fun `an editing card past its deadline is not late`() {
        val delivered = book("d", "editing", deadline = "2026-05-30")
        assertFalse(isLate(delivered, today))
        assertTrue("but the same card pre-delivery would be", isLate(delivered.copy(status = "recording"), today))
    }

    @Test fun `late is only ever pre-delivery and past deadline`() {
        assertTrue(isLate(book("a", "recording", deadline = "2026-08-25"), today))
        assertTrue(isLate(book("b", "contracted", deadline = "2026-01-01"), today))
        assertTrue(isLate(book("c", "prepping", deadline = "2026-08-25"), today))

        assertFalse("today is not yet late", isLate(book("d", "recording", deadline = "2026-08-26"), today))
        assertFalse("tomorrow is not late", isLate(book("e", "recording", deadline = "2026-08-27"), today))
        assertFalse("no deadline cannot be late", isLate(book("f", "recording", deadline = null), today))
        assertFalse("released is not pre-delivery", isLate(book("g", "released", deadline = "2020-01-01"), today))
    }

    // ─── one card per book ──────────────────────────────────────────────────

    /**
     * §3's assertion, and the reason this file's shape changed.
     *
     * A book qualifying twice used to render twice, which made a count of "what is on
     * today" wrong and left one copy stale when the other was acted on.
     */
    @Test fun `every distinct book renders exactly once`() {
        val result = agenda(
            listOf(
                // late AND recording today AND (no) due soon
                book("both", "recording", deadline = "2026-08-19", dates = listOf("2026-08-26")),
                // recording today AND due soon — the live shape
                book("cowboy", "recording", deadline = "2026-08-31", dates = listOf("2026-08-26")),
                book("plain", "contracted", deadline = "2026-08-28"),
            ),
        )
        val rendered = AgendaReason.entries.sumOf { result.grouped(it).size }
        val distinctBooks = result.items.map { it.card.id }.distinct().size

        assertEquals("one card per book", distinctBooks, rendered)
        assertEquals(3, rendered)
    }

    @Test fun `a book groups under its highest-priority reason and chips the rest`() {
        val result = agenda(
            listOf(book("both", "recording", deadline = "2026-08-19", dates = listOf("2026-08-26"))),
        )
        val item = result.items.single()
        assertEquals("late outranks recording today", AgendaReason.LATE, item.primary)
        assertEquals(listOf(AgendaReason.RECORDING_TODAY), item.secondary)
        assertEquals(listOf("both"), result.ids(AgendaReason.LATE))
        assertTrue("and it is not also in the lower group", result.ids(AgendaReason.RECORDING_TODAY).isEmpty())
    }

    /** The live case: recording today outranks due soon. */
    @Test fun `recording today outranks due soon`() {
        val result = agenda(
            listOf(book("cowboy", "recording", deadline = "2026-08-31", dates = listOf("2026-08-26"))),
        )
        val item = result.items.single()
        assertEquals(AgendaReason.RECORDING_TODAY, item.primary)
        assertEquals(listOf(AgendaReason.DUE_SOON), item.secondary)
        assertTrue(result.ids(AgendaReason.DUE_SOON).isEmpty())
    }

    @Test fun `a single-reason book has no chips`() {
        val result = agenda(listOf(book("plain", "contracted", deadline = "2026-08-28")))
        assertEquals(emptyList<AgendaReason>(), result.items.single().secondary)
    }

    @Test fun `priority order is late then recording then due`() {
        assertEquals(
            listOf(AgendaReason.LATE, AgendaReason.RECORDING_TODAY, AgendaReason.DUE_SOON),
            AgendaReason.entries.toList(),
        )
    }

    // ─── one relative-date formatter ────────────────────────────────────────

    /**
     * The chips and the section rows share this. Two formatters would disagree on a
     * boundary day — "due today" against "0 days late" — and only one would be right.
     */
    @Test fun `the relative formatter covers both sides of today`() {
        assertEquals("due today", relativeDeadline(LocalDate.parse("2026-08-26"), today))
        assertEquals("due tomorrow", relativeDeadline(LocalDate.parse("2026-08-27"), today))
        assertEquals("in 5 days", relativeDeadline(LocalDate.parse("2026-08-31"), today))
        assertEquals("1 day late", relativeDeadline(LocalDate.parse("2026-08-25"), today))
        assertEquals("7 days late", relativeDeadline(LocalDate.parse("2026-08-19"), today))
    }

    // ─── recording today ────────────────────────────────────────────────────

    @Test fun `only books with today actually chosen appear`() {
        val result = agenda(
            listOf(
                book("today", "recording", dates = listOf("2026-08-26", "2026-08-27")),
                book("tomorrow", "recording", dates = listOf("2026-08-27")),
                book("nodates", "recording"),
            ),
        )
        assertEquals(listOf("today"), result.ids(AgendaReason.RECORDING_TODAY))
    }

    @Test fun `a book with no chosen days and no deadline contributes nothing`() {
        val result = agenda(listOf(book("x", "recording")))
        assertTrue(result.isEmpty)
        assertEquals(0.0, result.weekHours, 0.0001)
        assertEquals(0.0, result.monthHours, 0.0001)
    }

    @Test fun `an editing book never appears even when scheduled today`() {
        val result = agenda(listOf(book("e", "editing", dates = listOf("2026-08-26"))))
        assertTrue(result.isEmpty)
        assertEquals(0.0, result.weekHours, 0.0001)
    }

    // ─── due soon ───────────────────────────────────────────────────────────

    @Test fun `due soon spans today through seven days and is sorted`() {
        val result = agenda(
            listOf(
                book("far", "recording", deadline = "2026-09-05"),
                book("edge", "recording", deadline = "2026-09-02"),
                book("today", "recording", deadline = "2026-08-26"),
                book("past", "recording", deadline = "2026-08-25"),
            ),
        )
        assertEquals("inclusive at both ends, soonest first", listOf("today", "edge"), result.ids(AgendaReason.DUE_SOON))
    }

    /** The dropped case: past-deadline falls out of dueSoon and into late. */
    @Test fun `a slipped card leaves due soon and enters late`() {
        val result = agenda(listOf(book("slipped", "recording", deadline = "2026-08-25")))
        assertTrue("no longer due soon", result.ids(AgendaReason.DUE_SOON).isEmpty())
        assertEquals("but not invisible", listOf("slipped"), result.ids(AgendaReason.LATE))
    }

    // ─── week and month hours ───────────────────────────────────────────────

    @Test fun `the week ends Sunday and the month ends on the last of the month`() {
        assertEquals(LocalDate.parse("2026-08-30"), endOfWeek(today))
        assertEquals(LocalDate.parse("2026-08-31"), endOfMonth(today))
    }

    @Test fun `a Sunday is its own end of week`() {
        val sunday = LocalDate.parse("2026-08-30")
        assertEquals(sunday, endOfWeek(sunday))
    }

    @Test fun `a Monday runs to the following Sunday`() {
        assertEquals(LocalDate.parse("2026-08-30"), endOfWeek(LocalDate.parse("2026-08-24")))
    }

    @Test fun `a day past the month end counts to neither`() {
        val result = agenda(listOf(book("m", "recording", dates = listOf("2026-09-15"), deadline = "2026-09-30")))
        assertEquals(0.0, result.weekHours, 0.0001)
        assertEquals(0.0, result.monthHours, 0.0001)
    }

    @Test fun `a day inside the month but past the week counts only to the month`() {
        val result = agenda(listOf(book("m", "recording", dates = listOf("2026-08-31"), deadline = "2026-09-30")))
        assertEquals("31 August is past Sunday the 30th", 0.0, result.weekHours, 0.0001)
        assertTrue("but inside August", result.monthHours > 0.0)
    }

    // ─── progress ───────────────────────────────────────────────────────────

    /**
     * ONE DENOMINATOR.
     *
     * The percentage on the progress bar and the percentage implied by the remaining
     * hours must come from the same place. They did not: the bar divided by
     * `word_count` while `narrationPlan` divides by the narrator's share, so a duet
     * showed 20% and 40% on the same screen. `narrationPlan.fractionDone` is the
     * reference because it is the one that was right.
     */
    @Test fun `the progress bar and the remaining hours share a denominator`() {
        val cards = listOf(
            book("duet", "recording", wordCount = 92_000, recorded = 18_400).copy(narrationFormat = "duet"),
            book("solo", "recording", wordCount = 90_000, recorded = 45_000).copy(narrationFormat = "solo"),
            book("dual", "recording", wordCount = 80_000, recorded = 10_000).copy(narrationFormat = "dual"),
            book("explicit", "recording", wordCount = 100_000, recorded = 10_000)
                .copy(narrationFormat = "multicast", narratorSharePercent = 25),
        )
        for (c in cards) {
            val plan = narrationPlan(
                NarrationInput(
                    wordCount = c.wordCount,
                    narrationFormat = c.narrationFormat,
                    narratorSharePercent = c.narratorSharePercent,
                    deadline = c.deadline,
                    wordsPerNarrationHour = settings.wordsPerNarrationHour,
                    wordsRecorded = c.wordsRecorded ?: 0,
                    today = today,
                ),
            )!!
            assertEquals(
                "${c.id}: the bar and the hours must agree",
                plan.fractionDone,
                recordedFraction(c)!!,
                0.000001,
            )
        }
    }

    /** A duet is half a manuscript, so 18,400 of 92,000 is 40% of the share. */
    @Test fun `a duet measures against half the manuscript`() {
        val duet = book("a", "recording", wordCount = 92_000, recorded = 18_400).copy(narrationFormat = "duet")
        assertEquals(0.4, recordedFraction(duet)!!, 0.0001)
    }

    @Test fun `a solo measures against the whole manuscript`() {
        val solo = book("a", "recording", wordCount = 92_000, recorded = 18_400).copy(narrationFormat = "solo")
        assertEquals(0.2, recordedFraction(solo)!!, 0.0001)
    }

    /**
     * Multicast has no default split, so there is no honest percentage to show.
     * Guessing an equal one would print a confident wrong number.
     */
    @Test fun `multicast without an explicit share renders nothing`() {
        val multicast = book("a", "recording", wordCount = 92_000, recorded = 18_400)
            .copy(narrationFormat = "multicast")
        assertNull(recordedFraction(multicast))
    }

    @Test fun `an explicit share wins for any format including multicast`() {
        val explicit = book("a", "recording", wordCount = 100_000, recorded = 10_000)
            .copy(narrationFormat = "multicast", narratorSharePercent = 25)
        assertEquals("10,000 of 25,000", 0.4, recordedFraction(explicit)!!, 0.0001)
    }

    @Test fun `over-recorded clamps to one rather than reading as more than finished`() {
        assertEquals(1.0, recordedFraction(book("a", "recording", wordCount = 100, recorded = 250))!!, 0.0001)
    }

    @Test fun `a negative figure clamps to zero`() {
        assertEquals(0.0, recordedFraction(book("a", "recording", wordCount = 100, recorded = -50))!!, 0.0001)
    }

    @Test fun `an absent or zero word count has no fraction at all`() {
        assertNull(recordedFraction(book("a", "recording", wordCount = null)))
        assertNull(recordedFraction(book("a", "recording", wordCount = 0)))
    }

    // ─── the whole thing ────────────────────────────────────────────────────

    @Test fun `an agenda with nothing in any group is empty`() {
        assertTrue(agenda(emptyList()).isEmpty)
        assertTrue(agenda(listOf(book("e", "editing", deadline = "2020-01-01"))).isEmpty)
    }
}
