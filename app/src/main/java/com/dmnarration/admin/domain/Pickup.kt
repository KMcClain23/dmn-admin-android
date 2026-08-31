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

    /** The narrator has sent it back; it is the editor's turn to listen and close. */
    val isReturned: Boolean get() = status == PickupStatus.RETURNED

    /** Out with a narrator. The only state from which "Re-recorded" makes sense. */
    val isAwaitingNarrator: Boolean get() = status == PickupStatus.SENT

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

    /** The narrator has re-recorded it. Waiting on the editor to listen and close. */
    RETURNED("returned", "Re-recorded"),

    RESOLVED("resolved", "Resolved"),
    DISMISSED("dismissed", "Dismissed"),

    /**
     * A STATUS THIS BUILD DOES NOT KNOW.
     *
     * This is the class-level fix, and it matters more than adding RETURNED.
     *
     * `fromStored` used to fall back to SENT, which is how a returned pickup came
     * to render as "Sent" AND be offered a Resolve button — the one row where
     * Resolve happened to work was the one whose label was wrong, and the rows
     * labelled correctly got a guaranteed error. Adding RETURNED to the list
     * fixes today and leaves the NEXT status to reproduce this exactly.
     *
     * A phone shipped against a schema that keeps growing has to be able to say
     * "I do not know what this is". So an unrecognised value lands here, is
     * rendered honestly, and is offered NO ACTIONS AT ALL — because this build
     * cannot know which transitions the server would accept from a state it has
     * never heard of.
     */
    UNKNOWN("", "Unknown status — update the app");

    /** Whether this build understands the row well enough to offer anything. */
    val isKnown: Boolean get() = this != UNKNOWN

    companion object {
        fun fromStored(value: String?): PickupStatus =
            entries.firstOrNull { it != UNKNOWN && it.stored == value?.trim()?.lowercase() }
                ?: UNKNOWN
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
