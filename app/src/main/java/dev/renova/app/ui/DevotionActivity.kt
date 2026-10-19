package dev.renova.app.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.renova.app.data.AppStore
import dev.renova.app.data.Bible
import dev.renova.app.data.DevotionVerses
import dev.renova.app.domain.Devotion
import dev.renova.app.domain.VerseRef
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * "7 minutos com o Senhor": as sete etapas em sequência, cada uma com seu tempo.
 * Não dá para pular etapas; o cronômetro pausa fora do app e a tela fica acesa.
 */
class DevotionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { AppTheme { DevotionScreen(onClose = ::finish) } }
    }
}

private data class PrayReading(val title: String, val verses: List<String>, val firstVerse: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevotionScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    var started by rememberSaveable { mutableStateOf(false) }
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    var finished by rememberSaveable { mutableStateOf(false) }
    val prayReading by produceState<PrayReading?>(null) {
        val bible = Bible.load(context)
        val ref = Devotion.pickForDay(DevotionVerses.load(context, bible), Devotion.day(LocalDateTime.now()))
            ?: FALLBACK_VERSE
        value = PrayReading(bible.title(ref), bible.verses(ref), ref.fromVerse)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("7 minutos com o Senhor") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fechar") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                finished -> Finished(onClose)
                !started -> Intro { started = true }
                else -> Running(elapsed, prayReading)
            }
        }
    }

    if (started && !finished) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(Unit) {
            val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()
            try {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (elapsed < Devotion.TOTAL_SECONDS) {
                        delay(1000)
                        val before = Devotion.stepAt(elapsed)?.first
                        elapsed++
                        // Aviso sonoro curto a cada troca de etapa.
                        if (Devotion.stepAt(elapsed)?.first != before) tone?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                    }
                    store.setDevotionDone(Devotion.dayKey(LocalDateTime.now()))
                    finished = true
                }
            } finally {
                tone?.release()
            }
        }
    }
}

@Composable
private fun Intro(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Sete etapas, sete minutos. Cada etapa avança sozinha quando o tempo acaba (com um aviso sonoro). " +
                "Se sair do app, o tempo pausa.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Devotion.STEPS.forEachIndexed { i, step ->
            Row {
                Text("${i + 1}. ${step.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(formatCountdown(step.seconds * 1000L), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Começar") }
    }
}

@Composable
private fun Running(elapsed: Int, prayReading: PrayReading?) {
    val (index, inStep) = Devotion.stepAt(elapsed) ?: (Devotion.STEPS.lastIndex to Devotion.STEPS.last().seconds)
    val step = Devotion.STEPS[index]
    LinearProgressIndicator(
        progress = { elapsed.toFloat() / Devotion.TOTAL_SECONDS },
        modifier = Modifier.fillMaxWidth(),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Etapa ${index + 1} de ${Devotion.STEPS.size}", style = MaterialTheme.typography.labelLarge)
        Text(step.title, style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Serif)
        Text(
            formatCountdown((step.seconds - inStep) * 1000L),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(step.instruction, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (index == Devotion.PRAY_READ_STEP && prayReading != null) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    prayReading.verses.forEachIndexed { i, verse ->
                        Text(
                            "${prayReading.firstVerse + i} $verse",
                            fontFamily = FontFamily.Serif,
                            fontSize = 19.sp,
                            lineHeight = 28.sp,
                        )
                    }
                    Text(prayReading.title, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(
            step.references,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Devotion.STEPS.getOrNull(index + 1)?.let { next ->
            Text(
                "A seguir: ${next.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun Finished(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "“Provai, e vede que o Senhor é bom.”",
            fontFamily = FontFamily.Serif,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
        Text("Salmos 34:8", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(32.dp))
        Text("7 minutos feitos hoje", style = MaterialTheme.typography.titleLarge)
        Text(
            "Os apps da lista dos 7 minutos ficam soltos até o dia virar, às ${Devotion.DAY_STARTS_AT_HOUR}h.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose) { Text("Fechar") }
    }
}

private val FALLBACK_VERSE = VerseRef(book = 48, chapter = 6, fromVerse = 17, toVerse = 18) // Efésios 6:17-18
