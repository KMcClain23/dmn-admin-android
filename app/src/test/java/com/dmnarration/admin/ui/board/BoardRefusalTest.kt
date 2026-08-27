package com.dmnarration.admin.ui.board

import com.dmnarration.admin.data.BoardAccessNotEnabledException
import com.dmnarration.admin.data.BoardRepository
import com.dmnarration.admin.data.StudioSettingsRepository
import com.dmnarration.admin.domain.ArchivedCard
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.ReleasedBook
import com.dmnarration.admin.domain.DEFAULT_STUDIO_SETTINGS
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Bug 6, at the layer that actually failed.
 *
 * The repository's own tests passed throughout: loadBoard did raise. What
 * nothing asserted was what the screen did with the raise — and the answer was
 * that no raise ever reached it, because the client asked "what may an admin
 * read?" using a role cached at sign-in while RLS evaluated the live one. A
 * demoted session received zero rows with HTTP 200 and rendered them as an
 * ordinary empty board: "No active projects", Pipeline (0), In Production (0),
 * no banner. That does not say "you cannot see this". It says "you have no
 * work", and a person would believe it.
 *
 * So every test here asserts BOTH halves. The refusal message must be present
 * AND the ordinary empty board must be absent, because the defect was precisely
 * a state where "no error" and "no cards" combined into a legitimate-looking
 * screen. Asserting only the first half would still pass on the broken build.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardRefusalTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /** Answers whatever the current script says, so a role change mid-session is expressible. */
    private class FakeBoard(
        var result: () -> Result<List<BoardCard>>,
    ) : BoardRepository {
        var loads = 0

        override suspend fun loadBoard(): Result<List<BoardCard>> {
            loads++
            return result()
        }

        override suspend fun updateCard(cardId: String, patch: JsonObject) =
            Result.success<BoardCard?>(null)

        // Stage 6 additions. Present so the compiler enforces that this fake
        // covers the whole interface; these tests do not exercise the shelf.
        override suspend fun released() = Result.success(emptyList<ReleasedBook>())
        override suspend fun archived() = Result.success(emptyList<ArchivedCard>())
        override suspend fun unarchive(cardId: String): Result<ArchivedCard?> =
            Result.success(null)
        override suspend fun cardDetail(cardId: String) =
            Result.success<com.dmnarration.admin.domain.CardDetail?>(null)
    }

    private class FakeStudio : StudioSettingsRepository {
        override suspend fun load() =
            Result.success(com.dmnarration.admin.domain.StudioSettingsRead(DEFAULT_STUDIO_SETTINGS, emptyList()))

        override suspend fun loadAll() = Result.success(
            com.dmnarration.admin.domain.SiteSettings(
                acceptingProjects = true,
                availableMonths = listOf(11, 12, 1, 2),
                availableMonthsRaw = null,
                acceptingProjectsRaw = null,
                studio = com.dmnarration.admin.domain.StudioSettingsRead(DEFAULT_STUDIO_SETTINGS, emptyList()),
            ),
        )
    }

    private val cards = listOf(card(id = "a"), card(id = "b"))

    private fun viewModel(board: FakeBoard) = BoardViewModel(board, FakeStudio())

    private val refusalMessage = "Board access is not enabled for this account yet."

    private fun BoardUiState.cardCount() = pipelineCount + productionCount

    // ─── the exact sequence that shipped the bug ────────────────────────────

    /**
     * Signed in as admin, board loads, then the role is changed underneath the
     * session and the next refresh is refused. Dean's demotion, reproduced
     * without a device.
     */
    @Test
    fun `a session refused after loading shows the refusal, not an empty board`() = runTest(dispatcher) {
        val board = FakeBoard { Result.success(cards) }
        val vm = viewModel(board)

        vm.start(UserRole.ADMIN)
        advanceUntilIdle()
        assertEquals("precondition: the board loaded", 2, vm.state.value.cardCount())

        // demoted on the server; the client still believes it is admin
        board.result = { Result.failure(BoardAccessNotEnabledException()) }
        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("the refusal must be said out loud", refusalMessage, state.error)
        assertFalse(
            "the ordinary empty board must NOT render — this is the half that was missing, " +
                "since EmptyBoard is gated on error == null and nothing ever set an error",
            state.isEmpty && state.error == null,
        )
    }

    /**
     * The gestures go with it. A refused session that still shows twenty cards
     * and offers to change them contradicts the answer the server just gave.
     */
    @Test
    fun `a refusal withdraws the cards and the gestures`() = runTest(dispatcher) {
        val board = FakeBoard { Result.success(cards) }
        val vm = viewModel(board)
        vm.start(UserRole.ADMIN)
        advanceUntilIdle()
        assertTrue("precondition: admin may edit", vm.state.value.capabilities.canEdit)

        board.result = { Result.failure(BoardAccessNotEnabledException()) }
        vm.refresh()
        advanceUntilIdle()

        assertEquals(0, vm.state.value.cardCount())
        assertFalse(
            "a session the server refuses must not be offered a pen",
            vm.state.value.capabilities.canEdit,
        )
    }

    /**
     * A refusal on the very first load — an account that was never admin —
     * reaches the same screen. The bug's route is not the only route to it.
     */
    @Test
    fun `a first load that is refused shows the refusal too`() = runTest(dispatcher) {
        val board = FakeBoard { Result.failure(BoardAccessNotEnabledException()) }
        val vm = viewModel(board)

        vm.start(UserRole.ADMIN)
        advanceUntilIdle()

        assertEquals(refusalMessage, vm.state.value.error)
        assertFalse(vm.state.value.isEmpty && vm.state.value.error == null)
    }

    // ─── the distinction the fix must not blur ──────────────────────────────

    /**
     * A transport fault is not a refusal and must not be dressed as one.
     * Telling someone on a train that their access was revoked is the same
     * species of confident wrong answer pointing the other way — and it keeps
     * the cards, because not knowing anything new is not the board emptying.
     */
    @Test
    fun `a network failure keeps the cards and says something different`() = runTest(dispatcher) {
        val board = FakeBoard { Result.success(cards) }
        val vm = viewModel(board)
        vm.start(UserRole.ADMIN)
        advanceUntilIdle()

        board.result = { Result.failure(java.io.IOException("Unable to resolve host")) }
        vm.refresh()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("No connection. Pull down to try again.", state.error)
        assertEquals("a timed-out pull must not drop good cards", 2, state.cardCount())
        assertTrue("and must not withdraw the gestures", state.capabilities.canEdit)
    }

    // ─── the second fault: start() only ever started once ───────────────────

    /**
     * The flag made the first call the only call, so a role change that WAS
     * noticed still would not re-load. Rotation must still skip the fetch.
     */
    @Test
    fun `re-starting with the same role does not re-load`() = runTest(dispatcher) {
        val board = FakeBoard { Result.success(cards) }
        val vm = viewModel(board)
        vm.start(UserRole.ADMIN)
        advanceUntilIdle()
        vm.start(UserRole.ADMIN) // the composable recreated around a surviving ViewModel
        advanceUntilIdle()
        assertEquals("rotation must not re-fetch", 1, board.loads)
    }

    @Test
    fun `starting with a different role re-loads`() = runTest(dispatcher) {
        val board = FakeBoard { Result.success(cards) }
        val vm = viewModel(board)
        vm.start(UserRole.ADMIN)
        advanceUntilIdle()

        board.result = { Result.failure(BoardAccessNotEnabledException()) }
        vm.start(UserRole.EDITOR)
        advanceUntilIdle()

        assertEquals("a role change must re-fetch", 2, board.loads)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.capabilities.canEdit)
    }

    /**
     * And once the role is restored the board comes back. A refusal clears
     * loadedForRole, so returning to admin is a change rather than a repeat —
     * without that, the fix would strand a re-promoted session on the error.
     */
    @Test
    fun `a restored role loads the board again`() = runTest(dispatcher) {
        val board = FakeBoard { Result.failure(BoardAccessNotEnabledException()) }
        val vm = viewModel(board)
        vm.start(UserRole.ADMIN)
        advanceUntilIdle()
        assertEquals(refusalMessage, vm.state.value.error)

        board.result = { Result.success(cards) }
        vm.start(UserRole.ADMIN)
        advanceUntilIdle()

        assertEquals(2, board.loads)
        assertEquals(null, vm.state.value.error)
        assertEquals(2, vm.state.value.cardCount())
        assertTrue(vm.state.value.capabilities.canEdit)
    }

    /**
     * Recovery through the path the device actually uses.
     *
     * `start()` is only called when the role changes, and nothing re-resolves
     * the role mid-session — so a re-promoted account comes back via
     * pull-to-refresh, not via start(). Withdrawing the gestures on refusal
     * without restoring them here would return the cards and leave the board
     * read-only until the app was restarted.
     */
    @Test
    fun `a refresh that succeeds after a refusal restores the gestures`() = runTest(dispatcher) {
        val board = FakeBoard { Result.failure(BoardAccessNotEnabledException()) }
        val vm = viewModel(board)
        vm.start(UserRole.ADMIN)
        advanceUntilIdle()
        assertFalse("precondition: the refusal withdrew them", vm.state.value.capabilities.canEdit)

        board.result = { Result.success(cards) }
        vm.refresh()          // the pull, not another start()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.cardCount())
        assertEquals(null, vm.state.value.error)
        assertTrue(
            "a successful read proves the server still calls this session an admin",
            vm.state.value.capabilities.canEdit,
        )
    }


    /**
     * The flag that strips the chrome. Distinct from `error != null` on purpose:
     * a timeout must keep the counts and chips, because there really is a
     * pipeline and the app simply does not know it right now.
     */
    @Test
    fun `only a refusal strips the counts and chips`() = runTest(dispatcher) {
        val board = FakeBoard { Result.success(cards) }
        val vm = viewModel(board)
        vm.start(UserRole.ADMIN)
        advanceUntilIdle()
        assertFalse("a loaded board keeps its chrome", vm.state.value.refused)

        board.result = { Result.failure(java.io.IOException("Unable to resolve host")) }
        vm.refresh()
        advanceUntilIdle()
        assertNotNull("precondition: this is an error state", vm.state.value.error)
        assertFalse("a timeout is not a refusal — the counts still mean something", vm.state.value.refused)

        board.result = { Result.failure(BoardAccessNotEnabledException()) }
        vm.refresh()
        advanceUntilIdle()
        assertTrue("a zero beside a refusal is the ambiguity bug 6 was made of", vm.state.value.refused)

        board.result = { Result.success(cards) }
        vm.refresh()
        advanceUntilIdle()
        assertFalse("and it clears when the board comes back", vm.state.value.refused)
    }

}
