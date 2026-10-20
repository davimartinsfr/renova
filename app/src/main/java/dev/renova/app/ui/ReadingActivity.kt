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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
    val screenScope = rememberCoroutineScope()

    suspend fun finishReading() {
        unlockedUntil = store.grantUnlock(option)
        Notifications.showUnlock(context, unlockedUntil)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopRow(
                label = when {
                    unlockedUntil > 0 -> null
                    goal == 0 -> "1 versículo · ${option.duration}"
                    else -> "Capítulo ${minOf(done + 1, goal)} de $goal"
                },
                navIcon = Icons.Default.Close,
                navLabel = "Fechar",
                onNav = onClose,
                modifier = Modifier.statusBarsPadding(),
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
                        // Roda no escopo da tela: avançar a posição descarta este ChapterReader e
                        // cancelaria o escopo dele no meio, antes de mostrar a tela final.
                        screenScope.launch {
                            done++
                            // Libera antes de avançar, para a tela final entrar no lugar do capítulo lido.
                            if (done >= goal) finishReading()
                            store.completeChapter(b.chaptersPerBook)
                        }.join()
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChapterReader(bible: Bible, pos: BiblePosition, onComplete: suspend () -> Unit) {
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
        ThinProgress(elapsed.toFloat() / minSeconds)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    bible.title(pos.book, pos.chapter),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
            }
            itemsIndexed(verses) { i, verse ->
                Text(
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                fontFamily = Inter,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                baselineShift = BaselineShift.Superscript,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        ) { append("${i + 1} ") }
                        append(verse)
                    },
                    fontFamily = Gelasio,
                    fontSize = 19.sp,
                    lineHeight = 31.sp,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        CompleteBar(
            remainingSeconds = minSeconds - elapsed,
            hint = if (reachedEnd) "Fim do capítulo" else "Role até o fim",
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
        ThinProgress(elapsed.toFloat() / minSeconds)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("“$text”", fontFamily = Gelasio, fontSize = 24.sp, lineHeight = 36.sp)
            Text(
                "${bible.title(book, chapter)}:${verse + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp),
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
    Column(Modifier.navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (remainingSeconds <= 0) "Tempo cumprido" else "Faltam ${formatCountdown(remainingSeconds * 1000L)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrimaryButton("Concluir", onClick = onComplete, enabled = enabled)
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
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Livre · ${formatCountdown(remaining)}", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(20.dp))
            Text("“Lâmpada para os meus pés é a tua palavra e, luz para os meus caminhos.”", fontFamily = Gelasio, fontSize = 22.sp, lineHeight = 32.sp)
            Text(
                "Salmos 119:105",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        PrimaryButton("Fechar", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}
