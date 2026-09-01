package com.dmnarration.admin.domain

/**
 * The application role, read from `public.profiles` on every cold start.
 *
 * UNKNOWN is not a placeholder. It is what a missing profile row, an
 * unreadable one, or a role string this build does not recognise all map to,
 * and it must fail closed: it grants nothing and the UI shows an error rather
 * than a board. A session whose permissions cannot be established is not a
 * session to proceed with.
 */
enum class UserRole {
    ADMIN,
    EDITOR,
    UNKNOWN;

    /**
     * Whether this session's permissions are actually known.
     *
     * The one question the UI has to ask about a role, named here so it does
     * not get asked as `role == UNKNOWN` in three different files — which is
     * how a fourth role, or a change to what "usable" means, ends up half
     * applied.
     */
    val isRecognised: Boolean get() = this != UNKNOWN

    companion object {
        /** Anything unrecognised is UNKNOWN — never a default. */
        fun fromStored(value: String?): UserRole = when (value?.trim()?.lowercase()) {
            "admin" -> ADMIN
            "editor" -> EDITOR
            else -> UNKNOWN
        }
    }
}

/**
 * What this session may see, derived from the role exactly once.
 *
 * Composables branch on these and never on UserRole. That is not ceremony: an
 * `if (role == ADMIN)` scattered through the UI is how a fourth role, or a
 * change to what an editor may see, becomes a hunt through every composable for
 * the checks — and how one gets missed. Here a role's meaning is defined in one
 * place and the card does not know roles exist.
 *
 * `canEdit` was present and false for everyone through Stage 1, so Stage 2
 * could wire into a seam that already existed rather than introducing one.
 * Stage 2 is that wiring: admin may write. Nothing else in the UI learns about
 * roles — the gestures ask this.
 */
data class Capabilities(
    /** pfh_rate, payment_type, estimated earnings. */
    val canViewFinancials: Boolean,
    /**
     * Whether the Settings screen EXISTS for this account — reachability, not
     * just whether its fetch runs.
     *
     * ── THE ORDERING RULE, WHICH IS THE WHOLE POINT OF THIS COMMENT ─────────
     *
     * This gate must land BEFORE OR WITH any fix to the Settings screen's
     * reading of `site_settings`. NEVER AFTER.
     *
     * For a while it gated only a fetch in BoardViewModel, so an editor could
     * open the screen and see Dean's availability, rates and capacity. It leaked
     * nothing solely because a SECOND bug stopped the screen reading any value —
     * every row said "Not set in site_settings" for admin and editor alike,
     * while all five rows are in fact populated (words_per_finished_hour 9400,
     * words_per_narration_hour 5000, daily_capacity 6, heavy_day 4,
     * max_books 2).
     *
     * Two bugs cancelling is not safety. The "Not set" bug looks cosmetic and
     * somebody will fix it; the moment they do, Dean's rates appear on his
     * editor's phone. That is why reachability is gated here first and the
     * reader bug is deliberately left alone in this release.
     *
     * Sign out is NOT lost by hiding this: it lives on the board's overflow menu
     * and on the Editing screen's, both reachable by an editor.
     */
    val canViewStudioSettings: Boolean,
    val canViewConfidentialCovers: Boolean,
    val canEdit: Boolean,
    /**
     * Whether the "Edit on web" escape hatch is worth offering.
     *
     * Its referent is F2, not F3. The web admin authenticates with one shared
     * secret and reads everything through the service-role key — it has no
     * concept of users at all — so an editor cannot use it even after F3 grants
     * them the board here. This stays false for them until that migration
     * happens, which is a different question from whether they may edit.
     */
    val canUseWebAdmin: Boolean,
    /**
     * Whether Payments and Expenses exist for this account at all.
     *
     * The first capability that hides a WHOLE TAB rather than a field, which is
     * the architecture this type was built for in Stage 0 and has been carrying
     * without ever gating anything. It earns itself here because both screens
     * are financial end to end — there is no non-financial content to keep, so
     * per-field gating has nothing to gate and the honest unit is the tab.
     *
     * Distinct from `canViewFinancials`, which decides whether a rate appears on
     * a card that is otherwise perfectly readable. Same role answer today; two
     * different questions, and merging them would mean a future role that may
     * see a card's earnings automatically gets the whole ledger.
     *
     * ABSENT, not disabled and not empty. A disabled tab advertises a room the
     * account may not enter; an empty one says there is no money, which is a
     * claim about Dean's finances that nobody made.
     */
    val canSeeMoney: Boolean,

    /**
     * Whether this account may CLOSE a pickup the narrator has sent back.
     *
     * Separate from canEdit on purpose. resolve_pickup admits editor OR admin
     * since P1 — verification is Marizete's job, she is the one who listens to
     * the new audio — but canEdit is false for editors and also gates board
     * gestures, card editing and archiving. Reusing it would have granted her the
     * whole board to give her one button.
     */
    val canVerifyPickups: Boolean,
) {
    companion object {
        fun of(role: UserRole): Capabilities = when (role) {
            UserRole.ADMIN -> Capabilities(
                canViewFinancials = true,
                canViewStudioSettings = true,
                canViewConfidentialCovers = true,
                canEdit = true,
                canUseWebAdmin = true,
                canSeeMoney = true,
                canVerifyPickups = true,
            )
            UserRole.EDITOR -> Capabilities(
                canViewFinancials = false,
                canViewStudioSettings = false,
                canViewConfidentialCovers = false,
                canEdit = false,
                canUseWebAdmin = false,
                canSeeMoney = false,
                // The one thing an editor may do that canEdit does not cover.
                canVerifyPickups = true,
            )
            UserRole.UNKNOWN -> Capabilities(
                canViewFinancials = false,
                canViewStudioSettings = false,
                canViewConfidentialCovers = false,
                canEdit = false,
                canUseWebAdmin = false,
                canSeeMoney = false,
                canVerifyPickups = false,
            )
        }
    }
}
