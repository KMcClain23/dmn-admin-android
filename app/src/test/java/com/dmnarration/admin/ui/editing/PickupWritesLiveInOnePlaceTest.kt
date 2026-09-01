package com.dmnarration.admin.ui.editing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THE RISK IN A REFACTOR IS A RULE QUIETLY NOT COMING ALONG.
 *
 * Editing progress and pickups moved out of CardDetailScreen and into the
 * Editing tab. Every behavioural test of pickup actions — which status offers
 * Re-recorded, which offers Resolve, that UNKNOWN offers nothing — is a test of
 * `PickupStatus` and passes identically whether the sections were MOVED or
 * COPIED. A green suite would therefore say nothing at all about the thing that
 * actually matters: that card detail no longer writes pickups behind the new
 * tab.
 *
 * So this asserts the structure directly. It reads source rather than
 * behaviour, which is unusual and is the point: "there is exactly one
 * implementation" is a statement about the codebase, not about a run.
 *
 * The compiler already enforces most of it — CardDetailScreen's signature no
 * longer accepts a single pickup callback, so it *cannot* write one. This exists
 * so that adding them back is a deliberate act with a failing test attached,
 * rather than a plausible-looking convenience.
 */
class PickupWritesLiveInOnePlaceTest {

    private val srcRoot: File by lazy {
        // Unit tests run with the module directory as the working directory;
        // the walk up covers being run from the repo root instead.
        val candidates = listOf(
            File("src/main/java/com/dmnarration/admin"),
            File("app/src/main/java/com/dmnarration/admin"),
        )
        candidates.firstOrNull { it.isDirectory }
            ?: error("cannot find the source tree from ${File(".").absolutePath}")
    }

    private fun source(relative: String): String {
        val f = File(srcRoot, relative)
        // A missing file must FAIL, not read as an empty string that satisfies
        // every "does not contain" assertion below.
        assertTrue("expected to find $relative under ${srcRoot.absolutePath}", f.isFile)
        return f.readText()
    }

    private fun callers(composable: String): List<String> =
        srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // The definition itself is not a call site.
            .filter { it.name != "EditingAndPickups.kt" }
            .filter { Regex("""\b$composable\s*\(""").containsMatchIn(it.readText()) }
            .map { it.name }
            .sorted()
            .toList()

    @Test fun `exactly one screen renders the pickups section`() {
        assertEquals(
            "PickupsSection must be rendered from exactly one place. Two screens " +
                "that both write pickups is how one rule becomes two implementations.",
            listOf("EditingBookPane.kt"),
            callers("PickupsSection"),
        )
    }

    @Test fun `exactly one screen renders the editing section`() {
        assertEquals(listOf("EditingBookPane.kt"), callers("EditingSection"))
    }

    @Test fun `card detail offers no pickup action at all`() {
        val text = source("ui/detail/CardDetailScreen.kt")
        // Every write these sections make. Named individually so a failure says
        // which one came back.
        for (action in listOf(
            "onRaisePickup", "onDeletePickup", "onResolvePickup", "onMarkReturned",
            "onAdminDeletePickup", "onSendChapter", "onSetProgress", "onMarkComplete",
            "PickupsSection", "EditingSection",
        )) {
            assertTrue(
                "CardDetailScreen mentions $action — it must only LINK to the " +
                    "Editing tab, never act. Adding an action here recreates the " +
                    "second implementation the move removed.",
                !text.contains(action),
            )
        }
    }

    @Test fun `card detail still links to editing - the move must not lose the way in`() {
        val text = source("ui/detail/CardDetailScreen.kt")
        // The other half of the move. Removing the sections without leaving a
        // route would be "no second implementation" achieved by making the
        // feature unreachable from the card.
        assertTrue("card detail must still offer a way through", text.contains("onOpenEditing"))
        assertTrue(text.contains("Editing & pickups"))
    }

    @Test fun `the sections really did move out of the detail package`() {
        assertTrue(
            "EditingAndPickups.kt must not exist under ui/detail — a copy left " +
                "behind is exactly the failure this whole test is about.",
            !File(srcRoot, "ui/detail/EditingAndPickups.kt").exists(),
        )
        assertTrue(File(srcRoot, "ui/editing/EditingAndPickups.kt").isFile)
    }
}
