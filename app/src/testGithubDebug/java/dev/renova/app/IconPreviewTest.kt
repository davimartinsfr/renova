package dev.renova.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Grava o ícone adaptativo como o Android mostra: camada inteira, máscaras e tamanhos pequenos. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h560dp-xhdpi")
class IconPreviewTest {
    @get:Rule val compose = createComposeRule()

    /** O launcher mostra só os 72dp centrais dos 108dp da camada. */
    @Composable
    private fun Masked(size: Dp, shape: Shape, bg: Color = Color(0xFF141416)) {
        Box(Modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(size * 108 / 72))
        }
    }

    @Test fun icone() {
        compose.setContent {
            Column(Modifier.background(Color(0xFF2A2A2E)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
                    // Camada inteira, com o círculo da área segura (66dp) em volta.
                    Box(Modifier.size(324.dp).background(Color(0xFF141416)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(198.dp).clip(CircleShape).background(Color(0x22FFFFFF)))
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(324.dp))
                    }
                    Masked(216.dp, CircleShape)
                    Masked(216.dp, RoundedCornerShape(48.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Masked(72.dp, CircleShape)
                    Masked(48.dp, CircleShape)
                    Masked(48.dp, RoundedCornerShape(12.dp))
                    Masked(32.dp, CircleShape)
                    Masked(72.dp, CircleShape, Color(0xFFF7F3EC))
                    Masked(48.dp, CircleShape, Color(0xFF3B5BA8))
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/icone.png")
    }

    /** Ícone de 512×512 da Play Store: quadrado cheio, a loja aplica a máscara. */
    @Test @Config(qualifiers = "w512dp-h512dp-mdpi")
    fun iconePlay() {
        compose.setContent { Masked(512.dp, androidx.compose.ui.graphics.RectangleShape) }
        compose.onRoot().captureRoboImage("build/screenshots/play-icon-512.png")
    }

    @Test fun pequenos() {
        compose.setContent {
            Row(Modifier.background(Color(0xFF141416)).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                androidx.compose.material3.Icon(painterResource(R.drawable.ic_notification), null, Modifier.size(24.dp), tint = Color.White)
                androidx.compose.material3.Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(108.dp), tint = Color.White)
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/icones-pequenos.png")
    }
}
