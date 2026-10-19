package dev.renova.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.renova.app.domain.UnlockOption

const val UNLOCK_BUTTON_LABEL = "Liberar uso de apps bloqueados"

/** Escolha de quanto tempo liberar e quanto ler para isso. */
@Composable
fun UnlockOptionsDialog(onDismiss: () -> Unit, onPick: (UnlockOption) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Liberar por quanto tempo?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UnlockOption.entries.forEach { option ->
                    OutlinedCard(Modifier.fillMaxWidth().clickable { onPick(option) }) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(option.duration, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Ler ${option.reading}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    "Afrouxar ou apagar regras só é possível depois de ler capítulos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Pede a leitura de capítulos antes de uma mudança que afrouxa o bloqueio. [action] completa a frase. */
@Composable
fun LooseningDialog(action: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Isso afrouxa o bloqueio") },
        text = {
            Text(
                "$action, leia ${UnlockOption.CHAPTER.reading} da Bíblia. " +
                    "Depois você terá ${UnlockOption.CHAPTER.duration} para fazer a alteração (os apps também ficam liberados).",
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

fun Context.startReading(option: UnlockOption) {
    startActivity(Intent(this, ReadingActivity::class.java).putExtra(ReadingActivity.EXTRA_OPTION, option.name))
}
