package org.wishyclip.app.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.wishyclip.app.R

/**
 * Plus Jakarta Sans (SIL OFL 1.1, docs/licenses/PlusJakartaSans-OFL.txt): the WishIssue interface
 * typeface. Bundled as Latin subsets to keep the APK small; other scripts fall back to the system font.
 */
val JakartaFamily = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold)
)

/**
 * Bagel Fat One (SIL OFL 1.1, docs/licenses/BagelFatOne-OFL.txt): expressive brand face. Basic Latin
 * only, so use it for the logo and short display moments, never for settings or dense text.
 */
val BagelFamily = FontFamily(Font(R.font.bagel_fat_one, FontWeight.Normal))

/** Kept so older call sites keep compiling; the UI font is now Plus Jakarta Sans. */
val InterFamily = JakartaFamily

private val base = Typography()

/** Material text styles re-cut in Plus Jakarta Sans, with slightly larger, friendlier small sizes. */
val WishyTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = JakartaFamily),
    displayMedium = base.displayMedium.copy(fontFamily = JakartaFamily),
    displaySmall = base.displaySmall.copy(fontFamily = JakartaFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = JakartaFamily, fontWeight = FontWeight.Bold),
    // Large title used on the Projects screen.
    headlineMedium = base.headlineMedium.copy(
        fontFamily = JakartaFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp
    ),
    headlineSmall = base.headlineSmall.copy(fontFamily = JakartaFamily, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = JakartaFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = JakartaFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = JakartaFamily, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = JakartaFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = JakartaFamily),
    bodySmall = base.bodySmall.copy(fontFamily = JakartaFamily),
    labelLarge = base.labelLarge.copy(fontFamily = JakartaFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = JakartaFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = base.labelSmall.copy(fontFamily = JakartaFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)
