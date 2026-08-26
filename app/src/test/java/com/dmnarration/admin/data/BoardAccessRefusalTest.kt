package com.dmnarration.admin.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The point where a server refusal becomes a typed one.
 *
 * This replaces EditorBoardAccessTest, whose premise the RPC removed. That test
 * asserted the client refused an editor *before making a request* — which was
 * true, and was also the shape of bug 6: a decision made from a cached role.
 * The client no longer decides. `board_for_session()` raises
 * BOARD_ACCESS_NOT_ENABLED and this translation is what turns that into
 * something the ViewModel can tell apart from a timeout.
 *
 * Matching a token the migration raises, not prose. If the message is ever
 * reworded, these fail rather than quietly returning to "no error, no cards".
 */
class BoardAccessRefusalTest {

    @Test
    fun `the marker is recognised at the top level`() {
        assertTrue(Exception(BOARD_ACCESS_MARKER).isBoardAccessRefusal())
    }

    /**
     * The transport wraps the PostgREST body, and how deeply is not worth
     * asserting on — only that depth cannot hide it.
     */
    @Test
    fun `the marker is recognised however deeply it is wrapped`() {
        val buried = RuntimeException(
            "request failed",
            IllegalStateException(
                "postgrest",
                Exception("""{"code":"42501","message":"BOARD_ACCESS_NOT_ENABLED"}"""),
            ),
        )
        assertTrue(buried.isBoardAccessRefusal())
    }

    /**
     * The other direction matters just as much. Telling someone on a train that
     * their access was revoked is the same confident wrong answer, mirrored —
     * so nothing that merely smells like a permission problem may claim to be
     * this one.
     */
    @Test
    fun `nothing else is mistaken for a refusal`() {
        val others = listOf(
            java.io.IOException("Unable to resolve host \"rtosqtzrwdbexvttbziv.supabase.co\""),
            Exception("permission denied for table board_cards"),
            Exception("""{"code":"PGRST205","message":"Could not find the table in the schema cache"}"""),
            Exception("JWT expired"),
            Exception(""),
            RuntimeException(),
        )
        for (t in others) {
            assertFalse("must not be read as a refusal: ${t.message}", t.isBoardAccessRefusal())
        }
    }

    /** A cycle in the cause chain must not hang the walk. */
    @Test
    fun `a self-referencing cause terminates`() {
        val a = RuntimeException("outer")
        assertFalse(a.isBoardAccessRefusal())
    }
}
