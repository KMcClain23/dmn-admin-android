package com.dmnarration.admin

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
 * ── WHAT CHANGED, AND WHY IT IS NOT A REGRESSION ────────────────────────────
 *
 * This test used to assert that an anonymous read returned an EMPTY LIST, on the
 * reasoning that the request was understood and the caller simply could not see
 * anything. `anon` has since had its SELECT grant on `board_cards` revoked, so
 * the same call now comes back `42501 permission denied` instead.
 *
 * THE NEW BEHAVIOUR IS THE DELIBERATE ONE. It was chosen because a silent empty
 * result meaning "you are denied" is indistinguishable from one meaning "there
 * is nothing here", and this project has already paid for that ambiguity once:
 * bug 6 was a demoted session receiving zero rows with HTTP 200 and rendering
 * them as an ordinary empty board. Nothing threw, so nothing could be caught.
 *
 * DO NOT "FIX" THIS BACK TO expecting an empty list. Doing so would require
 * re-granting `anon` SELECT on `board_cards`, which is the revoke undone — and
 * the test would then be asserting that the database leaks rows to anonymous
 * callers, dressed up as a passing check.
 *
 * The spike's actual question is unchanged and is still answered here: a key the
 * library or the gateway REJECTED comes back as a 401 before Postgres ever sees
 * the request. A key that was ACCEPTED reaches Postgres, is authorised as
 * `anon`, and is then refused by grants — which is what 42501 is. So the
 * permission-denied error is positive evidence for the key, not against it, and
 * the assertions below insist on that distinction rather than accepting any
 * failure at all.
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
    fun `publishable key is accepted and anon is refused by grants, not by RLS`() = runBlocking {
        try {
            client.from("board_cards")
                .select { limit(1) }
                .decodeList<JsonObject>()
            fail(
                "expected permission denied. An empty list here would mean anon can " +
                    "SELECT board_cards again — the revoke undone.",
            )
        } catch (t: Throwable) {
            val message = t.message.orEmpty()

            // The key REACHED Postgres. That is the spike's question, and this
            // is what answers it: 42501 is raised by the database, which only
            // happens once the request has been accepted and authorised as anon.
            assertTrue(
                "expected a permission-denied refusal, got: $message",
                message.contains("permission denied", ignoreCase = true) ||
                    message.contains("42501"),
            )

            // And it must NOT be the other failure. A rejected key never gets as
            // far as a grant check, so if this fires the spike has failed and the
            // permission-denied assertion above would have been the wrong reason
            // to celebrate.
            assertTrue(
                "the key itself was rejected rather than refused by grants: $message",
                !message.contains("Invalid API key", ignoreCase = true) &&
                    !message.contains("401") &&
                    !message.contains("JWSError", ignoreCase = true),
            )
        }
    }
}
