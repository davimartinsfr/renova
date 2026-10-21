package dev.renova.app.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.renova.app.R
import dev.renova.app.data.AppStore
import dev.renova.app.data.Bible
import dev.renova.app.data.DevotionVerses
import dev.renova.app.domain.Devotion
import dev.renova.app.domain.VerseRef
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * "7 minutos com o Senhor": as sete etapas em sequência, cada uma com seu tempo.
 * Não dá para pular etapas; o cronômetro pausa fora do app e a tela fica acesa.
 */
class DevotionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { AppTheme { DevotionScreen(onClose = ::finish, onDone = ::finishReturning) } }
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
    }

    override fun onPause() {
        isShowing = false
        super.onPause()
    }

    companion object {
        /** Na frente agora: o serviço não puxa outro app por cima dos 7 minutos, só pausa a mídia dele. */
        @Volatile
        var isShowing = false
            private set
    }
}

/** Abre os 7 minutos; [returnTo] é o app bloqueado, quando vêm da tela de bloqueio (ver [fromBlockScreen]). */
fun Context.startDevotion(returnTo: String? = null) {
    startActivity(Intent(this, DevotionActivity::class.java).fromBlockScreen(returnTo))
}

internal data class PrayReading(val title: String, val verses: List<String>, val firstVerse: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevotionScreen(onClose: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AppStore.get(context) }
    var started by rememberSaveable { mutableStateOf(false) }
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    var finished by rememberSaveable { mutableStateOf(false) }
    val prayReading by produceState<PrayReading?>(null) {
        val bible = Bible.load(context)
        val list = DevotionVerses.pool(DevotionVerses.load(context, bible), store.devotionVerses.first())
        val ref = store.devotionVerseFor(Devotion.dayKey(LocalDateTime.now()), list)
            ?: FALLBACK_VERSE
        value = PrayReading(bible.title(ref), bible.verses(ref), ref.fromVerse)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopRow(
                label = "7 minutos com o Senhor",
                navIcon = Icons.Default.Close,
                navLabel = "Fechar",
                // Concluídos, o X faz o mesmo que "Fechar": volta ao app liberado.
                onNav = if (finished) onDone else onClose,
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                finished -> Finished(onDone)
                !started -> Intro { started = true }
                else -> Running(elapsed, prayReading)
            }
        }
    }

    if (started && !finished) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (elapsed < Devotion.TOTAL_SECONDS) {
                    delay(1000)
                    val before = Devotion.stepAt(elapsed)?.first
                    elapsed++
                    // Aviso sonoro curto a cada troca de etapa, sem atrasar o cronômetro.
                    if (Devotion.stepAt(elapsed)?.first != before) launch { playStepTone(context) }
                }
                store.setDevotionDone(Devotion.dayKey(LocalDateTime.now()))
                finished = true
            }
        }
    }
}

@Composable
internal fun Intro(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sete etapas", style = MaterialTheme.typography.displaySmall)
            Text(
                "Cada uma avança sozinha, com um aviso sonoro. Fora do app, o tempo pausa.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Devotion.STEPS.forEachIndexed { i, step ->
                    ListRow(step.title, divider = i < Devotion.STEPS.lastIndex) {
                        Text(formatStepTime(step.seconds), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        PrimaryButton("Começar", onClick = onStart, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
    }
}

@Composable
internal fun Running(elapsed: Int, prayReading: PrayReading?) {
    val (index, inStep) = Devotion.stepAt(elapsed) ?: (Devotion.STEPS.lastIndex to Devotion.STEPS.last().seconds)
    val step = Devotion.STEPS[index]
    ThinProgress(elapsed.toFloat() / Devotion.TOTAL_SECONDS)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Etapa ${index + 1} de ${Devotion.STEPS.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(step.title, style = MaterialTheme.typography.displayMedium)
        Text(
            formatCountdown((step.seconds - inStep) * 1000L),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(step.instruction, style = MaterialTheme.typography.bodyLarge)
        if (index == Devotion.PRAY_READ_STEP && prayReading != null) {
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                prayReading.verses.forEachIndexed { i, verse ->
                    Text(
                        "${prayReading.firstVerse + i} $verse",
                        fontFamily = Gelasio,
                        fontSize = 19.sp,
                        lineHeight = 29.sp,
                    )
                }
                Text(prayReading.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(step.references, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Devotion.STEPS.getOrNull(index + 1)?.let { next ->
            Text(
                "A seguir: ${next.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun Finished(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Feito hoje", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "O bloqueio dos 7 minutos volta amanhã às ${Devotion.DAY_STARTS_AT_HOUR}h.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Text("“Oh! Provai e vede que o Senhor é bom.”", fontFamily = Gelasio, fontSize = 22.sp, lineHeight = 32.sp)
            Text(
                "Salmos 34:8",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        PrimaryButton("Fechar", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

private val FALLBACK_VERSE = VerseRef(book = 48, chapter = 6, fromVerse = 17, toVerse = 18) // Efésios 6:17-18

/**
 * Volume do aviso na faixa de alarme: 70% do volume atual dela, mas nunca menos de 10% do máximo.
 * Devolve o nível a pôr na faixa enquanto o toque soa (null = não mexer) e o volume do toque (0–100)
 * relativo a esse nível.
 */
internal fun stepToneLevel(current: Int, max: Int): Pair<Int?, Int> {
    if (max <= 0) return null to 70
    val currentFraction = current.toFloat() / max
    val target = maxOf(0.7f * currentFraction, 0.1f)
    if (currentFraction >= target) return null to (target / currentFraction * 100).roundToInt()
    // A faixa está baixa demais para chegar a 10%: sobe só durante o toque e depois volta.
    val raised = ceil(0.1f * max).toInt().coerceAtLeast(1)
    return raised to (target / (raised.toFloat() / max) * 100).roundToInt().coerceAtMost(100)
}

/** Toca o toque de troca de etapa (res/raw/step_chime) na faixa de alarme, que soa mesmo no silencioso. */
private suspend fun playStepTone(context: Context) {
    val audio = context.getSystemService(AudioManager::class.java)
    val stream = AudioManager.STREAM_ALARM
    val current = audio.getStreamVolume(stream)
    val (raised, percent) = stepToneLevel(current, audio.getStreamMaxVolume(stream))
    if (raised != null) runCatching { audio.setStreamVolume(stream, raised, 0) }
    val player = runCatching {
        MediaPlayer.create(
            context,
            R.raw.step_chime,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            audio.generateAudioSessionId(),
        )
    }.getOrNull()
    try {
        if (player != null) {
            player.setVolume(percent / 100f, percent / 100f)
            player.start()
            delay(player.duration.toLong() + 200)
        }
    } finally {
        withContext(NonCancellable) {
            player?.release()
            if (raised != null) runCatching { audio.setStreamVolume(stream, current, 0) }
        }
    }
}
