package com.dmnarration.admin.data

import com.dmnarration.admin.domain.Pickup
import com.dmnarration.admin.domain.PickupKind
import com.dmnarration.admin.domain.PickupStatus
import kotlinx.serialization.Serializable

/**
 * One row of pickups_for_editor() / pickups_for_session().
 *
 * Both return the same shape and differ only in their gate, so one DTO decodes
 * both. Every field carries a default: a DTO that throws on a missing key turns
 * a server-side column addition into a client crash, and E5 will add columns.
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
    val assigned_to: String = "",
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
    assignedTo = assigned_to,
    status = PickupStatus.fromStored(status),
    createdBy = created_by,
    createdAt = instantOrNull(created_at),
    sentAt = instantOrNull(sent_at),
    resolvedAt = instantOrNull(resolved_at),
    resolvedBy = resolved_by,
)
