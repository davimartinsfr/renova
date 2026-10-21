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
import dev.renova.app.ui.AppTheme
import dev.renova.app.ui.RuleEditorScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        compose.waitForIdle()
        assertEquals(before + 1, runBlocking { store.rules.first().size })
    }

    @Test fun ruleWithNewIdReplacesTheOldOne() = runBlocking {
        store.saveRule(Rule("old", TargetType.YT_SHORTS, label = "YouTube Shorts", dailyLimitMin = 10))
        store.saveRule(Rule("new", TargetType.IG_REELS, label = "Instagram Reels", dailyLimitMin = 10), replacing = "old")
        val ids = store.rules.first().map { it.id }
        assertEquals(listOf(false, true), listOf("old" in ids, "new" in ids))
    }
}
