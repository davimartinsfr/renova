package dev.renova.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.renova.app.R
import dev.renova.app.data.ThemeMode
import dev.renova.app.data.ThemePrefs
import dev.renova.app.domain.Rule
import dev.renova.app.domain.Schedule
import dev.renova.app.domain.TargetType

/** Cores dos blocos: iguais nos dois temas, sempre com texto branco por cima. */
object RenovaColors {
    val Teal = Color(0xFF2D5D68)
    val Mustard = Color(0xFF8A6A12)
    val Plum = Color(0xFF5A3A4A)
    val Olive = Color(0xFF4F6B3A)
    val OnTile = Color.White
}

/** Cores que o Material não tem: o verde de "Livre". */
@Immutable
data class ExtraColors(val ok: Color)

val LocalExtraColors = staticCompositionLocalOf { ExtraColors(ok = Color(0xFF8FBF7A)) }

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC8913F),
    onPrimary = Color(0xFF1A1306),
    primaryContainer = Color(0xFF3A2C16),
    onPrimaryContainer = Color(0xFFF2DDB8),
    secondary = Color(0xFFE0B66A),
    onSecondary = Color(0xFF1A1306),
    secondaryContainer = Color(0xFF3A2C16),
    onSecondaryContainer = Color(0xFFF2DDB8),
    background = Color(0xFF141416),
    onBackground = Color(0xFFF2EFE9),
    surface = Color(0xFF141416),
    onSurface = Color(0xFFF2EFE9),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFFA09A90),
    surfaceContainerLowest = Color(0xFF0F0F11),
    surfaceContainerLow = Color(0xFF1A1A1D),
    surfaceContainer = Color(0xFF1E1E21),
    surfaceContainerHigh = Color(0xFF242428),
    surfaceContainerHighest = Color(0xFF2A2A2E),
    outline = Color(0xFF4A4A4F),
    outlineVariant = Color(0xFF2A2A2E),
    error = Color(0xFFE0705A),
    onError = Color(0xFF1A0A06),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9C6A1E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0DFC0),
    onPrimaryContainer = Color(0xFF2E1F06),
    secondary = Color(0xFF9C6A1E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0DFC0),
    onSecondaryContainer = Color(0xFF2E1F06),
    background = Color(0xFFF7F3EC),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFF7F3EC),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE4DCCE),
    onSurfaceVariant = Color(0xFF6B665D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3EEE5),
    surfaceContainer = Color(0xFFEFE9DF),
    surfaceContainerHigh = Color(0xFFE9E2D6),
    surfaceContainerHighest = Color(0xFFE4DCCE),
    outline = Color(0xFFB5AC9C),
    outlineVariant = Color(0xFFE0D8CA),
    error = Color(0xFFB4442E),
    onError = Color.White,
)

/** Títulos em Fraunces Black (serifa pesada). */
val Fraunces = FontFamily(Font(R.font.fraunces_black, FontWeight.Black))

/** Texto bíblico em Gelasio, a versão livre da Georgia. */
val Gelasio = FontFamily(Font(R.font.gelasio_regular, FontWeight.Normal))

/** Texto corrido, botões e listas. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_semibold, FontWeight.Bold),
)

private fun display(size: Int, line: Int) = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Black, fontSize = size.sp, lineHeight = line.sp)
private fun text(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private val RenovaTypography = Typography(
    displayLarge = display(56, 58),
    displayMedium = display(48, 50),
    displaySmall = display(40, 44),
    headlineLarge = display(32, 36),
    headlineMedium = display(28, 32),
    headlineSmall = display(24, 28),
    titleLarge = display(22, 26),
    titleMedium = text(17, 24, FontWeight.SemiBold),
    titleSmall = text(15, 20, FontWeight.SemiBold),
    bodyLarge = text(16, 24),
    bodyMedium = text(15, 22),
    bodySmall = text(13, 18),
    labelLarge = text(15, 20, FontWeight.SemiBold),
    labelMedium = text(13, 16, FontWeight.Medium),
    labelSmall = text(12, 16, FontWeight.Medium),
)

private val RenovaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun AppTheme(dark: Boolean = chosenDarkTheme(), content: @Composable () -> Unit) {
    val extra = ExtraColors(ok = if (dark) Color(0xFF8FBF7A) else Color(0xFF3F6B2E))
    // Ícones da barra de status na cor certa mesmo quando o tema escolhido difere do sistema.
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).run {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = RenovaTypography,
            shapes = RenovaShapes,
            content = content,
        )
    }
}

/** Escuro ou claro conforme as configurações; em "Sistema", segue o celular. */
@Composable
fun chosenDarkTheme(): Boolean = when (ThemePrefs.mode(LocalContext.current)) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

val DAY_LETTERS = listOf("S", "T", "Q", "Q", "S", "S", "D")

fun formatMinuteOfDay(m: Int) = "%02d:%02d".format(m / 60, m % 60)

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    return if (m >= 60) "${m / 60}h${"%02d".format(m % 60)}" else "$m min"
}

fun formatCountdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun Schedule.describe(): String {
    val daysText = when (days) {
        (1..7).toSet() -> "Todos os dias"
        (1..5).toSet() -> "Seg–Sex"
        setOf(6, 7) -> "Fim de semana"
        else -> days.sorted().joinToString(" ") { DAY_LETTERS[it - 1] }
    }
    val time = if (startMin == endMin) "o dia todo" else "${formatMinuteOfDay(startMin)}–${formatMinuteOfDay(endMin)}"
    return "$daysText, $time"
}

fun formatStepTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return when {
        m == 0 -> "$s s"
        s == 0 -> "$m min"
        else -> "$m min $s s"
    }
}

fun Rule.typeLabel() = when (type) {
    TargetType.APP -> "App"
    TargetType.YT_SHORTS -> "YouTube Shorts"
    TargetType.IG_REELS -> "Instagram Reels"
    TargetType.WEBSITE -> "Site"
}
