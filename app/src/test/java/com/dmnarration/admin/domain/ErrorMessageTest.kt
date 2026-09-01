package com.dmnarration.admin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * A refusal must arrive as a sentence, not as a transport dump with a token in
 * it. The fixture below is the REAL message observed on the emulator.
 */
class ErrorMessageTest {

    private val real =
        "This book tracks chapters individually and 8 is marked done out of order. " +
            "Use the website to change it.\n" +
            "Code: 22023\nHint: null\nDetails: null\n" +
            "URL: https://rt.../rest/v1/rpc/set_editing_progress\n" +
            "Headers: {Authorization=[Bearer ey... (len=815)], apikey=[sb... (len=46)]}\n" +
            "Http Method: POST"

    @Test fun `only the database's sentence survives`() {
        assertEquals(
            "This book tracks chapters individually and 8 is marked done out of order. " +
                "Use the website to change it.",
            humanMessage(RuntimeException(real), "fallback"),
        )
    }

    @Test fun `no part of a credential can reach the screen`() {
        val shown = humanMessage(RuntimeException(real), "fallback")
        for (leak in listOf("Bearer", "apikey", "Authorization", "Headers", "URL:", "Http Method")) {
            assertFalse("`$leak` reached the screen: $shown", shown.contains(leak))
        }
    }

    @Test fun `an empty or absent message falls back`() {
        assertEquals("fallback", humanMessage(null, "fallback"))
        assertEquals("fallback", humanMessage(RuntimeException(""), "fallback"))
        assertEquals("fallback", humanMessage(RuntimeException("   \n Code: 1"), "fallback"))
    }

    @Test fun `an ordinary one-line message is untouched`() {
        assertEquals("No such pickup.", humanMessage(RuntimeException("No such pickup."), "fallback"))
    }

    @Test fun `Code on the same line is still cut`() {
        assertEquals(
            "That book is not available to edit.",
            humanMessage(RuntimeException("That book is not available to edit. Code: 22023"), "fallback"),
        )
    }
}
