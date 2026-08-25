package com.dmnarration.admin

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Does supabase-kt accept the modern publishable key?
 *
 * `sb_publishable_...` is not a JWT, and the legacy anon key is. A library that
 * parses the key to pull the project ref out of it would reject the new format
 * at client construction — which would be a one-line fallback to the legacy
 * JWT, but only if it is found now rather than at the end of the stage.
 *
 * Postgrest only, no Auth: Auth's default session store needs an Android
 * Context and cannot exist on a JVM test JVM. That half is covered by the
 * instrumented test beside this one; this half isolates the question of whether
 * the key is accepted on the wire.
 *
 * Deliberately unauthenticated. With no session, RLS should hand back an empty
 * list rather than an error: the request was understood and the caller simply
 * cannot see anything. That is the shape of a working key. A rejected key comes
 * back as a 401 and surfaces here as a thrown exception, not as an empty list —
 * so the two outcomes cannot be confused.
 */
class SupabaseKeySpikeTest {

    private val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Postgrest)
    }

    @Test
    fun `credentials are present in BuildConfig`() {
        assertTrue("SUPABASE_URL is empty", BuildConfig.SUPABASE_URL.isNotBlank())
        assertTrue("SUPABASE_ANON_KEY is empty", BuildConfig.SUPABASE_ANON_KEY.isNotBlank())
        assertTrue(
            "expected the modern publishable key format",
            BuildConfig.SUPABASE_ANON_KEY.startsWith("sb_publishable_"),
        )
    }

    @Test
    fun `publishable key is accepted and RLS returns nothing without a session`() = runBlocking {
        val rows = client.from("board_cards")
            .select { limit(1) }
            .decodeList<JsonObject>()

        // Zero, not one: the row exists, this caller may not see it. Stage 0's
        // whole point, observed from the client for the first time.
        assertEquals("expected RLS to hide every row from an anonymous caller", 0, rows.size)
    }
}
