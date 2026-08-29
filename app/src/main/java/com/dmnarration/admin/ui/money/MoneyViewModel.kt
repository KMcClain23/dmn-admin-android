package com.dmnarration.admin.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dmnarration.admin.data.BoardRepository
import com.dmnarration.admin.domain.Capabilities
import com.dmnarration.admin.domain.Expense
import com.dmnarration.admin.domain.Payment
import com.dmnarration.admin.domain.Payout
import com.dmnarration.admin.domain.PayoutSummary
import com.dmnarration.admin.domain.UserRole
import com.dmnarration.admin.ui.describeDataFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the two money screens show.
 *
 * Separate loading flags and separate errors, for the same reason the shelf has
 * them: two independent reads that fail independently. A failed expenses read
 * must not empty a payments list that loaded perfectly well — and on a financial
 * screen an empty list is the sentence "you have been paid nothing", which is
 * the one wrong conclusion available here.
 */
data class MoneyState(
    val paymentsLoading: Boolean = true,
    val expensesLoading: Boolean = true,
    val refreshing: Boolean = false,
    val payments: List<Payment> = emptyList(),
    /**
     * Money going out, joined to payments client-side on paymentId.
     *
     * Empty is NOT evidence there are none: payouts are admin-only at the
     * database and RLS answers a non-admin with an empty list rather than an
     * error. A screen that said "no payouts" on an empty read would be
     * asserting something it cannot know.
     */
    val payouts: List<Payout> = emptyList(),
    /** Null until it loads, and null again if it fails — never a zeroed pair. */
    val payoutSummary: PayoutSummary? = null,
    val expenses: List<Expense> = emptyList(),
    val paymentsError: String? = null,
    val expensesError: String? = null,
    val capabilities: Capabilities = Capabilities.of(UserRole.EDITOR),
) {
    /** True only when the read succeeded and returned nothing. */
    val paymentsGenuinelyEmpty: Boolean
        get() = !paymentsLoading && paymentsError == null && payments.isEmpty()

    val expensesGenuinelyEmpty: Boolean
        get() = !expensesLoading && expensesError == null && expenses.isEmpty()
}

/**
 * Payments and Expenses, read-only.
 *
 * There is no write path here and that is the stage's decision, not an omission:
 * invoicing and settling are multi-step money flows whose web versions have only
 * just been made to refuse rather than guess.
 *
 * There is also no owed computation. See `OUTSTANDING_NOT_COMPUTED` for why, and
 * note that the screens SAY so rather than leaving a blank.
 */
@HiltViewModel
class MoneyViewModel @Inject constructor(
    private val board: BoardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MoneyState())
    val state: StateFlow<MoneyState> = _state.asStateFlow()

    private var started = false
    private var stale = false

    fun start(role: UserRole) {
        _state.value = _state.value.copy(capabilities = Capabilities.of(role))
        // An editor never gets a read. The server refuses independently and the
        // tabs are absent from the bar, so this is the third layer, not the first
        // — but spending a request to be told no is still a request.
        if (!Capabilities.of(role).canSeeMoney) return
        if (started) return
        started = true
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    /** Something changed elsewhere; re-read when a money screen is next opened. */
    fun markStale() {
        stale = true
    }

    fun onShown() {
        if (!stale) return
        stale = false
        load(initial = false)
    }

    private fun load(initial: Boolean) {
        _state.value = _state.value.copy(
            paymentsLoading = initial,
            expensesLoading = initial,
            refreshing = !initial,
        )
        viewModelScope.launch {
            // Loaded beside payments; its failure is its own. A payouts read
            // that fails must not make the payments list look broken, and a
            // failing payments list must not blank payouts that did read.
            board.payouts().fold(
                onSuccess = { _state.value = _state.value.copy(payouts = it) },
                onFailure = { _state.value = _state.value.copy(payouts = emptyList()) },
            )
            board.payoutSummary().fold(
                onSuccess = { _state.value = _state.value.copy(payoutSummary = it) },
                onFailure = { _state.value = _state.value.copy(payoutSummary = null) },
            )
            board.payments().fold(
                onSuccess = { rows ->
                    _state.value = _state.value.copy(
                        payments = rows,
                        paymentsLoading = false,
                        paymentsError = null,
                    )
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        payments = emptyList(),
                        paymentsLoading = false,
                        paymentsError = describeDataFailure(
                            t = t,
                            tag = TAG,
                            logMessage = "payments load failed",
                            refused = "Payments are not visible to this account.",
                            revoked = "Your session is no longer allowed to read payments. " +
                                "Try signing out and in again.",
                            generic = "Could not load payments. Pull down to try again.",
                        ),
                    )
                },
            )
            settle()
        }
        viewModelScope.launch {
            board.expenses().fold(
                onSuccess = { rows ->
                    _state.value = _state.value.copy(
                        expenses = rows,
                        expensesLoading = false,
                        expensesError = null,
                    )
                },
                onFailure = { t ->
                    _state.value = _state.value.copy(
                        expenses = emptyList(),
                        expensesLoading = false,
                        expensesError = describeDataFailure(
                            t = t,
                            tag = TAG,
                            logMessage = "expenses load failed",
                            refused = "Expenses are not visible to this account.",
                            revoked = "Your session is no longer allowed to read expenses. " +
                                "Try signing out and in again.",
                            generic = "Could not load expenses. Pull down to try again.",
                        ),
                    )
                },
            )
            settle()
        }
    }

    private fun settle() {
        val s = _state.value
        if (!s.paymentsLoading && !s.expensesLoading) {
            _state.value = s.copy(refreshing = false)
        }
    }

    private companion object {
        const val TAG = "MoneyViewModel"
    }
}
