package com.rentmate.app.ui.theme

import androidx.compose.ui.graphics.Color

// RentMate palette (RM-0050). Values come from docs/ui-redesign-plan.html.
// Aloe carries navigation and confirmed state; Amber is reserved for
// anything that needs a decision (overdue bill, unsent request, unsynced action).

// Light
val Aloe = Color(0xFF14564B)
val AloeDark = Color(0xFF0E3F37)
val AloeSoft = Color(0xFFDAF6EF)
val AloeLight = AloeSoft // kept so LoginScreen and LockScreen compile until RM-0054
val Ground = Color(0xFFF7FBFA)
val Surface2 = Color(0xFFF1F4F3)
val Amber = Color(0xFFE8B23A)
val AmberDark = Color(0xFF8A6410)
val AmberSoft = Color(0xFFFCF2DC)
val Clay = Color(0xFFC35047)
val Ink = Color(0xFF101914)
val Muted = Color(0xFF566A60)
val Line = Color(0xFFDCE3E1)

// Dark, derived from the same ground and primary (#0C110F, #579487)
val DarkGround = Color(0xFF0C110F)
val DarkSurface = Color(0xFF141B18)
val DarkSurface2 = Color(0xFF1B2420)
val DarkSurface3 = Color(0xFF243029)
val DarkLine = Color(0xFF34423C)
val DarkPrimary = Color(0xFF579487)
val DarkPrimaryContainer = Color(0xFF1F3F38)
val DarkText = Color(0xFFE8EFEC)
val DarkMuted = Color(0xFFA3B2AC)
val DarkError = Color(0xFFE27A70)
