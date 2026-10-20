package dev.renova.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.data.AppStore
import dev.renova.app.data.ThemeMode
import dev.renova.app.data.ThemePrefs
import dev.renova.app.domain.BiblePosition
import dev.renova.app.ui.AppTheme
import dev.renova.app.ui.SettingsScreen
import dev.renova.app.ui.chosenDarkTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-notnight")
class SettingsTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store get() = AppStore.get(context)

    @Test fun jumpToChapter() {
        compose.setContent { AppTheme { SettingsScreen {} } }
        compose.onNodeWithText("Ir para um capítulo").performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Êxodo")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Êxodo").performClick()
        compose.onNodeWithText("3").performClick()
        compose.waitUntil(5_000) { runBlocking { store.biblePosition.first() } == BiblePosition(1, 2, 0) }
        compose.onNodeWithText("Próximo: Êxodo 3").assertExists()
    }

    @Test fun restartGoesToGenesis() {
        runBlocking { store.setPosition(42, 5) }
        compose.setContent { AppTheme { SettingsScreen {} } }
        compose.onNodeWithText("Recomeçar de Gênesis 1").performScrollTo().performClick()
        compose.onNodeWithText("Recomeçar").performClick()
        compose.waitUntil(5_000) { runBlocking { store.biblePosition.first() } == BiblePosition(0, 0, 0) }
    }

    @Test fun themeOverridesSystem() {
        var dark: Boolean? = null
        var system: Boolean? = null
        compose.setContent { system = isSystemInDarkTheme(); dark = chosenDarkTheme(); Text("x") }
        compose.waitForIdle()
        assertEquals(system, dark)
        compose.runOnUiThread { ThemePrefs.set(context, ThemeMode.DARK) }
        compose.waitForIdle()
        assertEquals(true, dark)
        compose.runOnUiThread { ThemePrefs.set(context, ThemeMode.LIGHT) }
        compose.waitForIdle()
        assertEquals(false, dark)
        compose.runOnUiThread { ThemePrefs.set(context, ThemeMode.SYSTEM) }
    }
}
