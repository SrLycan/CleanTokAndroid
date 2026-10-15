package ec.cleantok.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Paleta de marca, la misma de la web (CleanTok_Pantallas.pptx): azul, azul marino, verde, naranja y grises azulados. */
private val Claro = lightColorScheme(
    primary = Color(0xFF2476C8), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F1FB), onPrimaryContainer = Color(0xFF15365C),
    secondary = Color(0xFF15365C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF0F6), onSecondaryContainer = Color(0xFF15365C),
    tertiary = Color(0xFF5E7188), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE8F5), onTertiaryContainer = Color(0xFF15365C),
    error = Color(0xFFC62828), onError = Color.White,
    errorContainer = Color(0xFFFDE8E8), onErrorContainer = Color(0xFF8A1C1C),
    background = Color(0xFFF3F6FA), onBackground = Color(0xFF20344D),
    surface = Color(0xFFF3F6FA), onSurface = Color(0xFF20344D),
    surfaceVariant = Color(0xFFE3EAF2), onSurfaceVariant = Color(0xFF5E7188),
    outline = Color(0xFF8A9AAD), outlineVariant = Color(0xFFD9E4EE),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFEEF2F7), surfaceContainerHigh = Color(0xFFE8EDF3),
    surfaceContainerHighest = Color(0xFFE1E8EF),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF8CBCF0), onPrimary = Color(0xFF0B2F55),
    primaryContainer = Color(0xFF1D4F86), onPrimaryContainer = Color(0xFFE6F1FB),
    secondary = Color(0xFFB9C8DA), onSecondary = Color(0xFF15365C),
    secondaryContainer = Color(0xFF25384F), onSecondaryContainer = Color(0xFFD9E4EE),
    tertiary = Color(0xFFB4C3D6), onTertiary = Color(0xFF20344D),
    tertiaryContainer = Color(0xFF2C3E55), onTertiaryContainer = Color(0xFFDDE8F5),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF7A1D1D), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1722), onBackground = Color(0xFFE3EAF2),
    surface = Color(0xFF0F1722), onSurface = Color(0xFFE3EAF2),
    surfaceVariant = Color(0xFF2A3747), onSurfaceVariant = Color(0xFFA8B6C7),
    outline = Color(0xFF6E7E92), outlineVariant = Color(0xFF2C3A4C),
    surfaceContainerLowest = Color(0xFF141D29), surfaceContainerLow = Color(0xFF18222F),
    surfaceContainer = Color(0xFF1C2735), surfaceContainerHigh = Color(0xFF212C3A),
    surfaceContainerHighest = Color(0xFF2B3746),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val Tipografia = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun CleanTokTema(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        shapes = Formas,
        typography = Tipografia,
        content = contenido,
    )
}

/** Colores con significado (estado del stock, severidad, resultado de una acción). */
enum class Tono { OK, AVISO, CRITICO, INFO, NEUTRO }

@Immutable
data class ColoresTono(val fondo: Color, val contenido: Color)

@Composable
@ReadOnlyComposable
fun Tono.colores(): ColoresTono {
    val c = MaterialTheme.colorScheme
    val oscuro = c.surface.luminance() < 0.5f
    return when (this) {
        Tono.OK -> if (oscuro) ColoresTono(Color(0xFF16402B), Color(0xFF9FE3BF)) else ColoresTono(Color(0xFFE3F5EC), Color(0xFF1C7A4D))
        Tono.AVISO -> if (oscuro) ColoresTono(Color(0xFF4D3112), Color(0xFFFFD3A8)) else ColoresTono(Color(0xFFFDEEDC), Color(0xFFA0520F))
        Tono.CRITICO -> ColoresTono(c.errorContainer, c.onErrorContainer)
        Tono.INFO -> ColoresTono(c.tertiaryContainer, c.onTertiaryContainer)
        Tono.NEUTRO -> ColoresTono(c.surfaceVariant, c.onSurfaceVariant)
    }
}
