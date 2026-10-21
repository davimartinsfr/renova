package dev.renova.app

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.renova.app.ui.ReadingActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Ler o capítulo de 15 minutos leva à tela final, sem abrir o capítulo seguinte. Sem regras no intent, a leitura só libera a edição. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReadingFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ReadingActivity>()

    @Test fun oneChapterShowsFinished() {
        compose.waitUntil(20_000) { compose.onAllNodesWithTextExists("Concluir") }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(32) // Gênesis 1: título + 31 versículos + espaço
        compose.mainClock.advanceTimeBy(400_000)
        compose.onNodeWithText("Concluir").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTextExists("Pode alterar", substring = true) }
        compose.onNodeWithText("Capítulo 2 de 1").assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextExists(text: String, substring: Boolean = false) =
        onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
}
