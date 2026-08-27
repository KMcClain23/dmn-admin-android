package com.dmnarration.admin.data

import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.Chapter
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The wire shape of `card_detail()`, named exactly as its return type is.
 *
 * Same convention as [BoardCardDto]: snake_case so it stays diffable against the
 * function's column list, and everything nullable or defaulted so a narrowed
 * projection for a future editor deserialises with fields absent rather than
 * failing.
 */
@Suppress("PropertyName")
@Serializable
data class CardDetailDto(
    val id: String,
    val title: String = "",
    val subtitle: String? = null,
    val author: String = "",
    val co_narrator: String? = null,
    val cover_url: String? = null,
    val status: String = "",
    val deadline: String? = null,
    val first15_due: String? = null,
    /**
     * Nullable, because the column is. A default covers an ABSENT key; an explicit
     * null in the payload would throw against a non-nullable Boolean. No row holds
     * null today, which is exactly why it would have surfaced as a crash on whichever
     * card first did.
     *
     * Null means not complete: an unanswered question about whether the first fifteen
     * minutes were delivered is not a yes.
     */
    val first_15_complete: Boolean? = null,
    val word_count: Int? = null,
    val words_recorded: Int? = null,
    val pfh_rate: Double? = null,
    val payment_type: String? = null,
    val narration_format: String? = null,
    val narrator_share_percent: Int? = null,
    val royalty_split_percent: Int? = null,
    val is_confidential: Boolean = false,
    val production_type: String? = null,
    val production_company: String? = null,
    val recording_dates: List<String>? = null,
    val description: String? = null,
    val notes: String? = null,
    val tags: List<String>? = null,
    val trigger_warnings: List<String>? = null,
    val chapters: List<ChapterDto>? = null,
    val links: List<String>? = null,
    val audible_link: String? = null,
    val ar_link: String? = null,
    val spotify_link: String? = null,
    val script_url: String? = null,
    val released_at: String? = null,
    val amazon_rating: Double? = null,
    val amazon_review_count: Int? = null,
    val created_at: String? = null,
)

/**
 * `number` is nullable and that is load-bearing.
 *
 * Every one of the ten books that has chapters carries two or three chapters whose
 * `number` is null. A non-nullable Int here would not be a rare crash on one unlucky
 * book — it would crash every book with chapters at all.
 *
 * `status` stays a String for the same reason inverted: five distinct values exist
 * today and the web can add a sixth without telling anyone.
 */
@Suppress("PropertyName")
@Serializable
data class ChapterDto(
    val number: Int? = null,
    val title: String? = null,
    val status: String? = null,
    val wordCount: Int? = null,
    val pages: Int? = null,
    val notes: String? = null,
)

private fun date(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
}

private fun instant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return runCatching { Instant.parse(raw) }.getOrNull()
}

fun CardDetailDto.toDomain(): CardDetail = CardDetail(
    id = id,
    title = title,
    subtitle = subtitle?.takeIf { it.isNotBlank() },
    author = author,
    coNarrator = co_narrator,
    coverUrl = cover_url?.takeIf { it.isNotBlank() },
    status = status,
    deadline = date(deadline),
    first15Due = date(first15_due),
    first15Complete = first_15_complete ?: false,
    wordCount = word_count,
    wordsRecorded = words_recorded,
    pfhRate = pfh_rate,
    paymentType = payment_type,
    narrationFormat = narration_format,
    narratorSharePercent = narrator_share_percent,
    royaltySplitPercent = royalty_split_percent,
    isConfidential = is_confidential,
    productionType = production_type?.takeIf { it.isNotBlank() },
    productionCompany = production_company?.takeIf { it.isNotBlank() },
    recordingDates = recording_dates.orEmpty().mapNotNull(::date),
    description = description?.takeIf { it.isNotBlank() },
    notes = notes?.takeIf { it.isNotBlank() },
    tags = tags.orEmpty().filter { it.isNotBlank() },
    triggerWarnings = trigger_warnings.orEmpty().filter { it.isNotBlank() },
    chapters = chapters.orEmpty().map {
        Chapter(
            number = it.number,
            title = it.title?.takeIf { t -> t.isNotBlank() },
            status = it.status?.takeIf { s -> s.isNotBlank() },
            wordCount = it.wordCount,
            pages = it.pages,
            notes = it.notes?.takeIf { n -> n.isNotBlank() },
        )
    },
    links = links.orEmpty().filter { it.isNotBlank() },
    audibleLink = audible_link?.takeIf { it.isNotBlank() },
    arLink = ar_link?.takeIf { it.isNotBlank() },
    spotifyLink = spotify_link?.takeIf { it.isNotBlank() },
    scriptUrl = script_url?.takeIf { it.isNotBlank() },
    releasedAt = instant(released_at),
    amazonRating = amazon_rating,
    amazonReviewCount = amazon_review_count,
    createdAt = instant(created_at),
)
