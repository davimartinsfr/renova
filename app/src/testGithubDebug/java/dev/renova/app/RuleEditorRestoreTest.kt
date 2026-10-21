package dev.renova.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.data.AppStore
import dev.renova.app.domain.Rule
import dev.renova.app.domain.TargetType
import dev.renova.app.ui.AppTheme
import dev.renova.app.ui.RuleEditorScreen
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** O editor guarda o que já foi mexido quando a tela é recriada (girar, trocar o tema, processo morto). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-notnight")
class RuleEditorRestoreTest {
    @get:org.junit.Rule val compose = createComposeRule()

    private val store get() = AppStore.get(ApplicationProvider.getApplicationContext())

    @Test fun editsSurviveRecreation() {
        runBlocking { store.saveRule(Rule("ed", TargetType.YT_SHORTS, label = "YouTube Shorts", dailyLimitMin = 15)) }
        val restore = StateRestorationTester(compose)
        restore.setContent { AppTheme { RuleEditorScreen("ed") {} } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Editar regra")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reels").performClick()
        compose.onNodeWithText("Adicionar horário").performScrollTo().performClick()

        restore.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Só o Reels", substring = true).assertExists()
        compose.onNodeWithText("até").assertExists()
    }

    @Test fun offStepLimitOpensExact() {
        runBlocking { store.saveRule(Rule("tres", TargetType.YT_SHORTS, label = "YouTube Shorts", dailyLimitMin = 3)) }
        compose.setContent { AppTheme { RuleEditorScreen("tres") {} } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Editar regra")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("03").assertExists()
        compose.onNodeWithText("Sempre bloqueado.").assertDoesNotExist()
    }
}
