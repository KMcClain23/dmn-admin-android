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

    // ---- the breakdown must account for its own total ----

    @Test fun `the buckets always sum to the total`() {
        // THE defect this replaced. The screen showed $6,844.98 over 2026
        // $6,716.08 and 2025 $8.90 — years summing to $6,724.98 — because eight
        // rows carried money with no received_on and sat in neither year.
        val rows = listOf(
            payment("a", 6716.08, receivedOn = "2026-08-20"),
            payment("b", 8.90, receivedOn = "2025-11-02"),
            payment("c", 120.00, receivedOn = null),
        )
        val b = receivedBreakdown(rows)

        assertEquals(6844.98, b.total, 0.001)
        assertEquals(
            "the parts must sum to the whole",
            b.total,
            b.buckets.sumOf { it.amount },
            0.001,
        )
        assertEquals("and every row must be counted once", rows.size, b.count)
    }

    @Test fun `the breakdown total agrees with totalReceived`() {
        // Two public paths to the same figure. Pinned together so a change to
        // one cannot silently disagree with the other.
        val rows = listOf(
            payment("a", 100.0, receivedOn = "2026-01-05"),
            payment("b", 200.0, receivedOn = "2025-12-31"),
            payment("c", 400.0, receivedOn = null),
            payment("d", 0.0, receivedOn = null),
        )
        assertEquals(totalReceived(rows), receivedBreakdown(rows).total, 0.001)
    }

    @Test fun `undated money gets its own line, labelled and last`() {
        val rows = listOf(
            payment("a", 100.0, receivedOn = "2026-01-05"),
            payment("b", 200.0, receivedOn = "2025-12-31"),
            payment("c", 400.0, receivedOn = null),
        )
        val b = receivedBreakdown(rows)

        assertEquals(listOf("2026", "2025", NO_DATE_BUCKET), b.buckets.map { it.label })
        assertEquals(400.0, b.buckets.last().amount, 0.001)
        assertEquals(1, b.buckets.last().count)
    }

    @Test fun `no undated rows means no undated line`() {
        // An empty "No date recorded — $0.00" row would be a control that fires
        // on nothing, and would read as a defect rather than as reassurance.
        val rows = listOf(payment("a", 100.0, receivedOn = "2026-01-05"))
        val b = receivedBreakdown(rows)

        assertEquals(listOf("2026"), b.buckets.map { it.label })
        assertTrue(b.buckets.none { it.label == NO_DATE_BUCKET })
    }

    @Test fun `years run newest first and a year with no money is absent`() {
        val rows = listOf(
            payment("a", 1.0, receivedOn = "2025-03-01"),
            payment("b", 1.0, receivedOn = "2026-03-01"),
        )
        // 2024 is absent rather than rendered as a zero that reads like a bad year.
        assertEquals(listOf("2026", "2025"), receivedBreakdown(rows).buckets.map { it.label })
    }

    @Test fun `every row undated still reconciles`() {
        val rows = listOf(payment("a", 500.0, receivedOn = null), payment("b", 20.0, receivedOn = null))
        val b = receivedBreakdown(rows)

        assertEquals(listOf(NO_DATE_BUCKET), b.buckets.map { it.label })
        assertEquals(520.0, b.total, 0.001)
        assertEquals(b.total, b.buckets.sumOf { it.amount }, 0.001)
    }

    @Test fun `an empty ledger reconciles to zero rather than throwing`() {
        val b = receivedBreakdown(emptyList())
        assertEquals(0.0, b.total, 0.001)
        assertEquals(0, b.count)
        assertTrue(b.buckets.isEmpty())
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

    // ---- expenses reconcile too, and the year boundary is a tax boundary ----

    @Test fun `expense buckets sum to the expense total`() {
        val rows = listOf(
            expense("a", 19.00, on = "2026-08-24"),
            expense("b", 35.05, on = "2025-09-25"),
            expense("c", 1119.00, on = "2026-08-14"),
        )
        val b = spentBreakdown(rows)
        assertEquals(1173.05, b.total, 0.001)
        assertEquals("the parts must sum to the whole", b.total, b.buckets.sumOf { it.amount }, 0.001)
        assertEquals(listOf("2026", "2025"), b.buckets.map { it.label })
        assertEquals(3, b.count)
    }

    @Test fun `an expense whose date could not be read is still counted`() {
        // incurred_on is NOT NULL, so this should not occur — but the parser
        // answers null for a value it cannot read, and that row still has money
        // in it. Dropping it would understate a tax figure.
        val dated = expense("a", 10.0, on = "2026-01-01")
        val undated = dated.copy(id = "b", incurredOn = null, amount = 5.0)
        val b = spentBreakdown(listOf(dated, undated))
        assertEquals(15.0, b.total, 0.001)
        assertEquals(listOf("2026", NO_DATE_BUCKET), b.buckets.map { it.label })
    }

    // ---- Schedule C: named where known, raw where not ----

    @Test fun `a known slug renders as the tax form names it`() {
        assertEquals("Office expense", scheduleCLabel("office"))
        assertEquals("Legal & professional services", scheduleCLabel("legal_professional"))
        assertEquals("Other expenses", scheduleCLabel("other"))
    }

    @Test fun `all twelve web lines are carried, not only the six in use`() {
        // Copying only what today's data uses would mean the seventh category
        // Dean files renders as a raw slug on the phone and properly on the web.
        assertEquals(12, SCHEDULE_C_LABEL.size)
        for (slug in listOf("advertising", "contract_labor", "insurance", "legal_professional",
                            "office", "rent", "repairs", "supplies", "travel", "meals",
                            "utilities", "other")) {
            assertTrue("$slug must be mapped", SCHEDULE_C_LABEL.containsKey(slug))
        }
    }

    @Test fun `an unmapped slug renders raw and is never prettified`() {
        // "legal_professional" title-cased becomes "Legal Professional" — a label
        // the tax form does not use. A wrong label that looks deliberate is worse
        // than an obviously unmapped one.
        assertEquals("home_office", scheduleCLabel("home_office"))
        assertEquals("vehicle", scheduleCLabel("vehicle"))
        assertNull(scheduleCLabel(null))
        assertNull(scheduleCLabel(""))
    }

    @Test fun `the category line carries both names, in the web's order`() {
        val e = expense("a", 19.0).copy(label = "Software & subscriptions", scheduleC = "office")
        assertEquals("Software & subscriptions · Office expense", expenseCategoryLine(e))
        assertEquals("Office expense", expenseCategoryLine(e.copy(label = null)))
        assertEquals("Software & subscriptions", expenseCategoryLine(e.copy(scheduleC = null)))
        assertNull(expenseCategoryLine(e.copy(label = null, scheduleC = null)))
    }

    // ---- an editable field shows what is stored ----

    @Test fun `a whole setting renders without a trailing decimal`() {
        // The box showed "6.0" while the database held "6", and max_books_per_day
        // — an Int — showed "2". An editable field displaying something other
        // than what is stored is the app not showing what is there.
        assertEquals("6", settingText(6.0))
        assertEquals("4", settingText(4.0))
        assertEquals("2", settingText(2.0))
    }

    @Test fun `a fractional setting keeps its decimals`() {
        // A 6.5-hour day is a real thing Dean can set.
        assertEquals("6.5", settingText(6.5))
        assertEquals("0.5", settingText(0.5))
        assertNull(settingText(null))
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
