package com.dmnarration.admin.ui.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.dmnarration.admin.ui.components.Section
import com.dmnarration.admin.ui.components.SectionTabRow
import kotlinx.coroutines.launch

/**
 * Payments and Expenses, as one destination with two sections.
 *
 * The same argument as the shelf: `MoneyViewModel` already loads both, and their
 * two reads already fail independently. "Money" is the word the card detail
 * screen uses for its own financial section, so it is consistent here rather
 * than a new term invented for the nav.
 *
 * The sections keep the screens' own names — Payments and Expenses — rather than
 * the short bar labels, because inside the destination there is room for the
 * real word and no reason to abbreviate it.
 */
@Composable
fun MoneyScreen(
    state: MoneyState,
    pagerState: PagerState,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        SectionTabRow(
            sections = listOf(
                Section("Payments", state.payments.size),
                Section("Expenses", state.expenses.size),
            ),
            selectedIndex = pagerState.currentPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
        )

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> PaymentsScreen(state = state, onRefresh = onRefresh)
                else -> ExpensesScreen(state = state, onRefresh = onRefresh)
            }
        }
    }
}
