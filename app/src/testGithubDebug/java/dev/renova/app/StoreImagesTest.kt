package dev.renova.app

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.renova.app.data.AppStore
import dev.renova.app.data.Bible
import dev.renova.app.data.DailyUsage
import dev.renova.app.domain.BiblePosition
import dev.renova.app.domain.BlockReason
import dev.renova.app.domain.Rule
import dev.renova.app.domain.Schedule
import dev.renova.app.domain.TargetType
import dev.renova.app.service.BlockerAccessibilityService
import dev.renova.app.ui.AppTheme
import dev.renova.app.ui.BlockedScreen
import dev.renova.app.ui.ChapterReader
import dev.renova.app.ui.Fraunces
import dev.renova.app.ui.HomeScreen
import dev.renova.app.ui.HomeTab
import dev.renova.app.ui.Inter
import dev.renova.app.ui.RenovaColors
import dev.renova.app.ui.RuleEditorScreen
import dev.renova.app.ui.SettingsScreen
import kotlinx.coroutines.runBlocking
import org.junit.Rule as JUnitRule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Imagens da Play Store em app/build/screenshots/loja: capturas 1080×1920 com uma frase em cima
 * e a imagem de destaque 1024×500. Copiadas à mão para docs/play.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class StoreImagesTest {
    @get:JUnitRule val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    /** Tudo configurado: sem os avisos de permissão no topo da tela inicial. */
    private fun allSet() {
        val me = ComponentName(context, BlockerAccessibilityService::class.java).flattenToString()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, me)
        shadowOf(context.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(context.packageName, true)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun sampleData() = runBlocking {
        val store = AppStore.get(context)
        store.saveRule(Rule("1", TargetType.YT_SHORTS, label = "YouTube Shorts", dailyLimitMin = 0))
        store.saveRule(Rule("2", TargetType.IG_REELS, label = "Instagram Reels", dailyLimitMin = 15))
        store.saveRule(Rule("3", TargetType.APP, "com.zhiliaoapp.musically", "TikTok", schedules = listOf(Schedule(startMin = 22 * 60, endMin = 7 * 60))))
        store.saveUsage(DailyUsage(LocalDate.now().toString(), mapOf("2" to 9 * 60L)))
        // Doze capítulos lidos, de Lucas 15 até João 3.
        val bible = Bible.load(context)
        store.setPosition(41, 14)
        repeat(12) { store.completeChapter(bible.chaptersPerBook) }
    }

    /** Frase em cima e a tela do app num "celular" que sai pela borda de baixo. */
    private fun shot(name: String, headline: String, color: Color, dark: Boolean = true, screen: @Composable () -> Unit) {
        compose.setContent {
            AppTheme(dark = true) {
                Box(Modifier.fillMaxSize().background(color)) {
                    Text(
                        headline,
                        fontFamily = Fraunces,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 52.dp, start = 28.dp, end = 28.dp),
                    )
                    val shape = RoundedCornerShape(44.dp)
                    // Tela de 390dp (um celular comum) reduzida a 80%, presa ao topo e cortada embaixo.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.TopCenter, unbounded = true)
                            .offset(y = 168.dp)
                            .requiredSize(390.dp, 700.dp)
                            .graphicsLayer {
                                scaleX = 0.8f
                                scaleY = 0.8f
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            }
                            .clip(shape)
                            .border(10.dp, Color(0xFF0B0B0C), shape),
                    ) {
                        AppTheme(dark = dark) {
                            Surface(color = MaterialTheme.colorScheme.background) {
                                Box(Modifier.padding(top = 10.dp)) { screen() }
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/loja/$name.png")
    }

    @Test fun regras() {
        allSet(); sampleData()
        shot("1-regras", "Limite Shorts, Reels e apps", RenovaColors.Teal) {
            HomeScreen(HomeTab.RULES, {}, {}, {}, {})
        }
    }

    @Test fun bloqueado() = shot("2-bloqueado", "Acabou o tempo? O app para.", RenovaColors.Plum) {
        BlockedScreen("TikTok", BlockReason.LIMIT.name, "Próximo: João 3", {}, {}, {})
    }

    @Test fun leitura() {
        val bible = runBlocking { Bible.load(context) }
        shot("3-leitura", "Libere tempo lendo a Bíblia", RenovaColors.Olive) {
            ChapterReader(bible, BiblePosition(book = 42, chapter = 2)) {}
        }
    }

    @Test fun seteMinutos() {
        allSet()
        shot("4-sete-minutos", "Comece o dia com o Senhor", RenovaColors.Mustard) {
            HomeScreen(HomeTab.DEVOTION, {}, {}, {}, {})
        }
    }

    @Test fun editor() = shot("5-editor", "Limite e horários do seu jeito", RenovaColors.Teal) {
        RuleEditorScreen(null) {}
    }

    @Test fun configuracoes() {
        allSet()
        shot("6-configuracoes", "Claro ou escuro. Sem anúncios.", Color(0xFF1B1B1E), dark = false) { SettingsScreen {} }
    }

    /** Imagem de destaque: o broto, o nome e a frase. As cores do broto vêm do tema do qualificador. */
    private fun feature(name: String, background: Color, title: Color, subtitle: Color) {
        compose.setContent {
            AppTheme(dark = true) {
                Row(
                    Modifier.fillMaxSize().background(background).padding(horizontal = 72.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    // A camada do ícone tem 108 de lado e o desenho ocupa os 72 do meio.
                    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(260.dp * 108 / 72))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Renova", fontFamily = Fraunces, fontSize = 112.sp, lineHeight = 112.sp, color = title)
                        Text("Troque o tempo de tela pela Palavra", fontFamily = Inter, fontSize = 30.sp, lineHeight = 38.sp, color = subtitle)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/loja/$name.png")
    }

    @Test @Config(qualifiers = "w1024dp-h500dp-night-mdpi")
    fun destaque() = feature("destaque", Color(0xFF141416), Color(0xFFF2EFE9), Color(0xFFA09A90))

    @Test @Config(qualifiers = "w1024dp-h500dp-notnight-mdpi")
    fun destaqueClaro() = feature("destaque-claro", Color(0xFFF7F3EC), Color(0xFF1C1B19), Color(0xFF6B665D))
}
