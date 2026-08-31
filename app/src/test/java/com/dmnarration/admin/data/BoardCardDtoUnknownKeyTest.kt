package com.dmnarration.admin.data

import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlin.reflect.typeOf
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THE BOARD PAYLOAD IS FROZEN UNTIL A RELEASE IS INSTALLED, and this is the test
 * that says so out loud.
 *
 * `board_for_editor()` was widened server-side with an `editor_id` column so the
 * website could tell the editor's own books from the ones merely in editing.
 * That is the ordinary, safe-looking kind of change — additive, nothing removed,
 * no client asked to do anything. It would have emptied the board on every
 * installed copy of this app.
 *
 * SupabaseModule installs Postgrest without configuring a serializer, so the
 * client decodes through KotlinXSerializer()'s default Json, and that has
 * `ignoreUnknownKeys = false`. An unknown key is not ignored and does not
 * degrade the row: it throws, the whole list fails, and loadBoard surfaces it as
 * an error. For the EDITOR that is the entire app — the board is her only
 * screen — and versionCode 49 is on Play, where a shipped build cannot be
 * corrected after the fact.
 *
 * So this asserts the constraint rather than wishing it away. If somebody later
 * makes the serializer lenient, the first test fails and they get to decide that
 * deliberately, having read this. Until then the rule stands: A NEW COLUMN FOR
 * THE WEBSITE GOES IN A NEW FUNCTION. `editor_assignments()` exists for exactly
 * that reason.
 */
class BoardCardDtoUnknownKeyTest {

    private val serializer = KotlinXSerializer()

    private fun payload(extra: String) = """
        [{
          "id": "11111111-1111-1111-1111-111111111111",
          "title": "A Cowboy's Runaway",
          "status": "editing",
          "chapters_edited": 5$extra
        }]
    """.trimIndent()

    private fun decode(json: String) =
        serializer.decode<List<BoardCardDto>>(typeOf<List<BoardCardDto>>(), json)

    @Test
    fun `an unknown column BREAKS the board, it does not degrade it`() {
        val e = assertThrows(SerializationException::class.java) {
            decode(payload(""","editor_id": "22222222-2222-2222-2222-222222222222"""))
        }
        assertTrue(
            "the throw must be about the unknown key and not some other decode " +
                "fault — message was: ${e.message}",
            e.message?.contains("unknown key") == true,
        )
    }

    /**
     * THE CONTROL. Without it the test above passes just as well if BoardCardDto
     * is broken outright, if it stops matching the function, or if the payload is
     * malformed — every one of those throws the same exception type. This proves
     * the only difference between decoding and throwing is the extra key.
     */
    @Test
    fun `CONTROL - the same payload without the extra key decodes fine`() {
        val cards = decode(payload(""))

        assertEquals(1, cards.size)
        assertEquals("A Cowboy's Runaway", cards[0].title)
        assertEquals("editing", cards[0].status)
        assertEquals(5, cards[0].chapters_edited)
    }
}
