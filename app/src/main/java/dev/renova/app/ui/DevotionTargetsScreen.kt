package dev.renova.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.renova.app.data.AppStore
import dev.renova.app.domain.Rule
import dev.renova.app.domain.RuleEngine
import dev.renova.app.domain.TargetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/** Lista própria de apps/sites que ficam presos até os 7 minutos com o Senhor do dia. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DevotionTargetsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    val scope = rememberCoroutineScope()
    val targets by store.devotionTargets.collectAsState(initial = emptyList())
    var pickingApp by remember { mutableStateOf(false) }
    var typingSite by remember { mutableStateOf(false) }
    var needReading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apps dos 7 minutos") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Estes apps e sites ficam bloqueados todo dia até você fazer os 7 minutos com o Senhor. " +
                        "O dia recomeça às 4h. As liberações por leitura não valem para esta lista.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (targets.none { it.type == TargetType.YT_SHORTS }) {
                        OutlinedButton(onClick = { addTarget(store, scope, TargetType.YT_SHORTS, "", "YouTube Shorts") }) { Text("+ Shorts") }
                    }
                    OutlinedButton(onClick = { pickingApp = true }) { Text("+ App") }
                    OutlinedButton(onClick = { typingSite = true }) { Text("+ Site") }
                }
            }
            if (targets.isEmpty()) item {
                Text("Nenhum app na lista ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(targets, key = { it.id }) { rule ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                rule.typeLabel() + if (rule.type == TargetType.WEBSITE) " · ${rule.target}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                // Tirar da lista afrouxa o bloqueio: exige leitura de capítulos, como nas regras.
                                if (!store.isUnlockedForEdits()) {
                                    needReading = true
                                } else {
                                    store.saveDevotionTargets(store.devotionTargets.first().filterNot { it.id == rule.id })
                                }
                            }
                        }) { Icon(Icons.Default.Delete, "Tirar da lista") }
                    }
                }
            }
        }
    }

    if (pickingApp) {
        AppPickerDialog(
            onDismiss = { pickingApp = false },
            onPick = { app ->
                pickingApp = false
                addTarget(store, scope, TargetType.APP, app.packageName, app.label)
            },
        )
    }
    if (typingSite) {
        SiteDialog(onDismiss = { typingSite = false }) { domain ->
            typingSite = false
            addTarget(store, scope, TargetType.WEBSITE, domain, domain)
        }
    }
    if (needReading) LooseningDialog("Para tirar um app da lista dos 7 minutos") { needReading = false }
}

private fun addTarget(store: AppStore, scope: CoroutineScope, type: TargetType, target: String, label: String) {
    scope.launch {
        val current = store.devotionTargets.first()
        if (current.any { it.type == type && it.target == target }) return@launch
        store.saveDevotionTargets(current + Rule(id = UUID.randomUUID().toString(), type = type, target = target, label = label))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SiteDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val domain = RuleEngine.normalizeDomain(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar site") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.trim() },
                    label = { Text("Site (ex.: reddit.com)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SITE_SUGGESTIONS.forEach { s -> FilterChip(selected = text == s, onClick = { text = s }, label = { Text(s) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(domain) }, enabled = domain.isNotEmpty()) { Text("Adicionar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
