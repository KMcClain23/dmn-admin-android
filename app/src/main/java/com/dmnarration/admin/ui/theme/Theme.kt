package com.dmnarration.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The tokens Material has no slot for.
 *
 * Three ambers, two semantic status colours and two pill colours do not map
 * onto `primary`/`secondary`/`tertiary` without lying about what they mean, and
 * a token forced into the wrong slot is worse than one that is simply absent —
 * the next person reads `tertiary` and has no idea it means "a book is eating
 * the week". They live here instead, reached explicitly.
 */
@Immutable
data class DmnColors(
    val surfaceRaised: Color,
    val divider: Color,
    val textPrimary: Color,
    val textBody: Color,
    val textMuted: Color,
    val textDim: Color,
    val textFaint: Color,
    val accentAmber: Color,
    val accentAmberDim: Color,
    val accentAmberBright: Color,
    val alertRed: Color,
    val capacityLight: Color,
    val statusPrepping: Color,
    val pillNeutralBg: Color,
    val pillNeutralText: Color,
)

private val DmnColorValues = DmnColors(
    surfaceRaised = SurfaceRaised,
    divider = Divider,
    textPrimary = TextPrimary,
    textBody = TextBody,
    textMuted = TextMuted,
    textDim = TextDim,
    textFaint = TextFaint,
    accentAmber = AccentAmber,
    accentAmberDim = AccentAmberDim,
    accentAmberBright = AccentAmberBright,
    alertRed = AlertRed,
    capacityLight = CapacityLight,
    statusPrepping = StatusPrepping,
    pillNeutralBg = PillNeutralBg,
    pillNeutralText = PillNeutralText,
)

val LocalDmnColors = staticCompositionLocalOf { DmnColorValues }

private val DmnDarkScheme = darkColorScheme(
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceContainerHigh = SurfaceRaised,
    onSurfaceVariant = TextMuted,
    outline = SurfaceBorder,
    outlineVariant = Divider,
    primary = AccentAmber,
    onPrimary = Background,
    error = AlertRed,
    onError = TextPrimary,
)

/**
 * Dark, always.
 *
 * Not a preference the app reads and not a scheme the system can override:
 * there is one design and it is this one. The system dark-theme flag is
 * deliberately never consulted, and dynamic colour is deliberately absent — a board
 * tinted by the user's wallpaper would stop matching the web admin, and the
 * urgency colours on a card carry meaning that a generated palette would
 * happily reassign.
 */
@Composable
fun DmnAdminTheme(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalDmnColors provides DmnColorValues) {
        MaterialTheme(
            colorScheme = DmnDarkScheme,
            typography = Typography(
                titleLarge = DmnType.TitleLg,
                titleMedium = DmnType.Title,
                bodyMedium = DmnType.Body,
                bodySmall = DmnType.Small,
                labelSmall = DmnType.Label,
            ),
            content = content,
        )
    }
}

/** Shorthand so composables read `DmnTheme.colors.accentAmber`. */
object DmnTheme {
    val colors: DmnColors
        @Composable get() = LocalDmnColors.current
}
