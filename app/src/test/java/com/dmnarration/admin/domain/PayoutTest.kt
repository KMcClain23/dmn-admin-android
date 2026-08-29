package com.dmnarration.admin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two rules governing the estimated figure, and the payout's own shape.
 *
 * Numbers are Dean's real ones. A Cowboy's Runaway: 92,000 words, editor rate
 * $50/pfh, divisor 9,400. 92000/9400 = 9.787h x $50 = $489.36, stored as $489 —
 * so the payouts are formula-derived and ROUNDED, not exact and not
 * hand-entered.
 */
class PayoutTest {

    private val DIVISOR = 9400

    @Test fun `expected income uses the same formula the payouts round to`() {
        // The editor's side, from the payout's own rate.
        assertEquals(489.36, expectedIncome(92000, 50.0, DIVISOR)!!, 0.01)
        // Dean's side, from the card's pfh_rate.
        assertEquals(2104.26, expectedIncome(92000, 215.0, DIVISOR)!!, 0.01)
    }

    @Test fun `every stored payout equals the formula rounded to the dollar`() {
        // All nine, so a change to any of them shows up here rather than as a
        // quietly different total. Pairs are (word_count, rate_pfh, stored).
        val rows = listOf(
            Triple(120000, 50.0, 638.0),   // With a Broken Wing
            Triple(92000, 50.0, 489.0),    // A Cowboy's Runaway
            Triple(138000, 50.0, 734.0),   // All the Ways I'd Die for You
            Triple(184221, 50.0, 980.0),   // All the Ways I'd Kill for You
            Triple(66189, 100.0, 704.0),   // Joy Ride
            Triple(80613, 50.0, 429.0),    // Ruined
            Triple(89000, 50.0, 473.0),    // Sweetening the Deal
            Triple(107885, 50.0, 574.0),   // The Wolf King's Bride
            Triple(55803, 50.0, 297.0),    // Underworld Vows
        )
        for ((words, rate, stored) in rows) {
            val computed = expectedIncome(words, rate, DIVISOR)!!
            assertEquals(
                "words=$words rate=$rate computed=$computed",
                stored,
                Math.round(computed).toDouble(),
                0.0,
            )
        }
    }

    @Test fun `a stored amount_expected WINS over the computed one`() {
        // RULE (a). Two representations of one figure is two sources for one
        // number in the UI layer, and the computed one must never override a
        // figure somebody entered.
        val w = paymentWorth(amountExpected = 1200.0, wordCount = 92000, pfhRate = 215.0, wordsPerFinishedHour = DIVISOR)!!
        assertEquals(1200.0, w.amount, 0.0)
        assertFalse("a stored figure is not an estimate", w.isEstimate)
    }

    @Test fun `the computed figure appears only where the stored one is absent`() {
        val w = paymentWorth(amountExpected = null, wordCount = 92000, pfhRate = 215.0, wordsPerFinishedHour = DIVISOR)!!
        assertEquals(2104.26, w.amount, 0.01)
        // RULE (b). It must be VISIBLY an estimate; the flag is what the UI
        // hangs the "~" and the word "estimate" on.
        assertTrue("a computed figure must be marked an estimate", w.isEstimate)
    }

    @Test fun `no word count renders NOTHING, not zero`() {
        // RULE (b), second half. Zero would assert the work is worth nothing;
        // null says nobody has entered a length. Different claims about a book.
        assertNull(expectedIncome(null, 215.0, DIVISOR))
        assertNull(expectedIncome(0, 215.0, DIVISOR))
        assertNull(paymentWorth(null, null, 215.0, DIVISOR))
        assertNull(paymentWorth(null, 0, 215.0, DIVISOR))
    }

    @Test fun `a missing rate or divisor also renders nothing`() {
        assertNull(expectedIncome(92000, null, DIVISOR))
        assertNull(expectedIncome(92000, 0.0, DIVISOR))
        // The divisor comes from site_settings and can be unreadable — Stage 7
        // exists because of exactly that.
        assertNull(expectedIncome(92000, 215.0, null))
        assertNull(expectedIncome(92000, 215.0, 0))
    }

    @Test fun `an unpaid payout is pending, not overdue`() {
        val pending = Payout(
            id = "1", paymentId = "p", payeeName = "Marizete", kind = "editor",
            amount = 489.0, paidOn = null, ratePfh = 50.0, paidVia = "", notes = "",
        )
        assertFalse(pending.isPaid)
        // Empty renders as nothing — no label, no dash placeholder.
        assertTrue(pending.paidVia.isEmpty())
        assertTrue(pending.notes.isEmpty())
    }

    @Test fun `the summary carries the pair, never a bare liability`() {
        val s = PayoutSummary(
            expectedIn = 24142.56, committedOut = 4680.0, net = 19462.56,
            unpaidCount = 8, paidCount = 1, booksWithoutWordCount = 0,
        )
        // net is the difference, so a caller cannot render "owed" alone and
        // have the arithmetic still make sense.
        assertEquals(s.expectedIn - s.committedOut, s.net, 0.01)
        assertTrue("a derived figure is always an estimate", s.expectedIsEstimate)
    }
}
