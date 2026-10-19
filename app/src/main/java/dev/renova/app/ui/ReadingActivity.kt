package dev.renova.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.renova.app.data.AppStore
import dev.renova.app.data.Bible
import dev.renova.app.domain.BiblePosition
import dev.renova.app.domain.Reading
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Leitura dos capítulos da meta, seguidos. Cada capítulo só conta depois de rolar até o fim
 * e permanecer nele pelo tempo mínimo (o cronômetro pausa fora do app).
 */
class ReadingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { ReadingScreen(onClose = ::finish) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingScreen(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { AppStore.get(context) }
    val bible by produceState<Bible?>(null) { value = Bible.load(context) }
    val position by store.biblePosition.collectAsState(initial = null)
    var done by rememberSaveable { mutableIntStateOf(0) }
    var unlockedUntil by rememberSaveable { mutableStateOf(0L) }
    // A meta é fixada ao abrir a leitura: mudá-la no meio não altera a sessão em andamento.
    var goal by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { if (goal == 0) goal = store.chaptersPerUnlock.first() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (goal == 0 || done < goal) "Capítulo ${done + 1} de $goal" else "Leitura concluída") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fechar") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val b = bible
            val pos = position
            when {
                unlockedUntil > 0 -> Finished(unlockedUntil, onClose)
                b == null || pos == null || goal == 0 -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                // key(pos): cada capítulo ganha estado novo (rolagem, cronômetro).
                else -> key(pos) {
                    ChapterReader(b, pos) {
                        store.completeChapter(b.chaptersPerBook)
                        done++
                        if (done >= goal) {
                            unlockedUntil = store.grantUnlock()
                            Notifications.showUnlock(context, unlockedUntil)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterReader(bible: Bible, pos: BiblePosition, onComplete: suspend () -> Unit) {
    val key = "${pos.rounds}:${pos.book}:${pos.chapter}"
    val verses = remember(key) { bible.verses(pos.book, pos.chapter) }
    val minSeconds = remember(key) { Reading.minSeconds(verses) }
    var elapsed by rememberSaveable(key) { mutableIntStateOf(0) }
    var reachedEnd by rememberSaveable(key) { mutableStateOf(false) }
    var saving by remember(key) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(key) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(1000)
                elapsed++
            }
        }
    }
    val atEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && last.index == info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 4
        }
    }
    LaunchedEffect(atEnd) { if (atEnd) reachedEnd = true }

    val timeOk = elapsed >= minSeconds
    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (elapsed.toFloat() / minSeconds).coerceAtMost(1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    bible.title(pos.book, pos.chapter),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            itemsIndexed(verses) { i, verse ->
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, baselineShift = BaselineShift.Superscript, color = MaterialTheme.colorScheme.primary)) {
                            append("${i + 1} ")
                        }
                        append(verse)
                    },
                    fontFamily = FontFamily.Serif,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        Surface(tonalElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (timeOk) "Tempo mínimo cumprido" else "Tempo: ${formatCountdown((minSeconds - elapsed) * 1000L)} restante",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (reachedEnd) "Chegou ao fim do capítulo" else "Role até o fim do capítulo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    enabled = timeOk && reachedEnd && !saving,
                    onClick = {
                        saving = true
                        scope.launch { onComplete() }
                    },
                ) { Text("Concluir") }
            }
        }
    }
}

@Composable
private fun Finished(until: Long, onClose: () -> Unit) {
    val remaining by produceState(until - System.currentTimeMillis(), until) {
        while (value > 0) {
            delay(1000)
            value = until - System.currentTimeMillis()
        }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("“Lâmpada para os meus pés é tua palavra, e luz para o meu caminho.”", fontFamily = FontFamily.Serif, fontSize = 20.sp, textAlign = TextAlign.Center)
        Text("Salmos 119:105", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(32.dp))
        Text("Desbloqueado por ${formatCountdown(remaining)}", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose) { Text("Fechar") }
    }
}
