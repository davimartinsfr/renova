package dev.renova.app.data

import android.content.Context
import dev.renova.app.domain.BiblePosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

@Serializable
data class BibleBook(val name: String, val abbrev: String, val chapters: List<List<String>>)

class Bible(val books: List<BibleBook>) {
    val chaptersPerBook: List<Int> = books.map { it.chapters.size }

    fun verses(book: Int, chapter: Int): List<String> = books[book].chapters[chapter]

    fun title(book: Int, chapter: Int) = title(BiblePosition(book, chapter))

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

        private val json = Json { ignoreUnknownKeys = true }
        private val mutex = Mutex()
        @Volatile private var cached: Bible? = null

        fun parse(input: InputStream): Bible =
            Bible(json.decodeFromString(input.bufferedReader(Charsets.UTF_8).use { it.readText() }))

        /** Almeida Corrigida Fiel, embutida em assets (~4 MB); carregada uma vez. */
        suspend fun load(context: Context): Bible = cached ?: mutex.withLock {
            cached ?: withContext(Dispatchers.IO) {
                context.assets.open("bible/acf.json").use { parse(it) }
            }.also { cached = it }
        }
    }
}
