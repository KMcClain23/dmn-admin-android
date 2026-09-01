package com.dmnarration.admin.ui.shelf

import com.dmnarration.admin.data.BoardAccessNotEnabledException
import com.dmnarration.admin.data.BoardRepository
import com.dmnarration.admin.domain.CastMember
import com.dmnarration.admin.domain.EditorAssignment
import com.dmnarration.admin.domain.NeedsMe
import com.dmnarration.admin.domain.Payout
import com.dmnarration.admin.domain.PayoutSummary
import com.dmnarration.admin.domain.CareerTotals
import com.dmnarration.admin.domain.ArchivedCard
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.Expense
import com.dmnarration.admin.domain.Payment
import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.ReleasedBook
import com.dmnarration.admin.data.SendPickupsResult
import com.dmnarration.admin.domain.Narrator
import com.dmnarration.admin.domain.Pickup
import com.dmnarration.admin.domain.PickupKind
import com.dmnarration.admin.domain.PickupStatus
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The join between the shelf repository and the two screens.
 *
 * This is the layer bug 6 lived in: `loadBoard` raising proved nothing about
 * what the screen did with the raise. Every test here is about a failure being
 * *visible* — that a read which could not happen renders differently from a read
 * that found nothing, and that a refused write does not look like a save.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShelfViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val t0 = Instant.parse("2026-08-18T20:00:00Z")

    private fun book(id: String, archivedAt: Instant? = null) = ReleasedBook(
        id = id, title = id, author = "A", coverUrl = null, releasedAt = t0,
        amazonRating = 4.5, amazonReviewCount = 10, audibleLink = null,
        archivedAt = archivedAt,
    )

    private fun archivedCard(id: String) = ArchivedCard(
        id = id, title = id, author = "A", coverUrl = null, archivedAt = t0,
        archivedReason = "recasted", archivedNotes = "a note", status = "recording",
    )

    private class Fake : BoardRepository {
        var releasedRows: List<ReleasedBook> = emptyList()
        var archivedRows: List<ArchivedCard> = emptyList()
        var releasedFailure: Throwable? = null
        var archivedFailure: Throwable? = null

        /** true = the write returns zero rows, which is RLS refusing it. */
        var refuseWrite = false
        var writeFailure: Throwable? = null
        var unarchived = mutableListOf<String>()

        override suspend fun loadBoard(role: UserRole): Result<List<BoardCard>> = Result.success(emptyList())
        override suspend fun updateCard(cardId: String, patch: JsonObject): Result<BoardCard?> =
            Result.success(null)
        override suspend fun cardDetail(cardId: String, role: UserRole): Result<CardDetail?> = Result.success(null)


        // E2 write path. These fakes exist to test the board/shelf/money joins,
        // not the editor's writes, so they accept and record nothing.
        override suspend fun setCardFinancial(cardId: String, column: String, value: String): Result<Unit> = Result.success(Unit)
        override suspend fun pickups(role: UserRole): Result<List<Pickup>> = Result.success(emptyList())
        override suspend fun setEditingProgress(cardId: String, chaptersEdited: Int?, chaptersTotal: Int?): Result<Unit> = Result.success(Unit)
        override suspend fun setEditingComplete(cardId: String, complete: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun createPickup(cardId: String, chapter: String, timestampAt: String, kind: PickupKind, said: String, shouldBe: String, note: String, assignedNarratorId: String?): Result<String> = Result.success("fake-id")
        override suspend fun updateOwnDraftPickup(id: String, chapter: String, timestampAt: String, kind: PickupKind, said: String, shouldBe: String, note: String, assignedNarratorId: String?): Result<Unit> = Result.success(Unit)
        override suspend fun deleteOwnDraftPickup(id: String): Result<Unit> = Result.success(Unit)
        override suspend fun sendChapterPickups(cardId: String, chapter: String): Result<SendPickupsResult> = Result.success(SendPickupsResult())
    override suspend fun cardCast(cardId: String) = Result.success(emptyList<CastMember>())
    override suspend fun pickupsNeedingMe() = Result.success(emptyList<NeedsMe>())
    override suspend fun editorAssignments() = Result.success(emptyList<EditorAssignment>())
    override suspend fun claimCard(cardId: String) = Result.success(Unit)
    override suspend fun releaseCard(cardId: String) = Result.success(Unit)
    override suspend fun resolvePickup(id: String, status: PickupStatus) = Result.success(Unit)
    override suspend fun markPickupReturned(id: String) = Result.success(Unit)
    override suspend fun deletePickup(id: String) = Result.success(Unit)
        override suspend fun released(): Result<List<ReleasedBook>> =
            releasedFailure?.let { Result.failure(it) } ?: Result.success(releasedRows)

        override suspend fun archived(): Result<List<ArchivedCard>> =
            archivedFailure?.let { Result.failure(it) } ?: Result.success(archivedRows)

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
        override suspend fun unarchive(cardId: String): Result<ArchivedCard?> {
            unarchived += cardId
            writeFailure?.let { return Result.failure(it) }
            if (refuseWrite) return Result.success(null)
            val row = archivedRows.first { it.id == cardId }
            // The server clears all three; the client only guessed the timestamp.
            return Result.success(
                row.copy(archivedAt = null, archivedReason = null, archivedNotes = null)
            )
        }
    }

    private fun vm(fake: Fake, role: UserRole = UserRole.ADMIN) =
        ShelfViewModel(fake).also { it.start(role) }

    // ---- reads that failed must not look like reads that found nothing ----

    @Test fun `a refused archive read is not an empty archive`() = runTest(dispatcher) {
        val fake = Fake().apply { archivedFailure = BoardAccessNotEnabledException() }
        val vm = vm(fake)
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue("the list is empty either way", s.archived.isEmpty())
        assertNotNull("but the screen has something to say", s.archivedError)
        assertFalse(
            "and must NOT tell Dean nothing is archived",
            s.archiveGenuinelyEmpty,
        )
    }

    @Test fun `an archive that really is empty says so`() = runTest(dispatcher) {
        val vm = vm(Fake())
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.archived.isEmpty())
        assertNull(s.archivedError)
        assertTrue("the reassuring empty state is reachable only this way", s.archiveGenuinelyEmpty)
    }

    @Test fun `a refused released read is not an empty shelf`() = runTest(dispatcher) {
        val fake = Fake().apply { releasedFailure = BoardAccessNotEnabledException() }
        val vm = vm(fake)
        advanceUntilIdle()

        assertNotNull(vm.state.value.releasedError)
        assertFalse(vm.state.value.releasedGenuinelyEmpty)
    }

    @Test fun `the two reads fail independently`() = runTest(dispatcher) {
        // The archive is unreadable; the released shelf is fine. A single shared
        // error would put the archive's message on the Released screen and, worse,
        // could empty a list that loaded perfectly well.
        val fake = Fake().apply {
            releasedRows = listOf(book("a"), book("b"))
            archivedFailure = java.io.IOException("no network")
        }
        val vm = vm(fake)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("released survived", 2, s.released.size)
        assertNull("and carries no error", s.releasedError)
        assertEquals("No connection. Pull down to try again.", s.archivedError)
    }

    @Test fun `a failed read empties the list rather than leaving stale rows under an error`() =
        runTest(dispatcher) {
            val fake = Fake().apply { releasedRows = listOf(book("a")) }
            val vm = vm(fake)
            advanceUntilIdle()
            assertEquals(1, vm.state.value.released.size)

            fake.releasedFailure = java.io.IOException("dropped")
            vm.refresh()
            advanceUntilIdle()

            assertTrue(
                "showing rows beside 'could not be read' asserts they are still true",
                vm.state.value.released.isEmpty(),
            )
            assertEquals(0, vm.state.value.counts.allTime)
        }

    // ---- the counts ----

    @Test fun `both counts come from the same read`() = runTest(dispatcher) {
        val fake = Fake().apply {
            releasedRows = listOf(book("a"), book("b", archivedAt = t0), book("c"))
        }
        val vm = vm(fake)
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("the list hides the archived release", listOf("a", "c"), s.released.map { it.id })
        assertEquals("the all-time count keeps it", 3, s.counts.allTime)
        assertEquals(2, s.counts.visible)
    }

    // ---- the write ----

    @Test fun `a saved restore removes the card and tells the board`() = runTest(dispatcher) {
        val fake = Fake().apply { archivedRows = listOf(archivedCard("a"), archivedCard("b")) }
        val vm = vm(fake)
        var restored = 0
        vm.onRestored = { restored++ }
        advanceUntilIdle()

        vm.unarchive("a")
        advanceUntilIdle()

        assertEquals(listOf("b"), vm.state.value.archived.map { it.id })
        assertNull(vm.state.value.writeError)
        assertEquals("the board has to re-fetch or the card is nowhere", 1, restored)
    }

    @Test fun `a refused restore brings the card back and says so`() = runTest(dispatcher) {
        // Zero rows arrives wearing HTTP 200. A client that reads "nothing was
        // thrown" as "saved" leaves the card gone from a screen whose whole job
        // is recovery.
        val fake = Fake().apply {
            archivedRows = listOf(archivedCard("a"))
            refuseWrite = true
        }
        val vm = vm(fake)
        var restored = 0
        vm.onRestored = { restored++ }
        advanceUntilIdle()

        vm.unarchive("a")
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("the card is back", listOf("a"), s.archived.map { it.id })
        assertEquals("recasted", s.archived.single().archivedReason)
        assertEquals("a note", s.archived.single().archivedNotes)
        assertEquals("You no longer have permission to make that change.", s.writeError)
        assertEquals("and the board was never told", 0, restored)
    }

    @Test fun `a failed restore rolls back with the transport's message`() = runTest(dispatcher) {
        val fake = Fake().apply {
            archivedRows = listOf(archivedCard("a"))
            writeFailure = java.io.IOException("no network")
        }
        val vm = vm(fake)
        advanceUntilIdle()

        vm.unarchive("a")
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.state.value.archived.map { it.id })
        assertEquals("No connection. Pull down to try again.", vm.state.value.writeError)
    }

    @Test fun `an editor cannot restore anything`() = runTest(dispatcher) {
        val fake = Fake().apply { archivedRows = listOf(archivedCard("a")) }
        val vm = vm(fake, role = UserRole.EDITOR)
        advanceUntilIdle()

        vm.unarchive("a")
        advanceUntilIdle()

        assertTrue("the write never left the app", fake.unarchived.isEmpty())
        assertEquals(listOf("a"), vm.state.value.archived.map { it.id })
    }

    @Test fun `a second tap while one is in flight does not write twice`() = runTest(dispatcher) {
        val fake = Fake().apply { archivedRows = listOf(archivedCard("a")) }
        val vm = vm(fake)
        advanceUntilIdle()

        vm.unarchive("a")
        vm.unarchive("a")
        advanceUntilIdle()

        assertEquals(listOf("a"), fake.unarchived)
    }

    @Test fun `a write error does not survive the next write`() = runTest(dispatcher) {
        val fake = Fake().apply {
            archivedRows = listOf(archivedCard("a"))
            refuseWrite = true
        }
        val vm = vm(fake)
        advanceUntilIdle()
        vm.unarchive("a")
        advanceUntilIdle()
        assertNotNull(vm.state.value.writeError)

        fake.refuseWrite = false
        vm.unarchive("a")
        advanceUntilIdle()
        assertNull("a stale refusal beside a successful restore is a lie", vm.state.value.writeError)
    }

    @Test fun `the read order is preserved rather than re-sorted`() = runTest(dispatcher) {
        // released_for_session() orders by released_at desc, title asc — matching
        // the web's route including its tiebreak. Sorting again here would be a
        // second implementation of that ordering, free to drift from it.
        val fake = Fake().apply { releasedRows = listOf(book("c"), book("a"), book("b")) }
        val vm = vm(fake)
        advanceUntilIdle()

        assertEquals(listOf("c", "a", "b"), vm.state.value.released.map { it.id })
    }
}
