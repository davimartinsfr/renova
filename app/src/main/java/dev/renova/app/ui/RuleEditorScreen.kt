package dev.renova.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.renova.app.data.AppStore
import dev.renova.app.domain.UnlockOption
import dev.renova.app.domain.Rule
import dev.renova.app.domain.RuleEngine
import dev.renova.app.domain.Schedule
import dev.renova.app.domain.TargetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private data class InstalledApp(val label: String, val packageName: String)

/** A regra original e os horários vão como JSON para o estado salvo da tela. */
private val RuleSaver = Saver<Rule?, String>(
    save = { rule -> rule?.let { Json.encodeToString(it) } },
    restore = { Json.decodeFromString<Rule>(it) },
)

private val SchedulesSaver = Saver<SnapshotStateList<Schedule>, String>(
    save = { Json.encodeToString(it.toList()) },
    restore = { Json.decodeFromString<List<Schedule>>(it).toMutableStateList() },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(ruleId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    val scope = rememberCoroutineScope()

    // Saveable: o formulário sobrevive a girar a tela, à troca de tema e à morte do processo
    // (voltar da leitura exigida para afrouxar a regra pode recriar a tela).
    var original by rememberSaveable(stateSaver = RuleSaver) { mutableStateOf<Rule?>(null) }
    var loaded by rememberSaveable { mutableStateOf(ruleId == null) }
    var type by rememberSaveable { mutableStateOf(TargetType.YT_SHORTS) }
    var target by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var limitOn by rememberSaveable { mutableStateOf(true) }
    var limitMin by rememberSaveable { mutableIntStateOf(15) }
    val schedules = rememberSaveable(saver = SchedulesSaver) { mutableStateListOf<Schedule>() }
    var enabled by rememberSaveable { mutableStateOf(true) }
    val newId = rememberSaveable { UUID.randomUUID().toString() }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var needReading by remember { mutableStateOf(false) }
    var pickingApp by remember { mutableStateOf(false) }

    LaunchedEffect(ruleId) {
        // Já carregada antes de a tela ser recriada: o que está no formulário vale mais que o salvo.
        if (ruleId == null || loaded) return@LaunchedEffect
        store.rules.first().find { it.id == ruleId }?.let { r ->
            original = r
            type = r.type
            target = r.target
            label = r.label
            limitOn = r.dailyLimitMin != null
            // O seletor anda de 5 em 5: arredonda para baixo, para nunca afrouxar sem querer.
            limitMin = (r.dailyLimitMin ?: 15).coerceAtMost(LIMIT_MAX_MIN) / 5 * 5
            schedules.addAll(r.schedules)
            enabled = r.enabled
        }
        loaded = true
    }

    fun build(): Rule? {
        val limit = if (limitOn) limitMin else null
        val normalizedTarget = if (type == TargetType.APP) target else ""
        error = when {
            type == TargetType.APP && normalizedTarget.isEmpty() -> "Escolha um app."
            !limitOn && schedules.isEmpty() -> "Defina um limite ou um horário."
            else -> null
        }
        if (error != null) return null
        return Rule(
            id = RuleEngine.idFor(original, type, normalizedTarget, newId),
            type = type,
            target = normalizedTarget,
            label = when (type) {
                TargetType.YT_SHORTS -> "YouTube Shorts"
                TargetType.IG_REELS -> "Instagram Reels"
                else -> label.ifBlank { target }
            },
            dailyLimitMin = limit,
            schedules = schedules.toList(),
            enabled = enabled,
        )
    }

    /** Afrouxar exige o desbloqueio por leitura; apertar é sempre permitido. */
    fun commit(new: Rule?) {
        // Toques repetidos enquanto grava não salvam de novo.
        if (saving) return
        saving = true
        scope.launch {
            if (RuleEngine.isLoosening(original, new) && !store.isUnlockedForEdits()) {
                needReading = true
                saving = false
                return@launch
            }
            if (new == null) original?.let { store.deleteRule(it.id) } else store.saveRule(new, replacing = original?.id)
            onDone()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopRow(
                label = null,
                navIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navLabel = "Voltar",
                onNav = onDone,
                modifier = Modifier.statusBarsPadding(),
            ) {
                if (original != null) IconButton(onClick = { commit(null) }) { Icon(Icons.Default.Delete, "Apagar") }
            }
        },
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (ruleId == null) "Nova regra" else "Editar regra", style = MaterialTheme.typography.displaySmall)
            val types = listOf(TargetType.YT_SHORTS to "Shorts", TargetType.IG_REELS to "Reels", TargetType.APP to "App")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { i, (t, name) ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = { if (type != t) { type = t; target = ""; label = "" } },
                        shape = SegmentedButtonDefaults.itemShape(i, types.size, MaterialTheme.shapes.small),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = MaterialTheme.colorScheme.background,
                            inactiveBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        icon = {},
                    ) { Text(name, style = MaterialTheme.typography.labelLarge) }
                }
            }
            when (type) {
                TargetType.YT_SHORTS -> Hint("Só o Shorts, no app e no site. O resto do YouTube continua livre.")
                TargetType.IG_REELS -> Hint("Só o Reels, no app e no site. Feed, stories e mensagens continuam livres.")
                else -> OutlinedButton(
                    onClick = { pickingApp = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium,
                ) { Text(if (target.isEmpty()) "Escolher app" else label, style = MaterialTheme.typography.labelLarge) }
            }

            Section("Limite diário", checked = limitOn, onCheckedChange = { limitOn = it })
            if (limitOn) {
                LimitPicker(limitMin, onChange = { limitMin = it })
                if (limitMin == 0) Hint("Sempre bloqueado.")
            }

            Section("Horários de bloqueio")
            Hint("Bloqueia mesmo com tempo sobrando.")
            schedules.forEachIndexed { index, s ->
                ScheduleCard(s, onChange = { schedules[index] = it }, onDelete = { schedules.removeAt(index) })
            }
            QuietButton("Adicionar horário", icon = Icons.Default.Add, onClick = { schedules.add(Schedule((1..5).toSet(), 8 * 60, 18 * 60)) })

            Section("Regra ativa", checked = enabled, onCheckedChange = { enabled = it })

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            PrimaryButton("Salvar", onClick = { build()?.let { commit(it) } }, modifier = Modifier.fillMaxWidth())
        }
    }

    if (pickingApp) {
        AppPickerDialog(
            onDismiss = { pickingApp = false },
            onPick = { app ->
                target = app.packageName
                label = app.label
                pickingApp = false
            },
        )
    }

    if (needReading) LooseningDialog { needReading = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(s: Schedule, onChange: (Schedule) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, true).show()
    }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DAY_LETTERS.forEachIndexed { i, letter ->
                val day = i + 1
                FilterChip(
                    selected = day in s.days,
                    onClick = {
                        val days = if (day in s.days) s.days - day else s.days + day
                        if (days.isNotEmpty()) onChange(s.copy(days = days))
                    },
                    label = { Text(letter) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickTime(s.startMin) { onChange(s.copy(startMin = it)) } }, shape = MaterialTheme.shapes.small) {
                Text(formatMinuteOfDay(s.startMin))
            }
            Text("até")
            OutlinedButton(onClick = { pickTime(s.endMin) { onChange(s.copy(endMin = it)) } }, shape = MaterialTheme.shapes.small) {
                Text(formatMinuteOfDay(s.endMin))
            }
            if (s.startMin == s.endMin) Text("dia todo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Remover horário") }
        }
    }
}

private const val LIMIT_MAX_MIN = 23 * 60 + 55

/** Limite diário em duas rodas lado a lado: horas de 1 em 1 e minutos de 5 em 5. */
@Composable
private fun LimitPicker(totalMin: Int, onChange: (Int) -> Unit) {
    val hours = totalMin / 60
    val minutes = totalMin % 60
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        // Faixa atrás da linha do meio, que marca o valor escolhido.
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(44.dp)
                .clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WheelPicker((0..23).toList(), hours, onSelected = { onChange(it * 60 + minutes) })
            Text("h", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(24.dp))
            WheelPicker((0..55 step 5).toList(), minutes, onSelected = { onChange(hours * 60 + it) }, format = { "%02d".format(it) })
            Text("min", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Título de seção do editor, com um interruptor opcional à direita. */
@Composable
private fun Section(title: String, checked: Boolean? = null, onCheckedChange: (Boolean) -> Unit = {}) {
    Column(Modifier.padding(top = 8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (checked != null) Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AppPickerDialog(onDismiss: () -> Unit, onPick: (InstalledApp) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolher app") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val list = apps
                if (list == null) {
                    CircularProgressIndicator(Modifier.padding(16.dp))
                } else {
                    val filtered = list.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        modifier = Modifier.width(360.dp),
    )
}
