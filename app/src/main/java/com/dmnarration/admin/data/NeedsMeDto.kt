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

/** One row of `editor_assignments()`. Only CLAIMED books appear. */
@Serializable
data class EditorAssignmentDto(
    val card_id: String,
    val editor_id: String,
    val editor_name: String = "",
)

fun EditorAssignmentDto.toDomain(): EditorAssignment = EditorAssignment(
    cardId = card_id,
    editorId = editor_id,
    editorName = editor_name,
)
