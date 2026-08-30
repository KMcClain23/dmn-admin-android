package com.dmnarration.admin.data

import com.dmnarration.admin.domain.Narrator
import kotlinx.serialization.Serializable

@Serializable
data class NarratorDto(
    val id: String,
    val display_name: String = "",
    val active: Boolean = true,
)

fun NarratorDto.toDomain(): Narrator = Narrator(
    id = id,
    displayName = display_name,
    active = active,
)

/**
 * What the send endpoint reports back.
 *
 * `skipped` is why this is a type and not a boolean: a narrator with no email on
 * file is SKIPPED AND REPORTED, never silently dropped, and the screen has to be
 * able to say which ones.
 */
@Serializable
data class SendPickupsResult(
    val book: String = "",
    val chapter: String = "",
    val moved: Int = 0,
    val emailed: List<SendParty> = emptyList(),
    val skipped: List<SendParty> = emptyList(),
    val failed: List<SendParty> = emptyList(),
    val manifests: List<ManifestResult> = emptyList(),
    val error: String? = null,
)

@Serializable
data class SendParty(
    val narrator: String = "",
    val count: Int = 0,
    val reason: String? = null,
)

@Serializable
data class ManifestResult(
    val narrator: String = "",
    val path: String? = null,
    val error: String? = null,
)
