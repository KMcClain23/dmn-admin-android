package com.dmnarration.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards one rule about the release signing material:
 *
 *   THE KEYSTORE AND ITS PASSWORDS NEVER ENTER THE REPOSITORY.
 *
 * Written in the same spirit as [CredentialDestructionGuardTest], and for the
 * same reason: the property being protected is "is this in git", which no
 * runtime assertion can observe, and which looks fine in every individual diff
 * right up until it is not.
 *
 * ─── why .gitignore is not enough ───────────────────────────────────────────
 *
 * The .gitignore entries are the INTENTION. They are not the enforcement, and
 * treating them as such is the mistake this file exists to prevent:
 *
 *   * `git add -f` walks straight past them, and someone in a hurry will use it
 *     when git says a file is ignored.
 *   * A file staged BEFORE the pattern existed stays tracked forever; .gitignore
 *     only governs untracked files.
 *   * A password does not have to arrive in a .jks. `KEYSTORE_PASSWORD=hunter2`
 *     in a checked-in build script, a CI yaml, or a README is the same leak with
 *     none of the file extensions.
 *
 * So this asks git what is actually TRACKED, which is the only question that
 * matters. An upload key cannot be rotated the way a password can: Play binds
 * the app to it, and losing control of it is a support ticket with Google, not
 * a change of secret.
 */
class ReleaseSecretsGuardTest {

    /** The repo root. Tests run with the module directory as the working dir. */
    private val repoRoot = File("..").canonicalFile

    private fun git(vararg args: String): List<String> {
        val proc = ProcessBuilder(listOf("git", "-C", repoRoot.path) + args)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        // A guard that cannot run is NOT a guard that passed. If git is missing
        // or this is not a checkout, say so loudly rather than reporting clean.
        assertEquals("git ${args.joinToString(" ")} failed:\n$out", 0, code)
        return out.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun rule(what: String, found: List<String>): String = """
        THE KEYSTORE AND ITS PASSWORDS NEVER ENTER THE REPOSITORY.

        $what

        found: $found

        If this is failing because a file was added, the fix is to remove it from
        git — not to loosen this test and not to add another .gitignore line. A
        .gitignore does not untrack anything that is already tracked.

            git rm --cached <file>

        And if a keystore or a password has actually been committed, the history
        contains it even after the file is deleted. An upload key cannot be
        rotated like a password: Play binds the app to it. Treat it as
        compromised and start the key-reset process with Google.
    """.trimIndent()

    @Test
    fun `no keystore file is tracked`() {
        val tracked = git("ls-files")
        val keystores = tracked.filter {
            it.endsWith(".jks", ignoreCase = true) ||
                it.endsWith(".keystore", ignoreCase = true) ||
                it.substringAfterLast('/') == "keystore.properties"
        }
        assertTrue(
            rule("A keystore file is tracked by git.", keystores),
            keystores.isEmpty(),
        )
    }

    @Test
    fun `no tracked file contains a signing password assignment`() {
        // Only the assignment form. The bare word appears legitimately — in the
        // build script that READS the property, in this test, and in the error
        // message that tells someone to set it. A guard that fires on the word
        // would be turned off within a week.
        val needles = listOf("KEYSTORE_PASSWORD=", "KEY_PASSWORD=", "KEYSTORE_FILE=")
        val offenders = mutableListOf<String>()

        for (path in git("ls-files")) {
            val f = File(repoRoot, path)
            if (!f.isFile || f.length() > 512_000) continue
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            for (needle in needles) {
                // The assignment is only a leak when something follows it. The
                // documentation in build.gradle.kts writes these with a
                // placeholder after them, which is the shape being excluded.
                // No whitespace allowed after the '=': a real assignment has none.
                // Without this, prose that merely MENTIONS the pattern — "a
                // KEYSTORE_PASSWORD= line was staged" — matched the next word and
                // flagged the file. The guard caught its own documentation, which was
                // correct behaviour for the rule as first written and the wrong rule.
                val leaked = Regex(
                    Regex.escape(needle) + """([^\s"'\n]+)""",
                ).findAll(text).any { m ->
                    val value = m.groupValues[1]
                    value !in PLACEHOLDERS && !value.startsWith("$")
                }
                if (leaked) offenders += "$path ($needle)"
            }
        }
        assertTrue(
            rule("A tracked file assigns a signing secret.", offenders),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the gitignore still names the signing material`() {
        // The .gitignore is not the enforcement, but removing it would mean
        // every future keystore shows up as an untracked file someone might add
        // without thinking. Both layers or neither.
        val ignore = File(repoRoot, ".gitignore").readText()
        for (pattern in listOf("*.jks", "*.keystore", "local.properties")) {
            assertTrue(
                "'$pattern' is no longer in .gitignore — the intention layer was removed",
                ignore.lineSequence().any { it.trim() == pattern },
            )
        }
    }

    private companion object {
        /**
         * Values that are documentation rather than secrets. Deliberately short:
         * every addition here is a hole, so a real password must never be able
         * to look like one of these.
         */
        val PLACEHOLDERS = setOf(
            "...",
            "/absolute/path/to/upload-keystore.jks",
        )
    }
}
