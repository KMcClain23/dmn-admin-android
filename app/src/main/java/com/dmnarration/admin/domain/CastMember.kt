package com.dmnarration.admin.domain

/**
 * One narrator ON A GIVEN BOOK. The cast, not the roster.
 *
 * `narrators_for_editor` returns all nineteen people, which is a directory.
 * Offering that as the assignee list for a two-hander is how a pickup reaches
 * somebody who never read the chapter — and 27 of 33 books have exactly one
 * co-narrator, so the list was nineteen names to choose between two.
 *
 * `isOwner` marks whose book it is. It is NOT viewer-aware: card_cast never
 * reads auth.uid() and cannot know who is calling, so it must never be rendered
 * as "you" — the web made exactly that mistake and told Marizete she was Dean.
 */
data class CastMember(
    val narratorId: String,
    val displayName: String,
    val isOwner: Boolean,
)
