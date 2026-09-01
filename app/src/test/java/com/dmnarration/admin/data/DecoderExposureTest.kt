package com.dmnarration.admin.data

import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlin.reflect.typeOf
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ── EVERY RELATION THIS APP DECODES IS FROZEN UNTIL A RELEASE IS INSTALLED ──
 *
 * SupabaseModule installs Postgrest without configuring a serializer, so every
 * `decodeList` in BoardRepository runs through KotlinXSerializer()'s default
 * Json, which has `ignoreUnknownKeys = false`. A column added server-side to any
 * of the fourteen relations the app decodes does not degrade a row — it throws,
 * the whole list fails, and the screen shows an error.
 *
 * That makes an ADDITIVE server change a BREAKING client change, for every copy
 * already installed, which is not a thing a Play release can fix retroactively.
 *
 * These tests exist to make that visible at the moment somebody would otherwise
 * cause it, and their failure messages say what the consequence is rather than
 * only what the assertion was.
 */
class DecoderExposureTest {

    private val serializer = KotlinXSerializer()

    private inline fun <reified T> decode(json: String): T =
        serializer.decode(typeOf<T>(), json)

    // ── the board ──────────────────────────────────────────────────────────

    private fun boardPayload(extra: String) = """
        [{
          "id": "11111111-1111-1111-1111-111111111111",
          "title": "A Cowboy's Runaway",
          "status": "editing",
          "chapters_edited": 5$extra
        }]
    """.trimIndent()

    @Test
    fun `a column added to board_for_editor would empty the board on shipped builds`() {
        val e = assertThrows(
            "board_for_editor gained a column and BoardCardDto decoded it without " +
                "complaint. If that is because someone set ignoreUnknownKeys = true, " +
                "read this test before keeping the change.",
            SerializationException::class.java,
        ) {
            decode<List<BoardCardDto>>(
                boardPayload(""","editor_id": "22222222-2222-2222-2222-222222222222"""),
            )
        }
        assertTrue(
            "THIS IS WHAT A SERVER-SIDE COLUMN ADDITION DOES TO AN INSTALLED BUILD: " +
                "the decode throws, loadBoard surfaces an error, and the board is EMPTY " +
                "for everyone who has not updated — including the editor, for whom the " +
                "board is the entire app. The fix is a NEW FUNCTION for the new column " +
                "(see editor_assignments), never a new column on a function the app " +
                "already decodes. Thrown message was: ${e.message}",
            e.message?.contains("unknown key") == true,
        )
    }

    /**
     * THE CONTROL. Without it the test above passes just as well if BoardCardDto
     * is broken outright or the payload is malformed — both throw the same
     * exception type. This proves the only difference is the extra key.
     */
    @Test
    fun `CONTROL - the same board payload without the extra key decodes fine`() {
        val cards = decode<List<BoardCardDto>>(boardPayload(""))

        assertEquals(1, cards.size)
        assertEquals("A Cowboy's Runaway", cards[0].title)
        assertEquals("editing", cards[0].status)
        assertEquals(5, cards[0].chapters_edited)
    }

    // ── payouts, which the audit found already broken ──────────────────────

    /**
     * `payouts_for_session()` returns card_id and PayoutDto did not declare it,
     * so Money → Payouts threw for Dean on every build that had the screen. It
     * was not masked by an empty table: payment_payouts holds nine rows.
     *
     * This payload is that function's real column list, in its order.
     */
    private val payoutsPayload = """
        [{
          "id": "33333333-3333-3333-3333-333333333333",
          "card_id": "44444444-4444-4444-4444-444444444444",
          "payment_id": "55555555-5555-5555-5555-555555555555",
          "payee_name": "Ann Dahlia",
          "kind": "narrator",
          "amount": 1250.0,
          "paid_on": "2026-07-14",
          "rate_pfh": 120.0,
          "paid_via": "PayPal",
          "notes": ""
        }]
    """.trimIndent()

    @Test
    fun `payouts_for_session decodes - every column it returns is declared`() {
        val payouts = decode<List<PayoutDto>>(payoutsPayload)

        assertEquals(1, payouts.size)
        assertEquals("Ann Dahlia", payouts[0].payee_name)
        assertEquals(1250.0, payouts[0].amount, 0.001)
    }
}
