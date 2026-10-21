package dev.renova.app.data

import kotlinx.serialization.Serializable

/** Versão nova encontrada pelo [Updater] (só existe na versão do GitHub; na da Play Store a loja atualiza). */
@Serializable
data class AvailableUpdate(val version: String, val apkUrl: String, val notes: String = "")

/** Se a versão [candidate] ("1.12.1") é mais nova que [current], comparando número por número. */
fun isNewerVersion(candidate: String, current: String): Boolean {
    val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
    val b = current.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}
