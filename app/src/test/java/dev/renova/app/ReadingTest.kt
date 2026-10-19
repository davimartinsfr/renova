package dev.renova.app

import dev.renova.app.data.Bible
import dev.renova.app.domain.BiblePosition
import dev.renova.app.domain.Reading
import dev.renova.app.domain.UnlockOption
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

class UnlockOptionTest {
    @Test fun optionsMatchSpec() {
        assertEquals(1L to 0, UnlockOption.VERSE.minutes to UnlockOption.VERSE.chapters)
        assertEquals(15L to 1, UnlockOption.CHAPTER.minutes to UnlockOption.CHAPTER.chapters)
        assertEquals(60L to 3, UnlockOption.CHAPTERS.minutes to UnlockOption.CHAPTERS.chapters)
        assertEquals("1 minuto", UnlockOption.VERSE.duration)
        assertEquals("1 hora", UnlockOption.CHAPTERS.duration)
        assertEquals("1 versículo aleatório", UnlockOption.VERSE.reading)
        assertEquals("3 capítulos", UnlockOption.CHAPTERS.reading)
    }

    @Test fun onlyChaptersAllowRuleEdits() {
        assertFalse(UnlockOption.VERSE.allowsRuleEdits)
        assertTrue(UnlockOption.CHAPTER.allowsRuleEdits)
    }

    @Test fun unlockNeverShortensLongerOne() {
        val now = 1_000_000L
        assertEquals(now + 60_000, Reading.unlockUntil(UnlockOption.VERSE, 0L, now))
        val hourLeft = now + 3_600_000
        assertEquals(hourLeft, Reading.unlockUntil(UnlockOption.VERSE, hourLeft, now))
    }
}

class RandomVerseTest {
    private val bible by lazy { File("src/main/assets/bible/acf.json").inputStream().use { Bible.parse(it) } }

    @Test fun drawsValidVersesAcrossTheBible() {
        val random = kotlin.random.Random(42)
        val books = mutableSetOf<Int>()
        repeat(2000) {
            val (b, c, v) = bible.randomVerse(random)
            assertTrue(bible.verses(b, c)[v].isNotBlank())
            books += b
        }
        assertTrue(books.size > 50)
    }
}
