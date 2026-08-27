package com.dmnarration.admin.data

import android.util.Log
import com.dmnarration.admin.domain.Expense
import com.dmnarration.admin.domain.Payment
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

private const val TAG = "MoneyDto"

/**
 * A Postgres `date` to a LocalDate, with no timezone anywhere near it.
 *
 * Its own function rather than reusing BoardDto's: that one is private, and
 * widening it collided with CardDetailDto's identically-named private copy —
 * three files in one package cannot all export `date`. Named for what it
 * returns instead.
 *
 * Bad values are dropped rather than thrown, and logged rather than swallowed:
 * a payment with an unreadable received_on should render as a payment with no
 * date, not take the screen down — but somebody should be able to find out.
 */
internal fun localDate(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(raw.take(10)) }
        .onFailure { Log.w(TAG, "unparseable date '$raw'", it) }
        .getOrNull()
}

/**
 * The wire shapes for `payments_for_session()` and `expenses_for_session()`,
 * named exactly as the columns are, for the same reason `BoardCardDto` is.
 *
 * Neither DTO has a field for anything the function does not return. The six
 * actionable-money columns are absent from the function's return type, so there
 * is nowhere here for a payment link to land even if one were added upstream —
 * the type would have to change first, deliberately.
 *
 * `has_receipt` is likewise absent: `receipt_url` is an empty string on all 21
 * expense rows, so the function stopped returning a field about a thing that
 * does not exist. When receipts arrive, the field and the indicator come back
 * together with the signed-URL work they need.
 */
@Suppress("PropertyName")
@Serializable
data class PaymentDto(
    val id: String,
    val card_id: String = "",
    val label: String = "",
    val kind: String = "",
    val period: String = "",
    val amount_expected: Double? = null,
    val amount_gross: Double? = null,
    /** NOT NULL in Postgres, defaulted here for an absent key, never for a null. */
    val amount_received: Double = 0.0,
    val due_on: String? = null,
    val invoiced_on: String? = null,
    val received_on: String? = null,
    val invoice_number: String? = null,
    val method: String? = null,
    val notes: String? = null,
    val sort_order: Int = 0,
)

@Suppress("PropertyName")
@Serializable
data class ExpenseDto(
    val id: String,
    val incurred_on: String? = null,
    val vendor: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val label: String? = null,
    val schedule_c: String? = null,
    val method: String? = null,
    val notes: String? = null,
    val source: String? = null,
)

/**
 * The three date columns are Postgres `date`, not `timestamptz`, so they go
 * through the same no-timezone-anywhere-near-it parser the board's deadline uses.
 * A payment received on the 20th must not read as the 19th west of Greenwich.
 */
fun PaymentDto.toDomain(): Payment = Payment(
    id = id,
    cardId = card_id,
    label = label,
    kind = kind,
    period = period,
    // NOT `takeIf { it > 0 }`. These columns are nullable with no default, so a
    // null is a figure nobody entered and a zero is a figure someone entered as
    // zero. Collapsing them would be the $367.02 mistake in the other direction.
    amountExpected = amount_expected,
    amountGross = amount_gross,
    amountReceived = amount_received,
    dueOn = localDate(due_on),
    invoicedOn = localDate(invoiced_on),
    receivedOn = localDate(received_on),
    invoiceNumber = invoice_number?.takeIf { it.isNotBlank() },
    method = method?.takeIf { it.isNotBlank() },
    notes = notes?.takeIf { it.isNotBlank() },
    sortOrder = sort_order,
)

fun ExpenseDto.toDomain(): Expense = Expense(
    id = id,
    incurredOn = localDate(incurred_on),
    vendor = vendor,
    description = description,
    amount = amount,
    label = label?.takeIf { it.isNotBlank() },
    scheduleC = schedule_c?.takeIf { it.isNotBlank() },
    method = method?.takeIf { it.isNotBlank() },
    notes = notes?.takeIf { it.isNotBlank() },
    source = source?.takeIf { it.isNotBlank() },
)
