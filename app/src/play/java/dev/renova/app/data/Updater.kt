package dev.renova.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Na versão da Play Store quem atualiza é a loja: o app não procura nem instala APKs. */
object Updater {
    suspend fun checkIfDue(context: Context, intervalMs: Long) = Unit

    suspend fun check(context: Context): Boolean = true

    val downloading: StateFlow<Boolean> = MutableStateFlow(false)

    fun startDownload(context: Context, update: AvailableUpdate) = Unit
}
