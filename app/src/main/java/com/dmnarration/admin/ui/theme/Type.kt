package com.dmnarration.admin.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dmnarration.admin.R

/**
 * Manrope, bundled rather than fetched.
 *
 * A downloadable font arrives after the first frame, so the board would draw
 * once in the system sans and then reflow — on the one screen the app exists to
 * show, on every cold start, and not at all if the phone is offline. 165KB in
 * the APK buys a first paint that is already correct.
 *
 * One variable file serves every weight: the axis is set per registered weight
 * rather than shipping three static cuts.
 */
// The variable-axis Font overload is still @ExperimentalTextApi. Opted in
// rather than avoided: the alternative is shipping three static cuts of the
// same typeface, which is more bytes for the same result.
@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(manrope(400), manrope(500), manrope(700))

/**
 * The scale from narration-site's `src/lib/design-tokens.ts`.
 *
 * Named for the role each plays on a card rather than mapped onto Material's
 * typography slots, because that is how the web file is organised and how the
 * two get compared when one of them looks wrong.
 */
object DmnType {
    /** Page titles. */
    val TitleLg = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp)

    /** Card and section titles. */
    val Title = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp)

    val Body = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)

    val BodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)

    /** Secondary text — co-narrators, hints. */
    val Small = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)

    /** Section dividers and labels. */
    val Label = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.88.sp, // 0.08em at 11sp
    )

    /**
     * Pill and inline-badge text — the format pill, the "15:" prefix.
     *
     * Same size as Label but without its 0.08em tracking: tracking is what
     * makes a section divider read as a divider, and what makes a two-character
     * badge read as a mistake.
     */
    val Pill = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)

    /**
     * Dates and counts.
     *
     * `tnum` is not decoration. These sit in a column down the board, and
     * proportional digits make the whole column jitter as the numbers change.
     */
    val Numeric = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = "tnum",
    )
}
