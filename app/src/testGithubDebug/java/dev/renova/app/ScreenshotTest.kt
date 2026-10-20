package dev.renova.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.renova.app.data.AppStore
import dev.renova.app.data.Bible
import dev.renova.app.domain.BiblePosition
import dev.renova.app.domain.BlockReason
import dev.renova.app.domain.Rule
import dev.renova.app.domain.Schedule
import dev.renova.app.domain.TargetType
import dev.renova.app.domain.VerseRef
import dev.renova.app.ui.AddVerseDialog
import dev.renova.app.ui.PrayReadSection
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import dev.renova.app.ui.AppTheme
import dev.renova.app.ui.BlockedScreen
import dev.renova.app.ui.ChapterReader
import dev.renova.app.ui.HomeScreen
import dev.renova.app.ui.HomeTab
import dev.renova.app.ui.OnboardingScreen
import dev.renova.app.ui.onboardingSteps
import dev.renova.app.ui.PrayReading
import dev.renova.app.ui.RuleEditorScreen
import dev.renova.app.ui.Running
import dev.renova.app.ui.SettingsScreen
import kotlinx.coroutines.runBlocking
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Não verifica nada: só grava PNGs das telas em app/build/screenshots para conferir o visual. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class ScreenshotTest {
    @get:JUnitRule val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            AppTheme(dark = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name-${if (dark) "escuro" else "claro"}.png")
    }

    private fun sampleRules() = runBlocking {
        val store = AppStore.get(context)
        store.saveRule(Rule("1", TargetType.YT_SHORTS, label = "YouTube Shorts", dailyLimitMin = 0))
        store.saveRule(Rule("2", TargetType.IG_REELS, label = "Instagram Reels", dailyLimitMin = 15))
        store.saveRule(Rule("3", TargetType.APP, "com.reddit.frontpage", "Reddit", schedules = listOf(Schedule(startMin = 22 * 60, endMin = 7 * 60))))
    }

    @Test fun regrasEscuro() { sampleRules(); shot("regras", true) { HomeScreen(HomeTab.RULES, {}, {}, {}, {}) } }
    @Test fun regrasClaro() { sampleRules(); shot("regras", false) { HomeScreen(HomeTab.RULES, {}, {}, {}, {}) } }
    @Test fun seteMinutosEscuro() { sampleRules(); sampleVerses(); shot("sete-minutos", true) { HomeScreen(HomeTab.DEVOTION, {}, {}, {}, {}) } }
    @Test fun seteMinutosClaro() { sampleRules(); sampleVerses(); shot("sete-minutos", false) { HomeScreen(HomeTab.DEVOTION, {}, {}, {}, {}) } }

    private fun sampleVerses() = runBlocking {
        val store = AppStore.get(context)
        store.addDevotionVerse(VerseRef(18, 23, 1, 2))
        store.addDevotionVerse(VerseRef(42, 3, 16, 16))
    }

    @Test fun tutorialClaro() = tutorial(dark = false)
    @Test fun tutorialEscuro() = tutorial(dark = true)

    /** Um setContent só por teste: troca de passo por estado e grava um PNG de cada. */
    private fun tutorial(dark: Boolean) {
        var step by mutableIntStateOf(0)
        compose.setContent { AppTheme(dark = dark) { key(step) { OnboardingScreen(initialStep = step) {} } } }
        onboardingSteps().forEachIndexed { i, s ->
            step = i
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/screenshots/tutorial-${i + 1}-${s.name.lowercase()}-${if (dark) "escuro" else "claro"}.png")
        }
    }
    @Test fun configuracoesEscuro() = shot("configuracoes", true) { SettingsScreen {} }
    @Test fun configuracoesClaro() = shot("configuracoes", false) { SettingsScreen {} }
    @Test fun editorEscuro() = shot("editor", true) { RuleEditorScreen(null) {} }
    @Test fun editorClaro() = shot("editor", false) { RuleEditorScreen(null) {} }

    @Test fun bloqueadoEscuro() = shot("bloqueado", true) {
        BlockedScreen("Instagram", BlockReason.LIMIT.name, "Próximo: João 3", {}, {}, {})
    }

    @Test fun bloqueadoClaro() = shot("bloqueado", false) {
        BlockedScreen("Instagram", BlockReason.LIMIT.name, "Próximo: João 3", {}, {}, {})
    }

    @Test fun leituraEscuro() {
        val bible = runBlocking { Bible.load(context) }
        shot("leitura", true) { ChapterReader(bible, BiblePosition(book = 42, chapter = 2)) {} }
    }

    @Test fun leituraClaro() {
        val bible = runBlocking { Bible.load(context) }
        shot("leitura", false) { ChapterReader(bible, BiblePosition(book = 42, chapter = 2)) {} }
    }

    @Test fun oracaoEscuro() {
        val bible = runBlocking { Bible.load(context) }
        val pray = PrayReading("Efésios 6:17-18", bible.verses(48, 5).subList(16, 18), 17)
        shot("orar-ler", true) { androidx.compose.foundation.layout.Column { Running(200, pray) } }
    }
}

/**
 * A caixa de adicionar trecho fica numa classe à parte: com os qualificadores de tela da [ScreenshotTest]
 * o campo de texto dentro da caixa nunca deixa o Compose ocioso no Robolectric.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AddVerseScreenshotTest {
    @get:JUnitRule val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun adicionarTrechoClaro() {
        val bible = runBlocking { Bible.load(context) }
        compose.setContent { AppTheme(dark = false) { AddVerseDialog(bible, emptyList(), {}, {}) } }
        captureScreenRoboImage("build/screenshots/adicionar-trecho-claro.png")
        // Com o campo focado, o cursor piscando nunca deixa o Compose ocioso: o relógio só anda quando o teste manda.
        compose.mainClock.autoAdvance = false
        compose.onNode(hasSetTextAction()).performTextInput("Sl 23:1-3")
        compose.mainClock.advanceTimeBy(500)
        captureScreenRoboImage("build/screenshots/adicionar-trecho-preenchido-claro.png")
    }

    @Test fun orarLerListaClaro() {
        runBlocking {
            val store = AppStore.get(context)
            store.addDevotionVerse(VerseRef(18, 23, 1, 2))
            store.addDevotionVerse(VerseRef(42, 3, 16, 16))
        }
        compose.setContent {
            AppTheme(dark = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(20.dp)) { PrayReadSection() }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/orar-ler-lista-claro.png")
    }
}
