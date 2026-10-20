package dev.renova.app

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.data.AppStore
import dev.renova.app.data.OnboardingPrefs
import dev.renova.app.ui.AppTheme
import dev.renova.app.ui.OnboardingScreen
import dev.renova.app.ui.OnboardingStep
import dev.renova.app.ui.onboardingSteps
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp")
class OnboardingTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val store get() = AppStore.get(context)

    @Before fun reset() {
        context.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking { store.clearDevotion() }
    }

    @Test fun showsOnlyOnFreshInstall() {
        assertTrue(OnboardingPrefs.shouldShow(context))
        OnboardingPrefs.setDone(context)
        assertFalse(OnboardingPrefs.shouldShow(context))
    }

    @Test fun updatedInstallSkipsTutorial() {
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).lastUpdateTime = 1_000
        assertFalse(OnboardingPrefs.shouldShow(context))
        // Fica marcado: não volta a aparecer.
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).lastUpdateTime = 0
        assertFalse(OnboardingPrefs.shouldShow(context))
    }

    @Test fun notificationsStepOnlyOnAndroid13() {
        assertFalse(OnboardingStep.NOTIFICATIONS in onboardingSteps(32))
        assertTrue(OnboardingStep.NOTIFICATIONS in onboardingSteps(33))
    }

    @Test fun skipFinishesWithoutRule() {
        var result: Boolean? = null
        compose.setContent { AppTheme { OnboardingScreen { result = it } } }
        compose.onNodeWithText("Pular").performClick()
        assertEquals(false, result)
    }

    @Test fun walkThroughTurnsDevotionOnAndCreatesRule() {
        var result: Boolean? = null
        compose.setContent { AppTheme { OnboardingScreen { result = it } } }
        compose.onNodeWithText("Começar").performClick()
        compose.onNodeWithText("Continuar").performClick()
        repeat(3) { compose.onNodeWithText("Depois").performClick() } // acessibilidade, segundo plano e notificações
        compose.onNodeWithText("Ativar o bloqueio").performClick()
        compose.waitUntil(5_000) { runBlocking { store.devotionEnabled.first() } }
        compose.onNodeWithText("Criar minha primeira regra").performClick()
        assertEquals(true, result)
    }
}
