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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.renova.app.BuildConfig
import dev.renova.app.data.AppStore
import dev.renova.app.data.Updater
import dev.renova.app.data.Bible
import dev.renova.app.data.DailyUsage
import dev.renova.app.domain.BiblePosition
import dev.renova.app.domain.Reading
import dev.renova.app.domain.Rule
import dev.renova.app.domain.RuleEngine
import dev.renova.app.service.BlockerAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onEditRule: (String) -> Unit, onNewRule: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    val rules by store.rules.collectAsState(initial = emptyList())
    val usage by store.usage.collectAsState(initial = DailyUsage("", emptyMap()))
    val unlockUntil by store.unlockUntil.collectAsState(initial = 0L)
    val position by store.biblePosition.collectAsState(initial = BiblePosition())
    val chaptersRead by store.chaptersRead.collectAsState(initial = 0)
    val goal by store.chaptersPerUnlock.collectAsState(initial = Reading.DEFAULT_CHAPTERS_PER_UNLOCK)
    val update by store.availableUpdate.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }

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
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewRule, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nova regra") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
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
            item {
                ReadingCard(
                    position, chaptersRead, goal, unlocked, unlockUntil - now,
                    onGoalChange = { new -> scope.launch { store.setChaptersPerUnlock(new) } },
                ) {
                    context.startActivity(Intent(context, ReadingActivity::class.java))
                }
            }
            item {
                Text("Regras", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            if (rules.isEmpty()) item {
                Text(
                    "Nenhuma regra ainda. Toque em “Nova regra” para bloquear o YouTube Shorts, o Reddit ou qualquer app/site.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(rules, key = { it.id }) { rule ->
                RuleCard(rule, today[rule.id] ?: 0L, unlocked) { onEditRule(rule.id) }
            }
        }
    }
}

private fun requestIgnoreBattery(context: Context) {
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
private fun ReadingCard(
    position: BiblePosition,
    chaptersRead: Int,
    goal: Int,
    unlocked: Boolean,
    remainingMs: Long,
    onGoalChange: (Int) -> Unit,
    onRead: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (unlocked) {
                Text("Desbloqueado por ${formatCountdown(remainingMs)}", style = MaterialTheme.typography.titleLarge)
                Text("Durante o desbloqueio você também pode afrouxar ou apagar regras.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Bloqueios ativos", style = MaterialTheme.typography.titleLarge)
            }
            Text("Próxima leitura: ${Bible.title(position)}", style = MaterialTheme.typography.bodyLarge)
            val roundsText = if (position.rounds > 0) " · ${position.rounds}ª volta completa" else ""
            Text("$chaptersRead capítulos lidos$roundsText", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Meta por desbloqueio: ${Reading.chapters(goal)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onGoalChange(goal - 1) },
                    enabled = goal > Reading.MIN_CHAPTERS_PER_UNLOCK && unlocked,
                ) { Text("−", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { contentDescription = "Diminuir meta" }) }
                IconButton(
                    onClick = { onGoalChange(goal + 1) },
                    enabled = goal < Reading.MAX_CHAPTERS_PER_UNLOCK,
                ) { Text("+", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { contentDescription = "Aumentar meta" }) }
            }
            if (!unlocked && goal > Reading.MIN_CHAPTERS_PER_UNLOCK) {
                Text("Diminuir a meta só é possível durante um desbloqueio.", style = MaterialTheme.typography.bodySmall)
            }
            if (!unlocked) {
                OutlinedButton(onClick = onRead) {
                    Text("Ler ${Reading.chapters(goal)} (libera ${Reading.UNLOCK_MINUTES} min)")
                }
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
                rule.typeLabel() + if (rule.type == dev.renova.app.domain.TargetType.WEBSITE) " · ${rule.target}" else "",
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
