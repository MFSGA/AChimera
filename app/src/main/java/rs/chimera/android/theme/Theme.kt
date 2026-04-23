package rs.chimera.android.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Color(0xFF062B64),
    primaryContainer = Color(0xFF12335F),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = Slate80,
    secondaryContainer = Color(0xFF222B3A),
    onSecondaryContainer = Color(0xFFDDE5F4),
    tertiary = Teal80,
    tertiaryContainer = Color(0xFF123D38),
    onTertiaryContainer = Color(0xFFC9FFF4),
    background = SurfaceDark,
    onBackground = Color(0xFFE8EDF4),
    surface = SurfaceDark,
    onSurface = Color(0xFFE8EDF4),
    surfaceContainer = SurfaceVariantDark,
    surfaceContainerHigh = Color(0xFF172131),
    surfaceContainerHighest = Color(0xFF202B3B),
    outline = OutlineDark,
    errorContainer = Color(0xFF4A1717),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF092352),
    secondary = Slate40,
    secondaryContainer = Color(0xFFE6ECF6),
    onSecondaryContainer = Color(0xFF182130),
    tertiary = Teal40,
    tertiaryContainer = Color(0xFFCCF4EC),
    onTertiaryContainer = Color(0xFF002A25),
    background = SurfaceLight,
    onBackground = Color(0xFF182018),
    surface = SurfaceLight,
    onSurface = Color(0xFF182018),
    surfaceContainer = SurfaceVariantLight,
    surfaceContainerHigh = Color(0xFFECF1EA),
    surfaceContainerHighest = Color(0xFFE0E9DF),
    outline = OutlineLight,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun ChimeraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
