package dev.renova.app

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.data.OnboardingPrefs
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Tutorial aberto pelas Configurações: o Voltar no 1º passo volta para elas, sem fechar o app. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-notnight")
class TutorialBackTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun backOnFirstStepReturnsToSettings() {
        OnboardingPrefs.setDone(ApplicationProvider.getApplicationContext<Context>())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithContentDescription("Configurações").performClick()
            compose.onNodeWithText("Ver o tutorial").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("Configurações")).fetchSemanticsNodes().isEmpty() }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("Ver o tutorial").assertExists()
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }
}
