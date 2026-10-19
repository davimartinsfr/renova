package dev.renova.app.ui

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
import dev.renova.app.domain.Rule
import dev.renova.app.domain.Schedule
import dev.renova.app.domain.TargetType

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFE0B66A), secondary = Color(0xFFB9C7E8))
        else -> lightColorScheme(primary = Color(0xFF7A5416), secondary = Color(0xFF3F5A8C))
    }
    MaterialTheme(colorScheme = colors, content = content)
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

fun Rule.typeLabel() = when (type) {
    TargetType.APP -> "App"
    TargetType.YT_SHORTS -> "YouTube Shorts"
    TargetType.WEBSITE -> "Site"
}
