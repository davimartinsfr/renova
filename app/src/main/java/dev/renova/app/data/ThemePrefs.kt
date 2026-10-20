package dev.renova.app.data

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.mutableStateOf

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Escuro") }

/**
 * Tema escolhido nas configurações. Fica em SharedPreferences (leitura síncrona, para a primeira
 * tela já abrir certa) e num estado do Compose, para todas as telas trocarem na hora.
 */
object ThemePrefs {
    private const val FILE = "ui"
    private const val KEY = "theme"
    private var loaded = false
    private val current = mutableStateOf(ThemeMode.SYSTEM)

    fun mode(context: Context): ThemeMode {
        if (!loaded) {
            val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            current.value = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
            loaded = true
        }
        return current.value
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
        current.value = mode
        loaded = true
        // No Android 12+ o sistema guarda o modo do app: o fundo da janela (values-night) também acompanha.
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(UiModeManager::class.java).setApplicationNightMode(
                when (mode) {
                    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                },
            )
        }
    }
}
