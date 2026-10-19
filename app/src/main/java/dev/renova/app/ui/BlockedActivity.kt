package dev.renova.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.renova.app.data.AppStore
import dev.renova.app.data.Bible
import dev.renova.app.domain.BlockReason
import dev.renova.app.domain.UnlockOption
import dev.renova.app.domain.TargetType
import kotlinx.coroutines.launch

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
                val nextReading = position?.let { "Próximo capítulo: ${Bible.title(it)}" }
                BlockedScreen(
                    label = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
                    reason = intent.getStringExtra(EXTRA_REASON),
                    nextReading = nextReading,
                    onLeave = ::leave,
                    onRead = ::startReading,
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
        // Voltando da leitura com o desbloqueio concedido: some da frente.
        lifecycleScope.launch { if (AppStore.get(this@BlockedActivity).isUnlocked()) finish() }
    }

    private fun leave() {
        if (intent.getStringExtra(EXTRA_TYPE) != TargetType.YT_SHORTS.name) {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    companion object {
        const val EXTRA_LABEL = "label"
        const val EXTRA_REASON = "reason"
        const val EXTRA_TYPE = "type"
    }
}

@androidx.compose.runtime.Composable
private fun BlockedScreen(
    label: String,
    reason: String?,
    nextReading: String?,
    onLeave: () -> Unit,
    onRead: (UnlockOption) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    if (choosing) {
        UnlockOptionsDialog(onDismiss = { choosing = false }) { option ->
            choosing = false
            onRead(option)
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("$label bloqueado", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                when (reason) {
                    BlockReason.LIMIT.name -> "Você atingiu o limite de hoje."
                    else -> "Este é um horário de bloqueio."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Button(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) { Text(UNLOCK_BUTTON_LABEL) }
            if (nextReading != null) {
                Text(
                    nextReading,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
        }
    }
}
