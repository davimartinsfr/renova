package dev.renova.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.renova.app.domain.Rule
import dev.renova.app.domain.UnlockOption

const val UNLOCK_BUTTON_LABEL = "Ler para liberar"

/** Escolha de quanto tempo liberar e quanto ler para isso. */
@Composable
fun UnlockOptionsDialog(onDismiss: () -> Unit, onPick: (UnlockOption) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quanto tempo?") },
        text = {
            Column {
                UnlockOption.entries.forEachIndexed { i, option ->
                    ListRow(
                        title = option.duration,
                        subtitle = "Ler ${option.reading}",
                        divider = i < UnlockOption.entries.lastIndex,
                        onClick = { onPick(option) },
                    )
                }
                Text(
                    "Para afrouxar regras, leia capítulos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Pede a leitura de capítulos antes de uma mudança que afrouxa o bloqueio. */
@Composable
fun LooseningDialog(action: String = "mudar esta regra", onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Isso afrouxa o bloqueio") },
        text = {
            Text(
                "Leia ${UnlockOption.CHAPTER.reading} para $action. " +
                    "Depois você tem ${UnlockOption.CHAPTER.duration} para fazer alterações.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                context.startReading(UnlockOption.CHAPTER)
            }) { Text("Ler agora") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Abre a leitura de [option]; ao terminar, libera só as regras [ruleIds] (vazio: só a edição das regras). */
fun Context.startReading(option: UnlockOption, ruleIds: List<String> = emptyList(), label: String? = null) {
    startActivity(
        Intent(this, ReadingActivity::class.java)
            .putExtra(ReadingActivity.EXTRA_OPTION, option.name)
            .putExtra(ReadingActivity.EXTRA_RULE_IDS, ruleIds.toTypedArray())
            .putExtra(ReadingActivity.EXTRA_LABEL, label),
    )
}

/** Qual regra liberar, antes de escolher o tempo. As bloqueadas vêm primeiro. */
@Composable
fun RulePickerDialog(rules: List<Rule>, status: (Rule) -> String, onDismiss: () -> Unit, onPick: (Rule) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Liberar qual?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rules.forEachIndexed { i, rule ->
                    ListRow(
                        title = rule.label,
                        subtitle = status(rule),
                        divider = i < rules.lastIndex,
                        onClick = { onPick(rule) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
