package com.dmnarration.admin.ui.board

import com.dmnarration.admin.data.BoardRepository
import com.dmnarration.admin.data.StudioSettingsRepository
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.DEFAULT_STUDIO_SETTINGS
import com.dmnarration.admin.domain.STATUS_RELEASED
import com.dmnarration.admin.domain.StudioSettings
import com.dmnarration.admin.domain.UserRole
import com.dmnarration.admin.domain.card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
 * The three gestures, and the three ways each one fails.
 *
 * Every gesture creates a refusal case, an offline case and a rollback case by
 * construction, and this stage has produced four bugs that lived exclusively in
 * those. They all run through one `mutate()` now, so these tests are as much
 * about that path staying single as about any individual gesture — a gesture
 * added later that does not go through it will not be covered by any of this.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardGestureWriteTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeBoard : BoardRepository {
        var cards = listOf(
            card(id = "p", status = "contracted"),
            card(id = "r", status = "recording"),
        )
        /** null = the write is refused (zero rows); set a throwable to fail it. */
        var refuse = false
        var failWith: Throwable? = null
        var patches = mutableListOf<JsonObject>()

        override suspend fun loadBoard(): Result<List<BoardCard>> = Result.success(cards)

        override suspend fun updateCard(cardId: String, patch: JsonObject): Result<BoardCard?> {
            patches += patch
            failWith?.let { return Result.failure(it) }
            if (refuse) return Result.success(null)
            // The server echoes the row back; the app's optimistic guess is not
            // what lands, so echo something the client did not compute.
            return Result.success(cards.first { it.id == cardId }.let { base ->
                base.copy(
                    status = patch["status"]?.toString()?.trim('"') ?: base.status,
                    first15Complete = patch["first_15_complete"]?.toString() == "true",
                    archivedAt = if (patch.containsKey("archived_at")) kotlin.time.Clock.System.now() else null,
                )
            })
        }

        override suspend fun cardDetail(cardId: String) =
            Result.success<com.dmnarration.admin.domain.CardDetail?>(null)
    }

    private class FakeStudio : StudioSettingsRepository {
        override suspend fun load(): Result<StudioSettings> = Result.success(DEFAULT_STUDIO_SETTINGS)

        override suspend fun loadAll() = Result.success(
            com.dmnarration.admin.domain.SiteSettings(
                acceptingProjects = true,
                availableMonths = listOf(11, 12, 1, 2),
                studio = DEFAULT_STUDIO_SETTINGS,
            ),
        )
    }

    private fun loaded(board: FakeBoard, role: UserRole = UserRole.ADMIN): BoardViewModel =
        BoardViewModel(board, FakeStudio()).also { it.start(role) }

    private fun BoardUiState.total() = pipelineCount + productionCount

    // ─── status moves ───────────────────────────────────────────────────────

    @Test fun `a move lands the card in the other tab`() = runTest(dispatcher) {
        val board = FakeBoard()
        val vm = loaded(board)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.pipelineCount)
        assertEquals(1, vm.state.value.productionCount)

        vm.moveTo("p", "recording")
        advanceUntilIdle()

        assertEquals("the contracted card moved to production", 2, vm.state.value.productionCount)
        assertEquals(0, vm.state.value.pipelineCount)
        assertEquals("the board did not gain or lose a card", 2, vm.state.value.total())
    }

    /**
     * DoD 8. 'released' is outside the active statuses, so the card leaves the
     * board entirely — it must not simply fall through to the Pipeline tab
     * because it is "not production".
     */
    @Test fun `mark as released removes the card from the board`() = runTest(dispatcher) {
        val board = FakeBoard()
        val vm = loaded(board)
        advanceUntilIdle()

        vm.moveTo("r", STATUS_RELEASED)
        advanceUntilIdle()

        assertEquals("a released card is on neither tab", 1, vm.state.value.total())
        assertEquals(0, vm.state.value.productionCount)
    }

    /** 2C.4: released_at is a trigger's job and must never appear in a patch. */
    @Test fun `no gesture ever writes released_at or updated_at`() = runTest(dispatcher) {
        val board = FakeBoard()
        val vm = loaded(board)
        advanceUntilIdle()

        vm.moveTo("r", STATUS_RELEASED)
        advanceUntilIdle()
        vm.toggleFirst15("p")
        advanceUntilIdle()
        vm.archive("p", "recasted", "")
        advanceUntilIdle()

        assertTrue("something was written", board.patches.isNotEmpty())
        for (patch in board.patches) {
            assertFalse("released_at is the trigger's, not the app's", patch.containsKey("released_at"))
            assertFalse("updated_at is the trigger's, not the app's", patch.containsKey("updated_at"))
        }
    }

    @Test fun `a refused move rolls back to the previous status`() = runTest(dispatcher) {
        val board = FakeBoard()
        val vm = loaded(board)
        advanceUntilIdle()
        board.refuse = true

        vm.moveTo("p", "recording")
        advanceUntilIdle()

        assertEquals("back on the pipeline tab", 1, vm.state.value.pipelineCount)
        assertEquals(1, vm.state.value.productionCount)
        assertNotNull(vm.state.value.error)
    }

    @Test fun `an offline move rolls back and keeps the board`() = runTest(dispatcher) {
        val board = FakeBoard()
        val vm = loaded(board)
        advanceUntilIdle()
        board.failWith = java.io.IOException("Unable to resolve host")

        vm.moveTo("p", "recording")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.pipelineCount)
        assertEquals("No connection. Pull down to try again.", vm.state.value.error)
        assertEquals(2, vm.state.value.total())
    }

    // ─── archive ────────────────────────────────────────────────────────────

    @Test fun `archiving removes the card`() = runTest(dispatcher) {
        val board = FakeBoard()
        val vm = loaded(board)
        advanceUntilIdle()

        vm.archive("r", "recasted", "partial fee settled")
        advanceUntilIdle()

        assertEquals(1, vm.state.value.total())
        assertNull(vm.state.value.error)
    }

    @Test fun `the archive patch carries the reason and notes`() = runTest(dispatcher) {
        val board = FakeBoard()
        val vm = loaded(board)
        advanceUntilIdle()

        vm.archive("r", "canceled", "author withdrew")
        advanceUntilIdle()

        val patch = board.patches.single()
        assertTrue(patch.containsKey("archived_at"))
        assertEquals("\"canceled\"", patch["archived_reason"].toString())
        assertEquals("\"author withdrew\"", patch["archived_notes"].toString())
    }

    /**
     * The rollback that matters most: an archive that fails must put the card
     * back on the board. A card that vanishes and stays vanished looks exactly
     * like a successful archive.
     */
    @Test fun `a refused archive puts the card back`() = runTest(dispatcher) {
        val board = FakeBoard()
        val vm = loaded(board)
        advanceUntilIdle()
        board.refuse = true

        vm.archive("r", "recasted", "")
        advanceUntilIdle()

        assertEquals("the card must return", 2, vm.state.value.total())
        assertEquals(1, vm.state.value.productionCount)
        assertNotNull(vm.state.value.error)
    }

    @Test fun `an offline archive puts the card back`() = runTest(dispatcher) {
        val board = FakeBoard()
        val vm = loaded(board)
        advanceUntilIdle()
        board.failWith = java.io.IOException("Unable to resolve host")

        vm.archive("r", "recasted", "")
        advanceUntilIdle()

        assertEquals(2, vm.state.value.total())
        assertEquals("No connection. Pull down to try again.", vm.state.value.error)
    }

    // ─── the gate ───────────────────────────────────────────────────────────

    /**
     * An editor must not be able to write through any of them. The UI withholds
     * the gestures, but the gate is here too — a composable is not a permission
     * boundary.
     */
    @Test fun `a session that may not edit writes nothing`() = runTest(dispatcher) {
        val board = FakeBoard()
        val vm = BoardViewModel(board, FakeStudio())
        vm.start(UserRole.EDITOR)
        advanceUntilIdle()

        vm.moveTo("r", "editing")
        vm.archive("r", "recasted", "")
        vm.toggleFirst15("r")
        advanceUntilIdle()

        assertTrue("no write may be attempted", board.patches.isEmpty())
    }

    /** A second tap while the first is still in flight must not race it. */
    @Test fun `a card already in flight is not written twice`() = runTest(dispatcher) {
        val board = FakeBoard()
        val vm = loaded(board)
        advanceUntilIdle()

        vm.moveTo("r", "editing")
        vm.moveTo("r", "prepping") // before the first has settled
        advanceUntilIdle()

        assertEquals("only the first write goes", 1, board.patches.size)
    }
}
