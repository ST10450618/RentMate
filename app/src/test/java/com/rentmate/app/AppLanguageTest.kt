package com.rentmate.app

import com.rentmate.app.data.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * US-4 requires the app to default to a supported language and fall back to
 * English for anything unsupported. These run on the JVM, so they execute in
 * GitHub Actions on every push.
 */
class AppLanguageTest {

    @Test
    fun `known tags map to their language`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.AFRIKAANS, AppLanguage.fromTag("af"))
        assertEquals(AppLanguage.XHOSA, AppLanguage.fromTag("xh"))
    }

    @Test
    fun `unsupported tag falls back to English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("zu"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(""))
    }

    @Test
    fun `absent preference falls back to English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(null))
    }

    @Test
    fun `tags are unique and non-blank`() {
        val tags = AppLanguage.entries.map { it.tag }
        assertEquals(tags.size, tags.toSet().size)
        assertTrue(tags.none { it.isBlank() })
    }

    @Test
    fun `locale list maps to the language on its first supported entry`() {
        assertEquals(AppLanguage.AFRIKAANS, AppLanguage.fromLocaleTags("af"))
        assertEquals(AppLanguage.AFRIKAANS, AppLanguage.fromLocaleTags("af-ZA"))
        assertEquals(AppLanguage.XHOSA, AppLanguage.fromLocaleTags("xh-ZA,en-US"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocaleTags("en-GB"))
        assertEquals(AppLanguage.XHOSA, AppLanguage.fromLocaleTags("zu-ZA, xh"))
    }

    @Test
    fun `empty or unsupported locale list means follow the system`() {
        assertNull(AppLanguage.fromLocaleTags(""))
        assertNull(AppLanguage.fromLocaleTags(null))
        assertNull(AppLanguage.fromLocaleTags("zu-ZA,fr"))
    }

    @Test
    fun `every language survives the round trip through its tag`() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguage.fromTag(language.tag))
            assertEquals(language, AppLanguage.fromLocaleTags(language.tag))
        }
    }

    @Test
    fun `every language has a values folder`() {
        AppLanguage.entries.filter { it != AppLanguage.ENGLISH }.forEach { language ->
            assertTrue(File("src/main/res/values-${language.tag}/strings.xml").isFile)
        }
    }

    @Test
    fun `at least two South African languages are supported`() {
        val saLanguages = AppLanguage.entries.filter { it.tag in setOf("af", "xh", "zu", "st", "tn") }
        assertTrue(saLanguages.size >= 2)
    }
}
