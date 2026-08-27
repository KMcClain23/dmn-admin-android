package com.dmnarration.admin.domain

import kotlin.time.Instant

/**
 * An archived card, for a screen whose job is recovery rather than browsing.
 *
 * `status` is carried because un-archiving returns the card to the board under
 * the status it already had — the archive did not change it and neither does
 * the restore — and the screen says so before Dean commits to the write.
 */
data class ArchivedCard(
    override val id: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val archivedAt: Instant?,
    /** The stored value, not a label. Unrecognised values are shown, not hidden. */
    val archivedReason: String?,
    val archivedNotes: String?,
    val status: String,
) : Identified

/**
 * Still archived, as far as this screen is concerned.
 *
 * The same shape the board uses for the other direction: an optimistic
 * un-archive clears `archivedAt` locally and the row drops out of the projection
 * while staying in the backing list, so a refusal or a timeout can put it back
 * verbatim rather than re-fetching to find out what it was.
 */
fun stillArchived(all: List<ArchivedCard>): List<ArchivedCard> =
    all.filter { it.archivedAt != null }

/**
 * The reason as a person reads it, falling back to the stored value.
 *
 * An unrecognised reason renders raw rather than as "Other". The stored string
 * is evidence; replacing it with a guess would hide the fact that something
 * wrote a value this app does not know about — which is exactly how the
 * `recast` status and the `recasted` archive reason came to look like one idea.
 */
fun archiveReasonLabel(stored: String?): String? {
    if (stored.isNullOrBlank()) return null
    return ArchiveReason.entries.firstOrNull { it.stored == stored }?.label ?: stored
}

/**
 * The patch that un-archives a card: all three fields, cleared together.
 *
 * All three and not just the timestamp. Verified against the database: no row
 * anywhere carries an `archived_reason` or `archived_notes` with a null
 * `archived_at`, so clearing only the timestamp would make this app the first
 * thing to break that invariant, and the orphaned reason would then show up on
 * the next card that was archived and restored.
 *
 * Returned as a list of column names rather than built inline so the test and
 * the write cannot disagree about which columns are involved.
 */
val UNARCHIVE_COLUMNS = listOf("archived_at", "archived_reason", "archived_notes")
