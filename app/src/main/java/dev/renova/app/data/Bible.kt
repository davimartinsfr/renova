package dev.renova.app.data

import android.content.Context
import dev.renova.app.domain.BiblePosition
import dev.renova.app.domain.VerseRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.text.Normalizer
import kotlin.random.Random

@Serializable
data class BibleBook(val name: String, val abbrev: String, val chapters: List<List<String>>)

class Bible(val books: List<BibleBook>) {
    val chaptersPerBook: List<Int> = books.map { it.chapters.size }

    fun verses(book: Int, chapter: Int): List<String> = books[book].chapters[chapter]

    fun title(book: Int, chapter: Int) = title(BiblePosition(book, chapter))

    /** Versículo sorteado com chance igual para todos: (livro, capítulo, versículo), base 0. */
    fun randomVerse(random: Random = Random.Default): Triple<Int, Int, Int> {
        var n = random.nextInt(verseCount)
        books.forEachIndexed { b, book ->
            book.chapters.forEachIndexed { c, verses ->
                if (n < verses.size) return Triple(b, c, n)
                n -= verses.size
            }
        }
        error("unreachable")
    }

    private val verseCount = books.sumOf { book -> book.chapters.sumOf { it.size } }

    /**
     * Lê referências como "Salmos 23:1-2", "Sl 23:1", "1 João 1:9" ou "joao 3:16" (sem acento vale).
     * Devolve null se o livro não existir ou o capítulo/versículo estiver fora da Bíblia.
     */
    fun parseReference(text: String): VerseRef? {
        val m = REFERENCE.matchEntire(text.trim()) ?: return null
        val book = findBook(m.groupValues[1]) ?: return null
        val chapter = m.groupValues[2].toInt()
        val from = m.groupValues[3].toInt()
        val to = m.groupValues[4].ifEmpty { m.groupValues[3] }.toInt()
        val verses = books[book].chapters.getOrNull(chapter - 1) ?: return null
        if (from < 1 || to < from || to > verses.size) return null
        return VerseRef(book, chapter, from, to)
    }

    fun verses(ref: VerseRef): List<String> = verses(ref.book, ref.chapter - 1).subList(ref.fromVerse - 1, ref.toVerse)

    fun title(ref: VerseRef): String {
        val range = if (ref.fromVerse == ref.toVerse) "${ref.fromVerse}" else "${ref.fromVerse}-${ref.toVerse}"
        return "${BOOK_NAMES[ref.book]} ${ref.chapter}:$range"
    }

    private fun findBook(raw: String): Int? {
        val exact = raw.lowercase().replace(" ", "")
        val plain = exact.withoutAccents()
        // Abreviação exata primeiro: "jó" (Jó) e "jo" (João) só se distinguem pelo acento.
        books.indexOfFirst { it.abbrev == exact }.takeIf { it >= 0 }?.let { return it }
        BOOK_NAMES.indexOfFirst { it.lowercase().replace(" ", "").withoutAccents() == plain }.takeIf { it >= 0 }?.let { return it }
        return books.indexOfFirst { it.abbrev.withoutAccents() == plain }.takeIf { it >= 0 }
    }

    companion object {
        val BOOK_NAMES = listOf(
            "Gênesis", "Êxodo", "Levítico", "Números", "Deuteronômio", "Josué",
            "Juízes", "Rute", "1 Samuel", "2 Samuel", "1 Reis", "2 Reis",
            "1 Crônicas", "2 Crônicas", "Esdras", "Neemias", "Ester", "Jó",
            "Salmos", "Provérbios", "Eclesiastes", "Cânticos", "Isaías", "Jeremias",
            "Lamentações de Jeremias", "Ezequiel", "Daniel", "Oséias", "Joel", "Amós",
            "Obadias", "Jonas", "Miquéias", "Naum", "Habacuque", "Sofonias",
            "Ageu", "Zacarias", "Malaquias", "Mateus", "Marcos", "Lucas",
            "João", "Atos", "Romanos", "1 Coríntios", "2 Coríntios", "Gálatas",
            "Efésios", "Filipenses", "Colossenses", "1 Tessalonicenses", "2 Tessalonicenses", "1 Timóteo",
            "2 Timóteo", "Tito", "Filemom", "Hebreus", "Tiago", "1 Pedro",
            "2 Pedro", "1 João", "2 João", "3 João", "Judas", "Apocalipse",
        )

        fun title(pos: BiblePosition) = "${BOOK_NAMES[pos.book]} ${pos.chapter + 1}"

        private val REFERENCE = Regex("""(\d?\s*\D+?)\s*(\d+)\s*:\s*(\d+)(?:\s*[-–]\s*(\d+))?""")

        private fun String.withoutAccents() =
            Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

        private val json = Json { ignoreUnknownKeys = true }
        private val mutex = Mutex()
        @Volatile private var cached: Bible? = null

        fun parse(input: InputStream): Bible =
            Bible(json.decodeFromString(input.bufferedReader(Charsets.UTF_8).use { it.readText() }))

        /** Almeida Revista e Atualizada, embutida em assets (~4 MB); carregada uma vez. */
        suspend fun load(context: Context): Bible = cached ?: mutex.withLock {
            cached ?: withContext(Dispatchers.IO) {
                context.assets.open("bible/ara.json").use { parse(it) }
            }.also { cached = it }
        }
    }
}
