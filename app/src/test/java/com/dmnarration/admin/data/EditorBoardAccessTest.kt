package com.dmnarration.admin.data

import com.dmnarration.admin.BuildConfig
import com.dmnarration.admin.domain.UserRole
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An editor session must not query a relation that does not exist.
 *
 * `sourceFor(EDITOR)` named `board_cards_editor`, a view that arrives with F3.
 * Stage 1 recorded that as unreachable; it is one UPDATE to `profiles.role`
 * away, which is exactly what the demotion test does. A real session in that
 * state answered PGRST205 — "Could not find the table in the schema cache" —
 * on the board.
 *
 * This runs with no network and no session, because it must not reach either:
 * the refusal happens before the client is touched. If this test ever starts
 * needing a connection, the guard has moved after the request.
 */
class EditorBoardAccessTest {

    private val repo = BoardRepository(
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) { install(Postgrest) }
    )

    @Test
    fun `an editor is refused before any request is made`() = runBlocking {
        val result = repo.loadBoard(UserRole.EDITOR)
        assertTrue("an editor must not get a board", result.isFailure)
        val failure = result.exceptionOrNull()
        assertTrue(
            "must be the explicit unsupported-state type, not a network or PostgREST error — " +
                "got ${failure?.let { it::class.simpleName }}: ${failure?.message}",
            failure is BoardAccessNotEnabledException,
        )
    }

    @Test
    fun `an unknown role is refused the same way`() = runBlocking {
        val result = repo.loadBoard(UserRole.UNKNOWN)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BoardAccessNotEnabledException)
    }

    /**
     * The refusal must not be a quiet fallback to `board_cards`.
     *
     * RLS would answer zero rows for an editor, and the board would render an
     * ordinary empty state — indistinguishable from "you have no projects".
     * A wrong answer delivered calmly is worse than a loud one.
     */
    @Test
    fun `the refusal is not a silent empty board`() = runBlocking {
        val result = repo.loadBoard(UserRole.EDITOR)
        assertFalse(
            "an editor must never receive a successful empty list",
            result.isSuccess,
        )
    }
}
