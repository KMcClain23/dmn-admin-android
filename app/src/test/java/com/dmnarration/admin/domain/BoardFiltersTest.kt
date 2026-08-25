package com.dmnarration.admin.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bucketing, sorting and the due-soon chips.
 *
 * Two behaviours here look wrong on a device and are not: an overdue card sits
 * in *This Week*, and an editing card never matches a chip however close its
 * deadline is. Both have a test that says so in its name, so a future reader
 * who "fixes" one has to delete an assertion that explains why they shouldn't.
 */
class BoardFiltersTest {

    private val today = LocalDate.parse("2026-08-25")

    // ─── pipelineBucketFor ──────────────────────────────────────────────────

    @Test fun `only an undated card goes to Later`() {
        assertEquals(PipelineBucket.LATER, pipelineBucketFor(card(deadline = null), today))
    }

    @Test fun `an overdue card belongs in This Week, not Later`() {
        // days = -36 satisfies `days <= 7`. Deliberate: an overdue book is the
        // most urgent thing on the board. Live data has none today; the three
        // Later cards are the undated ones.
        val overdue = card(deadline = "2026-07-20")
        assertEquals(-36, daysUntil(LocalDate.parse("2026-07-20"), today))
        assertEquals(PipelineBucket.THIS_WEEK, pipelineBucketFor(overdue, today))
    }

    @Test fun `bucket boundaries are inclusive at 7 and 30`() {
        assertEquals(PipelineBucket.THIS_WEEK, pipelineBucketFor(card(deadline = "2026-08-25"), today)) // 0
        assertEquals(PipelineBucket.THIS_WEEK, pipelineBucketFor(card(deadline = "2026-09-01"), today)) // 7
        assertEquals(PipelineBucket.THIS_MONTH, pipelineBucketFor(card(deadline = "2026-09-02"), today)) // 8
        assertEquals(PipelineBucket.THIS_MONTH, pipelineBucketFor(card(deadline = "2026-09-24"), today)) // 30
        assertEquals(PipelineBucket.LATER, pipelineBucketFor(card(deadline = "2026-09-25"), today)) // 31
    }

    // ─── compareCards ───────────────────────────────────────────────────────

    @Test fun `earlier deadlines lead and undated cards trail`() {
        val soon = card(id = "soon", deadline = "2026-08-26")
        val later = card(id = "later", deadline = "2026-12-01")
        val undated = card(id = "undated", deadline = null)
        val sorted = listOf(undated, later, soon).sortedWith(::compareCards).map { it.id }
        assertEquals(listOf("soon", "later", "undated"), sorted)
    }

    @Test fun `a deadline tie breaks to the newest card`() {
        val older = card(id = "older", deadline = "2026-09-01", createdAt = "2026-01-01T00:00:00Z")
        val newer = card(id = "newer", deadline = "2026-09-01", createdAt = "2026-08-01T00:00:00Z")
        assertEquals(listOf("newer", "older"), listOf(older, newer).sortedWith(::compareCards).map { it.id })
    }

    @Test fun `two undated cards still order by newest`() {
        val older = card(id = "older", createdAt = "2026-01-01T00:00:00Z")
        val newer = card(id = "newer", createdAt = "2026-08-01T00:00:00Z")
        assertEquals(listOf("newer", "older"), listOf(older, newer).sortedWith(::compareCards).map { it.id })
    }

    // ─── passesDateFilter ───────────────────────────────────────────────────

    @Test fun `no filter passes everything, including undated cards`() {
        assertTrue(passesDateFilter(card(deadline = null), null, today))
        assertTrue(passesDateFilter(card(status = "editing"), null, today))
    }

    @Test fun `an editing card never matches a chip, however close its deadline`() {
        // Once a book is in editing the deadline belongs to the editor. It still
        // renders in its own subgroup; it just cannot be highlighted as due soon.
        val tomorrow = card(status = "editing", deadline = "2026-08-26")
        assertFalse(passesDateFilter(tomorrow, DateFilter.WEEK, today))
        assertFalse(passesDateFilter(tomorrow, DateFilter.MONTH, today))
    }

    @Test fun `the three attention statuses do match`() {
        for (s in listOf("contracted", "prepping", "recording")) {
            assertTrue(s, passesDateFilter(card(status = s, deadline = "2026-08-26"), DateFilter.WEEK, today))
        }
    }

    @Test fun `week and month chips have different reaches`() {
        val inTwentyDays = card(status = "recording", deadline = "2026-09-14")
        assertFalse(passesDateFilter(inTwentyDays, DateFilter.WEEK, today))
        assertTrue(passesDateFilter(inTwentyDays, DateFilter.MONTH, today))
    }

    @Test fun `an undated card matches no chip`() {
        assertFalse(passesDateFilter(card(deadline = null), DateFilter.WEEK, today))
        assertFalse(passesDateFilter(card(deadline = null), DateFilter.MONTH, today))
    }

    // ─── tab split and grouping ─────────────────────────────────────────────

    @Test fun `production is the three in-production statuses and pipeline is the rest`() {
        assertTrue(isProduction(card(status = "prepping")))
        assertTrue(isProduction(card(status = "recording")))
        assertTrue(isProduction(card(status = "editing")))
        assertTrue(isPipeline(card(status = "contracted")))
    }

    @Test fun `an unrecognised status lands in Pipeline rather than vanishing`() {
        // The negative test is why. A card that passed the query but matched no
        // tab would exist and be invisible, which is the worst of both.
        assertTrue(isPipeline(card(status = "something_new")))
    }

    @Test fun `every bucket is present even when empty`() {
        val buckets = bucketPipeline(listOf(card(deadline = null)), today)
        assertEquals(PipelineBucket.entries.toSet(), buckets.keys)
        assertEquals(1, buckets[PipelineBucket.LATER]!!.size)
        assertTrue(buckets[PipelineBucket.THIS_WEEK]!!.isEmpty())

        val production = bucketProduction(listOf(card(status = "recording")))
        assertEquals(ProductionSubgroup.entries.toSet(), production.keys)
        assertEquals(1, production[ProductionSubgroup.RECORDING]!!.size)
        assertTrue(production[ProductionSubgroup.EDITING]!!.isEmpty())
    }

    @Test fun `cards inside a bucket are sorted`() {
        val a = card(id = "a", deadline = "2026-09-20")
        val b = card(id = "b", deadline = "2026-09-10")
        val buckets = bucketPipeline(listOf(a, b), today)
        assertEquals(listOf("b", "a"), buckets[PipelineBucket.THIS_MONTH]!!.map { it.id })
    }
}
