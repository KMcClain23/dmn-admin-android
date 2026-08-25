package com.dmnarration.admin

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The other half of the publishable-key spike: Auth installed, on a device.
 *
 * The JVM test beside this one had to leave Auth out — its default session
 * store needs an Android Context, and failed for that reason rather than for
 * anything to do with the key. This runs where that Context exists, so it
 * exercises the arrangement 1.4 will actually ship: Auth and Postgrest on one
 * client, keyed by `sb_publishable_...`.
 *
 * Still unauthenticated. Signing in is 1.4's job; all this has to establish is
 * that nothing rejects the key on the way up.
 */
@RunWith(AndroidJUnit4::class)
class SupabaseAuthInitTest {

    private val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
    }

    @Test
    fun authInstallsAndPostgrestStillReadsWithPublishableKey() = runBlocking {
        // Constructing Auth is the step that failed on the JVM. Reaching a
        // session status at all means the session store exists and the client
        // was built.
        assertNotNull("Auth did not initialise", client.auth.sessionStatus.value)

        val rows = client.from("board_cards")
            .select { limit(1) }
            .decodeList<JsonObject>()

        assertEquals("expected RLS to hide every row from an anonymous caller", 0, rows.size)
    }
}
