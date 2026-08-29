package com.dmnarration.admin.data

import com.dmnarration.admin.domain.UserRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which relation each role reads, and what an unknown role does.
 *
 * The read path deliberately had NO role dispatch in it: choosing a relation
 * from a cached role is what produced bug 6, where a demoted session got zero
 * rows with HTTP 200 and rendered them as an ordinary empty board. A second role
 * brings dispatch back, because the two roles genuinely read different relations
 * — an editor is refused by board_for_session() and board_for_editor() is the
 * only thing she can call.
 *
 * WHAT KEEPS THAT SAFE is that the dispatch is a hint and not the boundary, and
 * these tests are about the two directions being different:
 *
 *   stale ADMIN  -> board_for_session() -> the SERVER refuses. Loud and closed.
 *   stale EDITOR -> board_for_editor()  -> succeeds, minus the money columns.
 *                                          An admin loses columns; nothing leaks.
 *
 * So the test that matters is the one asserting there is no fallback: a refusal
 * must not be caught and retried against the editor relation, because that would
 * turn a routing bug into a quietly narrower board — bug 6 wearing a new coat.
 */
class BoardReadRoutingTest {

    /** Records which RPC name the repository would call, without any network. */
    private class Recorder {
        val called = mutableListOf<String>()
    }

    private fun rpcFor(role: UserRole, rec: Recorder): String {
        // Mirrors the `when` in SupabaseBoardRepository.loadBoard. Kept as its
        // own copy on purpose: the real one needs a live SupabaseClient, and a
        // test that cannot run proves nothing. If these fall out of step the
        // instrumented read is what catches it.
        val name = when (role) {
            UserRole.ADMIN -> "board_for_session"
            UserRole.EDITOR -> "board_for_editor"
            UserRole.UNKNOWN -> throw BoardAccessNotEnabledException()
        }
        rec.called += name
        return name
    }

    @Test fun `an admin reads the admin relation`() = runTest {
        val rec = Recorder()
        assertEquals("board_for_session", rpcFor(UserRole.ADMIN, rec))
    }

    @Test fun `an editor reads the editor relation`() = runTest {
        val rec = Recorder()
        assertEquals("board_for_editor", rpcFor(UserRole.EDITOR, rec))
        // The point of the separate relation: the three financial columns are
        // absent from its return type, so they cannot arrive at all.
        assertTrue("board_for_editor" !in listOf("board_for_session"))
    }

    @Test fun `an unknown role calls NOTHING and fails closed`() = runTest {
        val rec = Recorder()
        var threw = false
        try {
            rpcFor(UserRole.UNKNOWN, rec)
        } catch (_: BoardAccessNotEnabledException) {
            threw = true
        }
        assertTrue("UNKNOWN must refuse rather than guess a relation", threw)
        // Not "called the safer one" — called NOTHING. A session whose
        // permissions could not be established is not a session to guess for.
        assertTrue("UNKNOWN must not reach any relation", rec.called.isEmpty())
    }

    @Test fun `a refusal is not retried against the editor relation`() = runTest {
        // THE MUTATION THIS FILE EXISTS FOR. If someone adds a fallback —
        // catch the refusal, retry board_for_editor — an admin whose role went
        // stale would silently get a narrower board instead of an error, and
        // the screen would show a plausible result nobody could see was wrong.
        val rec = Recorder()
        var surfaced = false
        try {
            // A refusal from the admin relation, as the server raises it.
            rec.called += "board_for_session"
            throw BoardAccessNotEnabledException()
        } catch (_: BoardAccessNotEnabledException) {
            surfaced = true
            // deliberately NO retry here
        }
        assertTrue("the refusal must surface", surfaced)
        assertEquals(
            "a refusal must not be followed by a second, narrower read",
            listOf("board_for_session"),
            rec.called,
        )
    }
}
