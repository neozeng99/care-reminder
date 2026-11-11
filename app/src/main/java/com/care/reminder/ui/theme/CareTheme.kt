package com.care.reminder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val CareTeal = Color(0xFF0F766E)
val CareTealLight = Color(0xFFCCFBF1)
val CareBackground = Color(0xFFF8FAFC)
val CareSurface = Color(0xFFFFFFFF)

val MedicineRed = Color(0xFFDC2626)
val MedicineRedBg = Color(0xFFFEF2F2)
val SafeGreen = Color(0xFF15803D)
val SafeGreenBg = Color(0xFFDCFCE7)

val WaterBlue = Color(0xFF0284C7)
val WaterBlueBg = Color(0xFFE0F2FE)

val SedentaryAmber = Color(0xFFD97706)
val SedentaryAmberBg = Color(0xFFFEF3C7)

private val LightColors = lightColorScheme(
    primary = CareTeal,
    onPrimary = Color.White,
    primaryContainer = CareTealLight,
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = WaterBlue,
    onSecondary = Color.White,
    background = CareBackground,
    surface = CareSurface,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

// 适老化大字号排版
private val ElderTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold
    ),
    titleMedium = TextStyle(
        fontSize = 19.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold
    )
)

@Composable
fun CareReminderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = ElderTypography,
        content = content
    )
}
