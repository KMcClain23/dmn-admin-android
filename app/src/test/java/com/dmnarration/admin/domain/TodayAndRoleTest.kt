package com.dmnarration.admin.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

class TodayAndRoleTest {

    /** A clock stopped at one instant, so "today" is a fact rather than a race. */
    private fun stoppedAt(iso: String) = object : Clock {
        override fun now(): Instant = Instant.parse(iso)
    }

    private val pacific = TimeZone.of("America/Los_Angeles")

    /**
     * The evening window. 00:21 UTC on the 26th is 17:21 on the 25th at UTC-7,
     * so the two zones disagree about what day it is for seven hours every
     * night. Local is the answer: the deadlines are the narrator's dates.
     */
    @Test fun `today follows the device zone, not UTC`() {
        val clock = stoppedAt("2026-08-26T00:21:00Z")
        assertEquals(LocalDate.parse("2026-08-25"), currentDay(pacific, clock))
        assertEquals(LocalDate.parse("2026-08-26"), currentDay(TimeZone.UTC, clock))
    }

    @Test fun `the two zones agree for the rest of the day`() {
        val morning = stoppedAt("2026-08-25T16:00:00Z") // 09:00 Pacific
        assertEquals(currentDay(TimeZone.UTC, morning), currentDay(pacific, morning))
    }

    /**
     * What the leak would actually cost. A card 31 days out locally is a Later
     * card; read in UTC during the evening window it is 30 days out and jumps
     * a bucket. Every urgency colour moves with it.
     */
    @Test fun `a UTC-derived today would move a card a whole bucket early`() {
        val clock = stoppedAt("2026-08-26T00:21:00Z")
        val card = card(deadline = "2026-09-25")

        assertEquals(31, daysUntil(LocalDate.parse("2026-09-25"), currentDay(pacific, clock)))
        assertEquals(PipelineBucket.LATER, pipelineBucketFor(card, currentDay(pacific, clock)))

        assertEquals(30, daysUntil(LocalDate.parse("2026-09-25"), currentDay(TimeZone.UTC, clock)))
        assertEquals(PipelineBucket.THIS_MONTH, pipelineBucketFor(card, currentDay(TimeZone.UTC, clock)))
    }

    @Test fun `the default zone is the device's`() {
        val clock = stoppedAt("2026-08-26T00:21:00Z")
        assertEquals(currentDay(TimeZone.currentSystemDefault(), clock), currentDay(clock = clock))
    }

    // ─── role mapping (DoD item 15) ─────────────────────────────────────────

    @Test fun `known roles map to themselves`() {
        assertEquals(UserRole.ADMIN, UserRole.fromStored("admin"))
        assertEquals(UserRole.EDITOR, UserRole.fromStored("editor"))
        assertEquals(UserRole.ADMIN, UserRole.fromStored("  ADMIN  "))
    }

    /**
     * Anything else is UNKNOWN, never a default. A role string this build does
     * not recognise is a permission question the app cannot answer, and the
     * only safe answer to that is to show an error rather than a board.
     */
    @Test fun `an unrecognised role string fails closed`() {
        for (raw in listOf(null, "", "  ", "superuser", "Admin ", "ADMINISTRATOR", "viewer", "0")) {
            val role = UserRole.fromStored(raw)
            if (raw?.trim()?.lowercase() == "admin") continue
            assertEquals("'$raw' should be UNKNOWN", UserRole.UNKNOWN, role)
            assertFalse("'$raw' must not be recognised", role.isRecognised)
            val caps = Capabilities.of(role)
            assertFalse(caps.canViewFinancials)
            assertFalse(caps.canViewStudioSettings)
            assertFalse(caps.canViewConfidentialCovers)
            assertFalse(caps.canEdit)
            assertFalse(caps.canUseWebAdmin)
        }
    }

    @Test fun `admin is the only role that can reach the web admin`() {
        // Its referent is F2, not F3: until the web admin understands users at
        // all, an editor cannot use it even once they are granted the board.
        assertTrue(Capabilities.of(UserRole.ADMIN).canUseWebAdmin)
        assertFalse(Capabilities.of(UserRole.EDITOR).canUseWebAdmin)
        assertFalse(Capabilities.of(UserRole.UNKNOWN).canUseWebAdmin)
    }

    /**
     * Stage 2 grants writes to admin and to nobody else. The editor case is the
     * one that matters: they are read-only by design, and F3 grants them a
     * board without granting them a pen.
     */
    @Test fun `only an admin may write`() {
        assertTrue(Capabilities.of(UserRole.ADMIN).canEdit)
        assertFalse(Capabilities.of(UserRole.EDITOR).canEdit)
        assertFalse(Capabilities.of(UserRole.UNKNOWN).canEdit)
    }
}
