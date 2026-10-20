package dev.renova.app.data

import android.content.Context

/**
 * Se o tutorial da primeira abertura já foi visto. Fica em SharedPreferences (leitura síncrona, para a
 * tela inicial não aparecer antes do tutorial).
 */
object OnboardingPrefs {
    private const val FILE = "ui"
    private const val KEY = "onboarding_done"

    /** Só numa instalação nova: quem atualizou de uma versão sem tutorial já conhece o app. */
    fun shouldShow(context: Context): Boolean {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return false
        if (!isFreshInstall(context)) {
            setDone(context)
            return false
        }
        return true
    }

    fun setDone(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }

    @Suppress("DEPRECATION") // a versão com PackageInfoFlags só existe no Android 13+
    private fun isFreshInstall(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(true)
}
