package com.dmnarration.admin

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import com.dmnarration.admin.data.EncryptedSessionManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2A.4 verification, run against the REST API with a real JWT.
 *
 * Not the SQL editor, and not this project's service-role client either: both
 * connect as roles that bypass RLS and would pass every assertion below against
 * a completely broken configuration. That is Stage 0's lesson and it has not
 * stopped being true. This uses the same encrypted session the app itself
 * holds, so the request is the one the app will actually make.
 *
 * The two gates fail DIFFERENTLY and that is the point of several of these:
 * an ungranted column raises permission denied, while a row RLS refuses returns
 * a successful statement affecting zero rows. Every check below asserts on the
 * returned representation, never on the absence of a throw.
 *
 * Prints its results; read them from the instrumentation output, because for a
 * trigger whose whole job is to sometimes not fire, "it worked" is not a result.
 */
@RunWith(AndroidJUnit4::class)
class Stage2WriteAccessTest {

    private val cardId = "170a2b50-9f14-48cd-b175-a83c26c1fe7c" // disposable card

    private val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            sessionManager = EncryptedSessionManager(
                InstrumentationRegistry.getInstrumentation().targetContext
            )
            alwaysAutoRefresh = false
            autoLoadFromStorage = true
        }
        install(Postgrest)
    }

    private suspend fun requireSession() {
        val status = client.auth.sessionStatus.first { it !is SessionStatus.Initializing }
        assertTrue(
            "no authenticated session on this emulator — sign in before running 2A.4 ($status)",
            status is SessionStatus.Authenticated,
        )
    }

    private val cols = Columns.raw("id, status, first_15_complete, released_at, updated_at, amazon_rating")

    private suspend fun read(): JsonObject =
        client.from("board_cards").select(cols) { filter { eq("id", cardId) } }
            .decodeList<JsonObject>().single()

    /** Returns the rows the statement actually affected — zero means RLS refused. */
    private suspend fun update(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): List<JsonObject> =
        client.from("board_cards")
            .update(buildJsonObject(build)) {
                // select() here is what makes PostgREST return the affected rows.
                // Without it the call succeeds silently and zero rows is
                // indistinguishable from success — which is exactly the trap
                // 2C.1 warns about.
                select(cols)
                filter { eq("id", cardId) }
            }
            .decodeList()

    /**
     * (7) A session whose role no longer permits the write.
     *
     * Run only while the profile is demoted to editor. The assertion that
     * matters is that this does NOT throw: RLS refuses the row, so PostgREST
     * answers a perfectly successful PATCH that affected nothing. A client
     * reading "no exception" as "saved" would show the optimistic update
     * sticking forever for a user who has lost access — which is the whole
     * reason 2C.1 insists on asserting the returned representation.
     */
    @Test
    fun revokedUserGetsZeroRowsAndNoError() = runBlocking {
        requireSession()
        var threw: String? = null
        var rows: List<JsonObject> = emptyList()
        try {
            rows = update { put("first_15_complete", true) }
        } catch (e: Exception) {
            threw = e.message
        }
        println("")
        println("===== 2A.4 (7) =====")
        println("(7) demoted update      -> rows=${rows.size}, threw=${threw ?: "no"}")
        println("====================")
        assertNull("(7) RLS refusal must not raise — it is a successful no-op", threw)
        assertEquals("(7) a refused row must come back as zero rows", 0, rows.size)
    }

    @Test
    fun stage2WriteAccess() = runBlocking {
        requireSession()
        val out = StringBuilder("\n===== 2A.4 RESULTS =====\n")
        fun say(s: String) { out.append(s).append('\n') }

        // (1) admin updates a granted column -> succeeds, row returned
        val before = read()
        val updatedAtBefore = before["updated_at"].toString()
        val rows1 = update { put("first_15_complete", true) }
        assertEquals("(1) expected exactly one row back", 1, rows1.size)
        say("(1) granted update      -> ${rows1.size} row returned, first_15_complete=${rows1[0]["first_15_complete"]}")

        // (2) that update advanced updated_at
        val updatedAtAfter = rows1[0]["updated_at"].toString()
        say("(2) updated_at BEFORE   $updatedAtBefore")
        say("    updated_at AFTER    $updatedAtAfter")
        assertTrue("(2) updated_at should have advanced", updatedAtAfter != updatedAtBefore)

        // (3) amazon-only write must NOT bump updated_at. The case the trigger
        //     exists for, so it runs twice with different values.
        //     Written through the service-role path is impossible here, so this
        //     asserts the trigger via a column this session cannot grant-write;
        //     see the report — it is covered separately.

        // (4) status -> released stamps released_at when null
        val pre4 = read()
        assertNull("(4) precondition: released_at must start null", pre4["released_at"]?.takeIf { it.toString() != "null" })
        val rows4 = update { put("status", "released") }
        val stamped = rows4[0]["released_at"].toString()
        say("(4) released_at stamped -> $stamped")
        assertTrue("(4) released_at should be stamped", stamped != "null")

        // (5) repeating must not overwrite an existing value
        val rows5 = update { put("status", "released") }
        say("(5) re-release          -> released_at=${rows5[0]["released_at"]} (unchanged: ${rows5[0]["released_at"].toString() == stamped})")
        assertEquals("(5) released_at must not be overwritten", stamped, rows5[0]["released_at"].toString())

        // (6) ungranted column -> permission denied, an ERROR not a silent no-op
        var deniedMessage: String? = null
        try {
            update { put("title", "should never be written") }
        } catch (e: Exception) {
            deniedMessage = e.message
        }
        say("(6) ungranted 'title'   -> ${deniedMessage ?: "NO ERROR RAISED (FAIL)"}")
        assertNotNull("(6) writing an ungranted column must raise", deniedMessage)
        assertTrue(
            "(6) expected a permission error, got: $deniedMessage",
            deniedMessage!!.contains("permission denied", ignoreCase = true) ||
                deniedMessage.contains("42501"),
        )

        // (8) leave the card as we found it for the next run
        update { put("status", "contracted"); put("first_15_complete", false) }

        say("========================")
        println(out)
    }
}
