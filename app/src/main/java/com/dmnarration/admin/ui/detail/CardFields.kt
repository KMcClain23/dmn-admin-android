package com.dmnarration.admin.ui.detail

import com.dmnarration.admin.domain.CardDetail
import com.dmnarration.admin.domain.parseCoNarrators
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * Every editable field on a card, in one list.
 *
 * PORTED FROM CardEditModal, NOT DESIGNED HERE. The web modal made its labels,
 * grouping and order against real data over a long time — "PFH (Per Finished
 * Hour)" rather than "PFH", Royalty split appearing only for royalty books,
 * Word count sitting first in Financials. Re-deciding any of that on the phone
 * would produce two vocabularies for one table, and the project has already
 * watched two spellings of one rule drift apart.
 *
 * The list exists as DATA rather than as a screen so it can be checked against
 * the grant. `CardFieldsTest` asserts that the set of columns here is exactly
 * the 28-column UPDATE grant minus the documented exclusions — so a column
 * granted later without a field, or a field added without a grant, fails a test
 * rather than showing Dean a control that silently cannot save.
 */

/** What the value is, which decides the keyboard and how it is parsed. */
enum class CardFieldKind {
    Text,
    MultilineText,
    Integer,
    Decimal,
    Date,
    Bool,
    Choice,
}

data class CardChoice(val value: String, val label: String)

data class CardField(
    /** The database column. This is the identity — writes and FieldWrite key on it. */
    val column: String,
    val label: String,
    val group: CardFieldGroup,
    val kind: CardFieldKind,
    /** Present for [CardFieldKind.Choice]. */
    val choices: List<CardChoice> = emptyList(),
    /** A sentence under the field, where the web carries one worth keeping. */
    val help: String? = null,
    /** Current value as the EDITOR should show it; empty string means unset. */
    val read: (CardDetail) -> String,
    /**
     * Current value as the ROW should read it — formatted the way the card
     * always formatted it: "Sep 24, 2026", "Duet", "$250.00", "Ann Dahlia".
     *
     * Separate from [read] because the two jobs genuinely differ: a date picker
     * wants 2026-09-24 and a person wants Sep 24, 2026. Keeping both on ONE
     * field is what lets there be one row. The previous shape had a formatted
     * read-only row in the summary and a raw editable row in a second section,
     * which is two representations of one value — and the raw one, which nobody
     * chose, is what Dean saw.
     */
    val display: (CardDetail) -> String = read,
)

enum class CardFieldGroup(val title: String) {
    Details("Details"),
    People("People"),
    Timing("Timing"),
    Production("Production"),
    Money("Money"),
    Content("Content"),
    Links("Links"),
}


internal val MONTH_ABBR = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "Sep 24, 2026" — the form the card has always used for a date. */
internal fun longDate(d: LocalDate) = "${MONTH_ABBR[d.month.number - 1]} ${d.day}, ${d.year}"

private fun choiceLabel(choices: List<CardChoice>, raw: String?): String =
    choices.find { it.value == raw }?.label ?: raw.orEmpty()

private fun LocalDate?.orEmpty(): String = this?.toString() ?: ""
private fun Int?.orEmpty(): String = this?.toString() ?: ""
private fun Double?.orEmpty(): String = this?.toString() ?: ""

/** The seven states the web offers, in its order. */
val STATUS_CHOICES = listOf(
    CardChoice("audition", "Audition"),
    CardChoice("contracted", "Contracted"),
    CardChoice("prepping", "Prepping"),
    CardChoice("recording", "Recording"),
    CardChoice("editing", "Editing"),
    CardChoice("released", "Released"),
    // An author can replace a narrator mid-project. The work stops but a partial
    // fee is usually still due, so the card survives as something billable.
    CardChoice("recast", "Recast"),
)

val PAYMENT_TYPE_CHOICES = listOf(
    CardChoice("pfh", "PFH (Per Finished Hour)"),
    CardChoice("rs", "Royalty Share (RS)"),
    CardChoice("rs_plus", "Royalty Share Plus (RS+)"),
)

val NARRATION_FORMAT_CHOICES = listOf(
    CardChoice("solo", "Solo"),
    CardChoice("dual", "Dual"),
    CardChoice("duet", "Duet"),
    CardChoice("multicast", "Multicast"),
)

val PRODUCTION_TYPE_CHOICES = listOf(
    CardChoice("indie", "Indie"),
    CardChoice("company", "Company"),
)

/**
 * WORD COUNT COMES FIRST, in its own group ahead of everything else.
 *
 * It is the field Dean named and the reason this stage exists: correcting a
 * wrong one. It also feeds hours, earnings, page-derived progress and the career
 * total, so it is the single most consequential number on the card.
 */
val CARD_FIELDS: List<CardField> = listOf(
    CardField(
        column = "word_count",
        label = "Word count",
        group = CardFieldGroup.Money,
        kind = CardFieldKind.Integer,
        help = "Feeds hours, earnings and the career total. 1,000–500,000, or empty.",
        read = { it.wordCount.orEmpty() },
        display = { it.wordCount?.let { n -> "%,d".format(n) } ?: "" },
    ),

    CardField(
        column = "title", label = "Book title", group = CardFieldGroup.Details, kind = CardFieldKind.Text,
        read = { it.title },
    ),
    CardField(
        column = "subtitle", label = "Subtitle", group = CardFieldGroup.Details, kind = CardFieldKind.Text,
        read = { it.subtitle ?: "" },
    ),
    CardField(
        column = "is_confidential",
        label = "Confidential",
        group = CardFieldGroup.Details,
        kind = CardFieldKind.Bool,
        help = "Hides title, author and cover from the public site.",
        read = { if (it.isConfidential) "true" else "false" },
    ),

    CardField(
        column = "author", label = "Author", group = CardFieldGroup.People, kind = CardFieldKind.Text,
        read = { it.author },
    ),
    CardField(
        column = "co_narrator",
        label = "Co-narrators",
        group = CardFieldGroup.People,
        kind = CardFieldKind.Text,
        read = { it.coNarrator ?: "" },
        // The column stores a JSON array. Rendering it raw put ["Ann Dahlia"]
        // on screen; the summary had always parsed it, and now one row does.
        display = { parseCoNarrators(it.coNarrator).joinToString(", ") },
    ),
    CardField(
        column = "narration_format",
        label = "Narration format",
        group = CardFieldGroup.People,
        kind = CardFieldKind.Choice,
        choices = NARRATION_FORMAT_CHOICES,
        help = "Duet and Dual halve the narrator's share unless a share is set.",
        read = { it.narrationFormat ?: "" },
        display = { choiceLabel(NARRATION_FORMAT_CHOICES, it.narrationFormat) },
    ),

    CardField(
        column = "status", label = "Status", group = CardFieldGroup.Timing,
        kind = CardFieldKind.Choice, choices = STATUS_CHOICES,
        read = { it.status },
        display = { choiceLabel(STATUS_CHOICES, it.status) },
    ),
    CardField(
        column = "deadline", label = "Deadline", group = CardFieldGroup.Timing,
        kind = CardFieldKind.Date,
        read = { it.deadline.orEmpty() },
        display = { d -> d.deadline?.let(::longDate) ?: "" },
    ),
    CardField(
        column = "first15_due", label = "First 15 due", group = CardFieldGroup.Timing,
        kind = CardFieldKind.Date,
        read = { it.first15Due.orEmpty() },
        display = { d -> d.first15Due?.let(::longDate) ?: "" },
    ),
    CardField(
        column = "first_15_complete",
        label = "First 15 approved",
        group = CardFieldGroup.Timing,
        kind = CardFieldKind.Bool,
        read = { if (it.first15Complete) "true" else "false" },
    ),
    CardField(
        column = "released_at",
        label = "Release date",
        group = CardFieldGroup.Timing,
        kind = CardFieldKind.Date,
        help = "Anchored to Pacific noon by the database, so it matches the web exactly.",
        read = { it.releasedAt?.toString()?.take(10) ?: "" },
        display = { d ->
            d.releasedAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date?.let(::longDate) ?: ""
        },
    ),

    CardField(
        column = "production_type",
        label = "Production type",
        group = CardFieldGroup.Production,
        kind = CardFieldKind.Choice,
        choices = PRODUCTION_TYPE_CHOICES,
        read = { it.productionType ?: "" },
        display = { choiceLabel(PRODUCTION_TYPE_CHOICES, it.productionType) },
    ),
    CardField(
        column = "production_company",
        label = "Production company",
        group = CardFieldGroup.Production,
        kind = CardFieldKind.Text,
        read = { it.productionCompany ?: "" },
    ),

    // Pages sit in Production rather than Money, though word_count is in Money
    // where the web puts it. Money is behind canViewFinancials, and progress is
    // not a financial fact — gating it would hide how far a book has got from
    // someone allowed to see the book.
    CardField(
        column = "total_pages",
        label = "Total pages",
        group = CardFieldGroup.Production,
        kind = CardFieldKind.Integer,
        help = "Sets the page line. Without it a book shows its percentage alone.",
        read = { it.totalPages.orEmpty() },
        display = { d -> d.totalPages?.let { "%,d".format(it) } ?: "" },
    ),
    CardField(
        column = "current_page",
        label = "Current page",
        group = CardFieldGroup.Production,
        kind = CardFieldKind.Integer,
        // Writing this moves words_recorded, by trigger. Writing words_recorded
        // directly clears it, because the page is then no longer known to be
        // accurate. One writer, enforced rather than agreed.
        help = "Updates words recorded. Also settable from Today while recording.",
        read = { it.currentPage.orEmpty() },
        display = { d -> d.currentPage?.let { "%,d".format(it) } ?: "" },
    ),

    CardField(
        column = "payment_type",
        label = "Payment type",
        group = CardFieldGroup.Money,
        kind = CardFieldKind.Choice,
        choices = PAYMENT_TYPE_CHOICES,
        read = { it.paymentType ?: "" },
        display = { choiceLabel(PAYMENT_TYPE_CHOICES, it.paymentType) },
    ),
    CardField(
        column = "pfh_rate", label = "PFH rate", group = CardFieldGroup.Money,
        kind = CardFieldKind.Decimal,
        read = { it.pfhRate.orEmpty() },
        display = { d -> d.pfhRate?.let { "$%.2f".format(it) } ?: "" },
    ),
    CardField(
        column = "narrator_share_percent",
        label = "Narrator share (%)",
        group = CardFieldGroup.Money,
        kind = CardFieldKind.Integer,
        help = "1–100, or empty for the format default. Zero is refused — leave it empty.",
        read = { it.narratorSharePercent.orEmpty() },
        display = { d -> d.narratorSharePercent?.let { "$it%" } ?: "" },
    ),
    CardField(
        column = "royalty_split_percent",
        label = "Royalty split (%)",
        group = CardFieldGroup.Money,
        kind = CardFieldKind.Integer,
        // The web's tooltip says "Enter 0 if the royalties are all yours". The
        // database refuses 0 as of 10A-bis, because null already means "not
        // set" and two spellings of one state make a screen unable to say which
        // it is looking at. This sentence says what the database will accept.
        help = "The co-narrator's share of each statement. 1–100, or empty when it is the default fifty-fifty.",
        read = { it.royaltySplitPercent.orEmpty() },
        display = { d -> d.royaltySplitPercent?.let { "$it%" } ?: "" },
    ),

    CardField(
        column = "description",
        label = "Description (public)",
        group = CardFieldGroup.Content,
        kind = CardFieldKind.MultilineText,
        read = { it.description ?: "" },
    ),

    CardField(
        column = "audible_link", label = "Amazon / Audible", group = CardFieldGroup.Links, kind = CardFieldKind.Text,
        read = { it.audibleLink ?: "" },
    ),
    CardField(
        column = "ar_link", label = "Author's Republic", group = CardFieldGroup.Links, kind = CardFieldKind.Text,
        read = { it.arLink ?: "" },
    ),
    CardField(
        column = "spotify_link", label = "Spotify", group = CardFieldGroup.Links, kind = CardFieldKind.Text,
        read = { it.spotifyLink ?: "" },
    ),
    CardField(
        column = "script_url", label = "Script (OneDrive)", group = CardFieldGroup.Links, kind = CardFieldKind.Text,
        read = { it.scriptUrl ?: "" },
    ),
)

/**
 * Granted, and deliberately not edited here. Each needs a REASON, because a
 * column that is writable but absent from the editor is indistinguishable from
 * one nobody thought about.
 */
val UNEDITED_GRANTED_COLUMNS: Map<String, String> = mapOf(
    // Owned by the archive flow, which already exists and writes all three
    // together. Editing them one at a time would let a card claim to be
    // archived with no reason, or carry a reason while still on the board.
    "archived_at" to "Set by archiving, not edited on its own.",
    "archived_reason" to "Set by archiving, not edited on its own.",
    "archived_notes" to "Set by archiving, not edited on its own.",
)

/**
 * Shapes this editor cannot handle yet, shown READ-ONLY with the reason.
 *
 * Not omitted. A field that vanishes reads as data that does not exist, and
 * these all hold real values on real cards.
 */
data class DeferredShape(val label: String, val reason: String)

val DEFERRED_SHAPES = listOf(
    DeferredShape("Tags", "List editing is not on the phone yet — edit on the web."),
    DeferredShape("Trigger warnings", "List editing is not on the phone yet — edit on the web."),
    DeferredShape("Days I record", "List editing is not on the phone yet — edit on the web."),
    DeferredShape("Cover image", "Uploading needs a signed two-step upload — edit on the web."),
)

/**
 * Read-only because something else owns them, which is different from deferred.
 * The cron writes these and its validation is client-only on the web; a phone
 * would bypass it entirely and store a wrong number that looks right.
 */
val CRON_OWNED_SHAPES = listOf(
    DeferredShape("Amazon rating", "Written by the nightly job."),
    DeferredShape("Amazon reviews", "Written by the nightly job."),
)
