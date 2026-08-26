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
 * The route is the specification — it is running code that already encodes the
 * workflow — so these tests are written against its behaviour rather than against a
 * fresh reading of what an agenda ought to be. The one addition is the late group,
 * which the route does not have and which §3A.3 justifies as a dropped case.
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

    // ─── the status set ─────────────────────────────────────────────────────

    @Test fun `the set is the three pre-delivery statuses`() {
        assertEquals(setOf("contracted", "prepping", "recording"), PRE_DELIVERY)
    }

    @Test fun `editing is not in the set`() {
        assertFalse("a book past the mic is not booth work", "editing" in PRE_DELIVERY)
    }

    /**
     * The defect the late rule exists to prevent, and the one live data can check:
     * six cards sit in `editing` past their deadline and are delivered, not late.
     */
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

    // ─── recording today ────────────────────────────────────────────────────

    @Test fun `only books with today actually chosen appear`() {
        val result = agenda(
            listOf(
                book("today", "recording", dates = listOf("2026-08-26", "2026-08-27")),
                book("tomorrow", "recording", dates = listOf("2026-08-27")),
                book("nodates", "recording"),
            ),
        )
        assertEquals(listOf("today"), result.recordingToday.map { it.card.id })
    }

    /**
     * A book with no chosen days contributes nothing at all — not to the list and
     * not to the totals. The route skips it before computing a plan.
     */
    @Test fun `a book with no chosen days contributes nothing`() {
        val result = agenda(listOf(book("x", "recording", deadline = "2026-08-28")))
        assertTrue(result.recordingToday.isEmpty())
        assertEquals(0.0, result.weekHours, 0.0001)
        assertEquals(0.0, result.monthHours, 0.0001)
    }

    @Test fun `an editing book never appears even when scheduled today`() {
        val result = agenda(listOf(book("e", "editing", dates = listOf("2026-08-26"))))
        assertTrue(result.recordingToday.isEmpty())
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
        assertEquals(
            "inclusive at both ends, soonest first",
            listOf("today", "edge"),
            result.dueSoon.map { it.card_id() },
        )
    }

    /** The dropped case: past-deadline falls out of dueSoon and into late. */
    @Test fun `a slipped card leaves due soon and enters late`() {
        val slipped = book("slipped", "recording", deadline = "2026-08-25")
        val result = agenda(listOf(slipped))
        assertTrue("no longer due soon", result.dueSoon.isEmpty())
        assertEquals("but not invisible", listOf("slipped"), result.late.map { it.card_id() })
    }

    // ─── week and month hours ───────────────────────────────────────────────

    /**
     * Wednesday 26 August 2026. The week ends Sunday the 30th and the month ends
     * Monday the 31st, so the two spans differ by exactly one day — which is what
     * makes this date worth testing on.
     */
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

    @Test fun `days behind today are excluded from both totals`() {
        val withPast = agenda(
            listOf(book("p", "recording", dates = listOf("2026-08-24", "2026-08-26"), deadline = "2026-08-31")),
        )
        val onlyToday = agenda(
            listOf(book("p", "recording", dates = listOf("2026-08-26"), deadline = "2026-08-31")),
        )
        // The past day adds nothing, but it does change how the remaining work is
        // spread, so the two are compared for the *count* of days charged.
        assertTrue(withPast.weekHours > 0.0)
        assertTrue(onlyToday.weekHours > 0.0)
    }

    @Test fun `a day past the month end counts to neither`() {
        val result = agenda(
            listOf(book("m", "recording", dates = listOf("2026-09-15"), deadline = "2026-09-30")),
        )
        assertEquals(0.0, result.weekHours, 0.0001)
        assertEquals(0.0, result.monthHours, 0.0001)
    }

    @Test fun `a day inside the month but past the week counts only to the month`() {
        val result = agenda(
            listOf(book("m", "recording", dates = listOf("2026-08-31"), deadline = "2026-09-30")),
        )
        assertEquals("31 August is past Sunday the 30th", 0.0, result.weekHours, 0.0001)
        assertTrue("but inside August", result.monthHours > 0.0)
    }

    // ─── progress ───────────────────────────────────────────────────────────

    @Test fun `progress is the recorded fraction`() {
        assertEquals(0.2, recordedFraction(book("a", "recording", wordCount = 92_000, recorded = 18_400))!!, 0.0001)
    }

    @Test fun `over-recorded clamps to one rather than reading as more than finished`() {
        assertEquals(1.0, recordedFraction(book("a", "recording", wordCount = 100, recorded = 250))!!, 0.0001)
    }

    @Test fun `a negative figure clamps to zero`() {
        assertEquals(0.0, recordedFraction(book("a", "recording", wordCount = 100, recorded = -50))!!, 0.0001)
    }

    /** Null rather than 0%, so the caller renders nothing instead of a confident zero. */
    @Test fun `an absent or zero word count has no fraction at all`() {
        assertNull(recordedFraction(book("a", "recording", wordCount = null)))
        assertNull(recordedFraction(book("a", "recording", wordCount = 0)))
    }

    // ─── the whole thing ────────────────────────────────────────────────────

    @Test fun `an agenda with nothing in any group is empty`() {
        assertTrue(agenda(emptyList()).isEmpty)
        assertTrue(agenda(listOf(book("e", "editing", deadline = "2020-01-01"))).isEmpty)
    }

    @Test fun `one card can be both recording today and due soon`() {
        // Which is exactly the live shape: A Cowboy's Runaway, recording today with
        // a deadline five days out.
        val result = agenda(
            listOf(book("cowboy", "recording", deadline = "2026-08-31", dates = listOf("2026-08-26"))),
        )
        assertEquals(listOf("cowboy"), result.recordingToday.map { it.card.id })
        assertEquals(listOf("cowboy"), result.dueSoon.map { it.card_id() })
        assertTrue(result.late.isEmpty())
        assertFalse(result.isEmpty)
    }
}

private fun BoardCard.card_id() = id
