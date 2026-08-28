package com.dmnarration.admin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Digging the database's sentence back out of a PostgREST error.
 *
 * This is the seam that makes "the phone and the web say the same thing" a
 * property rather than a coincidence. `check_site_setting()` raises the sentence;
 * the client displays it verbatim; nobody keeps two strings in step. If this
 * extraction fails, the phone falls back to its own wording and the drift is
 * back — which is exactly the 1000–30000 against 1,000–30,000 failure, one layer
 * down.
 */
class FieldWriteTest {

    /** The shape supabase-kt wraps around a PostgREST error body. */
    private fun postgrestError(code: String, message: String): Throwable =
        Exception(
            "Bad Request (400) {\"code\":\"$code\",\"details\":null,\"hint\":null," +
                "\"message\":\"$message\"}"
        )

    @Test fun `the out-of-range sentence survives extraction intact`() {
        // Escaped quotes inside the message are the whole difficulty: a naive
        // match to the next quote stops after "500000 and returns half a sentence.
        val t = postgrestError(
            "22023",
            "Stored value \\\"500000\\\" is outside 1000–30000 and is not being used.",
        )
        assertEquals(
            "Stored value \"500000\" is outside 1000–30000 and is not being used.",
            serverRefusalMessage(t),
        )
    }

    @Test fun `the not-a-number sentence survives extraction intact`() {
        val t = postgrestError("22023", "Stored value \\\"abc\\\" is not a number.")
        assertEquals("Stored value \"abc\" is not a number.", serverRefusalMessage(t))
    }

    @Test fun `a months sentence with brackets and quotes survives`() {
        val t = postgrestError("22023", "Stored value \\\"[\\\"x\\\"]\\\" is not a list of months.")
        assertEquals("Stored value \"[\"x\"]\" is not a list of months.", serverRefusalMessage(t))
    }

    @Test fun `a transport failure is not mistaken for a rule`() {
        // The difference decides whose words the user sees. A dropped connection
        // shown as a validation refusal would tell Dean his value was rejected
        // when it was never delivered.
        assertNull(serverRefusalMessage(java.io.IOException("Unable to resolve host")))
        assertNull(serverRefusalMessage(Exception("Bad Gateway (502)")))
    }

    @Test fun `a different postgres error is not treated as a validation refusal`() {
        // 42501 is the RLS refusal the board already handles. Only 22023 is the
        // rule in check_site_setting() talking.
        val t = postgrestError("42501", "permission denied for table site_settings")
        assertNull(serverRefusalMessage(t))
    }

    @Test fun `the sentence is found through a cause chain`() {
        val inner = postgrestError("22023", "Stored value \\\"yes\\\" is not true or false.")
        val wrapped = Exception("request failed", Exception("http layer", inner))
        assertEquals("Stored value \"yes\" is not true or false.", serverRefusalMessage(wrapped))
    }

    @Test fun `the refusal sentence is stated once`() {
        // Two screens wording a refusal differently is the smallest divergence
        // this project keeps finding. One constant, so they cannot.
        assertEquals("You no longer have permission to make that change.", WRITE_REFUSED_MESSAGE)
    }
}
