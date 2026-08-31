package com.dmnarration.admin.data

import com.dmnarration.admin.domain.CastMember
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CastMemberDto(
    @SerialName("narrator_id") val narratorId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("is_owner") val isOwner: Boolean,
)

fun CastMemberDto.toDomain(): CastMember = CastMember(
    narratorId = narratorId,
    displayName = displayName,
    isOwner = isOwner,
)
