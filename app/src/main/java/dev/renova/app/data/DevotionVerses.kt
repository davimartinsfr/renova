package dev.renova.app.data

import android.content.Context
import dev.renova.app.domain.VerseRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lista curada de trechos para orar-ler (assets/devotion/orar-ler.txt). */
object DevotionVerses {
    private const val ASSET = "devotion/orar-ler.txt"

    fun parse(lines: List<String>, bible: Bible): List<VerseRef> = lines
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { bible.parseReference(it) }

    /** Trechos do sorteio: os da pessoa; enquanto ela não adicionar nenhum, os do app. */
    fun pool(builtIn: List<VerseRef>, mine: List<VerseRef>): List<VerseRef> = mine.ifEmpty { builtIn }

    suspend fun load(context: Context, bible: Bible): List<VerseRef> = withContext(Dispatchers.IO) {
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { parse(it.readLines(), bible) }
    }
}
