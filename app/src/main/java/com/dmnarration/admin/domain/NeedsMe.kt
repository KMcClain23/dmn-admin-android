package com.dmnarration.admin.domain

/**
 * One pickup that is waiting on the SIGNED-IN PERSON right now.
 *
 * The role decides what that means and the DATABASE decides it, not this app:
 * `pickups_needing_me()` returns an admin's `sent` rows — lines he still owes a
 * booth — and an editor's `returned` ones, which a narrator has re-recorded and
 * is waiting for her to check.
 *
 * That is deliberately not a filter the client applies afterwards. Two surfaces
 * show this (the Today screen and the Editing tab) and the website shows the
 * same idea in its sidebar; three copies of "which rows count" is how one rule
 * becomes three that disagree.
 *
 * `line` is the correction itself, and it is the reason the row exists. It is
 * never truncated in the UI: an abbreviated line is a line you have to open the
 * card to read, which defeats the point of showing it.
 */
data class NeedsMe(
    val id: String,
    val cardId: String,
    val bookTitle: String,
    val chapter: String,
    val timestampAt: String,
    val kind: PickupKind,
    val said: String,
    val shouldBe: String,
    val note: String,
    val status: PickupStatus,
) {
    /**
     * What to show as the correction.
     *
     * A misread carries both halves and the pair IS the instruction, so it is
     * rendered as one. Everything else carries a note. Nothing here invents
     * text: an empty result renders as nothing rather than as a placeholder
     * standing in for a line somebody did write.
     */
    val line: String
        get() = when {
            said.isNotBlank() && shouldBe.isNotBlank() -> "“$said” → “$shouldBe”"
            note.isNotBlank() -> note
            shouldBe.isNotBlank() -> shouldBe
            else -> said
        }
}

/**
 * The assignment picture for one book, from `editor_assignments()`.
 *
 * A ROW EXISTS WHEN A BOOK IS CLAIMED **OR** EDITED OUTSIDE, so editorId and
 * editorName are both nullable now: a book somebody else is posting has an
 * assignment fact without having an editor here. A card absent from the result
 * is unclaimed and claimable.
 */
data class EditorAssignment(
    val cardId: String,
    val editorId: String?,
    val editorName: String?,
    /** Somebody outside is doing the post — not hers to take. */
    val editedExternally: Boolean,
)
