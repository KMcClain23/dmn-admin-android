package com.dmnarration.admin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DoD 9 on the client side: the three categories account for every non-archived
 * book, and a total that does not is not displayed.
 *
 * The categories are decided by career_totals_for_session(), which asserts the
 * same partition and RAISES rather than returning a short total. This is the
 * second half of that: the client refusing to DISPLAY one, so a stale build
 * cannot show a figure the server would have rejected.
 *
 * The numbers are today's real ones — 420,194 exact across 9 books, 23,444
 * estimated across 1, 23 not counted, 33 non-archived — so a change to the
 * shape of the data shows up here as a failure rather than as a quietly
 * different total.
 */
class CareerTotalsTest {

    private fun totals(
        exactWords: Int = 420194,
        exactBooks: Int = 9,
        estimatedWords: Int = 23444,
        estimatedBooks: Int = 1,
        notCountedBooks: Int = 23,
        totalBooks: Int = 33,
    ) = CareerTotals(
        exactWords = exactWords,
        exactBooks = exactBooks,
        estimatedWords = estimatedWords,
        estimatedBooks = estimatedBooks,
        notCountedBooks = notCountedBooks,
        notCountedTitles = List(notCountedBooks) { "Book $it" },
        totalBooks = totalBooks,
    )

    @Test fun `the three categories account for every non-archived book`() {
        val t = totals()
        assertEquals(t.totalBooks, t.exactBooks + t.estimatedBooks + t.notCountedBooks)
        assertTrue(t.partitionHolds)
    }

    @Test fun `dropping a category breaks the partition`() {
        // The mutation named in the DoD. The database raises on this; here it
        // must make partitionHolds false, which is what stops the screen
        // rendering. 420,194 across 9 books is a plausible-looking number, and
        // that is exactly the danger — it looks answered.
        val t = totals(notCountedBooks = 0)
        assertFalse("a total omitting 23 books must not be displayable", t.partitionHolds)
    }

    @Test fun `an over-count breaks the partition too`() {
        // Double-counting a book is as wrong as dropping one, and a check that
        // only tested for "too few" would pass it.
        assertFalse(totals(exactBooks = 10).partitionHolds)
    }

    @Test fun `counted words are exact plus estimated and nothing else`() {
        val t = totals()
        assertEquals(443638, t.countedWords)
        // NOT the whole catalogue: 23 books contribute nothing, and the figure
        // must not quietly imply otherwise.
        assertEquals(t.exactWords + t.estimatedWords, t.countedWords)
    }

    @Test fun `a career with nothing recorded still partitions`() {
        val t = totals(
            exactWords = 0, exactBooks = 0,
            estimatedWords = 0, estimatedBooks = 0,
            notCountedBooks = 33, totalBooks = 33,
        )
        assertTrue(t.partitionHolds)
        assertEquals(0, t.countedWords)
    }

    @Test fun `an empty catalogue partitions rather than dividing by nothing`() {
        val t = totals(0, 0, 0, 0, 0, 0)
        assertTrue(t.partitionHolds)
        assertEquals(0, t.countedWords)
    }
}
