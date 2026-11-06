package com.rentmate.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** RM-0056 part 2: Public Sans is bundled and every text style uses it. */
class TypographyTest {

    @Test
    fun everyStyleUsesPublicSans() {
        allTextStyles.forEach { assertEquals(PublicSans, it.fontFamily) }
    }

    @Test
    fun nothingIsSmallerThan12sp() {
        allTextStyles.forEach { assertTrue("${it.fontSize}", it.fontSize.value >= 12f) }
    }

    @Test
    fun fontFilesAndLicenceAreBundled() {
        listOf("regular", "medium", "semibold").forEach {
            assertTrue(it, File("src/main/res/font/public_sans_$it.ttf").isFile)
        }
        val licence = File("src/main/assets/licenses/PublicSans-OFL.txt")
        assertTrue(licence.readText().contains("SIL Open Font License"))
    }

    @Test
    fun tabularAddsTnum() {
        assertEquals("tnum", Typography.bodyLarge.tabular().fontFeatureSettings)
    }
}
