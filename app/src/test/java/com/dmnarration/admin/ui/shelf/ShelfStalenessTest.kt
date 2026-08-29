package com.dmnarration.admin.ui.shelf

import com.dmnarration.admin.data.BoardRepository
import com.dmnarration.admin.domain.Payout
import com.dmnarration.admin.domain.PayoutSummary
import com.dmnarration.admin.domain.CareerTotals
import com.dmnarration.admin.domain.ArchivedCard
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.Expense
import com.dmnarration.admin.domain.Payment
import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.ReleasedBook
import com.dmnarration.admin.domain.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * A card archived from the board must reach the Archive screen.
 *
 * Found on a device, not in review: archiving a card left it missing from the
 * board AND absent from the Archive until someone pulled to refresh — the card
 * existed in no visible place. The un-archive direction was already wired
 * (`onRestored` re-fetches the board); this is the same wire the other way.
 *
 * Staleness is marked rather than eagerly re-fetched, so these tests are about
 * where the cost is paid as much as whether the data arrives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShelfStalenessTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val t0 = Instant.parse("2026-08-26T12:00:00Z")

    private fun archivedCard(id: String) = ArchivedCard(
        id = id, title = id, author = "A", coverUrl = null, archivedAt = t0,
        archivedReason = "canceled", archivedNotes = null, status = "recording",
    )

    private class Fake : BoardRepository {
        var archivedRows: List<ArchivedCard> = emptyList()
        var archivedReads = 0

        override suspend fun loadBoard(role: UserRole): Result<List<BoardCard>> = Result.success(emptyList())
        override suspend fun updateCard(cardId: String, patch: JsonObject): Result<BoardCard?> =
            Result.success(null)
        override suspend fun cardDetail(cardId: String): Result<CardDetail?> = Result.success(null)
        override suspend fun released(): Result<List<ReleasedBook>> = Result.success(emptyList())
        override suspend fun archived(): Result<List<ArchivedCard>> {
            archivedReads++
            return Result.success(archivedRows)
        }
        // Not under test here. Null is the "could not read" answer, and the
        // screen shows no career figure for it — which is what these tests
        // want: a total absent rather than invented.
        override suspend fun careerTotals(): Result<CareerTotals?> = Result.success(null)
        // Payouts are admin-only at the database and answer a non-admin with
        // an EMPTY LIST, so empty here is the same shape the app must handle.
        override suspend fun payouts(): Result<List<Payout>> = Result.success(emptyList())
        override suspend fun payoutSummary(): Result<PayoutSummary?> = Result.success(null)
        // Stage 8 additions. Present so the compiler enforces that this fake
        // covers the whole interface; these tests do not exercise money.
        override suspend fun payments() = Result.success(emptyList<Payment>())
        override suspend fun expenses() = Result.success(emptyList<Expense>())
        override suspend fun unarchive(cardId: String): Result<ArchivedCard?> = Result.success(null)
    }

    @Test fun `a card archived from the board appears once the screen is opened`() =
        runTest(dispatcher) {
            val fake = Fake()
            val vm = ShelfViewModel(fake).also { it.start(UserRole.ADMIN) }
            advanceUntilIdle()
            assertEquals("nothing archived yet", 0, vm.state.value.archived.size)

            // The board writes; the card is now archived on the server.
            fake.archivedRows = listOf(archivedCard("a"))
            vm.markStale()
            advanceUntilIdle()
            assertEquals("still nothing, because nobody is looking", 0, vm.state.value.archived.size)

            vm.onShown()
            advanceUntilIdle()
            assertEquals(listOf("a"), vm.state.value.archived.map { it.id })
        }

    @Test fun `opening the screen without a board write costs nothing`() = runTest(dispatcher) {
        val fake = Fake()
        val vm = ShelfViewModel(fake).also { it.start(UserRole.ADMIN) }
        advanceUntilIdle()
        assertEquals(1, fake.archivedReads)

        vm.onShown()
        vm.onShown()
        advanceUntilIdle()
        assertEquals("no write happened, so there is nothing to re-read", 1, fake.archivedReads)
    }

    @Test fun `staleness is cleared by the read, not by the next visit`() = runTest(dispatcher) {
        val fake = Fake()
        val vm = ShelfViewModel(fake).also { it.start(UserRole.ADMIN) }
        advanceUntilIdle()

        vm.markStale()
        vm.onShown()
        advanceUntilIdle()
        assertEquals(2, fake.archivedReads)

        vm.onShown()
        advanceUntilIdle()
        assertEquals("one write, one re-read", 2, fake.archivedReads)
    }
}
