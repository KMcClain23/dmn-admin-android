package com.dmnarration.admin.domain

import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Released and Archive domain.
 *
 * The count pair is the point of most of this. Stage 6 stopped on the discovery
 * that the web answers "how many are released" two ways, from two queries with
 * two different archived predicates, agreeing today only because no released
 * book has ever been archived. Here both answers come from one list, so the
 * tests can construct the case the live data cannot currently produce.
 */
class ShelfTest {

    private val t0 = Instant.parse("2026-08-18T20:00:00Z")

    private fun released(
        id: String,
        title: String = id,
        archivedAt: Instant? = null,
        rating: Double? = 4.5,
        reviews: Int? = 10,
    ) = ReleasedBook(
        id = id,
        title = title,
        author = "A",
        coverUrl = null,
        releasedAt = t0,
        amazonRating = rating,
        amazonReviewCount = reviews,
        audibleLink = null,
        archivedAt = archivedAt,
    )

    private fun archived(id: String, at: Instant? = t0, reason: String? = "recasted") =
        ArchivedCard(
            id = id,
            title = id,
            author = "A",
            coverUrl = null,
            archivedAt = at,
            archivedReason = reason,
            archivedNotes = null,
            status = "recording",
        )

    // ---- the two populations ----

    @Test fun `the list shows released books that are not archived`() {
        val all = listOf(released("a"), released("b", archivedAt = t0), released("c"))
        assertEquals(listOf("a", "c"), visibleReleased(all).map { it.id })
    }

    @Test fun `all-time counts archived releases and the visible count does not`() {
        val all = listOf(released("a"), released("b", archivedAt = t0), released("c"))
        val counts = releasedCounts(all)
        assertEquals("all-time includes the archived release", 3, counts.allTime)
        assertEquals("visible excludes it", 2, counts.visible)
        assertFalse("and the screen must say which it means", counts.agree)
    }

    @Test fun `the two counts agree while nothing released has been archived`() {
        // Today's live data: 12 released, none archived. The disagreement is
        // latent, which is exactly why it needs a test rather than an eye.
        val counts = releasedCounts(List(12) { released("id$it") })
        assertEquals(12, counts.allTime)
        assertEquals(12, counts.visible)
        assertTrue(counts.agree)
    }

    @Test fun `one archived release is enough to separate them`() {
        val counts = releasedCounts(List(11) { released("id$it") } + released("x", archivedAt = t0))
        assertEquals(12, counts.allTime)
        assertEquals(11, counts.visible)
        assertFalse(counts.agree)
    }

    // ---- Amazon figures: absent is not zero ----

    @Test fun `an unknown rating renders as nothing, never as zero`() {
        assertNull(ratingLabel(null))
        assertEquals("0.0", ratingLabel(0.0))
    }

    @Test fun `a rating keeps one decimal`() {
        assertEquals("trailing zero kept, as Amazon prints it", "5.0", ratingLabel(5.0))
        assertEquals("4.2", ratingLabel(4.2))
        assertEquals("a second decimal is reduced to one", "4.3", ratingLabel(4.26))
        // Exact halves are deliberately not asserted. 4.25 is 42.499… once it is
        // a Double, so it rounds down, and pinning that here would enshrine a
        // floating-point artefact as intended behaviour. Amazon stores one
        // decimal, so the case does not arise.
    }

    @Test fun `zero reviews and unknown reviews are different facts`() {
        assertNull("unknown says nothing", reviewCountLabel(null))
        assertEquals("zero is something Amazon reported", "No reviews yet", reviewCountLabel(0))
    }

    @Test fun `review counts are pluralised`() {
        assertEquals("1 review", reviewCountLabel(1))
        assertEquals("14 reviews", reviewCountLabel(14))
    }

    // ---- the archive ----

    @Test fun `an optimistically restored card leaves the archive list`() {
        val all = listOf(archived("a"), archived("b", at = null))
        assertEquals(listOf("a"), stillArchived(all).map { it.id })
    }

    @Test fun `a known reason renders as its label`() {
        assertEquals("Recasted", archiveReasonLabel("recasted"))
        assertEquals("Canceled", archiveReasonLabel("canceled"))
    }

    @Test fun `an unrecognised reason is shown, not replaced with Other`() {
        // The stored string is evidence that something wrote a value this app
        // does not know about. Rendering it as "Other" would hide that.
        assertEquals("shelved", archiveReasonLabel("shelved"))
    }

    @Test fun `no reason renders as nothing`() {
        assertNull(archiveReasonLabel(null))
        assertNull(archiveReasonLabel(""))
        assertNull(archiveReasonLabel("   "))
    }

    @Test fun `un-archiving clears all three archive fields`() {
        // Not just the timestamp. No row anywhere carries a reason or notes with
        // a null archived_at, and this app must not be the first to break that.
        assertEquals(
            listOf("archived_at", "archived_reason", "archived_notes"),
            UNARCHIVE_COLUMNS,
        )
    }

    @Test fun `an empty note is stored as nothing, not as an empty note`() {
        // The web writes `archiveNotes.trim() || null`. Android wrote the raw
        // string, so the same action produced '' from the phone and null from
        // the browser in one column.
        assertNull(archiveNotes(""))
        assertNull(archiveNotes("   "))
        assertNull(archiveNotes("\n\t "))
    }

    @Test fun `a real note is trimmed and kept`() {
        assertEquals("recast by the publisher", archiveNotes("  recast by the publisher  "))
        assertEquals("a", archiveNotes("a"))
    }

    // ---- the write reducer, over a row that is not a BoardCard ----

    @Test fun `a refused restore puts the card back exactly as it was`() {
        val card = archived("a", reason = "recasted")
        val (optimistic, pending) = applyOptimistic(listOf(card), "a") { it.copy(archivedAt = null) }
        assertTrue("optimistically gone from the list", stillArchived(optimistic).isEmpty())

        val reduction = reconcileWrite(optimistic, pending!!, WriteOutcome.Refused)
        assertEquals("restored verbatim", listOf(card), reduction.cards)
        assertEquals(
            "You no longer have permission to make that change.",
            reduction.error,
        )
        assertTrue("a refusal means the whole view may have changed", reduction.refresh)
    }

    @Test fun `a failed restore rolls back and does not re-fetch`() {
        val card = archived("a")
        val (optimistic, pending) = applyOptimistic(listOf(card), "a") { it.copy(archivedAt = null) }
        val reduction = reconcileWrite(optimistic, pending!!, WriteOutcome.Failed("No connection."))
        assertEquals(listOf(card), reduction.cards)
        assertEquals("No connection.", reduction.error)
        assertFalse("the request never landed, so what is loaded is still the best there is",
            reduction.refresh)
    }

    @Test fun `a saved restore takes the server's row, not the optimistic guess`() {
        val card = archived("a")
        val (optimistic, pending) = applyOptimistic(listOf(card), "a") { it.copy(archivedAt = null) }
        // The server clears the reason and notes too; the client only guessed
        // at the timestamp.
        val server = card.copy(archivedAt = null, archivedReason = null, archivedNotes = null)
        val reduction = reconcileWrite(optimistic, pending!!, WriteOutcome.Saved(server))
        assertEquals(listOf(server), reduction.cards)
        assertNull(reduction.error)
        assertTrue(stillArchived(reduction.cards).isEmpty())
    }
}
