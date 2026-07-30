package dev.krfu.tagday.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material 3 defaults throughout, except `bodyLarge` — which is spelled out with the same
// values Material already uses for it, from the project template. No custom type scale has
// been designed for TagDay; every screen styles text with the scheme's named styles
// (`titleLarge`, `labelSmall`, …) rather than ad-hoc sizes.
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
)
