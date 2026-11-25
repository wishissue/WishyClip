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

/** Kept so older call sites keep compiling. */
val InterFamily = JakartaFamily

private val base = Typography()

/**
 * The standard Material 3 type scale (system font, so it follows the device's Material You font
 * settings), tightened slightly for an editor: compact labels, readable minimum of 11sp.
 * [JakartaFamily] is the interface font for the few places that set one explicitly.
 */
val WishyTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)
