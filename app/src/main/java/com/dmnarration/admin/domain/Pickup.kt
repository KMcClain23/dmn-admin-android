package com.dmnarration.admin.domain

import kotlinx.datetime.Instant

/**
 * One re-record request, raised against a chapter.
 *
 * `chapter` is FIRST CLASS and not part of a location string, because two
 * features depend on it: pickups are batched and sent per chapter, and E3 names
 * the email subject from it. A value two features read is not a substring.
 *
 * `assignedNarratorId` is a REAL reference to narrators(id) since E3. It was
 * free text through E2, when the narrators table did not exist; the conversion
 * was done while the table was empty, which made it free.
 */
data class Pickup(
    override val id: String,
    val cardId: String,
    val chapter: String,
    /** "04:32.1", as she reads it in Audacity. Nothing parses it. */
    val timestampAt: String,
    val kind: PickupKind,
    /** The misread pair. Required by the database when kind is MISREAD. */
    val said: String,
    val shouldBe: String,
    val note: String,
    val assignedNarratorId: String?,
    /** Denormalised for display; the read functions join it so the client needs no lookup. */
    val assignedNarratorName: String?,
    val status: PickupStatus,
    val createdBy: String?,
    val createdAt: Instant?,
    val sentAt: Instant?,
    val resolvedAt: Instant?,
    val resolvedBy: String?,
) : Identified {

    val isDraft: Boolean get() = status == PickupStatus.DRAFT

    /**
     * Whether THIS session may still change it.
     *
     * Ownership AND draft — the same two conditions update_own_draft_pickup and
     * delete_own_draft_pickup enforce. Once sent, the email has gone and the
     * record must stop moving, or it would disagree with what the narrator was
     * actually asked to do. The SERVER is the boundary; this only decides which
     * buttons are drawn.
     */
    fun isEditableBy(userId: String?): Boolean =
        isDraft && userId != null && createdBy == userId

    /** Null id means nobody is assigned — which is not the same as unreachable. */
    val isAssigned: Boolean get() = assignedNarratorId != null

    /** One line for a list, in the order a person reads it. */
    val summary: String
        get() = when (kind) {
            PickupKind.MISREAD -> "said \"$said\" — should be \"$shouldBe\""
            else -> note.ifBlank { kind.label }
        }
}

enum class PickupKind(val stored: String, val label: String) {
    MISREAD("misread", "Misread"),
    NOISE("noise", "Noise"),
    SENTENCE("sentence", "Sentence"),
    OTHER("other", "Other");

    /** Only a misread carries the said/should-be pair. The form follows this. */
    val needsSaidPair: Boolean get() = this == MISREAD

    companion object {
        fun fromStored(value: String?): PickupKind =
            entries.firstOrNull { it.stored == value?.trim()?.lowercase() } ?: OTHER
    }
}

enum class PickupStatus(val stored: String, val label: String) {
    DRAFT("draft", "Draft"),
    SENT("sent", "Sent"),
    RESOLVED("resolved", "Resolved"),
    DISMISSED("dismissed", "Dismissed");

    companion object {
        /**
         * Unrecognised maps to SENT, not DRAFT.
         *
         * DRAFT would offer edit and delete buttons for a row this build does not
         * understand, and the server would refuse them. SENT is the state that
         * offers nothing and hides nothing — visible, and not editable.
         */
        fun fromStored(value: String?): PickupStatus =
            entries.firstOrNull { it.stored == value?.trim()?.lowercase() } ?: SENT
    }
}

/**
 * Editing state, DERIVED and never stored.
 *
 * There is no editing_status column on purpose: a stored status and a chapter
 * count can disagree, and "done" beside 4 of 12 chapters is a row that cannot be
 * true and would still render. Deriving it means there is one fact, so there is
 * nothing to contradict.
 */
enum class EditingState { NOT_STARTED, IN_PROGRESS, DONE }

fun editingStateOf(chaptersEdited: Int?, editingCompletedAt: Instant?): EditingState = when {
    editingCompletedAt != null -> EditingState.DONE
    (chaptersEdited ?: 0) > 0 -> EditingState.IN_PROGRESS
    else -> EditingState.NOT_STARTED
}
