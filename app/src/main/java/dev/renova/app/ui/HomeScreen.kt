package dev.renova.app.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
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
    val unlocks by store.unlocks.collectAsState(initial = emptyMap())
    val position by store.biblePosition.collectAsState(initial = BiblePosition())
    val chaptersRead by store.chaptersRead.collectAsState(initial = 0)
    val editUnlockUntil by store.editUnlockUntil.collectAsState(initial = 0L)
    // Nulo até o DataStore carregar, para a aba dos 7 minutos não piscar o estado errado.
    val devotion by remember { store.devotionEnabled.combine(store.devotionDoneDay) { on, day -> on to day } }
        .collectAsState(initial = null)
    val tab = selectedTab
    val update by store.availableUpdate.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        val job = scope.launch { Updater.checkIfDue(context, 15 * 60_000L) }
        onPauseOrDispose { job.cancel() }
    }
    val permissions = rememberPermissionState()
    val serviceOn = permissions.serviceOn
    val batteryOk = permissions.batteryOk
    val notifOk = permissions.notifOk

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val today = usage.today()
    val enabledRules = rules.filter { it.enabled }
    val freeRules = enabledRules.filter { RuleEngine.isUnlocked(it, unlocks, now) }
    fun ruleStatus(rule: Rule): String {
        val until = unlocks[rule.id] ?: 0L
        return when {
            !rule.enabled -> "Desativada"
            until > now -> "Livre · ${formatCountdown(until - now)}"
            RuleEngine.blockReason(rule, today[rule.id] ?: 0L, LocalDateTime.now(), unlocked = false) != null -> "Bloqueado"
            else -> "Livre"
        }
    }

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
                        "E não vos conformeis com este século, mas transformai-vos pela renovação da vossa mente…",
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
                            // Configurações restritas só travam apps instalados por APK, do Android 13 em diante.
                            detail = if (BuildConfig.PLAY_STORE || Build.VERSION.SDK_INT < 33) null else
                                "Se não der para ativar, abra as informações do app Renova, toque nos três pontinhos e em Permitir configurações restritas.",
                            urgent = true,
                        ) { showDisclosure = true }
                    }
                    if (!batteryOk) {
                        NoticeRow(text = "Permita rodar em segundo plano", action = "Permitir") { requestIgnoreBattery(context) }
                    }
                    if (!notifOk) {
                        NoticeRow(text = "Permita notificações", action = "Permitir") {
                            permissions.requestNotifications()
                        }
                    }
                }
            }
            when (tab) {
                HomeTab.RULES -> {
                    item {
                        // Bloqueadas primeiro: é o que a pessoa costuma querer liberar.
                        val pickable = enabledRules.sortedBy { ruleStatus(it) != "Bloqueado" }
                        StatusTile(position, chaptersRead, pickable, ::ruleStatus, freeRules, editUnlockUntil > now) { option, rule ->
                            context.startReading(option, listOfNotNull(rule?.id), rule?.label)
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
                                RuleRow(rule, today[rule.id] ?: 0L, ruleStatus(rule), divider = i < rules.lastIndex) { onEditRule(rule.id) }
                            }
                        }
                    }
                }
                HomeTab.DEVOTION -> devotion?.let { (devotionEnabled, devotionDoneDay) ->
                    val devotionDone = Devotion.isDone(devotionDoneDay, LocalDateTime.now())
                    val hasTargets = rules.any { it.enabled }
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
                    } else if (!devotionDone && hasTargets) item {
                        Text(
                            "Até lá, os apps das regras ficam bloqueados.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item { PrayReadSection() }
                }
            }
        }
    }

    if (showDisclosure) AccessibilityDisclosure(
        onAccept = {
            showDisclosure = false
            openAccessibilitySettings(context)
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
        text = { Text(ACCESSIBILITY_DISCLOSURE) },
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
private fun StatusTile(
    position: BiblePosition,
    chaptersRead: Int,
    rules: List<Rule>,
    status: (Rule) -> String,
    freeRules: List<Rule>,
    editsAllowed: Boolean,
    onRead: (UnlockOption, Rule?) -> Unit,
) {
    // A leitura libera uma regra só: primeiro a regra (se houver mais de uma), depois o tempo.
    var picking by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Rule?>(null) }
    var choosing by remember { mutableStateOf(false) }
    if (picking) {
        RulePickerDialog(rules, status, onDismiss = { picking = false }) { rule ->
            picking = false
            chosen = rule
            choosing = true
        }
    }
    if (choosing) {
        UnlockOptionsDialog(onDismiss = { choosing = false }) { option ->
            choosing = false
            onRead(option, chosen)
        }
    }
    val rounds = if (position.rounds > 0) " · ${position.rounds}ª volta" else ""
    val reading = "Próximo: ${Bible.title(position)} · $chaptersRead capítulos lidos$rounds"
    Tile(
        color = RenovaColors.Teal,
        title = when (freeRules.size) {
            0 -> "Bloqueios ativos"
            1 -> "${freeRules.single().label} livre"
            else -> "${freeRules.size} regras livres"
        },
        subtitle = if (editsAllowed) "$reading\nVocê pode editar suas regras livremente" else reading,
        action = UNLOCK_BUTTON_LABEL,
        onClick = {
            if (rules.size > 1) {
                picking = true
            } else {
                chosen = rules.singleOrNull()
                choosing = true
            }
        },
    )
}

@Composable
private fun RuleRow(rule: Rule, usedSeconds: Long, status: String, divider: Boolean, onClick: () -> Unit) {
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
        val color = when (status) {
            "Desativada" -> MaterialTheme.colorScheme.onSurfaceVariant
            "Bloqueado" -> MaterialTheme.colorScheme.error
            else -> LocalExtraColors.current.ok
        }
        Text(status, color = color, style = MaterialTheme.typography.labelMedium)
    }
}
