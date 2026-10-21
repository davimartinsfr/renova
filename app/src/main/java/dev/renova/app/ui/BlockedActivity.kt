package dev.renova.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import dev.renova.app.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.renova.app.data.AppStore
import dev.renova.app.data.Bible
import dev.renova.app.domain.BlockReason
import dev.renova.app.domain.Devotion
import dev.renova.app.domain.TargetType
import dev.renova.app.domain.UnlockOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class BlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        val store = AppStore.get(this)
        setContent {
            AppTheme {
                val position by store.biblePosition.collectAsState(initial = null)
                val nextReading = position?.let { "Próximo: ${Bible.title(it)}" }
                BlockedScreen(
                    label = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
                    reason = intent.getStringExtra(EXTRA_REASON),
                    nextReading = nextReading,
                    onLeave = ::leave,
                    onRead = { option -> startReading(option, ruleIds(), intent.getStringExtra(EXTRA_LABEL), returnTo = blockedPackage()) },
                    onDevotion = { startDevotion(returnTo = blockedPackage()) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
        lastResumedAt = SystemClock.elapsedRealtime()
        // Voltando da leitura (ou dos 7 minutos) com o bloqueio resolvido: some da frente.
        lifecycleScope.launch {
            val store = AppStore.get(this@BlockedActivity)
            val resolved = if (intent.getStringExtra(EXTRA_REASON) == BlockReason.DEVOTION.name) {
                !store.devotionEnabled.first() || Devotion.isDone(store.devotionDoneDay.first(), LocalDateTime.now())
            } else {
                ruleIds().let { it.isNotEmpty() && store.isUnlocked(it) }
            }
            if (resolved) finish()
        }
    }

    override fun onPause() {
        isShowing = false
        super.onPause()
    }

    private fun ruleIds(): List<String> = intent.getStringArrayExtra(EXTRA_RULE_IDS)?.toList().orEmpty()

    private fun blockedPackage(): String? = intent.getStringExtra(EXTRA_PACKAGE)

    private fun leave() {
        val type = TargetType.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TYPE) }
        if (type?.isSection != true) {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    companion object {
        const val EXTRA_LABEL = "label"
        const val EXTRA_REASON = "reason"
        const val EXTRA_TYPE = "type"
        /** App bloqueado, para voltar a ele depois da leitura. */
        const val EXTRA_PACKAGE = "package"
        /** Regras que bloqueiam o app: ler libera todas elas. */
        const val EXTRA_RULE_IDS = "rule_ids"

        /** Na frente agora: o serviço confere isto para saber se o sistema deixou a tela abrir. */
        @Volatile
        var isShowing = false
            private set

        /**
         * Quando a tela esteve na frente pela última vez ([SystemClock.elapsedRealtime]). A pessoa pode sair
         * dela antes de o serviço conferir [isShowing]: isto diz que ela abriu, mesmo que já tenha fechado.
         */
        @Volatile
        var lastResumedAt = 0L
            private set
    }
}

@androidx.compose.runtime.Composable
internal fun BlockedScreen(
    label: String,
    reason: String?,
    nextReading: String?,
    onLeave: () -> Unit,
    onRead: (UnlockOption) -> Unit,
    onDevotion: () -> Unit,
    /** Versão desenhada pelo serviço quando o sistema não deixou a tela abrir: só dá para ir ao Renova. */
    onOpenApp: (() -> Unit)? = null,
) {
    val devotion = reason == BlockReason.DEVOTION.name
    var choosing by remember { mutableStateOf(false) }
    if (choosing) {
        UnlockOptionsDialog(onDismiss = { choosing = false }) { option ->
            choosing = false
            onRead(option)
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Hourglass(Modifier.height(52.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(label, style = MaterialTheme.typography.displayMedium)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when (reason) {
                        BlockReason.LIMIT.name -> "Limite de hoje atingido."
                        BlockReason.DEVOTION.name -> "Faça antes os 7 minutos de hoje."
                        else -> "Horário de bloqueio."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onOpenApp != null) {
                Text(
                    BLOCKED_FALLBACK_HINT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                PrimaryButton("Abrir o Renova", onClick = onOpenApp, modifier = Modifier.fillMaxWidth())
            } else if (devotion) {
                // As liberações por leitura não valem aqui: só os 7 minutos soltam estes alvos.
                PrimaryButton("Começar os 7 minutos", onClick = onDevotion, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton(UNLOCK_BUTTON_LABEL, onClick = { choosing = true }, modifier = Modifier.fillMaxWidth())
                if (nextReading != null) {
                    Text(
                        nextReading,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            QuietButton("Voltar", onClick = onLeave, modifier = Modifier.fillMaxWidth())
        }
    }
}

internal const val BLOCKED_FALLBACK_HINT =
    "O celular não deixou o Renova abrir a tela de leitura. Se o botão abaixo não funcionar, abra o Renova pela " +
        "tela inicial. Para isso não acontecer de novo, veja os avisos na tela inicial do Renova."

/** Ampulheta em duas cores: vidro na cor do texto, areia no ocre. */
@Composable
private fun Hourglass(modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(60f / 82f)) {
        Icon(painterResource(R.drawable.ic_hourglass_frame), null, Modifier.matchParentSize(), tint = MaterialTheme.colorScheme.onBackground)
        Icon(painterResource(R.drawable.ic_hourglass_sand), null, Modifier.matchParentSize(), tint = MaterialTheme.colorScheme.primary)
    }
}
