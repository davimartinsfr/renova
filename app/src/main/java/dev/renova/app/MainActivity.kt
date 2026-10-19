package dev.renova.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.renova.app.ui.AppTheme
import dev.renova.app.ui.DevotionTargetsScreen
import dev.renova.app.ui.HomeScreen
import dev.renova.app.ui.RuleEditorScreen

private const val DEVOTION_TARGETS = "#devotion"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                // null = início; "" = nova regra; DEVOTION_TARGETS = lista dos 7 minutos; caso contrário, id da regra.
                var editing by rememberSaveable { mutableStateOf<String?>(null) }
                val current = editing
                if (current == null) {
                    HomeScreen(
                        onEditRule = { editing = it },
                        onNewRule = { editing = "" },
                        onDevotionTargets = { editing = DEVOTION_TARGETS },
                    )
                } else {
                    BackHandler { editing = null }
                    if (current == DEVOTION_TARGETS) {
                        DevotionTargetsScreen(onDone = { editing = null })
                    } else {
                        RuleEditorScreen(ruleId = current.ifEmpty { null }, onDone = { editing = null })
                    }
                }
            }
        }
    }
}
