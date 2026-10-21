package dev.renova.app.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.renova.app.R

object Notifications {
    private const val CHANNEL_UNLOCK = "unlock"
    private const val ID_UNLOCK = 1

    /**
     * Notificação com contagem regressiva da liberação da regra [ruleId]; some sozinha quando expira.
     * Uma por regra: o id da regra é a tag, e ler de novo para a mesma regra só atualiza a dela.
     */
    @android.annotation.SuppressLint("MissingPermission") // permissão verificada logo no início
    fun showUnlock(context: Context, until: Long, label: String, ruleId: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UNLOCK, "Desbloqueio", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UNLOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$label liberado")
            .setContentText("O bloqueio volta quando o tempo acabar.")
            .setWhen(until)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(until - System.currentTimeMillis())
            .setOngoing(true)
            .build()
        NotificationManagerCompat.from(context).notify(ruleId, ID_UNLOCK, notification)
    }

    /** Tags (ids de regra) das notificações de liberação na tela. */
    fun unlockTags(context: Context): List<String> =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.id == ID_UNLOCK && it.tag != null }
            .map { it.tag }

    fun cancelUnlock(context: Context, ruleId: String) = NotificationManagerCompat.from(context).cancel(ruleId, ID_UNLOCK)
}
