package com.dmnarration.admin.data

import com.dmnarration.admin.domain.ArchivedCard
import com.dmnarration.admin.domain.Expense
import com.dmnarration.admin.domain.Payment
import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.ReleasedBook
import com.dmnarration.admin.domain.UNARCHIVE_COLUMNS
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.SettingKeys
import com.dmnarration.admin.domain.SiteSettings
import com.dmnarration.admin.domain.StudioSettings
import com.dmnarration.admin.domain.StudioSettingsRead
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

    /**
     * Every released book, archived ones included.
     *
     * The archived ones are returned rather than filtered away so that both
     * "how many has he released" and "which are visible" come from one query
     * with the predicate applied where it is read. Refusal is
     * [BoardAccessNotEnabledException], never an empty list.
     */
    suspend fun released(): Result<List<ReleasedBook>>

    /** Everything archived, whatever its status. Refusal is an exception, not zero rows. */
    suspend fun archived(): Result<List<ArchivedCard>>

    /**
     * Put an archived card back, clearing all three archive fields together.
     *
     * Same contract as [updateCard] and for the same reason: null means the
     * server accepted the statement and changed nothing, which is RLS refusing
     * the row and arrives wearing HTTP 200.
     */
    suspend fun unarchive(cardId: String): Result<ArchivedCard?>

    /**
     * Money that has moved. NOT what is owed — nothing in this app computes that.
     *
     * Refusal is [BoardAccessNotEnabledException], never an empty list: a
     * financial screen showing nothing must never be reachable by failing.
     */
    suspend fun payments(): Result<List<Payment>>

    /** Every expense, as stored. Refusal is an exception, not zero rows. */
    suspend fun expenses(): Result<List<Expense>>
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

    /**
     * Order comes from the function, not from here.
     *
     * `released_for_session()` orders by `released_at desc nulls last, title
     * asc`, matching `/api/released/route.ts` down to the tiebreak. Re-sorting
     * the list in Kotlin would be a second implementation of that ordering,
     * free to drift from the one the web uses; the list is rendered in the order
     * it arrives and `ShelfTest` pins that.
     */
    override suspend fun released(): Result<List<ReleasedBook>> = runCatching {
        try {
            client.postgrest.rpc(RELEASED_RPC)
                .decodeList<ReleasedBookDto>()
                .map { it.toDomain() }
        } catch (t: Throwable) {
            if (t.isBoardAccessRefusal()) throw BoardAccessNotEnabledException() else throw t
        }
    }

    override suspend fun archived(): Result<List<ArchivedCard>> = runCatching {
        try {
            client.postgrest.rpc(ARCHIVED_RPC)
                .decodeList<ArchivedCardDto>()
                .map { it.toDomain() }
        } catch (t: Throwable) {
            if (t.isBoardAccessRefusal()) throw BoardAccessNotEnabledException() else throw t
        }
    }

    /**
     * The patch is built from [UNARCHIVE_COLUMNS], so the columns the write
     * clears and the columns the test asserts on are the same list rather than
     * two lists that have to be kept in step.
     */
    override suspend fun unarchive(cardId: String): Result<ArchivedCard?> = runCatching {
        client.from("board_cards")
            .update(buildJsonObject { UNARCHIVE_COLUMNS.forEach { put(it, JsonNull) } }) {
                select(Columns.raw(ARCHIVED_COLUMNS))
                filter { eq("id", cardId) }
            }
            .decodeList<ArchivedCardDto>()
            .firstOrNull()
            ?.toDomain()
    }

    /**
     * Order comes from the function: `received_on desc nulls last`, then
     * sort_order, then label. Re-sorting here would be a second opinion about
     * an ordering the database already states.
     */
    override suspend fun payments(): Result<List<Payment>> = runCatching {
        try {
            client.postgrest.rpc(PAYMENTS_RPC)
                .decodeList<PaymentDto>()
                .map { it.toDomain() }
        } catch (t: Throwable) {
            if (t.isBoardAccessRefusal()) throw BoardAccessNotEnabledException() else throw t
        }
    }

    override suspend fun expenses(): Result<List<Expense>> = runCatching {
        try {
            client.postgrest.rpc(EXPENSES_RPC)
                .decodeList<ExpenseDto>()
                .map { it.toDomain() }
        } catch (t: Throwable) {
            if (t.isBoardAccessRefusal()) throw BoardAccessNotEnabledException() else throw t
        }
    }

    private companion object {
        const val BOARD_RPC = "board_for_session"
        const val CARD_RPC = "card_detail"
        const val PAYMENTS_RPC = "payments_for_session"
        const val EXPENSES_RPC = "expenses_for_session"
        const val RELEASED_RPC = "released_for_session"
        const val ARCHIVED_RPC = "archived_for_session"

        /** The un-archive's return shape; the reads' lists live in the RPC signatures. */
        const val ARCHIVED_COLUMNS =
            "id, title, author, cover_url, archived_at, archived_reason, archived_notes, status"

        /** The write's return shape. The read's list lives in the RPC signature. */
        const val ADMIN_COLUMNS =
            "id, title, author, co_narrator, cover_url, status, deadline, first15_due, " +
                "first_15_complete, word_count, pfh_rate, payment_type, is_confidential, " +
                "narration_format, narrator_share_percent, recording_dates, words_recorded, created_at, " +
                "total_pages, current_page, " +
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
    suspend fun load(): Result<StudioSettingsRead>

    /**
     * Everything the table holds, for the Settings screen.
     *
     * Read-only by the schema, not by convention: `site_settings` has a `Role read`
     * policy and no update policy at all, so there is deliberately no counterpart to
     * this and adding one needs a migration first.
     */
    suspend fun loadAll(): Result<SiteSettings>

    /**
     * Write one setting's `value`, and report what actually happened to it.
     *
     *   success(value) — the server returned the row; its value is the truth.
     *   success(null)  — zero rows. RLS refused this row, wearing HTTP 200.
     *   failure(t)     — rejected by the rule in the database, or a transport
     *                    failure. [serverRefusalMessage] tells them apart.
     *
     * `key` is never written: the column grant is `update (value)` only, so a
     * client physically cannot rename a setting. The filter selects the row; the
     * patch carries one column.
     */
    suspend fun updateSetting(key: String, value: String): Result<String?>
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

    override suspend fun load(): Result<StudioSettingsRead> = runCatching {
        val rows = client.from("site_settings")
            .select(Columns.raw("key, value")) {
                filter { isIn("key", keys) }
            }
            .decodeList<SiteSettingDto>()
        studioSettingsFrom(rows.mapNotNull { r -> r.value?.let { r.key to it } }.toMap())
    }

    /**
     * `select()` is what makes PostgREST return the affected rows at all.
     * Without it a refused write succeeds silently and is indistinguishable
     * from a save — Stage 2's bug 5, on the table that drives money figures.
     *
     * `updated_at` is not sent: `authenticated` has no grant for it and the
     * trigger stamps it. Nothing here computes a timestamp.
     */
    override suspend fun updateSetting(key: String, value: String): Result<String?> = runCatching {
        client.from("site_settings")
            .update(buildJsonObject { put("value", value) }) {
                select(Columns.raw("key, value"))
                filter { eq("key", key) }
            }
            .decodeList<SiteSettingDto>()
            .firstOrNull()
            ?.value
    }

    override suspend fun loadAll(): Result<SiteSettings> = runCatching {
        val rows = client.from("site_settings")
            .select(Columns.raw("key, value")) {
                filter { isIn("key", SettingKeys.ALL) }
            }
            .decodeList<SiteSettingDto>()
        val map = rows.mapNotNull { r -> r.value?.let { r.key to it } }.toMap()
        val rawMonths = map[SettingKeys.AVAILABLE_MONTHS]
        // Parsed without sorting: the stored order IS the booking window. Null when
        // the value cannot be read as months, which is not the same as no months —
        // `.orEmpty()` used to render both as "None".
        val months = rawMonths
            ?.trim()?.removePrefix("[")?.removeSuffix("]")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.let { parts ->
                val ints = parts.mapNotNull { it.toIntOrNull() }
                if (ints.size == parts.size && ints.all { it in 1..12 }) ints else null
            }

        SiteSettings(
            acceptingProjects = map[SettingKeys.ACCEPTING_PROJECTS]?.toBooleanStrictOrNull(),
            availableMonths = months,
            availableMonthsRaw = rawMonths?.takeIf { months == null },
            acceptingProjectsRaw = map[SettingKeys.ACCEPTING_PROJECTS]
                ?.takeIf { it.toBooleanStrictOrNull() == null },
            studio = studioSettingsFrom(map),
        )
    }
}
