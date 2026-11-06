package com.rentmate.app

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * RM-0025: every translatable string and plural in res/values must also exist in
 * values-af and values-xh, in the same file, with the same format arguments.
 *
 * Lint's MissingTranslation check is set to error too; this test runs on the JVM
 * in CI and gives a single readable list of what is missing.
 */
class TranslationCompletenessTest {

    private val res = File("src/main/res")
    private val locales = listOf("af", "xh")
    private val formatArg = Regex("""%(\d+\$)?[sd]""")

    /** A resource is identified by its tag and name, e.g. "string/save". */
    private data class Entry(val id: String, val texts: List<String>)

    private fun entries(file: File): Map<String, Entry> {
        if (!file.isFile) return emptyMap()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        return (0 until root.childNodes.length)
            .map(root.childNodes::item)
            .filterIsInstance<Element>()
            .filter { it.tagName == "string" || it.tagName == "plurals" }
            .filter { it.getAttribute("translatable") != "false" }
            .map { element ->
                val texts = if (element.tagName == "plurals") {
                    val items = element.getElementsByTagName("item")
                    (0 until items.length).map { items.item(it).textContent }
                } else {
                    listOf(element.textContent)
                }
                Entry("${element.tagName}/${element.getAttribute("name")}", texts)
            }
            .associateBy { it.id }
    }

    private fun args(texts: List<String>): Set<String> =
        texts.flatMap { text -> formatArg.findAll(text).map { it.value } }.toSet()

    private fun defaultFiles(): List<File> =
        res.resolve("values").listFiles { f -> f.extension == "xml" }.orEmpty()
            .filter { entries(it).isNotEmpty() }
            .sortedBy { it.name }

    @Test
    fun everyTranslatableKeyIsTranslated() {
        val files = defaultFiles()
        assertTrue("No string files found under ${res.absolutePath}", files.isNotEmpty())

        val missing = files.flatMap { file ->
            val defaults = entries(file)
            locales.flatMap { locale ->
                val translated = entries(res.resolve("values-$locale/${file.name}"))
                (defaults.keys - translated.keys).map { "values-$locale/${file.name}: $it" }
            }
        }

        assertTrue("Missing translations:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun translationsKeepTheSameFormatArguments() {
        val mismatched = defaultFiles().flatMap { file ->
            val defaults = entries(file)
            locales.flatMap { locale ->
                entries(res.resolve("values-$locale/${file.name}")).values.mapNotNull { entry ->
                    val expected = defaults[entry.id]?.let { args(it.texts) } ?: return@mapNotNull null
                    val actual = args(entry.texts)
                    if (actual == expected) null
                    else "values-$locale/${file.name}: ${entry.id} has $actual, expected $expected"
                }
            }
        }

        assertTrue("Format arguments differ:\n" + mismatched.joinToString("\n"), mismatched.isEmpty())
    }

    @Test
    fun untranslatableKeysAreNotTranslated() {
        val fixed = defaultFiles().flatMap { file ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val strings = doc.getElementsByTagName("string")
            (0 until strings.length).map { strings.item(it) as Element }
                .filter { it.getAttribute("translatable") == "false" }
                .map { "string/${it.getAttribute("name")}" }
        }.toSet()

        val overridden = locales.flatMap { locale ->
            res.resolve("values-$locale").listFiles { f -> f.extension == "xml" }.orEmpty()
                .flatMap { file -> entries(file).keys.filter { it in fixed }.map { "values-$locale/${file.name}: $it" } }
        }

        assertTrue("These are translatable=\"false\" and must not be translated:\n" + overridden.joinToString("\n"), overridden.isEmpty())
    }
}
