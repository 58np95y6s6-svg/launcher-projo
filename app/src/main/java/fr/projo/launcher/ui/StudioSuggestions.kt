package fr.projo.launcher.ui

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import fr.projo.launcher.data.AppCategories
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppRepository
import fr.projo.launcher.data.Rubrique
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/*
 * Suggestions du design « Studio » (rangée du haut de l'écran 1).
 *
 * Android TV publie les « chaînes » et le « Watch Next » des apps (Netflix, YouTube…) dans le
 * fournisseur TV (content://android.media.tv, TvContract / TvContractCompat). Leur lecture :
 *  - demande READ_EPG_DATA (permission « dangerous » : accord de l'utilisateur, ou
 *    `adb shell pm grant`) ;
 *  - et surtout, le fournisseur ne rend à une app que SES PROPRES lignes (colonne
 *    package_name) sauf si elle détient ACCESS_ALL_EPG_DATA, réservée aux apps signées par
 *    le constructeur ou installées en /system/priv-app (signature|privileged).
 * Un launcher installé normalement (comme iBiProjo) ne voit donc RIEN des autres apps : la
 * lecture n'est tentée que si les deux permissions sont accordées (installation privilégiée),
 * sinon on passe directement au repli. Le Fire TV n'a pas ce fournisseur : repli aussi.
 *
 * Repli : les « apps à la une » (favoris puis « Mes apps »), avec leur illustration ; la
 * grande carte est l'app la plus récemment utilisée si Android le dit (accès aux données
 * d'utilisation, accordé dans les réglages ou par `adb shell appops set fr.projo.launcher
 * GET_USAGE_STATS allow`), sinon le 1er favori. Aucun contenu inventé.
 */

/** Genre d'un programme, pour les onglets Explorer / Films / Séries. */
enum class StudioGenre { FILM, SERIE, AUTRE }

/** Un programme publié par une app (Watch Next ou chaîne de l'accueil Android TV). */
data class StudioProgramme(
    val id: String,
    val titre: String,
    /** Détail : « S2 · É5 · Titre de l'épisode », ou la description courte. */
    val detail: String?,
    val paquet: String,
    val genre: StudioGenre,
    /** Vignette 16:9 (thumbnail) ou affiche (poster), adresse content:// ou https://. */
    val image: String?,
    /** Intent qui ouvre le programme dans son app (Intent.toUri). */
    val intentUri: String?,
    /** true : vient de « Watch Next » (reprendre). */
    val reprise: Boolean,
)

/** Où les suggestions sont lues : fournisseur TV d'Android ; remplaçable dans les tests. */
interface StudioSource {
    /** Programmes lisibles (hors du thread principal) ; vide si indisponible. */
    fun programmes(): List<StudioProgramme>

    /** App la plus récemment utilisée parmi [paquets] (hors du thread principal), ou null. */
    fun plusRecente(paquets: Collection<String>): String?

    /** Image d'un programme, réduite à ~[largeur] px (hors du thread principal), ou null. */
    fun image(adresse: String, largeur: Int): ImageBitmap?
}

/** Source des suggestions ; null : celle d'Android (fournie par HomeStudio). */
val LocalStudioSource = staticCompositionLocalOf<StudioSource?> { null }

/** Lecture réelle : fournisseur TV (installation privilégiée seulement) et données d'utilisation. */
class StudioSourceAndroid(private val context: Context) : StudioSource {

    private fun accorde(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** Lecture de TOUS les programmes possible (sinon le fournisseur ne rend que les nôtres : aucun). */
    fun lecturePossible(): Boolean =
        Build.VERSION.SDK_INT >= 26 && accorde(PERMISSION_LECTURE) && accorde(PERMISSION_TOUT)

    override fun programmes(): List<StudioProgramme> {
        if (!lecturePossible()) return emptyList()
        val res = LinkedHashMap<String, StudioProgramme>()
        lire(URI_WATCH_NEXT, reprise = true, tri = "last_engagement_time_utc_millis DESC").forEach { res.putIfAbsent(it.cle(), it) }
        lire(URI_PREVIEW, reprise = false, tri = null).forEach { res.putIfAbsent(it.cle(), it) }
        Log.i(AppRepository.TAG, "Studio : ${res.size} programmes lus dans le fournisseur TV")
        return res.values.take(MAX_PROGRAMMES)
    }

    private fun StudioProgramme.cle() = "$paquet|$titre|${detail.orEmpty()}"

    private fun lire(uri: Uri, reprise: Boolean, tri: String?): List<StudioProgramme> = try {
        val colonnes = arrayOf(
            "_id", "package_name", "title", "episode_title", "season_display_number",
            "episode_display_number", "type", "poster_art_uri", "thumbnail_uri", "intent_uri", "short_description",
        )
        context.contentResolver.query(uri, colonnes, null, null, tri)?.use { c ->
            fun t(nom: String): String? = c.getColumnIndex(nom).takeIf { it >= 0 }?.let { c.getString(it) }?.trim()?.ifEmpty { null }
            val liste = mutableListOf<StudioProgramme>()
            while (c.moveToNext() && liste.size < MAX_PROGRAMMES * 2) {
                val titre = t("title") ?: continue
                val paquet = t("package_name") ?: continue
                val type = c.getColumnIndex("type").takeIf { it >= 0 && !c.isNull(it) }?.let { c.getInt(it) } ?: -1
                val genre = when (type) {
                    TYPE_FILM -> StudioGenre.FILM
                    TYPE_SERIE, TYPE_SAISON, TYPE_EPISODE -> StudioGenre.SERIE
                    else -> StudioGenre.AUTRE
                }
                val episode = listOfNotNull(
                    t("season_display_number")?.let { "S$it" },
                    t("episode_display_number")?.let { "É$it" },
                    t("episode_title"),
                ).joinToString(" · ").ifEmpty { null }
                liste += StudioProgramme(
                    id = "${if (reprise) "wn" else "pp"}:${t("_id")}",
                    titre = titre,
                    detail = episode ?: t("short_description")?.lineSequence()?.firstOrNull(),
                    paquet = paquet,
                    genre = genre,
                    image = t("thumbnail_uri") ?: t("poster_art_uri"),
                    intentUri = t("intent_uri"),
                    reprise = reprise,
                )
            }
            liste
        }.orEmpty()
    } catch (e: Exception) {
        // SecurityException (permission retirée), fournisseur absent (Fire TV)…
        Log.i(AppRepository.TAG, "Studio : lecture de $uri impossible (${e.javaClass.simpleName})")
        emptyList()
    }

    override fun plusRecente(paquets: Collection<String>): String? {
        if (paquets.isEmpty()) return null
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return null
            @Suppress("DEPRECATION")
            val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            if (mode != AppOpsManager.MODE_ALLOWED) return null
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val fin = System.currentTimeMillis()
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, fin - 14 * 24 * 3_600_000L, fin)
                .orEmpty()
                .filter { it.packageName in paquets && it.lastTimeUsed > 0 && it.totalTimeInForeground > 0 }
                .maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            null
        }
    }

    override fun image(adresse: String, largeur: Int): ImageBitmap? {
        images.get(adresse)?.let { return it }
        return try {
            val uri = Uri.parse(adresse)
            val octets: ByteArray = when (uri.scheme) {
                "http", "https" -> {
                    val cnx = URL(adresse).openConnection() as HttpURLConnection
                    cnx.connectTimeout = 4000
                    cnx.readTimeout = 6000
                    try { cnx.inputStream.use { lireLimite(it) } } finally { cnx.disconnect() }
                }
                else -> context.contentResolver.openInputStream(uri)?.use { lireLimite(it) } ?: return null
            } ?: return null
            val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(octets, 0, octets.size, bornes)
            var reduction = 1
            while (bornes.outWidth / (reduction * 2) >= largeur) reduction *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = reduction
                // 16 bits par pixel : deux fois moins de mémoire (1 Go de RAM sur le projecteur)
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(octets, 0, octets.size, options)?.asImageBitmap()?.also { images.put(adresse, it) }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Lit au plus 4 Mo (une image de programme pèse bien moins). */
    private fun lireLimite(flux: java.io.InputStream): ByteArray? {
        val sortie = ByteArrayOutputStream()
        val tampon = ByteArray(16 * 1024)
        while (true) {
            val n = flux.read(tampon)
            if (n < 0) break
            sortie.write(tampon, 0, n)
            if (sortie.size() > 4 * 1024 * 1024) return null
        }
        return sortie.toByteArray()
    }

    companion object {
        const val PERMISSION_LECTURE = "com.android.providers.tv.permission.READ_EPG_DATA"
        const val PERMISSION_TOUT = "com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA"
        private val URI_WATCH_NEXT: Uri = Uri.parse("content://android.media.tv/watch_next_program")
        private val URI_PREVIEW: Uri = Uri.parse("content://android.media.tv/preview_program")
        // Valeurs de TvContract.PreviewPrograms.TYPE_*
        private const val TYPE_FILM = 0
        private const val TYPE_SERIE = 1
        private const val TYPE_SAISON = 2
        private const val TYPE_EPISODE = 3
        private const val MAX_PROGRAMMES = 12

        /** Images des programmes (petit cache : ~6 Mo). */
        private val images = object : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 2
        }
    }
}

/** Une carte de la rangée des suggestions. */
sealed class StudioSuggestion {
    abstract val cle: String

    /** Vrai programme publié par une app ([app] : l'app qui l'a publié, si elle est installée). */
    class Programme(val programme: StudioProgramme, val app: AppInfo?) : StudioSuggestion() {
        override val cle get() = "prog:" + programme.id
    }

    /** Repli : une app à la une, avec une phrase (« Reprendre sur Netflix », « Films et séries »…). */
    class AppUne(val app: AppInfo, val phrase: String) : StudioSuggestion() {
        override val cle get() = "une:" + app.packageName
    }
}

/** Suggestions prêtes à afficher : vrais programmes (peut-être vide) et app la plus récente. */
class StudioSuggestionsEtat(val programmes: List<StudioProgramme>, val recente: String?)

/** Phrase d'une app à la une selon sa rubrique. */
fun phraseRubrique(app: AppInfo): String = when (AppCategories.rubrique(app)) {
    Rubrique.STREAMING -> "Films et séries"
    Rubrique.TV -> "TV en direct"
    Rubrique.MUSIQUE -> "Musique et audio"
    Rubrique.JEUX -> "Jeux"
    Rubrique.STORE -> "Applications"
    Rubrique.OUTILS -> "Internet et outils"
    Rubrique.SYSTEME -> "Système"
    Rubrique.AUTRES -> "Application"
}

/**
 * Apps à la une (repli) : favoris puis « Mes apps » (sans les entrées HDMI), au plus 6 ;
 * l'app la plus récemment utilisée en tête (grande carte), sinon le 1er favori.
 */
fun appsALaUne(favoris: List<AppInfo>, mesApps: List<AppInfo>, recente: String?): List<StudioSuggestion.AppUne> {
    val apps = (favoris + mesApps).filter { !it.isSpecial }.distinctBy { it.packageName }
    val tete = apps.firstOrNull { it.packageName == recente }
    val ordre = if (tete != null) listOf(tete) + (apps - tete) else apps
    return ordre.take(6).map { app ->
        StudioSuggestion.AppUne(app, if (app === tete) "Reprendre sur ${app.label}" else phraseRubrique(app))
    }
}

/** Lit les suggestions hors du thread principal, à l'affichage et à chaque retour sur l'accueil. */
@Composable
fun rememberStudioSuggestions(paquets: List<String>): StudioSuggestionsEtat {
    val context = LocalContext.current
    val fournie = LocalStudioSource.current
    val source = remember(fournie) { fournie ?: StudioSourceAndroid(context.applicationContext) }
    val cycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var reprises by remember { mutableIntStateOf(0) }
    LaunchedEffect(cycle) { if (cycle == Lifecycle.State.RESUMED) reprises++ }
    val etat by produceState(StudioSuggestionsEtat(emptyList(), null), source, paquets, reprises) {
        // Source d'Android : lue hors du thread principal ; source fournie (tests) : lue directement
        value = if (source is StudioSourceAndroid) {
            withContext(Dispatchers.IO) { StudioSuggestionsEtat(source.programmes(), source.plusRecente(paquets)) }
        } else {
            StudioSuggestionsEtat(source.programmes(), source.plusRecente(paquets))
        }
    }
    return etat
}

/** Ouvre un programme dans son app ; à défaut, l'app elle-même. */
fun ouvrirProgramme(context: Context, p: StudioProgramme, repli: () -> Unit) {
    val ok = p.intentUri?.let { uri ->
        try {
            context.startActivity(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            false
        }
    } ?: false
    if (!ok) repli()
}

/** Image d'un programme, chargée hors du thread principal (null tant qu'elle n'est pas prête). */
@Composable
fun rememberImageProgramme(adresse: String?, largeur: Int): ImageBitmap? {
    val context = LocalContext.current
    val fournie = LocalStudioSource.current
    val source = remember(fournie) { fournie ?: StudioSourceAndroid(context.applicationContext) }
    val image by produceState<ImageBitmap?>(null, adresse) {
        if (adresse != null) value = if (source is StudioSourceAndroid) withContext(Dispatchers.IO) { source.image(adresse, largeur) } else source.image(adresse, largeur)
    }
    return image
}
