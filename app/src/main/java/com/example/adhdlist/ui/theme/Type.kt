package com.example.adhdlist.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Slightly larger text and line height than Material defaults: easier to scan, less crowded.
private val Base = Typography()

val Typography = Typography(
    titleLarge = Base.titleLarge.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleSmall = Base.titleSmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = 0.2.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 15.sp),
)

