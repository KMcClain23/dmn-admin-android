package com.dmnarration.admin.data

import com.dmnarration.admin.domain.ArchivedCard
import com.dmnarration.admin.domain.ReleasedBook
import kotlinx.serialization.Serializable

/**
 * The wire shapes for `released_for_session()` and `archived_for_session()`,
 * named exactly as the columns are, for the same reason `BoardCardDto` is.
 *
 * Everything but `id` is nullable or defaulted. These come from RPCs whose
 * return types this repo controls, so a missing key should not be possible —
 * but "should not be possible" is how the first card with a null
 * `first_15_complete` would have crashed the board.
 */
@Suppress("PropertyName")
@Serializable
data class ReleasedBookDto(
    val id: String,
    val title: String = "",
    val author: String = "",
    val cover_url: String? = null,
    val released_at: String? = null,
    val amazon_rating: Double? = null,
    val amazon_review_count: Int? = null,
    val audible_link: String? = null,
    val archived_at: String? = null,
)

@Suppress("PropertyName")
@Serializable
data class ArchivedCardDto(
    val id: String,
    val title: String = "",
    val author: String = "",
    val cover_url: String? = null,
    val archived_at: String? = null,
    val archived_reason: String? = null,
    val archived_notes: String? = null,
    val status: String = "",
)

/**
 * `cover_url` and `audible_link` are NOT NULL DEFAULT '' in Postgres, so an
 * unset one arrives as an empty string rather than as null. Blank is absent
 * here, or the list would render an empty link as a live one.
 */
fun ReleasedBookDto.toDomain(): ReleasedBook = ReleasedBook(
    id = id,
    title = title,
    author = author,
    coverUrl = cover_url?.takeIf { it.isNotBlank() },
    releasedAt = instantOrNull(released_at),
    // NOT rounded, defaulted or coerced here. Null stays null all the way to the
    // renderer, which is what makes "no rating" distinguishable from "0.0".
    amazonRating = amazon_rating,
    amazonReviewCount = amazon_review_count,
    audibleLink = audible_link?.takeIf { it.isNotBlank() },
    archivedAt = instantOrNull(archived_at),
)

/**
 * `archived_notes` keeps its exact stored value, empty string included.
 *
 * Not `takeIf { isNotBlank() }`: the web writes null for an empty note and this
 * app writes "", so the two produce different data for the same action. Papering
 * over that here would hide the divergence rather than record it.
 */
fun ArchivedCardDto.toDomain(): ArchivedCard = ArchivedCard(
    id = id,
    title = title,
    author = author,
    coverUrl = cover_url?.takeIf { it.isNotBlank() },
    archivedAt = instantOrNull(archived_at),
    archivedReason = archived_reason,
    archivedNotes = archived_notes,
    status = status,
)
