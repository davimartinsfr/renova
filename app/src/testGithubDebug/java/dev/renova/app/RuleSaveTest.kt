package dev.renova.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.data.AppStore
import dev.renova.app.domain.Rule
import dev.renova.app.domain.TargetType
import dev.renova.app.domain.UnlockOption
import dev.renova.app.ui.AppTheme
import dev.renova.app.ui.RuleEditorScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-notnight")
class RuleSaveTest {
    @get:org.junit.Rule val compose = createComposeRule()

    private val store get() = AppStore.get(ApplicationProvider.getApplicationContext())

    @Test fun doubleTapOnSaveCreatesOneRule() {
        val before = runBlocking { store.rules.first().size }
        compose.setContent { AppTheme { RuleEditorScreen(null) {} } }
        val save = compose.onNodeWithText("Salvar").performScrollTo()
        save.performClick()
        save.performClick()
        // A gravação do DataStore roda em IO: waitForIdle sozinho não espera por ela.
        compose.waitUntil(5_000) { runBlocking { store.rules.first().size } == before + 1 }
        // Folga para um segundo toque, se gravasse, aparecer na lista.
        Thread.sleep(300)
        compose.waitForIdle()
        assertEquals(before + 1, runBlocking { store.rules.first().size })
    }

    @Test fun ruleWithNewIdReplacesTheOldOne() = runBlocking {
        store.saveRule(Rule("old", TargetType.YT_SHORTS, label = "YouTube Shorts", dailyLimitMin = 10))
        store.saveRule(Rule("new", TargetType.IG_REELS, label = "Instagram Reels", dailyLimitMin = 10), replacing = "old")
        val ids = store.rules.first().map { it.id }
        assertEquals(listOf(false, true), listOf("old" in ids, "new" in ids))
    }

    @Test fun swappingASectionForTheWholeAppDropsItsUnlock() = runBlocking {
        store.saveRule(Rule("reels", TargetType.IG_REELS, label = "Instagram Reels", dailyLimitMin = 10))
        store.saveRule(Rule("other", TargetType.YT_SHORTS, label = "YouTube Shorts", dailyLimitMin = 10))
        store.grantUnlock(UnlockOption.VERSE, listOf("reels", "other"))
        store.saveRule(
            Rule("reels", TargetType.APP, "com.instagram.android", label = "Instagram", dailyLimitMin = 10),
            replacing = "reels",
        )
        val unlocks = store.unlocks.first()
        assertEquals(listOf(false, true), listOf("reels" in unlocks, "other" in unlocks))
        // Salvar sem trocar o alvo não mexe na liberação.
        store.grantUnlock(UnlockOption.VERSE, listOf("reels"))
        store.saveRule(Rule("reels", TargetType.APP, "com.instagram.android", label = "Instagram", dailyLimitMin = 5))
        assertTrue("reels" in store.unlocks.first())
        store.clearUnlocks()
        Unit
    }
}
