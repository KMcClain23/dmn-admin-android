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
import com.dmnarration.admin.domain.totalExpenses
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
        Text(
            "Expenses",
            style = DmnType.TitleLg,
            color = c.textPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        )

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
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.surfaceRaised)
            .padding(14.dp),
    ) {
        Text("TOTAL SPEND", style = DmnType.Label, color = c.textFaint)
        Text(
            money(totalExpenses(expenses)),
            style = DmnType.TitleLg,
            color = c.textPrimary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "across ${expenses.size} ${if (expenses.size == 1) "expense" else "expenses"}",
            style = DmnType.Small,
            color = c.textMuted,
        )
        // Deliberately no Schedule C subtotals. Grouping by a tax category is a
        // tax calculation, and this screen does not do one.
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
                    Text(
                        expense.description,
                        style = DmnType.Small,
                        color = c.textMuted,
                        maxLines = 2,
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

        // The tax category as stored, with no interpretation laid over it.
        expense.scheduleC?.let {
            Text(
                it,
                style = DmnType.Small,
                color = c.accentAmberDim,
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
