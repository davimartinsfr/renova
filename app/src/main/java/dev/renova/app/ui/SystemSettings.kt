package dev.renova.app.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.renova.app.service.BlockerAccessibilityService

/*
 * Atalhos para as telas do sistema onde o usuário libera cada permissão, o mais perto possível do
 * interruptor final. O Android não deixa um app abrir direto o interruptor do próprio serviço de
 * acessibilidade, então lá abrimos a lista já rolada até o Renova.
 */

/** Divulgação exigida pela Play Store antes de o usuário ativar o serviço de acessibilidade. */
internal const val ACCESSIBILITY_DISCLOSURE =
    "O Renova lê a tela pelo serviço de acessibilidade para saber qual app está aberto e reconhecer " +
        "Shorts, Reels e sites no navegador. Assim ele conta o tempo e aplica suas regras.\n\n" +
        "Nada do que aparece na tela é guardado ou enviado."

/** Estado das três permissões, reavaliado sempre que a tela volta ao primeiro plano. */
internal class PermissionState(
    val serviceOn: Boolean,
    val batteryOk: Boolean,
    val notifOk: Boolean,
    /** Pop-up das notificações (ou as configurações, depois de duas recusas). */
    val requestNotifications: () -> Unit,
)

@Composable
internal fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    return remember(tick) {
        PermissionState(
            serviceOn = BlockerAccessibilityService.isEnabled(context),
            batteryOk = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            notifOk = Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            requestNotifications = { requestNotifications(context, launcher) },
        )
    }
}

/** Chave que o app Configurações usa para rolar até um item e destacá-lo. */
private const val FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
private const val SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

/** Lista de acessibilidade com o Renova destacado (onde o sistema aceita o destaque). */
internal fun openAccessibilitySettings(context: Context) {
    val me = ComponentName(context, BlockerAccessibilityService::class.java).flattenToString()
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .putExtra(FRAGMENT_ARGS_KEY, me)
        .putExtra(SHOW_FRAGMENT_ARGS, Bundle().apply { putString(FRAGMENT_ARGS_KEY, me) })
    context.startActivity(intent)
}

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

/** Pop-up do sistema para o Renova rodar em segundo plano sem restrições. */
internal fun requestIgnoreBattery(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

/**
 * Pede a permissão de notificações. Depois de duas recusas o sistema não mostra mais o pedido,
 * então nesse caso abrimos as notificações do Renova nas configurações.
 */
internal fun requestNotifications(context: Context, launcher: ActivityResultLauncher<String>) {
    val prefs = context.getSharedPreferences("permissions", Context.MODE_PRIVATE)
    val activity = context.findActivity()
    val blocked = prefs.getBoolean("notif_asked", false) && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
    if (blocked) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }.onFailure { context.startActivity(appDetails(context)) }
    } else {
        prefs.edit().putBoolean("notif_asked", true).apply()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
