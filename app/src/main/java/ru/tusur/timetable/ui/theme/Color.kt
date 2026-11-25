package ru.tusur.timetable.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ru.tusur.timetable.domain.model.LessonType

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2B6FE8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E3FF),
    onPrimaryContainer = Color(0xFF0A2E6B),
    secondary = Color(0xFF4A5C7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE4F5),
    onSecondaryContainer = Color(0xFF12233B),
    tertiary = Color(0xFF0C8F9C),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFD5384A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDADB),
    onErrorContainer = Color(0xFF41000A),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF1A1C22),
    surface = Color(0xFFFDFBFE),
    onSurface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFE6E9F2),
    onSurfaceVariant = Color(0xFF44475A),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC6C9D6),
    inverseSurface = Color(0xFF2E3038),
    inverseOnSurface = Color(0xFFF1F2F7),
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF74A7FF),
    onPrimary = Color(0xFF0A2E6B),
    primaryContainer = Color(0xFF1B3C7A),
    onPrimaryContainer = Color(0xFFD8E3FF),
    secondary = Color(0xFFB7C6E3),
    onSecondary = Color(0xFF22314A),
    secondaryContainer = Color(0xFF33465F),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = Color(0xFF52D2DD),
    onTertiary = Color(0xFF00363B),
    error = Color(0xFFFF7A87),
    onError = Color(0xFF5C0010),
    errorContainer = Color(0xFF7A0B1D),
    onErrorContainer = Color(0xFFFFDADB),
    background = Color(0xFF0C0E13),
    onBackground = Color(0xFFE4E6EE),
    surface = Color(0xFF12141A),
    onSurface = Color(0xFFE4E6EE),
    surfaceVariant = Color(0xFF2A2D36),
    onSurfaceVariant = Color(0xFFC2C5D0),
    outline = Color(0xFF8C8F9B),
    outlineVariant = Color(0xFF3A3D47),
    inverseSurface = Color(0xFFE4E6EE),
    inverseOnSurface = Color(0xFF12141A),
)

data class LessonColorPalette(val light: Color, val dark: Color)

val LessonPalettes: Map<LessonType, LessonColorPalette> = mapOf(
    LessonType.LECTURE to LessonColorPalette(Color(0xFF2B6FE8), Color(0xFF74A7FF)),
    LessonType.PRACTICE to LessonColorPalette(Color(0xFF1A9552), Color(0xFF55CF8D)),
    LessonType.LAB to LessonColorPalette(Color(0xFFCF7400), Color(0xFFFFB04D)),
    LessonType.COURSEWORK to LessonColorPalette(Color(0xFF8448D6), Color(0xFFB892FF)),
    LessonType.EXAM to LessonColorPalette(Color(0xFFD5384A), Color(0xFFFF7A87)),
    LessonType.CONSULTATION to LessonColorPalette(Color(0xFF0C8F9C), Color(0xFF52D2DD)),
    LessonType.OTHER to LessonColorPalette(Color(0xFF5C6470), Color(0xFFADB4C0)),
)

data class LessonColorSet(val colors: Map<LessonType, Color>) {
    fun color(type: LessonType): Color = colors[type] ?: colors.getValue(LessonType.OTHER)
}

val LocalLessonColors = staticCompositionLocalOf {
    LessonColorSet(
        LessonType.entries.associateWith { LessonPalettes.getValue(it).light },
    )
}

val LocalReducedMotion = staticCompositionLocalOf { false }
