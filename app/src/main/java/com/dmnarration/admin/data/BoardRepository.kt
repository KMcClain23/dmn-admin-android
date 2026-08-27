package com.dmnarration.admin.data

import com.dmnarration.admin.domain.CardDetail
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.SettingKeys
import com.dmnarration.admin.domain.SiteSettings
import com.dmnarration.admin.domain.StudioSettings
import com.dmnarration.admin.domain.studioSettingsFrom
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The board is not readable by this session — not an error to retry, and not a
 * board that happens to be empty.
 *
 * Carries no role, deliberately. The client's copy of the role is the thing
 * bug 6 proved cannot be trusted; this refusal comes from the server, which is
 * the only party that knows.
 */
class BoardAccessNotEnabledException :
    Exception("the signed-in account may not read the board")

/** The card read equivalent, raised by `card_detail()`. */
class CardAccessNotEnabledException :
    Exception("the signed-in account may not read this card")

/**
 * The board read and the one write Stage 2 performs.
 *
 * An interface because the join between this and the ViewModel is where bug 6
 * lived, and that join can only be tested against a fake. Proving `loadBoard`
 * raises proved nothing about what the screen did with the raise.
 */
interface BoardRepository {
    suspend fun loadBoard(): Result<List<BoardCard>>
    suspend fun updateCard(cardId: String, patch: JsonObject): Result<BoardCard?>

    /**
     * One card in full. Null means the id matched nothing.
     *
     * A refusal is [CardAccessNotEnabledException], never null, because a card that
     * was archived, a card that never existed and a role that was revoked would
     * otherwise all arrive as the same empty answer — the ambiguity Stage 2's bug 5
     * was made of.
     */
    suspend fun cardDetail(cardId: String): Result<CardDetail?>
}

/**
 * The Supabase implementation.
 *
 * The read goes through an RPC rather than a table, which is the fix for bug 6.
 * The app used to ask "what may an admin read?" using a role it had cached at
 * sign-in, while RLS evaluated the live one; a demoted session got zero rows
 * with HTTP 200 and rendered them as an ordinary empty board saying "No active
 * projects". Nothing threw, so nothing could be caught.
 *
 * `board_for_session()` answers the role and its consequence in one breath. It
 * raises before returning anything, so refusal arrives as an exception instead
 * of an indistinguishable empty list. Note that a `security_invoker` view with
 * an asserting predicate would NOT work here and was rejected: if RLS filters
 * rows to zero first, the predicate never evaluates and the assertion never
 * fires — inert in exactly the case it exists for. A function body always runs.
 *
 * There is deliberately no role dispatch left in the read path. Choosing the
 * relation from a cached role is what produced the bug, so the client no longer
 * chooses; the server refuses. The column list moved into the function's return
 * type for the same reason `select("*")` was never used — one explicit list, now
 * in the place that can actually enforce it.
 */
@Singleton
class SupabaseBoardRepository @Inject constructor(
    private val client: SupabaseClient,
) : BoardRepository {

    override suspend fun loadBoard(): Result<List<BoardCard>> = runCatching {
        try {
            client.postgrest.rpc(BOARD_RPC)
                .decodeList<BoardCardDto>()
                .map { it.toDomain() }
        } catch (t: Throwable) {
            // Matching a token the migration raises, not prose that may be
            // reworded. Anything else is a genuine transport or server fault and
            // must keep its own identity — conflating the two would turn "no
            // connection" into "you have no access", which is the same species
            // of confident wrong answer in the other direction.
            if (t.isBoardAccessRefusal()) throw BoardAccessNotEnabledException() else throw t
        }
    }

    /**
     * Update one card and report what actually happened to it.
     *
     *   success(row)  — the server returned the row. Its copy is the truth; a
     *                   trigger may have stamped released_at or moved
     *                   updated_at, neither of which the app computes.
     *   success(null) — zero rows. RLS refused this row. PostgREST answers 200
     *                   with an empty array, so this arrives wearing success
     *                   and nothing is thrown.
     *   failure(t)    — the request failed: no network, or permission denied on
     *                   an ungranted column.
     *
     * `select()` in the request is what makes PostgREST return the affected rows
     * at all. Without it the call succeeds silently and a refusal is
     * indistinguishable from a save.
     *
     * Still a direct table update rather than an RPC: a single-row write needs
     * no validation the schema cannot express, and its refusal is already
     * distinguishable via the zero-rows contract above. The read needed an RPC
     * because a refused read and an empty one are the same HTTP response.
     *
     * Nothing here sets updated_at or released_at. Those are triggers now.
     */
    override suspend fun updateCard(cardId: String, patch: JsonObject): Result<BoardCard?> =
        runCatching {
            client.from("board_cards")
                .update(patch) {
                    select(Columns.raw(ADMIN_COLUMNS))
                    filter { eq("id", cardId) }
                }
                .decodeList<BoardCardDto>()
                .firstOrNull()
                ?.toDomain()
        }

    override suspend fun cardDetail(cardId: String): Result<CardDetail?> = runCatching {
        try {
            client.postgrest.rpc(CARD_RPC, buildJsonObject { put("p_id", cardId) })
                .decodeList<CardDetailDto>()
                .firstOrNull()
                ?.toDomain()
        } catch (t: Throwable) {
            if (t.isCardAccessRefusal()) throw CardAccessNotEnabledException() else throw t
        }
    }

    private companion object {
        const val BOARD_RPC = "board_for_session"
        const val CARD_RPC = "card_detail"

        /** The write's return shape. The read's list lives in the RPC signature. */
        const val ADMIN_COLUMNS =
            "id, title, author, co_narrator, cover_url, status, deadline, first15_due, " +
                "first_15_complete, word_count, pfh_rate, payment_type, is_confidential, " +
                "narration_format, narrator_share_percent, recording_dates, words_recorded, created_at, " +
                "archived_at"
    }
}

/**
 * The marker `board_for_session()` raises, as it reaches the client.
 *
 * Checked against the whole cause chain because the transport wraps the
 * PostgREST body at a depth that is not worth asserting on.
 */
internal fun Throwable.isBoardAccessRefusal(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t.message?.contains(BOARD_ACCESS_MARKER) == true) return true
        t = t.cause
    }
    return false
}

internal const val BOARD_ACCESS_MARKER = "BOARD_ACCESS_NOT_ENABLED"

internal const val CARD_ACCESS_MARKER = "CARD_ACCESS_NOT_ENABLED"

/** The same cause-chain walk, for the card read's marker. */
internal fun Throwable.isCardAccessRefusal(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t.message?.contains(CARD_ACCESS_MARKER) == true) return true
        t = t.cause
    }
    return false
}

/**
 * The five tunable numbers.
 *
 * An interface for the same reason as the board: the ViewModel takes one, so a
 * test of the ViewModel needs one that does not reach the network.
 */
interface StudioSettingsRepository {
    suspend fun load(): Result<StudioSettings>

    /**
     * Everything the table holds, for the Settings screen.
     *
     * Read-only by the schema, not by convention: `site_settings` has a `Role read`
     * policy and no update policy at all, so there is deliberately no counterpart to
     * this and adding one needs a migration first.
     */
    suspend fun loadAll(): Result<SiteSettings>
}

@Singleton
class SupabaseStudioSettingsRepository @Inject constructor(
    private val client: SupabaseClient,
) : StudioSettingsRepository {
    private val keys = listOf(
        SettingKeys.WORDS_PER_NARRATION_HOUR,
        SettingKeys.WORDS_PER_FINISHED_HOUR,
        SettingKeys.DAILY_CAPACITY_HOURS,
        SettingKeys.MAX_BOOKS_PER_DAY,
        SettingKeys.HEAVY_DAY_HOURS,
    )

    override suspend fun load(): Result<StudioSettings> = runCatching {
        val rows = client.from("site_settings")
            .select(Columns.raw("key, value")) {
                filter { isIn("key", keys) }
            }
            .decodeList<SiteSettingDto>()
        studioSettingsFrom(rows.mapNotNull { r -> r.value?.let { r.key to it } }.toMap())
    }

    override suspend fun loadAll(): Result<SiteSettings> = runCatching {
        val rows = client.from("site_settings")
            .select(Columns.raw("key, value")) {
                filter { isIn("key", SettingKeys.ALL) }
            }
            .decodeList<SiteSettingDto>()
        val map = rows.mapNotNull { r -> r.value?.let { r.key to it } }.toMap()
        SiteSettings(
            acceptingProjects = map[SettingKeys.ACCEPTING_PROJECTS]?.toBooleanStrictOrNull(),
            // Parsed without sorting. The stored order IS the booking window.
            availableMonths = map[SettingKeys.AVAILABLE_MONTHS]
                ?.trim()?.removePrefix("[")?.removeSuffix("]")
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                .orEmpty(),
            studio = studioSettingsFrom(map),
        )
    }
}
