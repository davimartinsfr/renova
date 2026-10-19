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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
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
import java.util.UUID

private data class InstalledApp(val label: String, val packageName: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(ruleId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    val scope = rememberCoroutineScope()

    var original by remember { mutableStateOf<Rule?>(null) }
    var loaded by remember { mutableStateOf(ruleId == null) }
    var type by remember { mutableStateOf(TargetType.YT_SHORTS) }
    var target by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var limitOn by remember { mutableStateOf(true) }
    var limitText by remember { mutableStateOf("15") }
    val schedules = remember { mutableStateListOf<Schedule>() }
    var enabled by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var needReading by remember { mutableStateOf(false) }
    var pickingApp by remember { mutableStateOf(false) }

    LaunchedEffect(ruleId) {
        if (ruleId == null) return@LaunchedEffect
        store.rules.first().find { it.id == ruleId }?.let { r ->
            original = r
            type = r.type
            target = r.target
            label = r.label
            limitOn = r.dailyLimitMin != null
            limitText = (r.dailyLimitMin ?: 15).toString()
            schedules.addAll(r.schedules)
            enabled = r.enabled
        }
        loaded = true
    }

    fun build(): Rule? {
        val limit = if (limitOn) limitText.toIntOrNull()?.takeIf { it in 0..1440 } else null
        val normalizedTarget = if (type == TargetType.APP) target else ""
        error = when {
            limitOn && limit == null -> "Limite diário inválido."
            type == TargetType.APP && normalizedTarget.isEmpty() -> "Escolha um app."
            !limitOn && schedules.isEmpty() -> "Defina um limite diário ou ao menos um horário de bloqueio."
            else -> null
        }
        if (error != null) return null
        return Rule(
            id = original?.id ?: UUID.randomUUID().toString(),
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
    fun commit(new: Rule?) = scope.launch {
        if (RuleEngine.isLoosening(original, new) && !store.isUnlockedForEdits()) {
            needReading = true
            return@launch
        }
        if (new == null) original?.let { store.deleteRule(it.id) } else store.saveRule(new)
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId == null) "Nova regra" else "Editar regra") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    if (original != null) IconButton(onClick = { commit(null) }) { Icon(Icons.Default.Delete, "Apagar") }
                },
            )
        },
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("O que bloquear", style = MaterialTheme.typography.titleMedium)
            val types = listOf(TargetType.YT_SHORTS to "Shorts", TargetType.IG_REELS to "Reels", TargetType.APP to "App")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { i, (t, name) ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = { if (type != t) { type = t; target = ""; label = "" } },
                        shape = SegmentedButtonDefaults.itemShape(i, types.size),
                    ) { Text(name) }
                }
            }
            when (type) {
                TargetType.YT_SHORTS -> Text(
                    "Bloqueia só o Shorts no app do YouTube (e youtube.com/shorts no navegador). O resto do YouTube continua liberado.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TargetType.IG_REELS -> Text(
                    "Bloqueia só o Reels no app do Instagram (a aba Reels e Reels abertos em tela cheia) e instagram.com/reels no navegador. " +
                        "Feed, stories e mensagens continuam liberados.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> OutlinedButton(onClick = { pickingApp = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (target.isEmpty()) "Escolher app" else "$label ($target)")
                }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Limite diário", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = limitOn, onCheckedChange = { limitOn = it })
            }
            if (limitOn) {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter(Char::isDigit).take(4) },
                    label = { Text("Minutos por dia (0 = bloqueado sempre)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()
            Text("Horários de bloqueio", style = MaterialTheme.typography.titleMedium)
            Text(
                "Nesses horários fica bloqueado mesmo que ainda haja tempo no limite diário.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            schedules.forEachIndexed { index, s ->
                ScheduleCard(s, onChange = { schedules[index] = it }, onDelete = { schedules.removeAt(index) })
            }
            OutlinedButton(onClick = { schedules.add(Schedule((1..5).toSet(), 8 * 60, 18 * 60)) }) { Text("Adicionar horário") }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Regra ativa", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { build()?.let { commit(it) } }, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
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

    if (needReading) LooseningDialog("Para apagar, desativar ou afrouxar uma regra") { needReading = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(s: Schedule, onChange: (Schedule) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, true).show()
    }
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickTime(s.startMin) { onChange(s.copy(startMin = it)) } }) { Text(formatMinuteOfDay(s.startMin)) }
                Text("até")
                OutlinedButton(onClick = { pickTime(s.endMin) { onChange(s.copy(endMin = it)) } }) { Text(formatMinuteOfDay(s.endMin)) }
                Box(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Remover horário") }
            }
            if (s.startMin == s.endMin) {
                Text("Início igual ao fim = bloqueado o dia todo.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
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
                            Column(Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 10.dp)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        modifier = Modifier.width(360.dp),
    )
}
