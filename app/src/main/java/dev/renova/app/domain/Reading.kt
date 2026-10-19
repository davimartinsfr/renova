package dev.renova.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class BiblePosition(val book: Int = 0, val chapter: Int = 0, val rounds: Int = 0)

object Reading {
    const val DEFAULT_CHAPTERS_PER_UNLOCK = 3
    const val MIN_CHAPTERS_PER_UNLOCK = 1
    const val MAX_CHAPTERS_PER_UNLOCK = 10
    const val UNLOCK_MINUTES = 15L
    private const val WORDS_PER_MINUTE = 200
    private const val MIN_SECONDS = 30

    fun clampGoal(chapters: Int): Int = chapters.coerceIn(MIN_CHAPTERS_PER_UNLOCK, MAX_CHAPTERS_PER_UNLOCK)

    /** Diminuir a meta afrouxa o bloqueio, então só vale durante um desbloqueio (como afrouxar regras). */
    fun canSetGoal(current: Int, new: Int, unlocked: Boolean): Boolean = new >= current || unlocked

    fun chapters(n: Int): String = if (n == 1) "1 capítulo" else "$n capítulos"

    fun minSeconds(verses: List<String>): Int {
        val words = verses.sumOf { v -> v.split(' ').count { it.isNotBlank() } }
        return maxOf(MIN_SECONDS, words * 60 / WORDS_PER_MINUTE)
    }

    /** Avança um capítulo; depois de Apocalipse 22 volta para Gênesis 1. */
    fun next(pos: BiblePosition, chaptersPerBook: List<Int>): BiblePosition = when {
        pos.chapter + 1 < chaptersPerBook[pos.book] -> pos.copy(chapter = pos.chapter + 1)
        pos.book + 1 < chaptersPerBook.size -> pos.copy(book = pos.book + 1, chapter = 0)
        else -> BiblePosition(0, 0, pos.rounds + 1)
    }
}
