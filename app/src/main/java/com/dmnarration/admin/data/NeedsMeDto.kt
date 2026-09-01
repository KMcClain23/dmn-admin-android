package com.dmnarration.admin.data

import com.dmnarration.admin.domain.EditorAssignment
import com.dmnarration.admin.domain.NeedsMe
import com.dmnarration.admin.domain.PickupKind
import com.dmnarration.admin.domain.PickupStatus
import kotlinx.serialization.Serializable

/**
 * One row of `pickups_needing_me()`.
 *
 * A NEW FUNCTION RATHER THAN COLUMNS ON AN EXISTING ONE, and that is the whole
 * reason it exists as its own relation. The app decodes with
 * ignoreUnknownKeys = false, so widening `pickups_for_session` to carry the book
 * title would have thrown on every build already installed. A new function is
 * additive to every client that does not call it.
 *
 * Defaults cover a missing key, never an extra one — see PickupDto.
 */
@Serializable
data class NeedsMeDto(
    val id: String,
    val card_id: String,
    val book_title: String = "",
    val chapter: String = "",
    val timestamp_at: String = "",
    val kind: String = "other",
    val said: String = "",
    val should_be: String = "",
    val note: String = "",
    val status: String = "sent",
    val sent_at: String? = null,
)

fun NeedsMeDto.toDomain(): NeedsMe = NeedsMe(
    id = id,
    cardId = card_id,
    bookTitle = book_title,
    chapter = chapter,
    timestampAt = timestamp_at,
    kind = PickupKind.fromStored(kind),
    said = said,
    shouldBe = should_be,
    note = note,
    status = PickupStatus.fromStored(status),
)

/**
 * One row of `editor_assignments()`.
 *
 * ── THIS DTO WENT STALE AND IT WAS NOT CAUGHT ──────────────────────────────
 *
 * The function was widened server-side with `edited_externally`, and its
 * `editor_id` became nullable at the same time — a row now exists for a book
 * nobody holds but somebody outside is editing. Both changes were made for the
 * website without checking this file, and BOTH break decoding here:
 * ignoreUnknownKeys is false, so the extra key throws, and a non-null String
 * cannot take an explicit null.
 *
 * The Editing tab went to "No books are in editing" — a plausible, quiet, and
 * completely wrong answer. Play holds versionCode 49, which has no Editing tab
 * and never calls this, so nothing installed was affected; 0.3.0 would have
 * shipped broken.
 *
 * DecoderExposureTest existed and did not catch it, because it pinned
 * BoardCardDto alone while the audit that motivated it listed fourteen
 * relations. `npm run check-android-dtos` now checks all of them against the
 * live column lists.
 */
@Serializable
data class EditorAssignmentDto(
    val card_id: String,
    /** Null for a book nobody holds that is edited outside. */
    val editor_id: String? = null,
    val editor_name: String? = null,
    val edited_externally: Boolean = false,
)

fun EditorAssignmentDto.toDomain(): EditorAssignment = EditorAssignment(
    cardId = card_id,
    editorId = editor_id,
    editorName = editor_name,
    editedExternally = edited_externally,
)
