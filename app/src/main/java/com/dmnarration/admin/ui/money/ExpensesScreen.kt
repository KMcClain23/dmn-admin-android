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
import com.dmnarration.admin.domain.Expense
import com.dmnarration.admin.domain.NO_DATE_BUCKET
import com.dmnarration.admin.domain.expenseCategoryLine
import com.dmnarration.admin.domain.spentBreakdown
import com.dmnarration.admin.ui.components.MONEY_LIST_BOTTOM_CLEARANCE
import com.dmnarration.admin.ui.board.PullToRefreshSurface
import com.dmnarration.admin.ui.board.ScrollableContent
import com.dmnarration.admin.ui.theme.DmnTheme
import com.dmnarration.admin.ui.theme.DmnType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * What Dean has spent, as stored.
 *
 * `schedule_c` is rendered verbatim and never grouped or totalled by. It is a
 * tax category, and a tax figure this app invented would be worse than no tax
 * figure — the app has no business being the thing that files wrong.
 *
 * There is no receipt indicator. `receipt_url` is an empty string on all 21
 * rows, so a "receipt exists" control could never fire and would be testable
 * only by constructing the data that would make it fire. The field was dropped
 * from `expenses_for_session()` for the same reason, rather than returned and
 * ignored. When receipts exist, the indicator arrives with the signed-URL work
 * it needs.
 */
@Composable
fun ExpensesScreen(
    state: MoneyState,
    onRefresh: () -> Unit,
) {
    val c = DmnTheme.colors

    Column {
        state.expensesError?.let {
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
                // The nav bar was slicing the last row mid-value — "Round-trip
                // flights Portland–Boston, Oct 20–26, 2" cut at the comma. On a
                // money screen that is a hidden figure, not a cosmetic issue.
                extraBottomPadding = MONEY_LIST_BOTTOM_CLEARANCE,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.expenses.isEmpty()) {
                    item {
                        Text(
                            when {
                                state.expensesLoading -> "Loading…"
                                state.expensesError != null -> ""
                                else -> "No expenses recorded."
                            },
                            style = DmnType.Body,
                            color = c.textMuted,
                        )
                    }
                    return@list
                }

                item { Total(state.expenses) }
                rows(state.expenses)
            },
        )
    }
}

@Composable
private fun Total(expenses: List<Expense>) {
    val c = DmnTheme.colors
    // One object: the total IS the sum of the buckets, so a year that is
    // forgotten moves the headline rather than hiding under it. On expenses the
    // year boundary is a tax boundary, which is why this matters more here than
    // it did on payments.
    val breakdown = spentBreakdown(expenses)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.surfaceRaised)
            .padding(14.dp),
    ) {
        Text("TOTAL SPEND", style = DmnType.Label, color = c.textFaint)
        Text(
            money(breakdown.total),
            style = DmnType.TitleLg,
            color = c.textPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "across ${breakdown.count} ${if (breakdown.count == 1) "expense" else "expenses"}",
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
                    color = if (bucket.label == NO_DATE_BUCKET) c.textDim else c.textMuted,
                )
                Text(money(bucket.amount), style = DmnType.Numeric, color = c.textPrimary)
            }
        }
        // Still no Schedule C subtotals. Grouping by a tax category is a tax
        // calculation, and this screen does not do one. Years are a fact about
        // when money moved; a category total is an assertion about a return.
    }
}

private fun LazyListScope.rows(expenses: List<Expense>) {
    items(count = expenses.size, key = { expenses[it].id }) { i ->
        ExpenseRow(expenses[i])
    }
}

@Composable
private fun ExpenseRow(expense: Expense) {
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
                    expense.vendor.ifBlank { "Vendor not recorded" },
                    style = DmnType.BodyMedium,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (expense.description.isNotBlank()) {
                    // One line and dimmer. The vendor is the identifying fact;
                    // a two-line description outweighed it on the rows where it
                    // ran long, which inverted what the row is about.
                    Text(
                        expense.description,
                        style = DmnType.Small,
                        color = c.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    expense.incurredOn?.let { shortDate(it) } ?: "No date recorded",
                    style = DmnType.Small,
                    color = c.textDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                money(expense.amount),
                style = DmnType.Numeric,
                color = c.textPrimary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        // Both names the expense has: the everyday one Dean chose while typing,
        // then the Schedule C line it files under — the web's order exactly.
        //
        // NOT in the accent colour any more. Amber is the app's action colour,
        // and a tax category is the least actionable thing on the row while
        // being, until now, the most visually urgent.
        expenseCategoryLine(expense)?.let {
            Text(
                it,
                style = DmnType.Small,
                color = c.textDim,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun shortDate(d: LocalDate): String = "${d.day} ${MONTHS[d.month.number - 1]} ${d.year}"

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
