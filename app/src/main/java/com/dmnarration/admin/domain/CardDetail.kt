package com.dmnarration.admin.domain

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * One chapter, as the data actually is rather than as it ought to be.
 *
 * `number` is **nullable**, and that is not an edge case: every one of the ten books
 * that has chapters carries two or three chapters with a null number. A non-nullable
 * Int here is a parse crash on any book with chapters at all, so the nullability is
 * the whole point of this type existing.
 *
 * `status` is a String rather than an enum. Five distinct values are present today —
 * `not_started`, `in_progress`, `submitted`, `editing`, `live` — and a sixth added by
 * the web tomorrow must render as itself rather than crash a phone or silently fall
 * into an "unknown" bucket.
 */
data class Chapter(
    val number: Int?,
    val title: String?,
    val status: String?,
    val wordCount: Int?,
    val pages: Int?,
    val notes: String?,
)

/**
 * Everything `card_detail()` returns: thirty-five columns, chosen deliberately.
 *
 * Eight columns on `board_cards` are omitted on purpose and the reasons live in the
 * migration beside the function, because a wider return type is a wider surface for
 * F3 to narrow later.
 */
data class CardDetail(
    val id: String,
    val title: String,
    val subtitle: String?,
    val author: String,
    val coNarrator: String?,
    val coverUrl: String?,
    val status: String,
    val deadline: LocalDate?,
    val first15Due: LocalDate?,
    val first15Complete: Boolean,
    val wordCount: Int?,
    val wordsRecorded: Int?,
    val pfhRate: Double?,
    val paymentType: String?,
    val narrationFormat: String?,
    val narratorSharePercent: Int?,
    val royaltySplitPercent: Int?,
    val isConfidential: Boolean,
    val productionType: String?,
    val productionCompany: String?,
    val recordingDates: List<LocalDate>,
    val description: String?,
    val notes: String?,
    val tags: List<String>,
    val triggerWarnings: List<String>,
    val chapters: List<Chapter>,
    /** `not null` on the table and empty on every row today — render only when non-empty. */
    val links: List<String>,
    val audibleLink: String?,
    val arLink: String?,
    val spotifyLink: String?,
    val scriptUrl: String?,
    val releasedAt: Instant?,
    val amazonRating: Double?,
    val amazonReviewCount: Int?,
    val createdAt: Instant?,
)

/** How far through recording, clamped, or null when the word count cannot say. */
fun CardDetail.recordedFraction(): Double? {
    val total = wordCount ?: return null
    if (total <= 0) return null
    return ((wordsRecorded ?: 0).coerceAtLeast(0).toDouble() / total).coerceIn(0.0, 1.0)
}

/**
 * Chapters grouped by status, in the order the work actually moves.
 *
 * Statuses not in this list keep their own name and sort last, so a value the web
 * adds later appears rather than disappearing into a default.
 */
val CHAPTER_STATUS_ORDER = listOf("not_started", "in_progress", "submitted", "editing", "live")

fun chapterStatusLabel(raw: String?): String = when (raw) {
    null -> "No status"
    else -> raw.split('_').joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercase() }
    }
}
