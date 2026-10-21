package dev.renova.app.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.renova.app.domain.BiblePosition
import dev.renova.app.domain.Devotion
import dev.renova.app.domain.DevotionPick
import dev.renova.app.domain.Reading
import dev.renova.app.domain.Rule
import dev.renova.app.domain.TargetType
import dev.renova.app.domain.UnlockOption
import dev.renova.app.domain.VerseRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore("renova")

/** Uso de hoje, em segundos por id de regra. */
data class DailyUsage(val date: String, val seconds: Map<String, Long>) {
    fun today(): Map<String, Long> = if (date == LocalDate.now().toString()) seconds else emptyMap()
}

class AppStore private constructor(context: Context) {
    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val RULES = stringPreferencesKey("rules")
        val USAGE_DATE = stringPreferencesKey("usage_date")
        val USAGE = stringPreferencesKey("usage")
        val UNLOCK_UNTIL = longPreferencesKey("unlock_until")
        val BIBLE_BOOK = intPreferencesKey("bible_book")
        val BIBLE_CHAPTER = intPreferencesKey("bible_chapter")
        val BIBLE_ROUNDS = intPreferencesKey("bible_rounds")
        val CHAPTERS_READ = intPreferencesKey("chapters_read")
        val EDIT_UNLOCK_UNTIL = longPreferencesKey("edit_unlock_until")
        val DEVOTION_DONE_DAY = stringPreferencesKey("devotion_done_day")
        val DEVOTION_ENABLED = booleanPreferencesKey("devotion_enabled")
        val DEVOTION_VERSES = stringPreferencesKey("devotion_verses")
        val DEVOTION_PICK = stringPreferencesKey("devotion_pick")
        val UPDATE = stringPreferencesKey("update")
        val UPDATE_NOTIFIED = stringPreferencesKey("update_notified")
        val UPDATE_CHECKED_AT = longPreferencesKey("update_checked_at")
    }

    val rules: Flow<List<Rule>> = store.data.map { it.rules() }

    /** Regras salvas, sem as de site (tipo legado). */
    private fun Preferences.rules(): List<Rule> =
        this[Keys.RULES]?.let { json.decodeFromString<List<Rule>>(it) }.orEmpty().filter { it.type != TargetType.WEBSITE }

    val usage: Flow<DailyUsage> = store.data.map { p ->
        DailyUsage(
            p[Keys.USAGE_DATE] ?: "",
            p[Keys.USAGE]?.let { json.decodeFromString<Map<String, Long>>(it) } ?: emptyMap(),
        )
    }

    val unlockUntil: Flow<Long> = store.data.map { it[Keys.UNLOCK_UNTIL] ?: 0L }

    val biblePosition: Flow<BiblePosition> = store.data.map { it.position() }

    /** Dia (ver [Devotion.dayKey]) em que os 7 minutos foram feitos pela última vez. */
    val devotionDoneDay: Flow<String?> = store.data.map { it[Keys.DEVOTION_DONE_DAY] }

    suspend fun setDevotionDone(dayKey: String) = store.edit {
        // Grava a escolha atual antes, para fazer os 7 minutos não ligar o bloqueio (ver devotionEnabled).
        if (it[Keys.DEVOTION_ENABLED] == null) it[Keys.DEVOTION_ENABLED] = it[Keys.DEVOTION_DONE_DAY] != null
        it[Keys.DEVOTION_DONE_DAY] = dayKey
    }

    /** Apaga o estado dos 7 minutos, como numa instalação nova. Só para testes. */
    @VisibleForTesting
    suspend fun clearDevotion() = store.edit { it.remove(Keys.DEVOTION_ENABLED); it.remove(Keys.DEVOTION_DONE_DAY) }

    /** Grava o dia feito como as versões anteriores gravavam, sem fixar a escolha. Só para testes. */
    @VisibleForTesting
    suspend fun setDevotionDoneRaw(dayKey: String) = store.edit { it[Keys.DEVOTION_DONE_DAY] = dayKey }

    /**
     * Se os 7 minutos com o Senhor bloqueiam os apps das regras até serem feitos. Vem desligado;
     * quem já fez os 7 minutos numa versão anterior, quando o bloqueio era sempre ligado, continua com ele.
     */
    val devotionEnabled: Flow<Boolean> = store.data.map { it[Keys.DEVOTION_ENABLED] ?: (it[Keys.DEVOTION_DONE_DAY] != null) }

    suspend fun setDevotionEnabled(enabled: Boolean) = store.edit { it[Keys.DEVOTION_ENABLED] = enabled }

    /** Trechos de orar-ler que a pessoa acrescentou, na ordem em que foram adicionados. */
    val devotionVerses: Flow<List<VerseRef>> = store.data.map { it.devotionVerses() }

    private fun Preferences.devotionVerses(): List<VerseRef> =
        this[Keys.DEVOTION_VERSES]?.let { json.decodeFromString<List<VerseRef>>(it) }.orEmpty()

    suspend fun addDevotionVerse(ref: VerseRef) = store.edit { p ->
        val list = p.devotionVerses()
        if (ref !in list) p[Keys.DEVOTION_VERSES] = json.encodeToString(list + ref)
    }

    suspend fun removeDevotionVerse(ref: VerseRef) = store.edit { p ->
        p[Keys.DEVOTION_VERSES] = json.encodeToString(p.devotionVerses() - ref)
    }

    /** Último trecho sorteado para orar-ler e o dia do sorteio. */
    val devotionPick: Flow<DevotionPick?> = store.data.map { p ->
        p[Keys.DEVOTION_PICK]?.let { json.decodeFromString<DevotionPick>(it) }
    }

    /** Trecho de orar-ler de [dayKey] entre os de [list]: sorteado e gravado na primeira vez que é pedido no dia. */
    suspend fun devotionVerseFor(dayKey: String, list: List<VerseRef>): VerseRef? {
        var ref: VerseRef? = null
        store.edit { p ->
            val saved = p[Keys.DEVOTION_PICK]?.let { json.decodeFromString<DevotionPick>(it) }
            val pick = Devotion.pickForDay(list, saved, dayKey)
            if (pick != null && pick != saved) p[Keys.DEVOTION_PICK] = json.encodeToString(pick)
            ref = pick?.ref
        }
        return ref
    }

    val chaptersRead: Flow<Int> = store.data.map { it[Keys.CHAPTERS_READ] ?: 0 }

    /** Até quando dá para afrouxar regras (só liberações por capítulos contam). */
    val editUnlockUntil: Flow<Long> = store.data.map { it[Keys.EDIT_UNLOCK_UNTIL] ?: 0L }

    val availableUpdate: Flow<AvailableUpdate?> = store.data.map { p ->
        p[Keys.UPDATE]?.let { json.decodeFromString<AvailableUpdate>(it) }
    }

    suspend fun setAvailableUpdate(update: AvailableUpdate?) = store.edit { p ->
        if (update == null) p.remove(Keys.UPDATE) else p[Keys.UPDATE] = json.encodeToString(update)
    }

    suspend fun notifiedUpdateVersion(): String? = store.data.first()[Keys.UPDATE_NOTIFIED]

    suspend fun setNotifiedUpdateVersion(version: String) = store.edit { it[Keys.UPDATE_NOTIFIED] = version }

    /** Marca a checagem de atualização se a última foi há mais de [intervalMs]; devolve se é hora de checar. */
    suspend fun claimUpdateCheck(intervalMs: Long): Boolean {
        var due = false
        store.edit { p ->
            val now = System.currentTimeMillis()
            if (now - (p[Keys.UPDATE_CHECKED_AT] ?: 0L) >= intervalMs) {
                p[Keys.UPDATE_CHECKED_AT] = now
                due = true
            }
        }
        return due
    }

    private fun Preferences.position() = BiblePosition(
        this[Keys.BIBLE_BOOK] ?: 0,
        this[Keys.BIBLE_CHAPTER] ?: 0,
        this[Keys.BIBLE_ROUNDS] ?: 0,
    )

    suspend fun saveRule(rule: Rule) = store.edit { p ->
        val list = p.rules()
        val updated = if (list.any { it.id == rule.id }) list.map { if (it.id == rule.id) rule else it } else list + rule
        p[Keys.RULES] = json.encodeToString(updated)
    }

    suspend fun deleteRule(id: String) = store.edit { p ->
        val list = p.rules()
        p[Keys.RULES] = json.encodeToString(list.filterNot { it.id == id })
    }

    suspend fun saveUsage(usage: DailyUsage) = store.edit { p ->
        p[Keys.USAGE_DATE] = usage.date
        p[Keys.USAGE] = json.encodeToString(usage.seconds)
    }

    suspend fun isUnlocked(): Boolean = unlockUntil.first() > System.currentTimeMillis()

    suspend fun isUnlockedForEdits(): Boolean = editUnlockUntil.first() > System.currentTimeMillis()

    /** Os testes de UI dividem esta instância: zera a janela de edição deixada por um teste anterior. */
    @VisibleForTesting
    internal suspend fun clearEditUnlock() = store.edit { it.remove(Keys.EDIT_UNLOCK_UNTIL) }

    suspend fun grantUnlock(option: UnlockOption): Long {
        var until = 0L
        store.edit { p ->
            val now = System.currentTimeMillis()
            until = Reading.unlockUntil(option, p[Keys.UNLOCK_UNTIL] ?: 0L, now)
            p[Keys.UNLOCK_UNTIL] = until
            if (option.allowsRuleEdits) {
                p[Keys.EDIT_UNLOCK_UNTIL] = Reading.unlockUntil(option, p[Keys.EDIT_UNLOCK_UNTIL] ?: 0L, now)
            }
        }
        return until
    }

    /** Muda o próximo capítulo do plano (base 0). Mantém voltas e capítulos lidos. */
    suspend fun setPosition(book: Int, chapter: Int) = store.edit { p ->
        p[Keys.BIBLE_BOOK] = book
        p[Keys.BIBLE_CHAPTER] = chapter
    }

    /** Marca o capítulo atual como lido e avança para o próximo. */
    suspend fun completeChapter(chaptersPerBook: List<Int>) = store.edit { p ->
        val next = Reading.next(p.position(), chaptersPerBook)
        p[Keys.BIBLE_BOOK] = next.book
        p[Keys.BIBLE_CHAPTER] = next.chapter
        p[Keys.BIBLE_ROUNDS] = next.rounds
        p[Keys.CHAPTERS_READ] = (p[Keys.CHAPTERS_READ] ?: 0) + 1
    }

    companion object {
        @Volatile private var instance: AppStore? = null
        fun get(context: Context): AppStore =
            instance ?: synchronized(this) { instance ?: AppStore(context).also { instance = it } }
    }
}
