package dev.renova.app.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
import dev.renova.app.domain.UsageHistory
import dev.renova.app.domain.VerseRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import java.time.LocalDate

// Arquivo ilegível vira um DataStore vazio em vez de derrubar o app a cada leitura.
private val Context.dataStore by preferencesDataStore(
    "renova",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Uso de um dia, em segundos por id de regra. */
data class DailyUsage(val date: String, val seconds: Map<String, Long>) {
    fun today(): Map<String, Long> = if (date == LocalDate.now().toString()) seconds else emptyMap()
}

class AppStore private constructor(context: Context) {
    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true }

    /** Valor salvo decodificado, ou null se está corrompido ou é de uma versão que esta não entende. */
    private inline fun <reified T> decodeOrNull(raw: String?): T? =
        raw?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private object Keys {
        val RULES = stringPreferencesKey("rules")
        /** Uso de um dia só, das versões até a 1.12; agora fica em [USAGE_DAYS]. */
        val USAGE_DATE = stringPreferencesKey("usage_date")
        val USAGE = stringPreferencesKey("usage")
        val USAGE_DAYS = stringPreferencesKey("usage_days")
        /** Liberação geral das versões até a 1.11; agora cada regra tem a sua em [UNLOCKS]. */
        val UNLOCK_UNTIL = longPreferencesKey("unlock_until")
        val UNLOCKS = stringPreferencesKey("unlocks")
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

    /** Regras salvas, sem as de site (tipo legado) e sem as que esta versão não entende (ver [ruleElements]). */
    private fun Preferences.rules(): List<Rule> =
        ruleElements().mapNotNull { it.second }.filter { it.type != TargetType.WEBSITE }

    /**
     * Cada regra salva, crua e decodificada; null quando esta versão não a entende (um tipo novo gravado
     * por uma versão mais nova, antes de a pessoa voltar para esta). Uma regra ilegível não leva as outras junto.
     */
    private fun Preferences.ruleElements(): List<Pair<JsonElement, Rule?>> {
        val array = this[Keys.RULES]?.let { runCatching { json.parseToJsonElement(it).jsonArray }.getOrNull() }.orEmpty()
        return array.map { it to runCatching { json.decodeFromJsonElement<Rule>(it) }.getOrNull() }
    }

    /** Grava [rules] e mantém no fim as que esta versão não entende, para a versão mais nova reencontrá-las. */
    private fun MutablePreferences.setRules(rules: List<Rule>) {
        val unknown = ruleElements().filter { it.second == null }.map { it.first }
        this[Keys.RULES] = JsonArray(rules.map { json.encodeToJsonElement(it) } + unknown).toString()
    }

    /** Uso de hoje (ver [UsageHistory]). */
    val usage: Flow<DailyUsage> = store.data.map { p ->
        val today = LocalDate.now().toString()
        DailyUsage(today, p.usageHistory()[today].orEmpty())
    }

    /** Uso por data; o dia único das versões anteriores entra como o primeiro do histórico. */
    private fun Preferences.usageHistory(): Map<String, Map<String, Long>> {
        if (this[Keys.USAGE_DAYS] != null) return decodeOrNull<Map<String, Map<String, Long>>>(this[Keys.USAGE_DAYS]).orEmpty()
        val date = this[Keys.USAGE_DATE] ?: return emptyMap()
        return mapOf(date to decodeOrNull<Map<String, Long>>(this[Keys.USAGE]).orEmpty())
    }

    private fun MutablePreferences.setUsageHistory(history: Map<String, Map<String, Long>>) {
        this[Keys.USAGE_DAYS] = json.encodeToString(history)
        remove(Keys.USAGE_DATE)
        remove(Keys.USAGE)
    }

    /**
     * Até quando cada regra está liberada por leitura, por id. Uma liberação geral ainda em curso,
     * de antes da atualização, vale para todas as regras até acabar.
     */
    val unlocks: Flow<Map<String, Long>> = store.data.map { it.unlocks(System.currentTimeMillis()) }

    private fun Preferences.unlocks(nowMs: Long): Map<String, Long> {
        val saved = decodeOrNull<Map<String, Long>>(this[Keys.UNLOCKS]).orEmpty()
        val legacy = this[Keys.UNLOCK_UNTIL] ?: 0L
        if (legacy <= nowMs) return saved
        return saved + rules().associate { it.id to maxOf(legacy, saved[it.id] ?: 0L) }
    }

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
        decodeOrNull<List<VerseRef>>(this[Keys.DEVOTION_VERSES]).orEmpty()

    suspend fun addDevotionVerse(ref: VerseRef) = store.edit { p ->
        val list = p.devotionVerses()
        if (ref !in list) p[Keys.DEVOTION_VERSES] = json.encodeToString(list + ref)
    }

    suspend fun removeDevotionVerse(ref: VerseRef) = store.edit { p ->
        p[Keys.DEVOTION_VERSES] = json.encodeToString(p.devotionVerses() - ref)
    }

    /** Último trecho sorteado para orar-ler e o dia do sorteio. */
    val devotionPick: Flow<DevotionPick?> = store.data.map { p ->
        decodeOrNull<DevotionPick>(p[Keys.DEVOTION_PICK])
    }

    /** Trecho de orar-ler de [dayKey] entre os de [list]: sorteado e gravado na primeira vez que é pedido no dia. */
    suspend fun devotionVerseFor(dayKey: String, list: List<VerseRef>): VerseRef? {
        var ref: VerseRef? = null
        store.edit { p ->
            val saved = decodeOrNull<DevotionPick>(p[Keys.DEVOTION_PICK])
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
        decodeOrNull<AvailableUpdate>(p[Keys.UPDATE])
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
        p.setRules(updated)
    }

    suspend fun deleteRule(id: String) = store.edit { p ->
        val list = p.rules()
        p.setRules(list.filterNot { it.id == id })
    }

    suspend fun saveUsage(usage: DailyUsage) = store.edit { p ->
        p.setUsageHistory(UsageHistory.record(p.usageHistory(), usage.date, usage.seconds))
    }

    /** Grava o uso como as versões até a 1.12 gravavam (um dia só). Só para testes. */
    @VisibleForTesting
    internal suspend fun setLegacyUsage(usage: DailyUsage) = store.edit { p ->
        p.remove(Keys.USAGE_DAYS)
        p[Keys.USAGE_DATE] = usage.date
        p[Keys.USAGE] = json.encodeToString(usage.seconds)
    }

    /** Guarda o uso do dia que sai e devolve o já gravado para [entering] (vazio se a data é nova). */
    suspend fun rollOverUsage(leaving: DailyUsage, entering: String): Map<String, Long> {
        var seconds = emptyMap<String, Long>()
        store.edit { p ->
            val (history, enteringSeconds) = UsageHistory.rollOver(p.usageHistory(), leaving.date, leaving.seconds, entering)
            p.setUsageHistory(history)
            seconds = enteringSeconds
        }
        return seconds
    }

    /** Se todas as regras [ruleIds] estão liberadas por leitura agora. */
    suspend fun isUnlocked(ruleIds: Collection<String>): Boolean {
        val now = System.currentTimeMillis()
        val unlocks = unlocks.first()
        return ruleIds.all { (unlocks[it] ?: 0L) > now }
    }

    suspend fun isUnlockedForEdits(): Boolean = editUnlockUntil.first() > System.currentTimeMillis()

    /** Os testes de UI dividem esta instância: zera a janela de edição deixada por um teste anterior. */
    @VisibleForTesting
    internal suspend fun clearEditUnlock() = store.edit { it.remove(Keys.EDIT_UNLOCK_UNTIL) }

    /**
     * Libera as regras [ruleIds] pelo tempo de [option] (as outras continuam como estão) e, se a leitura
     * foi de capítulos, abre a janela de edição das regras. Devolve até quando vale a liberação.
     */
    suspend fun grantUnlock(option: UnlockOption, ruleIds: Collection<String>): Long {
        var until = 0L
        store.edit { p ->
            val now = System.currentTimeMillis()
            val current = p.unlocks(now).filterValues { it > now }
            val updated = current + ruleIds.associateWith { Reading.unlockUntil(option, current[it] ?: 0L, now) }
            p[Keys.UNLOCKS] = json.encodeToString(updated)
            // A liberação geral antiga já foi copiada para as regras acima.
            p.remove(Keys.UNLOCK_UNTIL)
            if (option.allowsRuleEdits) {
                p[Keys.EDIT_UNLOCK_UNTIL] = Reading.unlockUntil(option, p[Keys.EDIT_UNLOCK_UNTIL] ?: 0L, now)
            }
            until = ruleIds.maxOfOrNull { updated.getValue(it) } ?: Reading.unlockUntil(option, 0L, now)
        }
        return until
    }

    /**
     * O relógio do aparelho saltou [deltaMs]: desloca as liberações e a janela de edição junto, numa edição só,
     * para elas durarem o mesmo tempo real. Sem isso, atrasar a hora em 1 h esticava 1 min de liberação para 61.
     */
    suspend fun shiftClock(deltaMs: Long) = store.edit { p ->
        decodeOrNull<Map<String, Long>>(p[Keys.UNLOCKS])?.let { saved ->
            p[Keys.UNLOCKS] = json.encodeToString(saved.mapValues { it.value + deltaMs })
        }
        p[Keys.UNLOCK_UNTIL]?.let { p[Keys.UNLOCK_UNTIL] = it + deltaMs }
        p[Keys.EDIT_UNLOCK_UNTIL]?.let { p[Keys.EDIT_UNLOCK_UNTIL] = it + deltaMs }
    }

    /** Grava [value] cru na chave de texto [key], como um arquivo corrompido ou de outra versão. Só para testes. */
    @VisibleForTesting
    internal suspend fun setRaw(key: String, value: String) = store.edit { it[stringPreferencesKey(key)] = value }

    @VisibleForTesting
    internal suspend fun raw(key: String): String? = store.data.first()[stringPreferencesKey(key)]

    /** Apaga todas as liberações por leitura. Só para testes. */
    @VisibleForTesting
    internal suspend fun clearUnlocks() = store.edit { it.remove(Keys.UNLOCKS); it.remove(Keys.UNLOCK_UNTIL) }

    /** Grava a liberação geral como as versões até a 1.11 gravavam. Só para testes. */
    @VisibleForTesting
    internal suspend fun setLegacyUnlock(until: Long) = store.edit { it[Keys.UNLOCK_UNTIL] = until }

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
