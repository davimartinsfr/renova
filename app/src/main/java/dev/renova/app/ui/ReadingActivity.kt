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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dev.renova.app.domain.UnlockOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Leitura que libera o uso pelo tempo da [UnlockOption] escolhida: capítulos seguidos do plano
 * ou um versículo aleatório. Cada trecho só conta depois de rolar até o fim e permanecer nele
 * pelo tempo mínimo (o cronômetro pausa fora do app).
 */
class ReadingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val option = intent.getStringExtra(EXTRA_OPTION)
            ?.let { name -> UnlockOption.entries.firstOrNull { it.name == name } }
            ?: UnlockOption.CHAPTER
        setContent { AppTheme { ReadingScreen(option, onClose = ::finish) } }
    }

    companion object {
        const val EXTRA_OPTION = "option"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingScreen(option: UnlockOption, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { AppStore.get(context) }
    val bible by produceState<Bible?>(null) { value = Bible.load(context) }
    val position by store.biblePosition.collectAsState(initial = null)
    var done by rememberSaveable { mutableIntStateOf(0) }
    var unlockedUntil by rememberSaveable { mutableStateOf(0L) }
    val goal = option.chapters

    suspend fun finishReading() {
        unlockedUntil = store.grantUnlock(option)
        Notifications.showUnlock(context, unlockedUntil)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            unlockedUntil > 0 -> "Leitura concluída"
                            goal == 0 -> "Versículo para ${option.duration}"
                            else -> "Capítulo ${done + 1} de $goal"
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fechar") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val b = bible
            val pos = position
            when {
                unlockedUntil > 0 -> Finished(unlockedUntil, onClose)
                b == null || pos == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                // O versículo avulso não mexe no plano de leitura.
                goal == 0 -> VerseReader(b) { finishReading() }
                // key(pos): cada capítulo ganha estado novo (rolagem, cronômetro).
                else -> key(pos) {
                    ChapterReader(b, pos) {
                        store.completeChapter(b.chaptersPerBook)
                        done++
                        if (done >= goal) finishReading()
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
        CompleteBar(
            remainingSeconds = minSeconds - elapsed,
            hint = if (reachedEnd) "Chegou ao fim do capítulo" else "Role até o fim do capítulo",
            enabled = timeOk && reachedEnd && !saving,
        ) {
            saving = true
            scope.launch { onComplete() }
        }
    }
}

/** Um versículo sorteado; basta permanecer nele o tempo mínimo. */
@Composable
private fun VerseReader(bible: Bible, onComplete: suspend () -> Unit) {
    // Sorteado uma vez por leitura (sobrevive a girar a tela, não a reabrir).
    val (book, chapter, verse) = rememberSaveable { bible.randomVerse().let { intArrayOf(it.first, it.second, it.third) } }
    val text = remember { bible.verses(book, chapter)[verse] }
    val minSeconds = remember { Reading.minSeconds(listOf(text), floor = Reading.MIN_SECONDS_VERSE) }
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(1000)
                elapsed++
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (elapsed.toFloat() / minSeconds).coerceAtMost(1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("“$text”", fontFamily = FontFamily.Serif, fontSize = 22.sp, lineHeight = 32.sp, textAlign = TextAlign.Center)
            Text(
                "${bible.title(book, chapter)}:${verse + 1}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        CompleteBar(
            remainingSeconds = minSeconds - elapsed,
            hint = "Leia com atenção",
            enabled = elapsed >= minSeconds && !saving,
        ) {
            saving = true
            scope.launch { onComplete() }
        }
    }
}

@Composable
private fun CompleteBar(remainingSeconds: Int, hint: String, enabled: Boolean, onComplete: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (remainingSeconds <= 0) "Tempo mínimo cumprido" else "Tempo: ${formatCountdown(remainingSeconds * 1000L)} restante",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(enabled = enabled, onClick = onComplete) { Text("Concluir") }
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
