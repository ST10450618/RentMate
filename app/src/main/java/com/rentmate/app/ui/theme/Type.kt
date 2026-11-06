package com.rentmate.app.ui.theme

import androidx.compose.material3.Typography
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
 * The font is the platform sans-serif; bundling Public Sans was left out on purpose.
 */
private val Sans = FontFamily.SansSerif

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
