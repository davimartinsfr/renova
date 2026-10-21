package dev.renova.app.ui

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
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

/** No Xiaomi a acessibilidade abre numa tela própria, sem o Renova destacado. */
internal const val ACCESSIBILITY_XIAOMI_STEPS =
    "No Xiaomi: na Acessibilidade, toque em Apps baixados (ou Serviços baixados), depois em Renova, e ligue. " +
        "Se aparecer um aviso com contagem, espere e toque em OK."

/** Onde fica, no Xiaomi, a permissão que deixa a tela de bloqueio abrir. */
internal const val XIAOMI_POPUP_STEPS =
    "Ligue “Exibir janelas pop-up enquanto executado em segundo plano” (ou “Abrir novas janelas em segundo plano”). " +
        "Sem isso o Xiaomi impede a tela de bloqueio de abrir."

/** Xiaomi, Redmi e POCO: todos informam "Xiaomi" como fabricante. */
internal fun isXiaomi(): Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

/** Estado das três permissões, reavaliado sempre que a tela volta ao primeiro plano. */
internal class PermissionState(
    val serviceOn: Boolean,
    val batteryOk: Boolean,
    val notifOk: Boolean,
    /** Só no Xiaomi: a permissão de abrir janelas em segundo plano, sem a qual a tela de bloqueio não abre. */
    val popupNeeded: Boolean,
    /** null quando o sistema não deixa conferir. */
    val popupOk: Boolean?,
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
            popupNeeded = isXiaomi(),
            popupOk = if (isXiaomi()) backgroundPopupAllowed(context) else true,
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

/** Código da permissão "Exibir janelas pop-up enquanto executado em segundo plano" no MIUI/HyperOS. */
private const val OP_BACKGROUND_START_ACTIVITY = 10021

/**
 * O Xiaomi barra, sem erro nenhum, as telas abertas em segundo plano, e é assim que a tela de bloqueio abre.
 * A permissão não tem API pública: lemos pelo AppOpsManager por reflexão (null se não der).
 */
internal fun backgroundPopupAllowed(context: Context): Boolean? = runCatching {
    val ops = context.getSystemService(AppOpsManager::class.java)
    val check = AppOpsManager::class.java.getMethod(
        "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
    )
    check.invoke(ops, OP_BACKGROUND_START_ACTIVITY, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}.getOrNull()

/** "Outras permissões" do Renova no app Segurança do Xiaomi; se não existir, as informações do app. */
internal fun openXiaomiPermissions(context: Context) {
    val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        .putExtra("extra_pkgname", context.packageName)
    runCatching { context.startActivity(intent) }.onFailure { context.startActivity(appDetails(context)) }
}

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
