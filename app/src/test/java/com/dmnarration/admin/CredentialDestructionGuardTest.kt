package com.dmnarration.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the one rule that made three separate bugs impossible rather than
 * merely fixed:
 *
 *   CREDENTIAL DESTRUCTION IS REACHABLE ONLY FROM AN EXPLICIT USER ACTION.
 *
 * Bugs 3, 4 and 5 in Stage 1 were the same shape — an automatic path deciding,
 * on incomplete information, to throw away a working session. Each was fixed by
 * classifying more carefully, and each time a different automatic path was left
 * holding the same loaded gun. This asserts the gun has one trigger.
 *
 * A source-reading test is an unusual thing to write, and it is deliberate. The
 * property being protected is "how many places can call this", which no runtime
 * assertion can observe: every individual call looks correct in isolation, and
 * that is exactly how the third one got written.
 */
class CredentialDestructionGuardTest {

    private val sourceRoot = File("src/main/java/com/dmnarration/admin")

    private fun sources(): List<File> {
        assertTrue(
            "cannot find sources at ${sourceRoot.absolutePath} — has the module layout moved?",
            sourceRoot.isDirectory,
        )
        return sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** Lines that actually call it, ignoring the comments that describe it. */
    private fun callsTo(name: String): List<Pair<String, String>> =
        sources().flatMap { file ->
            file.readLines()
                .map { it.trim() }
                .filter { line ->
                    line.contains(name) &&
                        !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
                }
                .map { file.name to it }
        }

    @Test
    fun `clearSession is called from exactly one place`() {
        val calls = callsTo("clearSession()")
        assertEquals(
            "clearSession() must have exactly one call site, found: $calls",
            1,
            calls.size,
        )
        assertEquals(
            "the one clearSession() call must live in SessionRepository",
            "SessionRepository.kt",
            calls.single().first,
        )
    }

    @Test
    fun `the credential-destroying function is called from exactly one place`() {
        val calls = callsTo("signOutByUser()").filter { !it.second.startsWith("suspend fun") }
        assertEquals(
            "signOutByUser() must have exactly one caller, found: $calls",
            1,
            calls.size,
        )
        assertEquals(
            "its only caller must be the ViewModel function the Sign out button is wired to",
            "AppViewModel.kt",
            calls.single().first,
        )
    }

    /**
     * The specific regression: role loading must never destroy anything. It was
     * where bug 5 lived, because "no signed-in user" was read as "this account
     * has no usable profile" and answered with a sign-out.
     */
    @Test
    fun `role loading cannot sign anyone out`() {
        val repo = File(sourceRoot, "data/SessionRepository.kt").readText()
        val loadRole = repo.substringAfter("suspend fun loadRole(")
        assertTrue(
            "loadRole must not reach any credential-clearing call",
            !loadRole.contains("clearSession") && !loadRole.contains("signOutByUser"),
        )
        assertTrue(
            "loadRole must not discover authentication — it takes an already-authenticated id",
            !loadRole.contains("currentUserOrNull"),
        )
    }
}
