package com.dmnarration.admin.domain

import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * A board card, in the shape the app reasons about rather than the shape the
 * wire delivers. The DTO and its @SerialNames live in `data/`; this stays free
 * of them so the domain functions can be tested without a JSON payload.
 *
 * The financial fields are nullable, and not only because Postgres allows null.
 * A future editor session reads through a view that does not define
 * `pfh_rate`, `payment_type` or `is_confidential` at all, and a row from that
 * view has to land here cleanly with them simply absent.
 *
 * `deadline` and `first15Due` are LocalDate, not Instant, and that is load
 * bearing: they are Postgres `date` columns with no timezone, and the web
 * carries a long comment about `new Date("YYYY-MM-DD")` reading as UTC midnight
 * and displaying a day early west of Greenwich. `createdAt` is the opposite —
 * a real `timestamptz`, a real instant — which is why it has a different type.
 * (kotlin.time.Instant, not kotlinx.datetime's, which is now deprecated in
 * favour of it.)
 */
data class BoardCard(
    val id: String,
    val title: String,
    val author: String,
    /** Raw column: JSON-encoded array in most rows, a bare name in some. */
    val coNarrator: String?,
    val coverUrl: String?,
    val status: String,
    val deadline: LocalDate?,
    val first15Due: LocalDate?,
    val first15Complete: Boolean,
    val wordCount: Int?,
    val pfhRate: Double?,
    val paymentType: String?,
    val isConfidential: Boolean,
    val narrationFormat: String?,
    val narratorSharePercent: Int?,
    /** Chosen recording days. Empty means none picked yet. */
    val recordingDates: List<LocalDate>,
    val wordsRecorded: Int?,
    val createdAt: Instant,
)

/** Deadline colouring. Same three states the web pills use. */
enum class Urgency { DEFAULT, YELLOW, RED }

enum class PipelineBucket { THIS_WEEK, THIS_MONTH, LATER }

/** The two due-soon chips. Null means neither is on. */
enum class DateFilter { WEEK, MONTH }

enum class ProductionSubgroup(val status: String) {
    PREPPING("prepping"),
    RECORDING("recording"),
    EDITING("editing"),
}
