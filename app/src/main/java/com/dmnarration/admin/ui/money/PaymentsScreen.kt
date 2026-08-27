package com.dmnarration.admin.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dmnarration.admin.domain.OUTSTANDING_NOT_COMPUTED
import com.dmnarration.admin.domain.Payment
import com.dmnarration.admin.domain.paymentKindLabel
import com.dmnarration.admin.domain.paymentTitle
import com.dmnarration.admin.domain.NO_DATE_BUCKET
import com.dmnarration.admin.domain.receivedBreakdown
import com.dmnarration.admin.ui.board.PullToRefreshSurface
import com.dmnarration.admin.ui.board.ScrollableContent
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * What Dean has been paid.
 *
 * Deliberately NOT what he is owed. That sentence is on the screen, near the
 * top, because an absence has to be legible as a decision rather than as a gap —
 * a reader who sees only received totals and no outstanding line would
 * reasonably conclude nothing is outstanding, and this screen has no way of
 * knowing whether that is true.
 *
 * Every figure here is a stored amount summed. Nothing divides by a rate, so
 * nothing on this screen can be affected by a setting that could not be read —
 * which is why there is no Stage 7 gating anywhere in it.
 */
@Composable
fun PaymentsScreen(
    state: MoneyState,
    onRefresh: () -> Unit,
) {
    val c = DmnTheme.colors

    Column {
        state.paymentsError?.let {
            Text(
                it,
                style = DmnType.Body,
                color = c.alertRed,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        PullToRefreshSurface(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            content = ScrollableContent.list(
                contentPadding = 16.dp,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.payments.isEmpty()) {
                    item {
                        Text(
                            when {
                                state.paymentsLoading -> "Loading…"
                                state.paymentsError != null -> ""
                                // Only reachable from a read that succeeded.
                                else -> "No payments recorded."
                            },
                            style = DmnType.Body,
                            color = c.textMuted,
                        )
                    }
                    return@list
                }

                item { Totals(state.payments) }
                item { NotComputed() }
                rows(state.payments)
            },
        )
    }
}

/**
 * Received in total and by year.
 *
 * By year and not by month: 24 rows across two years is a history, and a
 * per-month breakdown of it would be mostly empty cells. Years with no payments
 * are absent rather than rendered as zero, so an empty year is never mistaken
 * for a bad one.
 */
@Composable
private fun Totals(payments: List<Payment>) {
    val c = DmnTheme.colors
    // One object, so the headline and the lines under it cannot be computed from
    // different populations. The total IS the sum of the buckets.
    val breakdown = receivedBreakdown(payments)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.surfaceRaised)
            .padding(14.dp),
    ) {
        Text("RECEIVED", style = DmnType.Label, color = c.textFaint)
        Text(
            money(breakdown.total),
            style = DmnType.TitleLg,
            color = c.accentAmberBright,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "across ${breakdown.count} ${if (breakdown.count == 1) "payment" else "payments"}",
            style = DmnType.Small,
            color = c.textMuted,
        )
        for (bucket in breakdown.buckets) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    bucket.label,
                    style = DmnType.Body,
                    // The undated line is a caveat rather than a period, and it
                    // is dimmer so the years still read as the history.
                    color = if (bucket.label == NO_DATE_BUCKET) c.textDim else c.textMuted,
                )
                Text(money(bucket.amount), style = DmnType.Numeric, color = c.textPrimary)
            }
        }
    }
}

/**
 * The sentence, not a blank.
 *
 * Rendered as ordinary body text rather than as a warning: nothing is wrong, and
 * dressing a deliberate scope decision in alert red would say otherwise.
 */
@Composable
private fun NotComputed() {
    Text(
        OUTSTANDING_NOT_COMPUTED,
        style = DmnType.Small,
        color = DmnTheme.colors.textDim,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

private fun LazyListScope.rows(payments: List<Payment>) {
    items(count = payments.size, key = { payments[it].id }) { i ->
        PaymentRow(payments[i])
    }
}

@Composable
private fun PaymentRow(payment: Payment) {
    val c = DmnTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.surfaceRaised)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    paymentTitle(payment),
                    style = DmnType.BodyMedium,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    receivedLine(payment),
                    style = DmnType.Small,
                    color = c.textMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                money(payment.amountReceived),
                style = DmnType.Numeric,
                color = c.textPrimary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        payment.notes?.let {
            Text(
                it,
                style = DmnType.Small,
                color = c.textDim,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * "Royalty · received 20 Aug 2026 · Card", with each part omitted when unknown.
 *
 * A missing received date says so rather than being left blank: `amount_received`
 * is NOT NULL and defaults to 0, so a row can carry money with no date, and a
 * silent gap there would read as an ordinary row.
 */
private fun receivedLine(payment: Payment): String {
    val parts = buildList {
        add(paymentKindLabel(payment.kind))
        add(payment.receivedOn?.let { "received ${shortDate(it)}" } ?: "no date recorded")
        payment.method?.let { add(it) }
    }
    return parts.joinToString(" · ")
}

private fun shortDate(d: LocalDate): String = "${d.day} ${MONTHS[d.month.number - 1]} ${d.year}"

/**
 * Two decimals, always, with a thousands separator.
 *
 * Written out rather than delegated to a locale formatter, because Stage 7's
 * smallest divergence was exactly a `toLocaleString()` inserting separators the
 * other client did not have. Money on this screen is not compared to the web
 * character for character, but the habit is worth keeping.
 */
internal fun money(amount: Double): String {
    val negative = amount < 0
    val cents = kotlin.math.round(kotlin.math.abs(amount) * 100).toLong()
    val whole = cents / 100
    val frac = cents % 100
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (negative) "-$" else "$") + grouped + "." + frac.toString().padStart(2, '0')
}

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
