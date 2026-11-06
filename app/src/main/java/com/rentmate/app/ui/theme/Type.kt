package com.rentmate.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import com.rentmate.app.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * RentMate type scale (RM-0050): seven styles, body at 16 sp, nothing under 12 sp.
 *
 * Figure     displaySmall   32/40 600  the one big number on a screen
 * Screen     headlineSmall  24/32 600
 * Card title titleMedium    18/24 600
 * Body       bodyLarge      16/24 400
 * Body small bodyMedium     14/20 400
 * Label      labelLarge     14/20 500
 * Caption    bodySmall      12/16 400
 *
 * Material slots the app does not use as their own style point at the nearest
 * of the seven, so a stray `labelSmall` can never be smaller than 12 sp.
 *
 * RM-0056: every style uses Public Sans, bundled in res/font as three static
 * TTFs (400, 500, 600) from Google Fonts, so the app no longer depends on the
 * sans-serif each phone ships. Public Sans is under the SIL Open Font License
 * 1.1; the licence ships in assets/licenses/PublicSans-OFL.txt.
 */
val PublicSans = FontFamily(
    Font(R.font.public_sans_regular, FontWeight.Normal),
    Font(R.font.public_sans_medium, FontWeight.Medium),
    Font(R.font.public_sans_semibold, FontWeight.SemiBold)
)

private val Sans = PublicSans

/**
 * Tabular figures, so columns of rand amounts line up digit under digit.
 * Use it on any text that shows an amount: `style = typography.bodyLarge.tabular()`.
 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** Every style in [Typography], for the test that checks they all use Public Sans. */
internal val allTextStyles: List<TextStyle>
    get() = with(Typography) {
        listOf(
            displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
            titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall,
            labelLarge, labelMedium, labelSmall
        )
    }

private val Figure = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, fontFeatureSettings = "tnum")
private val ScreenTitle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp)
private val CardTitle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
private val Body = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
private val BodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
private val Label = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
private val Caption = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)

val Typography = Typography(
    displayLarge = Figure,
    displayMedium = Figure,
    displaySmall = Figure,
    headlineLarge = ScreenTitle,
    headlineMedium = ScreenTitle,
    headlineSmall = ScreenTitle,
    titleLarge = CardTitle,
    titleMedium = CardTitle,
    titleSmall = Label,
    bodyLarge = Body,
    bodyMedium = BodySmall,
    bodySmall = Caption,
    labelLarge = Label,
    labelMedium = Caption,
    labelSmall = Caption
)
