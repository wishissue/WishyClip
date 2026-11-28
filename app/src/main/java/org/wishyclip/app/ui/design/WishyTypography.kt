package org.wishyclip.app.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
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

/** Kept so older call sites keep compiling. */
val InterFamily = JakartaFamily

/**
 * Bagel Fat One (SIL OFL 1.1, docs/licenses/BagelFatOne-OFL.txt): brand typeface by WishIssue.
 */
val BagelFatOne = FontFamily(Font(R.font.bagel_fat_one_regular, FontWeight.Normal))

private val base = Typography()

private fun TextStyle.jakarta() = copy(fontFamily = JakartaFamily)

/**
 * The Material 3 type scale in Plus Jakarta Sans (the WishIssue interface typeface, same as Wishy
 * Browser), tightened slightly for an editor: compact labels, readable minimum of 11sp.
 */
val WishyTypography = Typography(
    displayLarge = base.displayLarge.jakarta(),
    displayMedium = base.displayMedium.jakarta(),
    displaySmall = base.displaySmall.jakarta(),
    headlineLarge = base.headlineLarge.jakarta(),
    headlineMedium = base.headlineMedium.jakarta().copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.jakarta(),
    titleLarge = base.titleLarge.jakarta().copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.jakarta().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.jakarta().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.jakarta(),
    bodyMedium = base.bodyMedium.jakarta(),
    bodySmall = base.bodySmall.jakarta(),
    labelLarge = base.labelLarge.jakarta().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.jakarta().copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.jakarta().copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)
