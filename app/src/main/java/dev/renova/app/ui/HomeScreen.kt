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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
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
fun HomeScreen(tab: HomeTab, onTabChange: (HomeTab) -> Unit, onEditRule: (String) -> Unit, onNewRule: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    val rules by store.rules.collectAsState(initial = emptyList())
    val usage by store.usage.collectAsState(initial = DailyUsage("", emptyMap()))
    val unlockUntil by store.unlockUntil.collectAsState(initial = 0L)
    val position by store.biblePosition.collectAsState(initial = BiblePosition())
    val chaptersRead by store.chaptersRead.collectAsState(initial = 0)
    val editUnlockUntil by store.editUnlockUntil.collectAsState(initial = 0L)
    val devotionDoneDay by store.devotionDoneDay.collectAsState(initial = null)
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
        topBar = {
            TopAppBar(
                title = { Text("Renova") },
                actions = {
                    Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 16.dp))
                },
            )
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { onTabChange(t) },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == HomeTab.RULES) {
                ExtendedFloatingActionButton(onClick = onNewRule, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nova regra") })
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, if (tab == HomeTab.RULES) 96.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Avisos de configuração valem para o app todo: aparecem nas duas abas.
            update?.let { u ->
                item {
                    SetupCard(
                        title = "Atualização disponível: v${u.version}",
                        text = u.notes.ifBlank { "Uma nova versão foi publicada." } +
                            "\n\nSuas regras e o progresso da leitura são mantidos.",
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
            }
            if (!serviceOn) item {
                SetupCard(
                    title = "Bloqueio desativado",
                    text = "Ative o serviço de acessibilidade “Renova”. Se a opção estiver acinzentada (Android 13+): " +
                        "Configurações › Apps › Renova › ⋮ › Permitir configurações restritas.",
                    action = "Abrir acessibilidade",
                    urgent = true,
                ) { showDisclosure = true }
            }
            if (!batteryOk) item {
                SetupCard(
                    title = "Otimização de bateria",
                    text = "Permita rodar em segundo plano para o Android não desligar o bloqueio.",
                    action = "Permitir",
                ) { requestIgnoreBattery(context) }
            }
            if (!notifOk) item {
                SetupCard(
                    title = "Notificações",
                    text = "Mostra quanto tempo falta para o desbloqueio acabar.",
                    action = "Permitir",
                ) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
            when (tab) {
                HomeTab.RULES -> {
                    item {
                        ReadingCard(position, chaptersRead, unlocked, editUnlockUntil > now, unlockUntil - now) { option ->
                            context.startReading(option)
                        }
                    }
                    item {
                        Text("Regras", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    if (rules.isEmpty()) item {
                        Text(
                            "Nenhuma regra ainda. Toque em “Nova regra” para bloquear o YouTube Shorts, o Reddit ou qualquer app.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(rules, key = { it.id }) { rule ->
                        RuleCard(rule, today[rule.id] ?: 0L, unlocked) { onEditRule(rule.id) }
                    }
                }
                HomeTab.DEVOTION -> {
                    item {
                        DevotionCard(
                            done = Devotion.isDone(devotionDoneDay, LocalDateTime.now()),
                            targetCount = rules.count { it.enabled },
                            onStart = { context.startActivity(Intent(context, DevotionActivity::class.java)) },
                        )
                    }
                    item { DevotionStepsCard() }
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
private fun AccessibilityDisclosure(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uso da acessibilidade") },
        text = {
            Text(
                "O Renova usa o serviço de acessibilidade do Android para saber qual app está aberto e ler o " +
                    "conteúdo da tela só o suficiente para reconhecer o YouTube Shorts, o Reels do Instagram e o " +
                    "endereço dos sites abertos no navegador.\n\n" +
                    "Com isso ele conta o tempo de uso e bloqueia o que você escolheu limitar nas suas regras. " +
                    "Nada do que aparece na tela é guardado, enviado ou compartilhado: tudo fica no aparelho.",
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Concordo, ativar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Agora não") } },
    )
}

/** Abas da tela inicial. */
enum class HomeTab(val label: String, val icon: ImageVector) {
    RULES("Regras", Icons.Default.Lock),
    DEVOTION("7 minutos", Icons.Default.Favorite),
}

private fun requestIgnoreBattery(context: Context) {
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
private fun SetupCard(title: String, text: String, action: String, urgent: Boolean = false, onClick: () -> Unit) {
    Card(
        colors = if (urgent) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun DevotionCard(done: Boolean, targetCount: Int, onStart: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("7 minutos com o Senhor", style = MaterialTheme.typography.titleLarge)
            val apps = if (targetCount == 1) "1 regra" else "$targetCount regras"
            Text(
                when {
                    done -> "Feito hoje. ✓"
                    targetCount == 0 -> "Ainda não feito hoje."
                    else -> "Ainda não feito hoje: $apps aguardando."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (done) OutlinedButton(onClick = onStart) { Text("Fazer de novo") } else Button(onClick = onStart) { Text("Começar") }
        }
    }
}

@Composable
private fun DevotionStepsCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Como funciona", style = MaterialTheme.typography.titleMedium)
            Text(
                "Até você fazer os 7 minutos, os apps das regras ativas ficam bloqueados, e as liberações por " +
                    "leitura não valem. O dia recomeça às 4h.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Devotion.STEPS.forEachIndexed { i, step ->
                Row {
                    Text("${i + 1}. ${step.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        formatStepTime(step.seconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatStepTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return when {
        m == 0 -> "$s s"
        s == 0 -> "$m min"
        else -> "$m min $s s"
    }
}

@Composable
private fun ReadingCard(
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (unlocked) {
                Text("Desbloqueado por ${formatCountdown(remainingMs)}", style = MaterialTheme.typography.titleLarge)
                if (editsAllowed) {
                    Text("Durante o desbloqueio você também pode afrouxar ou apagar regras.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text("Bloqueios ativos", style = MaterialTheme.typography.titleLarge)
            }
            Text("Próximo capítulo: ${Bible.title(position)}", style = MaterialTheme.typography.bodyLarge)
            val roundsText = if (position.rounds > 0) " · ${position.rounds}ª volta completa" else ""
            Text("$chaptersRead capítulos lidos$roundsText", style = MaterialTheme.typography.bodySmall)
            if (!unlocked) {
                OutlinedButton(onClick = { choosing = true }) { Text(UNLOCK_BUTTON_LABEL) }
            }
        }
    }
}

@Composable
private fun RuleCard(rule: Rule, usedSeconds: Long, unlocked: Boolean, onClick: () -> Unit) {
    val reason = RuleEngine.blockReason(rule, usedSeconds, LocalDateTime.now(), unlocked)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rule.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val (status, color) = when {
                    !rule.enabled -> "Desativada" to MaterialTheme.colorScheme.outline
                    reason != null -> "Bloqueado" to MaterialTheme.colorScheme.error
                    else -> "Liberado" to Color(0xFF2E7D32)
                }
                Text(status, color = color, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                rule.typeLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val limit = rule.dailyLimitMin
            Text(
                if (limit != null) "Hoje: ${formatDuration(usedSeconds)} de ${formatDuration(limit * 60L)}"
                else "Hoje: ${formatDuration(usedSeconds)} · sem limite diário",
                style = MaterialTheme.typography.bodyMedium,
            )
            rule.schedules.forEach {
                Text("Bloqueado: ${it.describe()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
