package com.dmnarration.admin.domain

import kotlinx.datetime.LocalDate

/**
 * A payment row: money that has MOVED.
 *
 * This type deliberately cannot answer "what am I owed", and neither can the
 * screen built on it. That is a decision, not a gap — see [OUTSTANDING_NOT_COMPUTED].
 *
 * The six actionable-money columns are absent from the wire shape as well as
 * from here: both payment links, both processor ids, the links-closed timestamp
 * and the stored invoice draft. A read-only screen needs none of them, and a
 * live payment URL is the last thing that should travel to a phone.
 */
data class Payment(
    override val id: String,
    val cardId: String,
    val label: String,
    val kind: String,
    val period: String,
    /** Null on 16 of the 17 fee rows: the figure was never entered, not zero. */
    val amountExpected: Double?,
    /** The whole client-side fee where it differs from the narrator's share. */
    val amountGross: Double?,
    /** NOT NULL in Postgres. This is the stored fact the screen exists to show. */
    val amountReceived: Double,
    val dueOn: LocalDate?,
    val invoicedOn: LocalDate?,
    val receivedOn: LocalDate?,
    val invoiceNumber: String?,
    val method: String?,
    val notes: String?,
    val sortOrder: Int,
) : Identified

/**
 * An expense, as stored.
 *
 * `scheduleC` is a tax category and travels verbatim. Nothing here interprets,
 * groups or totals by it: a tax figure the app invented would be worse than no
 * tax figure, and this app has no business being the thing that files wrong.
 */
data class Expense(
    override val id: String,
    val incurredOn: LocalDate?,
    val vendor: String,
    val description: String,
    val amount: Double,
    val label: String?,
    val scheduleC: String?,
    val method: String?,
    val notes: String?,
    val source: String?,
) : Identified

/**
 * What this screen does not do, said in one place so every surface says it the same.
 *
 * The web computes what is outstanding from 16 functions and 683 lines across
 * three tables — the card's word count and finished-hour rate, the payment rows,
 * and the payouts table — with rules that are genuinely subtle: royalty rows
 * excluded so entering a statement does not inflate a forecast, recast work going
 * deliberately silent, a cent of tolerance so numeric(10,2) rounding does not
 * strand a settled invoice, off-the-top payouts.
 *
 * None of it is ported here, and the deciding reason is not the size. Nothing is
 * outstanding today — no payment row anywhere has `amount_received <
 * amount_expected` — so a correct port and a broken one would render the same
 * $0.00 on every project, and **no data exists that would tell them apart**.
 *
 * A second implementation of Dean's money, unvalidatable against the first, is
 * the worst trade available. If the figure is ever wanted it gets built once, as
 * shared logic with tests.
 *
 * This is a SENTENCE on the screen, not a blank where a figure would be. An
 * absence has to be legible as a decision rather than as a gap, which is the
 * whole lesson of the stage before this one.
 */
const val OUTSTANDING_NOT_COMPUTED: String =
    "This screen shows money received, not money owed. Outstanding amounts are " +
        "worked out on the web from the project rate and are not calculated here."

/** Received across every row — a sum of stored facts, owing nothing to any rate. */
fun totalReceived(payments: List<Payment>): Double =
    payments.sumOf { it.amountReceived }

/** One line of the breakdown: a year, or the rows that have no date at all. */
data class ReceivedBucket(val label: String, val amount: Double, val count: Int)

/**
 * The total and its breakdown, from one pass, with the total DERIVED FROM the
 * buckets rather than computed beside them.
 *
 * This shape exists because the first version got it wrong in a way that looked
 * right. It offered `totalReceived` and a per-year figure as two independent
 * functions, and the screen showed
 *
 *     RECEIVED  $6,844.98  across 24 payments
 *       2026    $6,716.08
 *       2025    $8.90
 *
 * where the years sum to $6,724.98. Eight rows — a third of the table — carry an
 * `amount_received` with `received_on` null, so they sat in the total and in
 * neither year. Nothing was wrong with any individual number; the breakdown just
 * did not account for its own total, and a reader has no way to tell a gap like
 * that from a rounding they should ignore.
 *
 * `received_on` is nullable while `amount_received` is NOT NULL, so money with no
 * date is a shape the schema positively allows. Dropping those rows from the
 * total instead would understate what Dean has been paid by $120 and silently
 * hide eight payments, which is the worse of the two errors.
 *
 * The invariant is structural, not asserted after the fact: `total` is the sum of
 * `buckets`, so a bucket that is forgotten cannot leave the total unchanged — it
 * changes the total instead, which is visible. `MoneyTest` pins it against
 * `totalReceived` so the two public paths cannot diverge either.
 */
data class ReceivedBreakdown(val total: Double, val buckets: List<ReceivedBucket>) {
    val count: Int get() = buckets.sumOf { it.count }
}

/** Undated last: it is a caveat on the history, not a period in it. */
const val NO_DATE_BUCKET = "No date recorded"

fun receivedBreakdown(payments: List<Payment>): ReceivedBreakdown {
    val byYear = payments
        .filter { it.receivedOn != null }
        .groupBy { it.receivedOn!!.year }
        .toSortedMap(compareByDescending { it })
        .map { (year, rows) ->
            ReceivedBucket("$year", rows.sumOf { it.amountReceived }, rows.size)
        }

    val undated = payments.filter { it.receivedOn == null }
    val buckets =
        if (undated.isEmpty()) byYear
        else byYear + ReceivedBucket(
            NO_DATE_BUCKET,
            undated.sumOf { it.amountReceived },
            undated.size,
        )

    return ReceivedBreakdown(total = buckets.sumOf { it.amount }, buckets = buckets)
}

/** Total spend. Expenses are stored amounts; nothing here is derived. */
fun totalExpenses(expenses: List<Expense>): Double =
    expenses.sumOf { it.amount }

/**
 * "Royalty" / "Fee", falling back to the stored value.
 *
 * An unrecognised kind renders raw rather than as one of the two known ones —
 * the same rule as the archive reason. The stored string is evidence that
 * something wrote a value this app does not know about.
 */
fun paymentKindLabel(kind: String): String = when (kind) {
    "fee" -> "Fee"
    "royalty" -> "Royalty"
    else -> kind
}

/**
 * A payment's own description, for a row that often has no label at all.
 *
 * `label` is NOT NULL but empty on several rows — His For Christmas's fee row
 * among them — so falling back to the kind is the difference between a row with
 * a name and a row with a blank space where one should be.
 */
fun paymentTitle(payment: Payment): String =
    payment.label.takeIf { it.isNotBlank() }
        ?: paymentKindLabel(payment.kind).let { kind ->
            payment.period.takeIf { it.isNotBlank() }?.let { "$kind · $it" } ?: kind
        }
