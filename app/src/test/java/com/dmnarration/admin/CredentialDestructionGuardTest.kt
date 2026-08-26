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
 * assertion can observe: every individual call looked correct in isolation, and
 * that is exactly how the third one got written.
 *
 * ─── the boundary of this guarantee ─────────────────────────────────────────
 *
 * This covers APPLICATION paths only. supabase-kt still clears the session
 * itself when the refresh endpoint answers 4xx, and that is correct — a server
 * refusing a token is the one authority entitled to end a session. So the
 * invariant is "no app code destroys credentials", not "nothing does". Do not
 * read it as broader than it is.
 */
class CredentialDestructionGuardTest {

    private val sourceRoot = File("src/main/java/com/dmnarration/admin")

    /**
     * Deliberately worded to survive being read by whoever it stops.
     *
     * The failure mode this test has to withstand is not someone ignoring it —
     * it is someone adding a fourth call site, seeing red, and "fixing" the test
     * by raising the number. A message that says only "expected 1 but was 2"
     * invites exactly that edit.
     */
    private fun rule(found: List<Pair<String, String>>): String = """
        CREDENTIAL DESTRUCTION IS REACHABLE ONLY FROM AN EXPLICIT USER ACTION.

        If this is failing because a new call site was added, the fix is to remove
        that call site — not to raise the expected count.

        Bugs 3, 4 and 5 of Stage 1 were each an automatic path deciding, on
        incomplete information, to throw away a working session: a failed profile
        fetch, an offline launch, an expired access token. Every one of those call
        sites looked correct where it was written, and every fix left a different
        automatic path holding the same loaded gun.

        The count IS the guarantee. Raising it removes the guarantee.

        An automatic path may refuse to show data. It may not destroy a credential.

        found: $found
    """.trimIndent()

    private fun sources(): List<File> {
        assertTrue(
            "cannot find sources at ${sourceRoot.absolutePath} — has the module layout moved?",
            sourceRoot.isDirectory,
        )
        return sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * Lines that call it, excluding comments and its own declaration.
     *
     * The declaration is matched precisely — `fun <name>(` — and not by any
     * broader shape. An earlier version excluded every line starting with
     * "suspend fun", which silently swallowed a call written on the same line as
     * a function that wrapped it. That is the exact evasion this guard exists to
     * catch, and it hid it.
     */
    private fun callsTo(name: String): List<Pair<String, String>> {
        val declaration = "fun ${name.removeSuffix("()")}("
        return sources().flatMap { file ->
            file.readLines()
                .map { it.trim() }
                .filter { line ->
                    line.contains(name) &&
                        !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*") &&
                        !line.contains(declaration)
                }
                .map { file.name to it }
        }
    }

    @Test
    fun `clearSession is called from exactly one place`() {
        val calls = callsTo("clearSession()")
        assertEquals(rule(calls), 1, calls.size)
        assertEquals(
            "the one clearSession() call must live in SessionRepository, inside signOutByUser()",
            "SessionRepository.kt",
            calls.single().first,
        )
    }

    @Test
    fun `the credential-destroying function is called from exactly one place`() {
        val calls = callsTo("signOutByUser()")
        assertEquals(rule(calls), 1, calls.size)
        assertEquals(
            "its only caller must be the ViewModel function the Sign out button is wired to",
            "AppViewModel.kt",
            calls.single().first,
        )
    }

    /**
     * The specific regression: role loading must never destroy anything. It is
     * where bug 5 lived, because "no signed-in user" was read as "this account
     * has no usable profile" and answered with a sign-out.
     */
    @Test
    fun `role loading cannot sign anyone out`() {
        val repo = File(sourceRoot, "data/SessionRepository.kt").readText()
        val loadRole = repo.substringAfter("suspend fun loadRole(")
        assertTrue(
            "loadRole must not reach any credential-clearing call. This is where bug 5 lived: " +
                "it read currentUserOrNull() == null as \"this account has no usable profile\" " +
                "and answered an expired token with a sign-out.",
            !loadRole.contains("clearSession") && !loadRole.contains("signOutByUser"),
        )
        assertTrue(
            "loadRole must not discover authentication — it takes an already-authenticated id, " +
                "so that an expired token can never be mistaken for a disqualifying fact.",
            !loadRole.contains("currentUserOrNull"),
        )
    }
}
