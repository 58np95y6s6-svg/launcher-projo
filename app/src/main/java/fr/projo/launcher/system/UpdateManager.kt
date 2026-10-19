package fr.projo.launcher.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import fr.projo.launcher.data.AppRepository
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Version disponible sur GitHub. */
data class UpdateInfo(
    /** Nom affichable, ex. « test #42 » ou « 1.2.0 ». */
    val remoteName: String,
    val downloadUrl: String,
    val available: Boolean,
)

/**
 * Mise à jour de Projo depuis GitHub Releases, sans Downloader :
 *  - version de TEST installée → compare avec la release « test » (numéro de compilation) ;
 *  - version STABLE installée → compare avec la dernière release « v… ».
 * L'installation passe par la fenêtre « Installer ? » d'Android (obligatoire).
 */
class UpdateManager(private val context: Context) {

    private val pm = context.packageManager

    private val versionInstallee: String = try {
        pm.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    @Suppress("DEPRECATION")
    private val codeInstalle: Long = try {
        val pi = pm.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    } catch (e: Exception) { 0L }

    /** true pour une version de test (nom « test-NN »). */
    val canalTest: Boolean = versionInstallee.startsWith("test") || versionInstallee.endsWith("-dev")

    val versionAffichee: String = if (canalTest) "test #$codeInstalle" else versionInstallee

    /** À appeler hors du thread principal. */
    fun check(): UpdateInfo {
        // Version Fire TV : sa propre release « firetv » (branche claude/launcher-firetv)
        val url = if (canalTest) "$API/releases/tags/$TAG_TEST" else "$API/releases/latest"
        val json = JSONObject(lireTexte(url))
        val assets = json.optJSONArray("assets")
        val nomFichier = if (canalTest) APK_TEST else "launcher.apk"
        var lien: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == nomFichier) lien = a.optString("browser_download_url")
            }
        }
        lien ?: error("APK introuvable dans la release")
        return if (canalTest) {
            // Titre de la release : « Version de test #NN » ; NN = versionCode de cette compilation
            val distant = Regex("#(\\d+)").find(json.optString("name"))?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            UpdateInfo("test #$distant", lien, distant > codeInstalle)
        } else {
            val tag = json.optString("tag_name").removePrefix("v")
            UpdateInfo(tag, lien, plusRecente(tag, versionInstallee))
        }
    }

    /** Télécharge l'APK (progression de 0 à 1). À appeler hors du thread principal. */
    fun download(url: String, progression: (Float) -> Unit): File {
        val dossier = File(context.cacheDir, "maj").apply { mkdirs() }
        dossier.listFiles()?.forEach { it.delete() }
        val fichier = File(dossier, "projo.apk")
        val c = connexion(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: -1L
            c.inputStream.use { entree ->
                fichier.outputStream().use { sortie ->
                    val tampon = ByteArray(64 * 1024)
                    var lu = 0L
                    while (true) {
                        val n = entree.read(tampon)
                        if (n < 0) break
                        sortie.write(tampon, 0, n)
                        lu += n
                        if (total > 0) progression(lu.toFloat() / total)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        return fichier
    }

    /** Autorisation « installer des apps inconnues » accordée à Projo ? */
    fun peutInstaller(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 26) pm.canRequestPackageInstalls() else true

    /** Ouvre l'écran système qui autorise Projo à installer des apps. */
    fun ouvrirAutorisation() {
        try {
            val intent = if (android.os.Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            } else {
                Intent(Settings.ACTION_SECURITY_SETTINGS)
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(AppRepository.TAG, "Écran d'autorisation indisponible", e)
        }
    }

    /** Lance la fenêtre d'installation d'Android. */
    fun installer(fichier: File) {
        val uri = if (android.os.Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(context, "${context.packageName}.fichiers", fichier)
        } else {
            // Android 5/6 (vieux Fire TV) : l'installeur ne lit que des fichiers publics
            val dossier = context.externalCacheDir ?: context.cacheDir
            val copie = File(dossier, "projo.apk")
            fichier.copyTo(copie, overwrite = true)
            copie.setReadable(true, false)
            Uri.fromFile(copie)
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun plusRecente(distante: String, locale: String): Boolean {
        val d = distante.split('.').map { it.toIntOrNull() ?: 0 }
        val l = locale.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(d.size, l.size)) {
            val a = d.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun connexion(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Projo-Launcher")
            setRequestProperty("Accept", "application/vnd.github+json")
        }

    private fun lireTexte(url: String): String {
        val c = connexion(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val API = "https://api.github.com/repos/58np95y6s6-svg/launcher-projo"
        /** Canal de test de cette branche : la version Fire TV a sa propre release. */
        private const val TAG_TEST = "firetv"
        private const val APK_TEST = "launcher-firetv.apk"
    }
}
