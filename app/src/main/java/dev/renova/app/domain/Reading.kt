package dev.renova.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class BiblePosition(val book: Int = 0, val chapter: Int = 0, val rounds: Int = 0)

/** Opções de liberação: quanto tempo de uso cada leitura dá. [chapters] = 0 é um versículo aleatório. */
enum class UnlockOption(val minutes: Long, val chapters: Int) {
    VERSE(1, 0),
    CHAPTER(15, 1),
    CHAPTERS(60, 3);

    val duration: String
        get() = when {
            minutes == 1L -> "1 minuto"
            minutes < 60 -> "$minutes minutos"
            minutes == 60L -> "1 hora"
            else -> "${minutes / 60} horas"
        }

    val reading: String get() = if (chapters == 0) "1 versículo" else Reading.chapters(chapters)

    /** Afrouxar regras exige ler capítulos: um versículo seria um atalho barato demais. */
    val allowsRuleEdits: Boolean get() = chapters > 0
}

object Reading {
    private const val WORDS_PER_MINUTE = 200
    private const val MIN_SECONDS = 30
    const val MIN_SECONDS_VERSE = 10

    fun chapters(n: Int): String = if (n == 1) "1 capítulo" else "$n capítulos"

    fun minSeconds(verses: List<String>, floor: Int = MIN_SECONDS): Int {
        val words = verses.sumOf { v -> v.split(' ').count { it.isNotBlank() } }
        return maxOf(floor, words * 60 / WORDS_PER_MINUTE)
    }

    /** Fim do desbloqueio ao concluir [option]: nunca encurta um desbloqueio maior já em andamento. */
    fun unlockUntil(option: UnlockOption, currentUntil: Long, now: Long): Long =
        maxOf(currentUntil, now + option.minutes * 60_000)

    /** Avança um capítulo; depois de Apocalipse 22 volta para Gênesis 1. */
    fun next(pos: BiblePosition, chaptersPerBook: List<Int>): BiblePosition = when {
        pos.chapter + 1 < chaptersPerBook[pos.book] -> pos.copy(chapter = pos.chapter + 1)
        pos.book + 1 < chaptersPerBook.size -> pos.copy(book = pos.book + 1, chapter = 0)
        else -> BiblePosition(0, 0, pos.rounds + 1)
    }
}
