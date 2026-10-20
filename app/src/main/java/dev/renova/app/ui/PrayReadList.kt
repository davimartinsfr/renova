package dev.renova.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.renova.app.data.AppStore
import dev.renova.app.data.Bible
import dev.renova.app.data.DevotionVerses
import dev.renova.app.domain.Devotion
import dev.renova.app.domain.VerseRef
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Trechos de orar-ler da aba dos 7 minutos: a lista do app e, depois, os que a pessoa adicionou.
 * Só os adicionados podem ser removidos.
 */
@Composable
internal fun PrayReadSection() {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    val scope = rememberCoroutineScope()
    val mine by store.devotionVerses.collectAsState(initial = emptyList())
    val loaded by produceState<Pair<Bible, List<VerseRef>>?>(null) {
        val bible = Bible.load(context)
        value = bible to DevotionVerses.load(context, bible)
    }
    var adding by remember { mutableStateOf(false) }
    val (bible, builtIn) = loaded ?: return
    val list = DevotionVerses.merge(builtIn, mine)
    val todayRef = Devotion.pickForDay(list, Devotion.day(LocalDateTime.now()))

    Column {
        SectionTitle("Orar-ler", onAdd = { adding = true }, addLabel = "Adicionar trecho")
        Text(
            "Um trecho por dia, na ordem da lista.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        list.forEachIndexed { i, ref ->
            ListRow(
                bible.title(ref),
                subtitle = if (ref == todayRef) "Hoje" else null,
                divider = i < list.lastIndex,
            ) {
                if (ref !in builtIn) {
                    IconButton(onClick = { scope.launch { store.removeDevotionVerse(ref) } }) {
                        Icon(Icons.Default.Close, "Remover ${bible.title(ref)}")
                    }
                }
            }
        }
    }

    if (adding) AddVerseDialog(
        bible = bible,
        existing = list,
        onDismiss = { adding = false },
        onAdd = { ref ->
            adding = false
            scope.launch { store.addDevotionVerse(ref) }
        },
    )
}

@Composable
internal fun AddVerseDialog(bible: Bible, existing: List<VerseRef>, onDismiss: () -> Unit, onAdd: (VerseRef) -> Unit) {
    var text by remember { mutableStateOf("") }
    val ref = remember(text) { bible.parseReference(text) }
    val duplicate = ref != null && ref in existing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar trecho") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    text,
                    { text = it },
                    label = { Text("Referência") },
                    placeholder = { Text("Ex.: Sl 23:1-2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    text.isBlank() -> Hint("Livro, capítulo e versículos, como Salmos 23:1-2 ou Jo 3:16.")
                    ref == null -> Hint("Não encontrei essa referência.", error = true)
                    else -> Column(
                        Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(bible.title(ref), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        bible.verses(ref).forEachIndexed { i, verse ->
                            Text("${ref.fromVerse + i} $verse", fontFamily = Gelasio, fontSize = 16.sp, lineHeight = 24.sp)
                        }
                        if (duplicate) Hint("Já está na lista.", error = true)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { ref?.let(onAdd) }, enabled = ref != null && !duplicate) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
