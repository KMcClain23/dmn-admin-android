package com.dmnarration.admin.ui.money

import com.dmnarration.admin.data.BoardAccessNotEnabledException
import com.dmnarration.admin.data.BoardRepository
import com.dmnarration.admin.domain.ArchivedCard
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.Expense
import com.dmnarration.admin.domain.Payment
import com.dmnarration.admin.domain.ReleasedBook
import com.dmnarration.admin.domain.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The join between the money repository and the two screens.
 *
 * On a financial screen an empty list is the sentence "you have been paid
 * nothing", so every test here is about a failed read being distinguishable from
 * a real absence — the same shape as bug 6, on the surface where being wrong
 * costs the most.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoneyViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun payment(id: String, received: Double) = Payment(
        id = id, cardId = "c", label = "", kind = "fee", period = "",
        amountExpected = null, amountGross = null, amountReceived = received,
        dueOn = null, invoicedOn = null, receivedOn = LocalDate.parse("2026-08-20"),
        invoiceNumber = null, method = "Card", notes = null, sortOrder = 0,
    )

    private fun expense(id: String) = Expense(
        id = id, incurredOn = LocalDate.parse("2026-08-24"), vendor = "V",
        description = "d", amount = 19.0, label = null, scheduleC = "Office expense",
        method = null, notes = null, source = "email",
    )

    private class Fake : BoardRepository {
        var paymentRows: List<Payment> = emptyList()
        var expenseRows: List<Expense> = emptyList()
        var paymentsFailure: Throwable? = null
        var expensesFailure: Throwable? = null
        var paymentReads = 0

        override suspend fun loadBoard(): Result<List<BoardCard>> = Result.success(emptyList())
        override suspend fun updateCard(cardId: String, patch: JsonObject): Result<BoardCard?> =
            Result.success(null)
        override suspend fun cardDetail(cardId: String): Result<CardDetail?> = Result.success(null)
        override suspend fun released(): Result<List<ReleasedBook>> = Result.success(emptyList())
        override suspend fun archived(): Result<List<ArchivedCard>> = Result.success(emptyList())
        override suspend fun unarchive(cardId: String): Result<ArchivedCard?> = Result.success(null)

        override suspend fun payments(): Result<List<Payment>> {
            paymentReads++
            return paymentsFailure?.let { Result.failure(it) } ?: Result.success(paymentRows)
        }

        override suspend fun expenses(): Result<List<Expense>> =
            expensesFailure?.let { Result.failure(it) } ?: Result.success(expenseRows)
    }

    private fun vm(fake: Fake, role: UserRole = UserRole.ADMIN) =
        MoneyViewModel(fake).also { it.start(role) }

    @Test fun `a refused payments read is not an empty ledger`() = runTest(dispatcher) {
        val fake = Fake().apply { paymentsFailure = BoardAccessNotEnabledException() }
        val vm = vm(fake)
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue("the list is empty either way", s.payments.isEmpty())
        assertEquals("Payments are not visible to this account.", s.paymentsError)
        assertFalse(
            "and must NOT say Dean has been paid nothing",
            s.paymentsGenuinelyEmpty,
        )
    }

    @Test fun `a ledger that really is empty says so`() = runTest(dispatcher) {
        val vm = vm(Fake())
        advanceUntilIdle()

        assertNull(vm.state.value.paymentsError)
        assertTrue(vm.state.value.paymentsGenuinelyEmpty)
    }

    @Test fun `the two reads fail independently`() = runTest(dispatcher) {
        val fake = Fake().apply {
            paymentRows = listOf(payment("a", 367.02), payment("b", 100.0))
            expensesFailure = java.io.IOException("no network")
        }
        val vm = vm(fake)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("payments survived", 2, s.payments.size)
        assertNull("and carry no error", s.paymentsError)
        assertEquals("No connection. Pull down to try again.", s.expensesError)
        assertFalse("an unreadable expenses list is not an empty one", s.expensesGenuinelyEmpty)
    }

    @Test fun `a failed refresh empties the list rather than leaving stale money on screen`() =
        runTest(dispatcher) {
            val fake = Fake().apply { paymentRows = listOf(payment("a", 367.02)) }
            val vm = vm(fake)
            advanceUntilIdle()
            assertEquals(1, vm.state.value.payments.size)

            fake.paymentsFailure = java.io.IOException("dropped")
            vm.refresh()
            advanceUntilIdle()

            assertTrue(
                "a total beside 'could not be read' asserts it is still true",
                vm.state.value.payments.isEmpty(),
            )
            assertNotNull(vm.state.value.paymentsError)
        }

    @Test fun `an editor never even asks`() = runTest(dispatcher) {
        // The tabs are absent for an editor and the server refuses independently.
        // Spending a request to be told no is still a request, and the refusal
        // message would have nowhere to render.
        val fake = Fake()
        vm(fake, role = UserRole.EDITOR)
        advanceUntilIdle()

        assertEquals(0, fake.paymentReads)
    }

    @Test fun `an unknown role is treated as an editor, not as an admin`() = runTest(dispatcher) {
        val fake = Fake()
        val vm = vm(fake, role = UserRole.UNKNOWN)
        advanceUntilIdle()

        assertEquals(0, fake.paymentReads)
        assertFalse(vm.state.value.capabilities.canSeeMoney)
    }

    @Test fun `opening a money screen without a change costs nothing`() = runTest(dispatcher) {
        val fake = Fake()
        val vm = vm(fake)
        advanceUntilIdle()
        assertEquals(1, fake.paymentReads)

        vm.onShown()
        vm.onShown()
        advanceUntilIdle()
        assertEquals(1, fake.paymentReads)
    }

    @Test fun `a marked change is re-read once, on arrival`() = runTest(dispatcher) {
        val fake = Fake()
        val vm = vm(fake)
        advanceUntilIdle()

        vm.markStale()
        advanceUntilIdle()
        assertEquals("nobody is looking yet", 1, fake.paymentReads)

        vm.onShown()
        advanceUntilIdle()
        assertEquals(2, fake.paymentReads)

        vm.onShown()
        advanceUntilIdle()
        assertEquals("one change, one re-read", 2, fake.paymentReads)
    }
}
