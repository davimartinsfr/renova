package dev.renova.app.data

import android.content.Context

/** Na versão da Play Store quem atualiza é a loja: o app não procura nem instala APKs. */
object Updater {
    suspend fun checkIfDue(context: Context, intervalMs: Long) = Unit

    suspend fun check(context: Context): Boolean = true

    suspend fun downloadAndInstall(context: Context, update: AvailableUpdate) = Unit
}
