package com.rentmate.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * RM-0056 part 8: checks the Compose sources for controls TalkBack cannot name
 * and for layouts that clip at the largest font size.
 *
 * These are source scans, like NoHardcodedUiStringsTest. They cannot prove a
 * screen works with TalkBack; the device check in RM-0047 does that. They do
 * stop the common mistakes from coming back.
 */
class AccessibilitySourceTest {

    private val roots = listOf("src/main/java/com/rentmate/app/ui", "src/main/java/com/rentmate/app/navigation").map(::File)

    private fun sources(): List<File> =
        roots.flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }

    /** Lines [from] to [from] + [count] of [lines], joined, for a look-ahead. */
    private fun window(lines: List<String>, from: Int, count: Int) =
        lines.subList(from, minOf(lines.size, from + count)).joinToString("\n")

    @Test
    fun everyIconButtonHasAName() {
        val offenders = sources().flatMap { file ->
            val lines = file.readLines()
            lines.indices.filter { lines[it].contains("IconButton(") && !lines[it].trimStart().startsWith("import") }
                .filterNot { window(lines, it, 4).contains(Regex("""contentDescription = (stringResource|[a-z]\w*)\b""")) &&
                    !window(lines, it, 4).contains("contentDescription = null") }
                .map { "${file.name}:${it + 1}" }
        }
        assertTrue("IconButton without a contentDescription:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun everyCheckboxAndSwitchIsNamed() {
        // A Checkbox or Switch is named when it has its own semantics label, or when
        // it hands its click to a toggleable row (onCheckedChange = null) whose text names it.
        val offenders = sources().flatMap { file ->
            val lines = file.readLines()
            lines.indices.filter { Regex("""\b(Checkbox|Switch)\(""").containsMatchIn(lines[it]) && !lines[it].trimStart().startsWith("import") }
                .filterNot {
                    val w = window(lines, it, 5)
                    w.contains("onCheckedChange = null") || w.contains("contentDescription")
                }
                .map { "${file.name}:${it + 1}" }
        }
        assertTrue("Checkbox or Switch with no name for TalkBack:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun chipsSitInARowThatWraps() {
        // A plain Row of FilterChips clips the last chip at the largest font size. Use ChipRow.
        val offenders = sources().flatMap { file ->
            val lines = file.readLines()
            lines.indices.filter { lines[it].trimStart().startsWith("Row(") }
                .filter { window(lines, it, 3).contains("FilterChip(") }
                .map { "${file.name}:${it + 1}" }
        }
        assertTrue("FilterChips in a Row; use ChipRow:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
