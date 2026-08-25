package com.dmnarration.admin.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The web admin's palette, ported value-for-value from the `@theme` block in
 * narration-site's `src/app/globals.css`.
 *
 * Copied rather than approximated on purpose: the two clients show the same
 * board to the same person, and a card that is a slightly different amber on
 * the phone reads as a different card. If a token changes on the web, it
 * changes here — these are the same design system, not two that resemble
 * each other.
 */
val Background = Color(0xFF0F1420)
val Surface = Color(0xFF1E2536)
val SurfaceRaised = Color(0xFF232B3F)
val SurfaceBorder = Color(0xFF2A3145)
val Divider = Color(0xFF232A3D)

val TextPrimary = Color(0xFFE8EBF2)
val TextBody = Color(0xFFC4C9D6)
val TextMuted = Color(0xFF8B93A7)
val TextDim = Color(0xFF5F6478)
val TextFaint = Color(0xFF6B6F7D)

val AccentAmber = Color(0xFFC9A55A)
val AccentAmberDim = Color(0xFF7A5A2E)
val AccentAmberBright = Color(0xFFD4A34E)

val AlertRed = Color(0xFFC85A5A)
val CapacityLight = Color(0xFF6A9C6E)

/** Board status "prepping" — between contracted's blue and recording's yellow. */
val StatusPrepping = Color(0xFF4A9EAE)

val PillNeutralBg = Color(0xFF4A5265)
val PillNeutralText = Color(0xFFC4C9D6)
