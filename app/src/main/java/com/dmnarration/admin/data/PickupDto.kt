package com.dmnarration.admin.data

import com.dmnarration.admin.domain.Pickup
import com.dmnarration.admin.domain.PickupKind
import com.dmnarration.admin.domain.PickupStatus
import kotlinx.serialization.Serializable

/**
 * One row of pickups_for_editor() / pickups_for_session().
 *
 * Both return the same shape and differ only in their gate, so one DTO decodes
 * both. Every field carries a default so that a MISSING key is tolerated — a
 * column dropped or omitted from one of the two functions.
 *
 * DEFAULTS DO NOT PROTECT AGAINST AN ADDED COLUMN, and an earlier version of
 * this comment claimed they did. They cover an absent key; an EXTRA key is a
 * different failure and throws, because the client decodes through
 * KotlinXSerializer()'s default Json with ignoreUnknownKeys = false. A column
 * added to pickups_for_editor would empty this list on every installed build.
 * See DecoderExposureTest — a near-miss on board_for_editor is what found this.
 */
@Serializable
data class PickupDto(
    val id: String,
    val card_id: String,
    val chapter: String = "",
    val timestamp_at: String = "",
    val kind: String = "other",
    val said: String = "",
    val should_be: String = "",
    val note: String = "",
    val assigned_narrator_id: String? = null,
    val assigned_narrator_name: String? = null,
    val manifest_path: String? = null,
    val status: String = "draft",
    val created_by: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val sent_at: String? = null,
    val resolved_at: String? = null,
    val resolved_by: String? = null,
)

fun PickupDto.toDomain(): Pickup = Pickup(
    id = id,
    cardId = card_id,
    chapter = chapter,
    timestampAt = timestamp_at,
    kind = PickupKind.fromStored(kind),
    said = said,
    shouldBe = should_be,
    note = note,
    assignedNarratorId = assigned_narrator_id,
    assignedNarratorName = assigned_narrator_name,
    status = PickupStatus.fromStored(status),
    createdBy = created_by,
    createdAt = instantOrNull(created_at),
    sentAt = instantOrNull(sent_at),
    resolvedAt = instantOrNull(resolved_at),
    resolvedBy = resolved_by,
)
