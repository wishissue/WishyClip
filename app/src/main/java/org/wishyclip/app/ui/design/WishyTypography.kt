package org.wishyclip.app.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.wishyclip.app.R

/**
 * Inter (SIL Open Font License 1.1, see docs/licenses/Inter-OFL.txt): the closest free,
 * open-source match to the clean grotesque look of Apple's system font. Apple's own
 * San Francisco font can't be redistributed in an Android app, so it is not used.
 */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private val base = Typography()

/** Material text styles re-cut in Inter, with slightly larger, friendlier small sizes. */
val WishyTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = InterFamily),
    displayMedium = base.displayMedium.copy(fontFamily = InterFamily),
    displaySmall = base.displaySmall.copy(fontFamily = InterFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = InterFamily, fontWeight = FontWeight.Bold),
    // iOS-style "large title" used on the Projects screen.
    headlineMedium = base.headlineMedium.copy(
        fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp
    ),
    headlineSmall = base.headlineSmall.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = InterFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = InterFamily),
    bodySmall = base.bodySmall.copy(fontFamily = InterFamily),
    labelLarge = base.labelLarge.copy(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = base.labelSmall.copy(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)
