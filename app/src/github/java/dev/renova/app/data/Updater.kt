package dev.renova.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import dev.renova.app.BuildConfig
import dev.renova.app.MainActivity
import dev.renova.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Atualizações publicadas como releases do GitHub (tag "v<versionName>", com o APK anexado). */
object Updater {
    private const val LATEST_RELEASE = "https://api.github.com/repos/davimartinsfr/renova/releases/latest"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 2

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Release(val tag_name: String, val body: String? = null, val assets: List<Asset> = emptyList())

    @Serializable
    private data class Asset(val name: String, val browser_download_url: String)

    /** Checa no máximo uma vez a cada [intervalMs] (chamado pelo serviço e pela tela inicial). */
    suspend fun checkIfDue(context: Context, intervalMs: Long) {
        if (AppStore.get(context).claimUpdateCheck(intervalMs)) check(context)
    }

    /**
     * Consulta o GitHub; guarda (ou limpa) a atualização disponível e avisa por notificação se for nova.
     * Devolve se conseguiu consultar: sem internet, "não há versão nova" seria mentira.
     */
    suspend fun check(context: Context): Boolean = withContext(Dispatchers.IO) {
        val release = runCatching {
            val conn = URL(LATEST_RELEASE).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.inputStream.use { json.decodeFromString<Release>(it.bufferedReader().readText()) }
        }.getOrNull() ?: return@withContext false
        val store = AppStore.get(context)
        val version = release.tag_name.removePrefix("v")
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
        if (apk == null || !isNewer(version, BuildConfig.VERSION_NAME)) {
            store.setAvailableUpdate(null)
            return@withContext true
        }
        val update = AvailableUpdate(version, apk.browser_download_url, release.body.orEmpty())
        if (store.notifiedUpdateVersion() != version) {
            notify(context, update)
            store.setNotifiedUpdateVersion(version)
        }
        store.setAvailableUpdate(update)
        true
    }

    fun isNewer(candidate: String, current: String): Boolean = isNewerVersion(candidate, current)

    /** Escopo do processo: sair da tela inicial no meio do download não o interrompe. */
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val downloadingState = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = downloadingState

    /** Baixa e instala [update] em segundo plano; um segundo toque enquanto baixa não faz nada. */
    fun startDownload(context: Context, update: AvailableUpdate) {
        if (!downloadingState.compareAndSet(expect = false, update = true)) return
        val app = context.applicationContext
        downloadScope.launch {
            runCatching { downloadAndInstall(app, update) }
                .onFailure { Toast.makeText(app, "Falha ao baixar: ${it.message}", Toast.LENGTH_LONG).show() }
            downloadingState.value = false
        }
    }

    /**
     * Se o APK baixado é mesmo o Renova na versão anunciada. O instalador já recusa uma assinatura
     * diferente, mas um arquivo trocado ou cortado no caminho é melhor parar aqui, com uma mensagem clara.
     */
    fun archiveMatches(archivePackage: String?, archiveVersion: String?, ownPackage: String, update: AvailableUpdate): Boolean =
        archivePackage == ownPackage && archiveVersion == update.version

    /** Baixa o APK e abre o instalador do Android (que sempre pede confirmação). */
    private suspend fun downloadAndInstall(context: Context, update: AvailableUpdate) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
            File(dir, "renova-${update.version}.apk").also { out ->
                val conn = URL(update.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
        val archive = context.packageManager.getPackageArchiveInfo(file.path, 0)
        check(archiveMatches(archive?.packageName, archive?.versionName, context.packageName, update)) {
            "o arquivo baixado não é o Renova v${update.version}."
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    @android.annotation.SuppressLint("MissingPermission") // sem permissão, notify() é ignorado
    private fun notify(context: Context, update: AvailableUpdate) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Atualizações", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Atualização disponível: v${update.version}")
            .setContentText("Toque para abrir o app e atualizar.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }
}
