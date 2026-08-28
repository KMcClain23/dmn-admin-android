package com.dmnarration.admin.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's field list must equal the database's UPDATE grant.
 *
 * The grant is the inventory of what this app is allowed to write. A column
 * granted with no field is a permission nobody uses; a field with no grant is a
 * control that looks editable and fails at the server. Neither is visible by
 * reading either side on its own, which is why this compares them.
 *
 * The grant is written out here rather than queried, because a test that asks
 * the database what the grant is would agree with any grant at all. This list
 * is the one Stage 10A deliberately chose, and changing it should require
 * changing this file — that is the point.
 */
class CardFieldsTest {

    /**
     * The 28 columns granted to `authenticated` for UPDATE on board_cards,
     * verified against information_schema on 28 August 2026.
     */
    private val grant = setOf(
        "ar_link", "archived_at", "archived_notes", "archived_reason", "audible_link",
        "author", "co_narrator", "current_page", "deadline", "description",
        "first15_due", "first_15_complete", "is_confidential", "narration_format",
        "narrator_share_percent", "payment_type", "pfh_rate", "production_company",
        "production_type", "released_at", "royalty_split_percent", "script_url",
        "spotify_link", "status", "subtitle", "title", "total_pages", "word_count",
    )

    @Test fun `every editable field is a granted column`() {
        val editable = CARD_FIELDS.map { it.column }.toSet()
        val ungranted = editable - grant
        assertEquals(
            "these fields would fail at the server, having no UPDATE grant: $ungranted",
            emptySet<String>(),
            ungranted,
        )
    }

    @Test fun `every granted column is either editable or has a stated reason`() {
        val editable = CARD_FIELDS.map { it.column }.toSet()
        val accounted = editable + UNEDITED_GRANTED_COLUMNS.keys
        val unaccounted = grant - accounted
        // A granted column nobody listed is the failure this test exists for: it
        // reads as a field nobody thought about rather than one deliberately
        // left out, and there is nothing on screen or in the source to say which.
        assertEquals(
            "granted but neither editable nor explained: $unaccounted",
            emptySet<String>(),
            unaccounted,
        )
    }

    @Test fun `nothing is both editable and excluded`() {
        val editable = CARD_FIELDS.map { it.column }.toSet()
        val both = editable intersect UNEDITED_GRANTED_COLUMNS.keys
        assertEquals("listed as both editable and excluded: $both", emptySet<String>(), both)
    }

    @Test fun `no column appears twice`() {
        val columns = CARD_FIELDS.map { it.column }
        assertEquals(
            "duplicate columns would give one value two editors: " +
                columns.groupingBy { it }.eachCount().filterValues { it > 1 }.keys,
            columns.size,
            columns.toSet().size,
        )
    }

    @Test fun `word count is present and reachable`() {
        // The field Dean named and the reason for the stage. Named explicitly so
        // that removing it fails here rather than going unnoticed.
        val wordCount = CARD_FIELDS.find { it.column == "word_count" }
        assertTrue("word_count must be editable", wordCount != null)
        assertEquals(CardFieldKind.Integer, wordCount!!.kind)
    }

    @Test fun `every choice field offers choices`() {
        val empty = CARD_FIELDS.filter { it.kind == CardFieldKind.Choice && it.choices.isEmpty() }
        // A choice field with no choices renders a list nobody can pick from,
        // which looks like a field with no options rather than a bug.
        assertEquals("choice fields with no options: ${empty.map { it.column }}", emptyList<CardField>(), empty)
    }

    @Test fun `non-choice fields do not carry choices`() {
        val stray = CARD_FIELDS.filter { it.kind != CardFieldKind.Choice && it.choices.isNotEmpty() }
        assertEquals("non-choice fields carrying options: ${stray.map { it.column }}", emptyList<CardField>(), stray)
    }

    @Test fun `the deferred shapes are stated, not omitted`() {
        // Four shapes this editor cannot handle. They must be VISIBLE with a
        // reason: a field that vanishes reads as data that does not exist, and
        // all four hold real values on real cards.
        assertEquals(4, DEFERRED_SHAPES.size)
        assertTrue(DEFERRED_SHAPES.all { it.reason.isNotBlank() })
        assertTrue(CRON_OWNED_SHAPES.all { it.reason.isNotBlank() })
    }

    @Test fun `amazon fields are not editable here`() {
        val editable = CARD_FIELDS.map { it.column }.toSet()
        // Cron-owned, and their validation is client-only on the web. A phone
        // would bypass it and store a wrong number that looks right — 45 clamped
        // to 5.0 ranks a book first on a figure nobody typed.
        assertTrue("amazon_rating must not be editable", "amazon_rating" !in editable)
        assertTrue("amazon_review_count must not be editable", "amazon_review_count" !in editable)
    }

    @Test fun `words_recorded is not editable, because the trigger owns it`() {
        val editable = CARD_FIELDS.map { it.column }.toSet()
        // Granting it would give the phone a second way to set the figure this
        // stage exists to keep single. It is not in the grant either.
        assertTrue("words_recorded must not be editable", "words_recorded" !in editable)
        assertTrue("words_recorded must not be granted", "words_recorded" !in grant)
    }
}
