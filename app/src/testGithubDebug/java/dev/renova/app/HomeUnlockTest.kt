package dev.renova.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.data.AppStore
import dev.renova.app.domain.Rule
import dev.renova.app.domain.TargetType
import dev.renova.app.domain.UnlockOption
import dev.renova.app.ui.AppTheme
import dev.renova.app.ui.HomeScreen
import dev.renova.app.ui.HomeTab
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A leitura pela tela inicial libera uma regra escolhida; as outras seguem bloqueadas. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-notnight")
class HomeUnlockTest {
    @get:JUnitRule val compose = createComposeRule()

    private val store get() = AppStore.get(ApplicationProvider.getApplicationContext())

    @Before fun rules() {
        runBlocking {
            store.clearUnlocks()
            store.saveRule(Rule("a", TargetType.APP, "com.reddit.frontpage", "Reddit", dailyLimitMin = 0))
            store.saveRule(Rule("b", TargetType.APP, "com.netflix.mediaclient", "Netflix", dailyLimitMin = 0))
        }
    }

    private fun exists(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    @Test fun readingAsksWhichRuleFirst() {
        compose.setContent { AppTheme { HomeScreen(HomeTab.RULES, {}, {}, {}, {}) } }
        compose.waitUntil(5_000) { exists("Netflix") }
        compose.onNodeWithText("Ler para liberar", substring = true).performClick()
        compose.waitUntil(5_000) { exists("Liberar qual?") }
        compose.onAllNodes(hasText("Netflix"))[1].performClick()
        compose.waitUntil(5_000) { exists("Quanto tempo?") }
    }

    @Test fun onlyTheUnlockedRuleShowsFree() {
        runBlocking { store.grantUnlock(UnlockOption.CHAPTER, listOf("a")) }
        compose.setContent { AppTheme { HomeScreen(HomeTab.RULES, {}, {}, {}, {}) } }
        compose.waitUntil(5_000) { exists("Reddit livre") }
        compose.waitUntil(5_000) { exists("Livre · ", substring = true) }
        compose.onNodeWithText("Bloqueado").assertExists()
    }
}
