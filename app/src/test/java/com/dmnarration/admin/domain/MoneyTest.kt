package com.dmnarration.admin.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The money domain — every figure a sum of stored amounts, nothing derived.
 *
 * The absences are as much the subject as the sums. `amountExpected` stays
 * nullable and is never collapsed to zero, because the claim that started this
 * stage — "His For Christmas carries a live $367.02" — came from reading
 * `amount_expected` without `amount_received` beside it, and from a later check
 * that returned the contradicting number being read as a confirmation.
 */
class MoneyTest {

    private fun payment(
        id: String,
        received: Double,
        receivedOn: String? = "2026-08-20",
        expected: Double? = null,
        kind: String = "fee",
        label: String = "",
        period: String = "",
    ) = Payment(
        id = id,
        cardId = "c",
        label = label,
        kind = kind,
        period = period,
        amountExpected = expected,
        amountGross = null,
        amountReceived = received,
        dueOn = null,
        invoicedOn = null,
        receivedOn = receivedOn?.let { LocalDate.parse(it) },
        invoiceNumber = null,
        method = "Card",
        notes = null,
        sortOrder = 0,
    )

    private fun expense(id: String, amount: Double, on: String = "2026-08-24") = Expense(
        id = id,
        incurredOn = LocalDate.parse(on),
        vendor = "V",
        description = "d",
        amount = amount,
        label = null,
        scheduleC = "Office expense",
        method = null,
        notes = null,
        source = "email",
    )

    // ---- received ----

    @Test fun `received is the sum of what actually arrived`() {
        val rows = listOf(payment("a", 367.02), payment("b", 1000.0), payment("c", 0.0))
        assertEquals(1367.02, totalReceived(rows), 0.001)
    }

    @Test fun `a year total counts only rows received in that year`() {
        val rows = listOf(
            payment("a", 100.0, receivedOn = "2026-01-05"),
            payment("b", 200.0, receivedOn = "2025-12-31"),
            payment("c", 400.0, receivedOn = null),
        )
        assertEquals(100.0, receivedInYear(rows, 2026), 0.001)
        assertEquals(200.0, receivedInYear(rows, 2025), 0.001)
    }

    @Test fun `only years with money in them are listed, newest first`() {
        val rows = listOf(
            payment("a", 1.0, receivedOn = "2025-03-01"),
            payment("b", 1.0, receivedOn = "2026-03-01"),
            payment("c", 1.0, receivedOn = null),
        )
        // 2024 is absent rather than rendered as a zero that reads like a bad year,
        // and the undated row contributes no year at all rather than defaulting.
        assertEquals(listOf(2026, 2025), yearsWithPayments(rows))
    }

    @Test fun `a row with money and no date still counts toward the total`() {
        // amount_received is NOT NULL and defaults to 0, so a row can carry money
        // with no received_on. Dropping it from the total would understate what
        // Dean has been paid — a partial sum wearing the label of a full one.
        val rows = listOf(payment("a", 500.0, receivedOn = null))
        assertEquals(500.0, totalReceived(rows), 0.001)
        assertTrue(yearsWithPayments(rows).isEmpty())
    }

    // ---- expected is never collapsed ----

    @Test fun `an unentered expected figure stays null and is not zero`() {
        val unentered = payment("a", 0.0, expected = null)
        val enteredAsZero = payment("b", 0.0, expected = 0.0)
        assertNull(unentered.amountExpected)
        assertEquals(0.0, enteredAsZero.amountExpected!!, 0.001)
        assertFalse(
            "null and 0.0 must not compare equal — 16 of 17 fee rows are null",
            unentered.amountExpected == enteredAsZero.amountExpected,
        )
    }

    // ---- labels ----

    @Test fun `a blank label falls back to the kind rather than a blank row`() {
        // His For Christmas's fee row has label = "". A row with an empty name is
        // a row that looks broken.
        assertEquals("Fee", paymentTitle(payment("a", 1.0, label = "")))
        assertEquals("Royalty · Q1", paymentTitle(payment("b", 1.0, kind = "royalty", period = "Q1")))
        assertEquals("Delivery", paymentTitle(payment("c", 1.0, label = "Delivery")))
    }

    @Test fun `an unrecognised kind renders as stored`() {
        assertEquals("Fee", paymentKindLabel("fee"))
        assertEquals("Royalty", paymentKindLabel("royalty"))
        // Evidence that something wrote a value this app does not know about.
        assertEquals("bonus", paymentKindLabel("bonus"))
    }

    // ---- expenses ----

    @Test fun `expenses total is a plain sum of stored amounts`() {
        assertEquals(54.05, totalExpenses(listOf(expense("a", 19.0), expense("b", 35.05))), 0.001)
    }

    // ---- who the screens exist for ----

    @Test fun `only an admin has money screens at all`() {
        // The bottom bar filters its destinations on exactly this flag, so an
        // editor's tabs are ABSENT rather than disabled or empty. A disabled tab
        // advertises a room the account may not enter; an empty one claims there
        // is no money.
        assertTrue(Capabilities.of(UserRole.ADMIN).canSeeMoney)
        assertFalse(Capabilities.of(UserRole.EDITOR).canSeeMoney)
        assertFalse("an unrecognised role fails closed", Capabilities.of(UserRole.UNKNOWN).canSeeMoney)
    }

    @Test fun `seeing a card's earnings is a different question from seeing the ledger`() {
        // Same answer for every role today, deliberately two flags. Merging them
        // would mean a future role allowed to see a rate on a card would silently
        // gain the whole payments history.
        val admin = Capabilities.of(UserRole.ADMIN)
        assertTrue(admin.canViewFinancials)
        assertTrue(admin.canSeeMoney)
    }

    // ---- the sentence ----

    @Test fun `the screen states what it does not compute`() {
        // A sentence, not a blank. This is asserted because the whole scope
        // decision rests on the absence being legible as a decision, and a
        // constant nobody renders would make that claim untrue and untestable.
        assertTrue(OUTSTANDING_NOT_COMPUTED.contains("not"))
        assertTrue(OUTSTANDING_NOT_COMPUTED.contains("owed"))
        assertTrue(OUTSTANDING_NOT_COMPUTED.isNotBlank())
    }
}
