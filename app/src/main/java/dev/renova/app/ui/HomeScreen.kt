package dev.renova.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.renova.app.BuildConfig
import dev.renova.app.data.AppStore
import dev.renova.app.data.Updater
import dev.renova.app.data.Bible
import dev.renova.app.data.DailyUsage
import dev.renova.app.domain.BiblePosition
import dev.renova.app.domain.Devotion
import dev.renova.app.domain.UnlockOption
import dev.renova.app.domain.Rule
import dev.renova.app.domain.RuleEngine
import dev.renova.app.service.BlockerAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    selectedTab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
    onEditRule: (String) -> Unit,
    onNewRule: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    val rules by store.rules.collectAsState(initial = emptyList())
    val usage by store.usage.collectAsState(initial = DailyUsage("", emptyMap()))
    val unlockUntil by store.unlockUntil.collectAsState(initial = 0L)
    val position by store.biblePosition.collectAsState(initial = BiblePosition())
    val chaptersRead by store.chaptersRead.collectAsState(initial = 0)
    val editUnlockUntil by store.editUnlockUntil.collectAsState(initial = 0L)
    val devotionDoneDay by store.devotionDoneDay.collectAsState(initial = null)
    val devotionEnabled by store.devotionEnabled.collectAsState(initial = false)
    val tab = selectedTab
    val update by store.availableUpdate.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }

    // Estados do sistema que mudam fora do app: reavaliados ao voltar para a tela.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        val job = scope.launch { Updater.checkIfDue(context, 15 * 60_000L) }
        onPauseOrDispose { job.cancel() }
    }
    val serviceOn = remember(resumeTick) { BlockerAccessibilityService.isEnabled(context) }
    val batteryOk = remember(resumeTick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val notifOk = remember(resumeTick) {
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val unlocked = unlockUntil > now
    val today = usage.today()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    HomeTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { onTabChange(t) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                when (tab) {
                    HomeTab.RULES -> PageHeader(
                        "Renova",
                        "E não vos conformeis com este século, mas transformai-vos pela renovação da vossa mente.",
                        "Rm 12:2",
                        highlight = "transformai-vos pela renovação da vossa mente",
                    ) {
                        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Configurações") }
                    }
                    HomeTab.DEVOTION -> PageHeader("7 minutos com o Senhor", "Provai e vede que o Senhor é bom.", "Sl 34:8")
                }
            }
            // Avisos de configuração valem para o app todo: aparecem nas duas abas.
            if (update != null || !serviceOn || !batteryOk || !notifOk) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    update?.let { u ->
                        NoticeRow(
                            text = "Nova versão: v${u.version}",
                            action = if (downloading) "Baixando…" else "Atualizar",
                        ) {
                            if (!downloading) scope.launch {
                                downloading = true
                                runCatching { Updater.downloadAndInstall(context, u) }
                                    .onFailure { Toast.makeText(context, "Falha ao baixar: ${it.message}", Toast.LENGTH_LONG).show() }
                                downloading = false
                            }
                        }
                    }
                    if (!serviceOn) {
                        NoticeRow(
                            text = "Ative o Renova na acessibilidade",
                            action = "Ativar",
                            // Configurações restritas só travam apps instalados por APK.
                            detail = if (BuildConfig.PLAY_STORE) null else "Acinzentado? Apps › Renova › ⋮ › Permitir configurações restritas",
                            urgent = true,
                        ) { showDisclosure = true }
                    }
                    if (!batteryOk) {
                        NoticeRow(text = "Permita rodar em segundo plano", action = "Permitir") { requestIgnoreBattery(context) }
                    }
                    if (!notifOk) {
                        NoticeRow(text = "Permita notificações", action = "Permitir") {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
            when (tab) {
                HomeTab.RULES -> {
                    item {
                        StatusTile(position, chaptersRead, unlocked, editUnlockUntil > now, unlockUntil - now) { option ->
                            context.startReading(option)
                        }
                    }
                    item { SectionTitle("Regras", onAdd = onNewRule, addLabel = "Nova regra") }
                    if (rules.isEmpty()) item {
                        Text(
                            "Nenhuma regra. Toque em + para criar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Column {
                            rules.forEachIndexed { i, rule ->
                                RuleRow(rule, today[rule.id] ?: 0L, unlocked, divider = i < rules.lastIndex) { onEditRule(rule.id) }
                            }
                        }
                    }
                }
                HomeTab.DEVOTION -> {
                    val devotionDone = Devotion.isDone(devotionDoneDay, LocalDateTime.now())
                    item {
                        DevotionTile(
                            done = devotionDone,
                            targetCount = if (devotionEnabled) rules.count { it.enabled } else 0,
                            onStart = { context.startActivity(Intent(context, DevotionActivity::class.java)) },
                        )
                    }
                    if (!devotionEnabled) item {
                        // Ligar o bloqueio é livre; desligar fica nas Configurações e pede leitura.
                        NoticeRow(
                            text = "Se quiser, o Renova bloqueia os apps das regras até você ter seu tempo com o Senhor",
                            action = "Ativar",
                        ) { scope.launch { store.setDevotionEnabled(true) } }
                    } else if (!devotionDone) item {
                        Text(
                            "Até lá, os apps das regras ficam bloqueados.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item { DevotionSteps() }
                }
            }
        }
    }

    if (showDisclosure) AccessibilityDisclosure(
        onAccept = {
            showDisclosure = false
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onDismiss = { showDisclosure = false },
    )
}

/** Divulgação exigida pela Play Store antes de o usuário ativar o serviço de acessibilidade. */
@Composable
internal fun AccessibilityDisclosure(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acessibilidade") },
        text = {
            Text(
                "O Renova lê a tela pelo serviço de acessibilidade para saber qual app está aberto e reconhecer " +
                    "Shorts, Reels e sites no navegador. Assim ele conta o tempo e aplica suas regras.\n\n" +
                    "Nada do que aparece na tela é guardado ou enviado.",
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Concordo, ativar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Agora não") } },
    )
}

/** Abas da tela inicial. */
/** Solzinho da aba 7 minutos: miolo cheio e oito raios de pontas redondas, no grid de 24 dos ícones Material. */
private val SunIcon: ImageVector = ImageVector.Builder("Sol", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 7f)
        arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 17f)
        arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 7f)
        close()
    }
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round) {
        moveTo(20f, 12f); lineTo(22.5f, 12f)
        moveTo(17.66f, 17.66f); lineTo(19.42f, 19.42f)
        moveTo(12f, 20f); lineTo(12f, 22.5f)
        moveTo(6.34f, 17.66f); lineTo(4.58f, 19.42f)
        moveTo(4f, 12f); lineTo(1.5f, 12f)
        moveTo(6.34f, 6.34f); lineTo(4.58f, 4.58f)
        moveTo(12f, 4f); lineTo(12f, 1.5f)
        moveTo(17.66f, 6.34f); lineTo(19.42f, 4.58f)
    }
}.build()

enum class HomeTab(val label: String, val icon: ImageVector) {
    RULES("Regras", Icons.Default.Lock),
    DEVOTION("7 minutos", SunIcon),
}

internal fun requestIgnoreBattery(context: Context) {
    // A Play Store não aceita a permissão de pedir a isenção direto: lá abrimos a lista de apps.
    if (BuildConfig.PLAY_STORE) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        return
    }
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }
        .onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

@Composable
private fun DevotionTile(done: Boolean, targetCount: Int, onStart: () -> Unit) {
    Tile(
        color = RenovaColors.Plum,
        title = if (done) "Feito hoje" else "Começar",
        subtitle = when {
            done -> "Toque para fazer de novo"
            targetCount == 1 -> "1 regra aguardando"
            targetCount > 1 -> "$targetCount regras aguardando"
            else -> "Ainda não feito hoje"
        },
        onClick = onStart,
    )
}

@Composable
private fun DevotionSteps() {
    Column {
        Devotion.STEPS.forEachIndexed { i, step ->
            ListRow(step.title, divider = i < Devotion.STEPS.lastIndex) {
                Text(formatStepTime(step.seconds), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusTile(
    position: BiblePosition,
    chaptersRead: Int,
    unlocked: Boolean,
    editsAllowed: Boolean,
    remainingMs: Long,
    onRead: (UnlockOption) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    if (choosing) {
        UnlockOptionsDialog(onDismiss = { choosing = false }) { option ->
            choosing = false
            onRead(option)
        }
    }
    val rounds = if (position.rounds > 0) " · ${position.rounds}ª volta" else ""
    Tile(
        color = RenovaColors.Teal,
        title = if (unlocked) "Livre · ${formatCountdown(remainingMs)}" else "Bloqueios ativos",
        subtitle = "Próximo: ${Bible.title(position)} · $chaptersRead capítulos lidos$rounds",
        action = when {
            !unlocked -> UNLOCK_BUTTON_LABEL
            editsAllowed -> "Você pode editar suas regras livremente"
            else -> null
        },
        onClick = if (unlocked) null else ({ choosing = true }),
    )
}

@Composable
private fun RuleRow(rule: Rule, usedSeconds: Long, unlocked: Boolean, divider: Boolean, onClick: () -> Unit) {
    val reason = RuleEngine.blockReason(rule, usedSeconds, LocalDateTime.now(), unlocked)
    val limit = rule.dailyLimitMin
    val summary = buildList {
        when {
            limit == 0 -> add("Sempre bloqueado")
            limit != null -> {
                val used = formatDuration(usedSeconds)
                val total = formatDuration(limit * 60L)
                // "5 de 15 min hoje" em vez de "5 min de 15 min hoje".
                val usedShort = if (used.endsWith(" min") && total.endsWith(" min")) used.removeSuffix(" min") else used
                add("$usedShort de $total hoje")
            }
        }
        rule.schedules.forEach { add(it.describe()) }
        if (limit == null) add("${formatDuration(usedSeconds)} hoje")
    }.joinToString(" · ")
    ListRow(rule.label, summary, divider = divider, onClick = onClick) {
        val (status, color) = when {
            !rule.enabled -> "Desativada" to MaterialTheme.colorScheme.onSurfaceVariant
            reason != null -> "Bloqueado" to MaterialTheme.colorScheme.error
            else -> "Livre" to LocalExtraColors.current.ok
        }
        Text(status, color = color, style = MaterialTheme.typography.labelMedium)
    }
}
