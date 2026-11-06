package com.rentmate.app.ui.components

import androidx.compose.ui.graphics.Color

/** Background and initial colour for one housemate's avatar. */
data class AvatarColors(val background: Color, val foreground: Color)

/**
 * Fixed palette for [MemberAvatar]. Every pair has at least 4.5:1 contrast
 * (checked in AvatarColorsTest), so the initial stays readable in light and dark.
 */
val AvatarPalette: List<AvatarColors> = listOf(
    AvatarColors(Color(0xFFDAF6EF), Color(0xFF0E3F37)),
    AvatarColors(Color(0xFFFCE3C8), Color(0xFF6B3A0A)),
    AvatarColors(Color(0xFFE6DDF7), Color(0xFF3F2A74)),
    AvatarColors(Color(0xFFD5E8F8), Color(0xFF17406B)),
    AvatarColors(Color(0xFFF8D9DC), Color(0xFF7A1F2B)),
    AvatarColors(Color(0xFFE4EFC8), Color(0xFF3F5A0F)),
    AvatarColors(Color(0xFFF6E7B8), Color(0xFF6B5200)),
    AvatarColors(Color(0xFFFADCEB), Color(0xFF7A1F55))
)

/**
 * Index into [AvatarPalette] for [key]. Uses FNV-1a, which gives the same value
 * on every device and run, unlike a value that depends on the JVM. The result
 * is taken as an unsigned 32-bit number before the modulo, so it is never negative.
 */
fun avatarIndex(key: String, paletteSize: Int = AvatarPalette.size): Int {
    var hash = 0x811C9DC5L
    for (byte in key.toByteArray(Charsets.UTF_8)) {
        hash = hash xor (byte.toLong() and 0xFF)
        hash = (hash * 0x01000193L) and 0xFFFFFFFFL
    }
    return (hash % paletteSize).toInt()
}

/** The colours for [key], usually a user id. The same key always gives the same pair. */
fun avatarColorsFor(key: String): AvatarColors = AvatarPalette[avatarIndex(key)]
