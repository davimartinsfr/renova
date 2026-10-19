package dev.renova.app

import dev.renova.app.data.Bible
import dev.renova.app.domain.BiblePosition
import dev.renova.app.domain.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadingTest {
    private val bible by lazy { File("src/main/assets/bible/acf.json").inputStream().use { Bible.parse(it) } }

    @Test fun minimumTime() {
        assertEquals(30, Reading.minSeconds(listOf("curto demais")))
        assertEquals(300, Reading.minSeconds(List(100) { "dez palavras aqui para testar o cálculo de tempo mínimo" }))
    }

    @Test fun bibleAssetIsComplete() {
        assertEquals(66, bible.books.size)
        assertEquals(1189, bible.chaptersPerBook.sum())
        assertEquals(Bible.BOOK_NAMES, bible.books.map { it.name })
        assertEquals("No princípio criou Deus o céu e a terra.", bible.verses(0, 0)[0])
        assertEquals("Gênesis 1", bible.title(0, 0))
    }

    @Test fun advancesThroughWholeBibleAndWraps() {
        var pos = BiblePosition()
        assertEquals(BiblePosition(0, 1), Reading.next(pos, bible.chaptersPerBook))
        repeat(50) { pos = Reading.next(pos, bible.chaptersPerBook) }
        assertEquals(BiblePosition(1, 0), pos) // Gênesis tem 50 capítulos → Êxodo 1
        pos = BiblePosition()
        repeat(1189) { pos = Reading.next(pos, bible.chaptersPerBook) }
        assertEquals(BiblePosition(0, 0, rounds = 1), pos)
    }
}

class ReadingGoalTest {
    @Test fun goalNeverBelowOneNorAboveMax() {
        assertEquals(1, Reading.clampGoal(0))
        assertEquals(1, Reading.clampGoal(-5))
        assertEquals(Reading.MAX_CHAPTERS_PER_UNLOCK, Reading.clampGoal(99))
        assertEquals(4, Reading.clampGoal(4))
    }

    @Test fun loweringGoalOnlyWhileUnlocked() {
        assertTrue(Reading.canSetGoal(3, 5, unlocked = false))
        assertTrue(Reading.canSetGoal(3, 3, unlocked = false))
        assertFalse(Reading.canSetGoal(3, 1, unlocked = false))
        assertTrue(Reading.canSetGoal(3, 1, unlocked = true))
    }

    @Test fun chapterLabel() {
        assertEquals("1 capítulo", Reading.chapters(1))
        assertEquals("3 capítulos", Reading.chapters(3))
    }
}
