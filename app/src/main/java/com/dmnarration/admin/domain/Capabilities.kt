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
            )
            UserRole.EDITOR -> Capabilities(
                canViewFinancials = false,
                canViewStudioSettings = false,
                canViewConfidentialCovers = false,
                canEdit = false,
                canUseWebAdmin = false,
                canSeeMoney = false,
            )
            UserRole.UNKNOWN -> Capabilities(
                canViewFinancials = false,
                canViewStudioSettings = false,
                canViewConfidentialCovers = false,
                canEdit = false,
                canUseWebAdmin = false,
                canSeeMoney = false,
            )
        }
    }
}
