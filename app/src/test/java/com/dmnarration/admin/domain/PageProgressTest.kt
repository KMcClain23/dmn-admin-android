package com.dmnarration.admin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Page progress: the percentage always, the page line only when there are pages.
 *
 * DoD 7 in one file. A book WITH total_pages shows the page line; one WITHOUT
 * shows the percentage alone and no empty page row — and the second half is the
 * one worth a test, because an empty row is the kind of thing that renders as a
 * stray separator nobody notices in a screenshot.
 *
 * The numbers come from A Cowboy's Runaway, which is Dean's own data rather
 * than a value constructed to make the test pass: 92,000 words, duet, page 132
 * of 259, and the database derived words_recorded 23,444 from exactly that.
 */
class PageProgressTest {

    private fun book(
        wordCount: Int? = null,
        wordsRecorded: Int? = null,
        totalPages: Int? = null,
        currentPage: Int? = null,
        format: String? = "duet",
    ) = card(
        wordCount = wordCount,
        format = format,
        wordsRecorded = wordsRecorded,
        totalPages = totalPages,
        currentPage = currentPage,
    )

    @Test fun `pages give the fraction when they are set`() {
        val c = book(wordCount = 92000, wordsRecorded = 23444, totalPages = 259, currentPage = 132)
        assertEquals(132.0 / 259, progressFraction(c)!!, 1e-9)
        assertEquals("page 132 of 259", pageLine(c))
    }

    @Test fun `pages and words agree where both exist`() {
        // apply_card_rules derives words_recorded as
        // word_count x share x (current_page / total_pages), so the two ratios
        // are the same number. If this ever fails, the trigger and the display
        // have diverged — which is the whole reason the rule lives in one place.
        val c = book(wordCount = 92000, wordsRecorded = 23444, totalPages = 259, currentPage = 132)
        assertEquals(recordedFraction(c)!!, progressFraction(c)!!, 1e-3)
    }

    @Test fun `a book with no pages shows the percentage and NO page line`() {
        val c = book(wordCount = 90000, wordsRecorded = 22500, totalPages = null, currentPage = null)
        assertEquals(0.5, progressFraction(c)!!, 1e-9)
        // The second half of DoD 7. Null means the caller renders nothing at
        // all, rather than an empty row that reads as a missing value.
        assertNull(pageLine(c))
    }

    @Test fun `a book with pages but no word count still has progress`() {
        // Hexes & Heartbreakers. recordedFraction gives up entirely when the
        // word count is 0, so before this a book genuinely part-narrated showed
        // no progress at all. Its pages say exactly where it is.
        val c = book(wordCount = 0, wordsRecorded = 0, totalPages = 300, currentPage = 150)
        assertNull("words alone cannot answer for this book", recordedFraction(c))
        assertEquals(0.5, progressFraction(c)!!, 1e-9)
        assertEquals("page 150 of 300", pageLine(c))
    }

    @Test fun `a book with neither shows nothing`() {
        val c = book(wordCount = 0, wordsRecorded = 0)
        // Not 0%. Zero asserts that nothing has been recorded; null says nobody
        // knows, and those are different claims about a book.
        assertNull(progressFraction(c))
        assertNull(pageLine(c))
    }

    @Test fun `total_pages without current_page is not progress`() {
        // A page count entered before recording starts. Falls back to words
        // rather than claiming page 0, which would read as 0% having been told
        // something it was not told.
        val c = book(wordCount = 90000, wordsRecorded = 22500, totalPages = 320, currentPage = null)
        assertEquals(0.5, progressFraction(c)!!, 1e-9)
        assertNull(pageLine(c))
    }

    @Test fun `a zero total_pages does not divide`() {
        val c = book(wordCount = 90000, wordsRecorded = 22500, totalPages = 0, currentPage = 0)
        assertEquals(0.5, progressFraction(c)!!, 1e-9)
        assertNull(pageLine(c))
    }

    @Test fun `a page past the end clamps rather than exceeding 100 percent`() {
        val c = book(wordCount = 92000, wordsRecorded = 46000, totalPages = 259, currentPage = 300)
        assertEquals(1.0, progressFraction(c)!!, 1e-9)
    }

    @Test fun `page zero is a real answer, not a missing one`() {
        // Recording started, nothing done. 0% is correct here BECAUSE a page
        // was entered — the distinction the null cases above are protecting.
        val c = book(wordCount = 92000, wordsRecorded = 0, totalPages = 259, currentPage = 0)
        assertEquals(0.0, progressFraction(c)!!, 1e-9)
        assertEquals("page 0 of 259", pageLine(c))
    }
}
