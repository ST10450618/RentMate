package com.rentmate.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * RM-0024: fails when a Compose screen passes literal text to a UI call instead of
 * a string resource, so new screens can't quietly skip translation.
 *
 * Android lint's HardcodedText check only covers XML layouts, which RentMate does
 * not use, so this test scans the Kotlin UI sources directly.
 */
class NoHardcodedUiStringsTest {

    private val uiCall = Regex(
        """\b(?:Text|StatusPill|PrimaryButton|SecondaryButton|SectionLabel)\(\s*"([^"]*)"""" +
            """|\b(?:title|subtitle|body|message|actionLabel|contentDescription|label|supportingText)\s*=\s*"([^"]*)""""
    )

    /** Template values like `${'$'}{bill.title}` are data, not text to translate. */
    private val template = Regex("""\$\{[^}]*}|\$\w+""")

    private fun hasWords(code: String) = uiCall.findAll(code).any { match ->
        val literal = match.groupValues[1].ifEmpty { match.groupValues[2] }
        literal.replace(template, "").any(Char::isLetter)
    }

    @Test
    fun uiSourcesUseStringResources() {
        val roots = listOf("src/main/java/com/rentmate/app/ui", "src/main/java/com/rentmate/app/navigation")
            .map(::File)
        assertTrue("UI source folders not found from ${File(".").absolutePath}", roots.all(File::isDirectory))

        val offenders = roots.flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//")
                    if (code.trimStart().startsWith("*") || !hasWords(code)) null
                    else "${file.name}:${index + 1}: ${line.trim()}"
                }
            }

        assertTrue(
            "Move these strings into res/values/strings.xml:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }
}
