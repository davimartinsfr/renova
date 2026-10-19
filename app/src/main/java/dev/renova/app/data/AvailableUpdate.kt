package dev.renova.app.data

import kotlinx.serialization.Serializable

/** Versão nova encontrada pelo [Updater] (só existe na versão do GitHub; na da Play Store a loja atualiza). */
@Serializable
data class AvailableUpdate(val version: String, val apkUrl: String, val notes: String = "")
