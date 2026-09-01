package com.dmnarration.admin.data

import com.dmnarration.admin.domain.ArchivedCard
import com.dmnarration.admin.domain.Expense
import com.dmnarration.admin.domain.Payment
import com.dmnarration.admin.domain.Payout
import com.dmnarration.admin.domain.PayoutSummary
import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.CareerTotals
import com.dmnarration.admin.domain.ReleasedBook
import com.dmnarration.admin.domain.UNARCHIVE_COLUMNS
import io.github.jan.supabase.functions.functions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.Pickup
import com.dmnarration.admin.domain.CastMember
import com.dmnarration.admin.domain.EditorAssignment
import com.dmnarration.admin.domain.NeedsMe
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import com.dmnarration.admin.domain.PickupKind
import com.dmnarration.admin.domain.PickupStatus
import kotlinx.serialization.json.JsonObjectBuilder
import com.dmnarration.admin.domain.UserRole
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
    /**
     * The role is PASSED IN, never cached here.
     *
     * Choosing the relation from a role the client had cached is what produced
     * bug 6, and the read path was deliberately left with no dispatch in it. A
     * second role brings dispatch back, because the two roles genuinely read
     * different relations — an editor is refused by board_for_session() and
     * board_for_editor() is the only thing she can call.
     *
     * What makes it safe is that the dispatch is a HINT and not the boundary. A
     * stale ADMIN still calls board_for_session(), and the server still refuses;
     * the failure is loud and closed, exactly as before. The direction that
     * degrades quietly — a stale EDITOR calling board_for_editor() — costs an
     * admin the money columns on the board and leaks nothing.
     *
     * UNKNOWN calls nothing at all. It is not a role to guess at.
     */
    suspend fun loadBoard(role: UserRole): Result<List<BoardCard>>
    suspend fun updateCard(cardId: String, patch: JsonObject): Result<BoardCard?>

    /**
     * The four financial columns, which no longer have a direct write path.
     *
     * `authenticated` had its UPDATE grant on pfh_rate, payment_type,
     * narrator_share_percent and royalty_split_percent revoked, because RLS
     * alone was the single layer protecting them — a non-admin attempt was
     * filtered to zero rows rather than refused, which looked like success.
     * set_card_financial is admin-gated and names the four columns statically.
     */
    suspend fun setCardFinancial(cardId: String, column: String, value: String): Result<Unit>

    /**
     * One card in full. Null means the id matched nothing.
     *
     * A refusal is [CardAccessNotEnabledException], never null, because a card that
     * was archived, a card that never existed and a role that was revoked would
     * otherwise all arrive as the same empty answer — the ambiguity Stage 2's bug 5
     * was made of.
     */
    /**
     * The role is PASSED IN, for the same reasons as [loadBoard]: the dispatch
     * is a hint, never the boundary. A stale ADMIN calls card_detail() and the
     * SERVER refuses; a stale EDITOR calls card_detail_for_editor() and gets a
     * card without the money fields. UNKNOWN calls nothing.
     *
     * Both functions take `p_id`, so the dispatch chooses a NAME and nothing
     * else — a dispatch that also had to remember a different argument name
     * would be a second thing to get wrong.
     */
    suspend fun cardDetail(cardId: String, role: UserRole): Result<CardDetail?>

    /**
     * Every released book, archived ones included.
     *
     * The archived ones are returned rather than filtered away so that both
     * "how many has he released" and "which are visible" come from one query
     * with the predicate applied where it is read. Refusal is
     * [BoardAccessNotEnabledException], never an empty list.
     */
    /**
     * Every pickup, routed by role exactly as the board reads are.
     *
     * The two functions return the same shape and differ only in their gate, so
     * a stale ADMIN hits pickups_for_session and the server refuses; a stale
     * EDITOR hits pickups_for_editor and gets the same rows. UNKNOWN calls
     * nothing.
     */
    suspend fun pickups(role: UserRole): Result<List<Pickup>>

    /**
     * The editor's writes. Every one goes through a SECURITY DEFINER function
     * with a role gate — `authenticated` has no write grant on board_cards for
     * these columns and no grant at all on pickups, so there is no other route.
     */
    suspend fun setEditingProgress(cardId: String, chaptersEdited: Int?, chaptersTotal: Int?): Result<Unit>
    suspend fun setEditingComplete(cardId: String, complete: Boolean): Result<Unit>
    /** Narrators an editor may assign to. Name and id only; no email. */
    /**
     * THIS BOOK'S CAST, not the nineteen-name roster.
     *
     * card_cast RAISES on an unparseable co_narrator or a name with no narrators
     * row. That is deliberate and must NOT be swallowed into an empty list: a
     * cast quietly short by one is a pickup assigned to the wrong person, and an
     * empty picker at least says something is wrong.
     */
    suspend fun cardCast(cardId: String): Result<List<CastMember>>

    /**
     * What is waiting on the signed-in person, whoever they are.
     *
     * NO ROLE PARAMETER, and that is the difference from `pickups(role)`. The
     * function resolves the role itself and returns an admin's `sent` rows or an
     * editor's `returned` ones, so the answer to "which rows count" lives in one
     * place. Passing a role here would invite the client to decide, and the
     * client deciding permissions from its own copy is bug 6.
     */
    suspend fun pickupsNeedingMe(): Result<List<NeedsMe>>

    /** Who holds which book. Only claimed books come back; absence is unclaimed. */
    suspend fun editorAssignments(): Result<List<EditorAssignment>>

    suspend fun claimCard(cardId: String): Result<Unit>

    suspend fun releaseCard(cardId: String): Result<Unit>

    /** Moves a SENT pickup to RETURNED. The narrator has re-recorded it. */
    suspend fun markPickupReturned(id: String): Result<Unit>

    /** ADMIN ONLY. Permanently removes a pickup. Not the same as dismissing it. */
    suspend fun deletePickup(id: String): Result<Unit>

    suspend fun createPickup(
        cardId: String, chapter: String, timestampAt: String, kind: PickupKind,
        said: String, shouldBe: String, note: String, assignedNarratorId: String?,
    ): Result<String>

    suspend fun updateOwnDraftPickup(
        id: String, chapter: String, timestampAt: String, kind: PickupKind,
        said: String, shouldBe: String, note: String, assignedNarratorId: String?,
    ): Result<Unit>

    suspend fun deleteOwnDraftPickup(id: String): Result<Unit>

    /**
     * Send a chapter's pickups: email each narrator, THEN mark them sent.
     *
     * Calls the send-pickups Edge Function rather than the RPC directly. The
     * ordering guarantee lives there — a failed email must leave everything
     * DRAFT — and calling send_chapter_pickups from here would skip the email
     * entirely while still marking the work done.
     */
    suspend fun sendChapterPickups(cardId: String, chapter: String): Result<SendPickupsResult>

    /** ADMIN ONLY. Gated by assert_board_access; an editor is refused. */
    suspend fun resolvePickup(id: String, status: PickupStatus): Result<Unit>

    suspend fun released(): Result<List<ReleasedBook>>

    /** Everything archived, whatever its status. Refusal is an exception, not zero rows. */
    suspend fun archived(): Result<List<ArchivedCard>>

    /**
     * Words narrated across the career, in three categories.
     *
     * Its own RPC because no list covers the population: board_for_session
     * excludes released, and released_for_session carries no words_recorded.
     * The categories are decided in the function, so this app cannot filter
     * differently from the figure it displays.
     */
    suspend fun careerTotals(): Result<CareerTotals?>

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

    /**
     * Money going OUT. Admin-only at the database: the RPC is SECURITY INVOKER
     * and payment_payouts carries a "Role read" policy, so a non-admin session
     * gets an EMPTY LIST rather than an error. Empty is therefore not evidence
     * that there are no payouts — only that this session may not see any.
     */
    suspend fun payouts(): Result<List<Payout>>

    /** The payout position. Null when it could not be read — never a zeroed pair. */
    suspend fun payoutSummary(): Result<PayoutSummary?>

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

    override suspend fun loadBoard(role: UserRole): Result<List<BoardCard>> = runCatching {
        // Fail closed. UNKNOWN is a session whose permissions could not be
        // established, and there is no relation that is safe to guess for it.
        // Deliberately NOT a fallback to the editor read: a fallback would turn
        // a routing bug into a quietly narrower board.
        val rpc = when (role) {
            UserRole.ADMIN -> BOARD_RPC
            UserRole.EDITOR -> BOARD_EDITOR_RPC
            UserRole.UNKNOWN -> throw BoardAccessNotEnabledException()
        }
        try {
            client.postgrest.rpc(rpc)
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
    override suspend fun setCardFinancial(
        cardId: String,
        column: String,
        value: String,
    ): Result<Unit> = rpcUnit("set_card_financial") {
        put("p_card_id", cardId)
        put("p_column", column)
        put("p_value", value)
    }

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

    override suspend fun cardDetail(cardId: String, role: UserRole): Result<CardDetail?> = runCatching {
        // Fail closed, and deliberately NOT a fallback to the editor read: a
        // fallback would turn a routing bug into a quietly narrower card.
        val rpc = when (role) {
            UserRole.ADMIN -> CARD_RPC
            UserRole.EDITOR -> CARD_EDITOR_RPC
            UserRole.UNKNOWN -> throw CardAccessNotEnabledException()
        }
        try {
            client.postgrest.rpc(rpc, buildJsonObject { put("p_id", cardId) })
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
    override suspend fun pickups(role: UserRole): Result<List<Pickup>> = runCatching {
        val rpc = when (role) {
            UserRole.ADMIN -> PICKUPS_RPC
            UserRole.EDITOR -> PICKUPS_EDITOR_RPC
            UserRole.UNKNOWN -> throw BoardAccessNotEnabledException()
        }
        try {
            client.postgrest.rpc(rpc).decodeList<PickupDto>().map { it.toDomain() }
        } catch (t: Throwable) {
            if (t.isBoardAccessRefusal()) throw BoardAccessNotEnabledException() else throw t
        }
    }

    override suspend fun setEditingProgress(
        cardId: String,
        chaptersEdited: Int?,
        chaptersTotal: Int?,
    ): Result<Unit> = rpcUnit("set_editing_progress") {
        put("p_card_id", cardId)
        if (chaptersEdited == null) put("p_chapters_edited", JsonNull) else put("p_chapters_edited", chaptersEdited)
        if (chaptersTotal == null) put("p_chapters_total", JsonNull) else put("p_chapters_total", chaptersTotal)
    }

    override suspend fun setEditingComplete(cardId: String, complete: Boolean): Result<Unit> =
        rpcUnit("set_editing_complete") {
            put("p_card_id", cardId)
            put("p_complete", complete)
        }

    override suspend fun cardCast(cardId: String): Result<List<CastMember>> = runCatching {
        try {
            client.postgrest.rpc("card_cast", buildJsonObject { put("p_card_id", cardId) })
                .decodeList<CastMemberDto>().map { it.toDomain() }
        } catch (t: Throwable) {
            if (t.isBoardAccessRefusal()) throw BoardAccessNotEnabledException() else throw t
        }
    }

    override suspend fun pickupsNeedingMe(): Result<List<NeedsMe>> = runCatching {
        try {
            client.postgrest.rpc("pickups_needing_me")
                .decodeList<NeedsMeDto>().map { it.toDomain() }
        } catch (t: Throwable) {
            if (t.isBoardAccessRefusal()) throw BoardAccessNotEnabledException() else throw t
        }
    }

    override suspend fun editorAssignments(): Result<List<EditorAssignment>> = runCatching {
        try {
            client.postgrest.rpc("editor_assignments")
                .decodeList<EditorAssignmentDto>().map { it.toDomain() }
        } catch (t: Throwable) {
            if (t.isBoardAccessRefusal()) throw BoardAccessNotEnabledException() else throw t
        }
    }

    override suspend fun claimCard(cardId: String): Result<Unit> =
        rpcUnit("claim_card_for_editing") { put("p_card_id", cardId) }

    override suspend fun releaseCard(cardId: String): Result<Unit> =
        rpcUnit("release_card_editing") { put("p_card_id", cardId) }

    override suspend fun markPickupReturned(id: String): Result<Unit> =
        rpcUnit("mark_pickup_returned") { put("p_id", id) }

    override suspend fun deletePickup(id: String): Result<Unit> =
        rpcUnit("delete_pickup") { put("p_id", id) }


    override suspend fun createPickup(
        cardId: String, chapter: String, timestampAt: String, kind: PickupKind,
        said: String, shouldBe: String, note: String, assignedNarratorId: String?,
    ): Result<String> = runCatching {
        try {
            client.postgrest.rpc("create_pickup", buildJsonObject {
                put("p_card_id", cardId); put("p_chapter", chapter)
                put("p_timestamp_at", timestampAt); put("p_kind", kind.stored)
                put("p_said", said); put("p_should_be", shouldBe)
                put("p_note", note)
                if (assignedNarratorId == null) put("p_assigned_narrator_id", JsonNull)
                else put("p_assigned_narrator_id", assignedNarratorId)
            }).decodeAs<String>()
        } catch (t: Throwable) {
            if (t.isBoardAccessRefusal()) throw BoardAccessNotEnabledException() else throw t
        }
    }

    override suspend fun updateOwnDraftPickup(
        id: String, chapter: String, timestampAt: String, kind: PickupKind,
        said: String, shouldBe: String, note: String, assignedNarratorId: String?,
    ): Result<Unit> = rpcUnit("update_own_draft_pickup") {
        put("p_id", id); put("p_chapter", chapter)
        put("p_timestamp_at", timestampAt); put("p_kind", kind.stored)
        put("p_said", said); put("p_should_be", shouldBe)
        put("p_note", note)
        if (assignedNarratorId == null) put("p_assigned_narrator_id", JsonNull)
        else put("p_assigned_narrator_id", assignedNarratorId)
    }

    override suspend fun deleteOwnDraftPickup(id: String): Result<Unit> =
        rpcUnit("delete_own_draft_pickup") { put("p_id", id) }

    override suspend fun sendChapterPickups(
        cardId: String,
        chapter: String,
    ): Result<SendPickupsResult> = runCatching {
        val res = client.functions.invoke("send-pickups") {
            setBody(buildJsonObject { put("cardId", cardId); put("chapter", chapter) })
        }
        val body = res.bodyAsText()
        val parsed = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<SendPickupsResult>(body)
        }.getOrNull()

        // 207 is a PARTIAL success and is NOT an error: some narrators were
        // emailed and some were not, and the caller needs the detail either way.
        // Treating it as a failure would hide the ones that did go out.
        if (res.status.value !in listOf(200, 207)) {
            throw IllegalStateException(parsed?.error ?: "Sending failed (${res.status.value}).")
        }
        parsed ?: throw IllegalStateException("The send endpoint returned something unreadable.")
    }

    override suspend fun resolvePickup(id: String, status: PickupStatus): Result<Unit> =
        rpcUnit("resolve_pickup") {
            put("p_id", id); put("p_status", status.stored)
        }

    /**
     * The shared shape of a void RPC.
     *
     * A refusal is translated once, here, so every write reports it the same
     * way. Anything else keeps its own identity — conflating "no connection"
     * with "you may not do that" is the same confident wrong answer in the other
     * direction.
     */
    private suspend fun rpcUnit(
        name: String,
        args: JsonObjectBuilder.() -> Unit,
    ): Result<Unit> = runCatching {
        try {
            client.postgrest.rpc(name, buildJsonObject(args))
            Unit
        } catch (t: Throwable) {
            if (t.isBoardAccessRefusal()) throw BoardAccessNotEnabledException() else throw t
        }
    }

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
    override suspend fun careerTotals(): Result<CareerTotals?> = runCatching {
        try {
            client.postgrest.rpc(CAREER_RPC)
                .decodeList<CareerTotalsDto>()
                .firstOrNull()
                ?.toDomain()
        } catch (t: Throwable) {
            if (t.isCardAccessRefusal()) throw CardAccessNotEnabledException() else throw t
        }
    }

    override suspend fun payments(): Result<List<Payment>> = runCatching {
        try {
            client.postgrest.rpc(PAYMENTS_RPC)
                .decodeList<PaymentDto>()
                .map { it.toDomain() }
        } catch (t: Throwable) {
            if (t.isBoardAccessRefusal()) throw BoardAccessNotEnabledException() else throw t
        }
    }

    override suspend fun payouts(): Result<List<Payout>> = runCatching {
        try {
            client.postgrest.rpc(PAYOUTS_RPC).decodeList<PayoutDto>().map { it.toDomain() }
        } catch (t: Throwable) {
            if (t.isCardAccessRefusal()) throw CardAccessNotEnabledException() else throw t
        }
    }

    override suspend fun payoutSummary(): Result<PayoutSummary?> = runCatching {
        try {
            client.postgrest.rpc(PAYOUT_SUMMARY_RPC)
                .decodeList<PayoutSummaryDto>().firstOrNull()?.toDomain()
        } catch (t: Throwable) {
            if (t.isCardAccessRefusal()) throw CardAccessNotEnabledException() else throw t
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

        /**
         * The editor's board. Returns the same card shape MINUS pfh_rate,
         * payment_type and narrator_share_percent — omitted from the function's
         * return type, so they are absent from the payload rather than null.
         * BoardCardDto defaults them to null, which is why the same DTO decodes
         * both.
         */
        const val BOARD_EDITOR_RPC = "board_for_editor"
        const val PICKUPS_RPC = "pickups_for_session"
        const val PICKUPS_EDITOR_RPC = "pickups_for_editor"
        const val CARD_RPC = "card_detail"

        /**
         * The editor's card. A strict SUBSET of card_detail()'s columns —
         * pfh_rate, payment_type, narrator_share_percent, royalty_split_percent,
         * production_type, production_company and notes are absent from its
         * return type, so they cannot arrive at all. CardDetailDto defaults them
         * to null, which is why the same DTO decodes both.
         */
        const val CARD_EDITOR_RPC = "card_detail_for_editor"
        const val PAYMENTS_RPC = "payments_for_session"
        const val PAYOUTS_RPC = "payouts_for_session"
        const val PAYOUT_SUMMARY_RPC = "payout_summary_for_session"
        const val EXPENSES_RPC = "expenses_for_session"
        const val RELEASED_RPC = "released_for_session"
        const val ARCHIVED_RPC = "archived_for_session"
        const val CAREER_RPC = "career_totals_for_session"

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
