package com.dmnarration.admin.data

import com.dmnarration.admin.domain.BoardCard
import com.dmnarration.admin.domain.SettingKeys
import com.dmnarration.admin.domain.StudioSettings
import com.dmnarration.admin.domain.UserRole
import com.dmnarration.admin.domain.studioSettingsFrom
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The board query, mirroring `/api/board-v2/cards`.
 *
 * Two things are resolved from the role here and nowhere else in the app: which
 * relation to read, and which columns to ask for. That is the entire hook for
 * the eventual editor — a view named in one constant and a column list that
 * narrows — and it is why neither may leak upward into a ViewModel or a
 * composable.
 *
 * `select("*")` is never used. An explicit list is what stops a column added to
 * board_cards next year from silently reaching a client that should not see it,
 * and what makes swapping in a narrower view a drop-in rather than a rewrite.
 */
/**
 * This role has no board to read — not an error to retry, a capability the
 * account does not have yet.
 */
class BoardAccessNotEnabledException(val role: UserRole) :
    Exception("no board source for role $role")

@Singleton
class BoardRepository @Inject constructor(
    private val client: SupabaseClient,
) {

    /**
     * Active, non-archived work only: 'released' belongs on the Released page
     * and 'audition' is not yet active production. Note that this excludes the
     * one archived 'recording' card, which is why the board shows 20 and not 21.
     */
    private val activeStatuses = listOf("contracted", "prepping", "recording", "editing")

    /**
     * The relation this role reads, or null when it has none.
     *
     * `board_cards_editor` does not exist until F3, and an editor role is one
     * UPDATE to profiles.role away — not the "unreachable" this comment used to
     * claim. Asking for a missing view answers PGRST205, which surfaced to a
     * real person as a schema-cache error on the board.
     *
     * Deliberately NOT falling back to `board_cards` for an editor. RLS would
     * answer zero rows and they would see an ordinary empty board,
     * indistinguishable from "you have no projects" — a silent wrong answer in
     * place of a loud one. Fail closed, and say which.
     */
    private fun sourceFor(role: UserRole): String? = when (role) {
        UserRole.ADMIN -> "board_cards"
        UserRole.EDITOR -> null
        UserRole.UNKNOWN -> null
    }

    private fun columnsFor(role: UserRole): String = when (role) {
        UserRole.ADMIN -> ADMIN_COLUMNS
        UserRole.EDITOR -> EDITOR_COLUMNS
        UserRole.UNKNOWN -> ADMIN_COLUMNS // unreachable: sourceFor already refused
    }

    suspend fun loadBoard(role: UserRole): Result<List<BoardCard>> = runCatching {
        val source = sourceFor(role) ?: throw BoardAccessNotEnabledException(role)
        client.from(source)
            .select(Columns.raw(columnsFor(role))) {
                filter {
                    isIn("status", activeStatuses)
                    exact("archived_at", null)
                }
            }
            .decodeList<BoardCardDto>()
            .map { it.toDomain() }
    }

    private companion object {
        /** Exactly the list `/api/board-v2/cards` selects. */
        const val ADMIN_COLUMNS =
            "id, title, author, co_narrator, cover_url, status, deadline, first15_due, " +
                "first_15_complete, word_count, pfh_rate, payment_type, is_confidential, " +
                "narration_format, narrator_share_percent, recording_dates, words_recorded, created_at"

        /**
         * The same list minus everything financial or confidential. Written now
         * so the shape is decided while the reasoning is fresh; nothing reads it
         * until the editor exists and `board_cards_editor` is created to match.
         */
        const val EDITOR_COLUMNS =
            "id, title, author, co_narrator, cover_url, status, deadline, first15_due, " +
                "first_15_complete, word_count, narration_format, narrator_share_percent, " +
                "recording_dates, words_recorded, created_at"
    }
}

/**
 * The five tunable numbers.
 *
 * Only ever queried when the session may read them. An editor has no policy
 * granting select on site_settings, so asking would produce an error that looks
 * like a bug rather than a rule — the caller checks the capability and falls
 * back to DEFAULT_STUDIO_SETTINGS instead.
 */
@Singleton
class StudioSettingsRepository @Inject constructor(
    private val client: SupabaseClient,
) {
    private val keys = listOf(
        SettingKeys.WORDS_PER_NARRATION_HOUR,
        SettingKeys.WORDS_PER_FINISHED_HOUR,
        SettingKeys.DAILY_CAPACITY_HOURS,
        SettingKeys.MAX_BOOKS_PER_DAY,
        SettingKeys.HEAVY_DAY_HOURS,
    )

    suspend fun load(): Result<StudioSettings> = runCatching {
        val rows = client.from("site_settings")
            .select(Columns.raw("key, value")) {
                filter { isIn("key", keys) }
            }
            .decodeList<SiteSettingDto>()
        studioSettingsFrom(rows.mapNotNull { r -> r.value?.let { r.key to it } }.toMap())
    }
}
