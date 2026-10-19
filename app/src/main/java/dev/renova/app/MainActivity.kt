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
import dev.renova.app.ui.HomeScreen
import dev.renova.app.ui.RuleEditorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                // null = início; "" = nova regra; caso contrário, id da regra em edição.
                var editing by rememberSaveable { mutableStateOf<String?>(null) }
                val current = editing
                if (current == null) {
                    HomeScreen(onEditRule = { editing = it }, onNewRule = { editing = "" })
                } else {
                    BackHandler { editing = null }
                    RuleEditorScreen(ruleId = current.ifEmpty { null }, onDone = { editing = null })
                }
            }
        }
    }
}
