package com.dmnarration.admin.data

import android.util.Log
import com.dmnarration.admin.domain.BoardCard
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The wire shape, named exactly as the columns are.
 *
 * Snake-case property names rather than @SerialName on every field: the point
 * of this type is to be diffable against the column list in
 * `/api/board-v2/cards/route.ts`, and renaming here would hide that.
 *
 * Everything is nullable or defaulted, including fields that are NOT NULL in
 * Postgres. A future editor session reads through a view that does not define
 * `pfh_rate`, `payment_type` or `is_confidential` at all, and those rows must
 * deserialise with the fields simply absent rather than failing.
 */
@Suppress("PropertyName")
@Serializable
data class BoardCardDto(
    val id: String,
    val title: String = "",
    val author: String = "",
    val co_narrator: String? = null,
    val cover_url: String? = null,
    val status: String = "",
    val deadline: String? = null,
    val first15_due: String? = null,
    val first_15_complete: Boolean = false,
    val word_count: Int? = null,
    val pfh_rate: Double? = null,
    val payment_type: String? = null,
    val is_confidential: Boolean = false,
    val narration_format: String? = null,
    val narrator_share_percent: Int? = null,
    val recording_dates: List<String>? = null,
    val words_recorded: Int? = null,
    val created_at: String? = null,
)

private const val TAG = "BoardDto"

/**
 * A `date` column to a LocalDate, with no timezone anywhere near it.
 *
 * Bad values are dropped rather than thrown: a card with an unreadable deadline
 * should render as a card with no deadline, not take the board down.
 */
private fun date(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(raw.take(10)) }
        .onFailure { Log.w(TAG, "unparseable date '$raw'", it) }
        .getOrNull()
}

/**
 * `created_at` is a real instant, unlike the two date columns.
 *
 * It only breaks ties in the sort, so an unreadable one falls back to the epoch
 * — which sorts it last among equal deadlines — rather than failing the row.
 */
private fun instant(raw: String?): Instant {
    if (raw.isNullOrBlank()) return Instant.DISTANT_PAST
    return runCatching { Instant.parse(raw) }
        .recoverCatching { Instant.parse(raw.replace(" ", "T")) }
        .onFailure { Log.w(TAG, "unparseable created_at '$raw'", it) }
        .getOrDefault(Instant.DISTANT_PAST)
}

fun BoardCardDto.toDomain(): BoardCard = BoardCard(
    id = id,
    title = title,
    author = author,
    coNarrator = co_narrator,
    coverUrl = cover_url?.takeIf { it.isNotBlank() },
    status = status,
    deadline = date(deadline),
    first15Due = date(first15_due),
    first15Complete = first_15_complete,
    // Zero is absent, not zero — both columns are NOT NULL DEFAULT 0, so an
    // unset value arrives as 0 and would otherwise render as "0 words"/"~$0".
    wordCount = word_count?.takeIf { it > 0 },
    pfhRate = pfh_rate?.takeIf { it > 0.0 },
    paymentType = payment_type,
    isConfidential = is_confidential,
    narrationFormat = narration_format,
    narratorSharePercent = narrator_share_percent,
    recordingDates = recording_dates.orEmpty().mapNotNull(::date),
    wordsRecorded = words_recorded,
    createdAt = instant(created_at),
)

@Serializable
data class ProfileDto(val id: String, val role: String? = null, val display_name: String? = null)

@Serializable
data class SiteSettingDto(val key: String, val value: String? = null)
