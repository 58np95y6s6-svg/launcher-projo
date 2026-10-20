package fr.projo.launcher.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Une équipe dans un match (NBA, football, rugby…). nom : nom complet (ex. « France »). */
data class NbaTeam(val abbr: String, val logoUrl: String?, val score: String?, val nom: String = "")

/** Un match NBA. state : "pre" (à venir), "in" (en cours), "post" (terminé). */
data class NbaGame(
    val id: String,
    val start: Long,
    val away: NbaTeam,
    val home: NbaTeam,
    val state: String,
    /** Texte d'état fourni par la source (ex. « Q3 4:12 », « Halftime », « Final »). */
    val detail: String,
)

/** Matchs d'une journée NBA. */
data class NbaNight(
    /** Journée NBA (date américaine, heure de New York) au format yyyyMMdd. */
    val day: String,
    /** true si c'est la journée en cours (sinon : prochaine journée avec des matchs). */
    val isTonight: Boolean,
    val games: List<NbaGame>,
)

/** Les deux onglets du bandeau : matchs de la nuit à venir et résultats de la nuit passée. */
data class NbaData(val tonight: NbaNight, val results: NbaNight?)

/**
 * Calendrier NBA depuis le tableau des scores public d'ESPN (sans clé, quelques Ko par appel).
 * Une « nuit » = la journée NBA en cours à New York : les matchs se jouent la nuit en France.
 * Une fois tous ses matchs terminés, on passe à la journée suivante qui a des matchs.
 */
class NbaRepository(private val context: Context) {

    private val ny = TimeZone.getTimeZone("America/New_York")

    /** À appeler hors du thread principal. Lève une exception si le réseau est indisponible. */
    fun fetchAll(): NbaData {
        val cal = Calendar.getInstance(ny)
        // Avant 6 h à New York, on est encore dans la journée NBA de la veille
        if (cal.get(Calendar.HOUR_OF_DAY) < 6) cal.add(Calendar.DAY_OF_MONTH, -1)
        val aujourdHui = jour(cal)
        val jeuxDuJour = fetchDay(aujourdHui)
        val journeeFinie = jeuxDuJour.isNotEmpty() && jeuxDuJour.all { it.state == "post" }

        // « Cette nuit » : la journée en cours, ou la prochaine qui a des matchs si elle est finie/vide
        var cetteNuit = NbaNight(aujourdHui, true, emptyList())
        if (jeuxDuJour.isNotEmpty() && !journeeFinie) {
            cetteNuit = NbaNight(aujourdHui, true, jeuxDuJour)
        } else {
            val c = cal.clone() as Calendar
            for (i in 1..7) {
                c.add(Calendar.DAY_OF_MONTH, 1)
                val day = jour(c)
                val jeux = fetchDay(day)
                if (jeux.isNotEmpty()) { cetteNuit = NbaNight(day, false, jeux); break }
            }
        }

        // « Résultats » : la dernière journée terminée (celle du jour si elle est finie, sinon la veille…)
        var resultats: NbaNight? = null
        if (journeeFinie) {
            resultats = NbaNight(aujourdHui, true, jeuxDuJour)
        } else {
            val c = cal.clone() as Calendar
            for (i in 1..7) {
                c.add(Calendar.DAY_OF_MONTH, -1)
                val day = jour(c)
                val finis = fetchDay(day).filter { it.state == "post" }
                if (finis.isNotEmpty()) { resultats = NbaNight(day, i == 1, finis); break }
            }
        }
        return NbaData(cetteNuit, resultats)
    }

    private fun jour(cal: Calendar): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = ny }.format(cal.time)

    private fun fetchDay(day: String): List<NbaGame> = lireMatchs(lireTexte("$URL_SCOREBOARD?dates=$day"))

    /**
     * Tableau des scores ESPN d'une autre compétition (même format que la NBA).
     * chemin : « soccer/fra.1 », « rugby/270559 »… ; dates : « yyyyMMdd » ou « yyyyMMdd-yyyyMMdd ».
     * À appeler hors du thread principal ; lève une exception si la source ne répond pas.
     */
    fun fetchScoreboard(chemin: String, dates: String?): List<NbaGame> {
        val base = "$URL_ESPN/$chemin/scoreboard"
        val json = try {
            lireTexte(if (dates != null) "$base?dates=$dates&limit=200" else "$base?limit=200")
        } catch (e: Exception) {
            // Certaines compétitions refusent une plage de dates : tableau du moment
            if (dates == null) throw e
            lireTexte(base)
        }
        return lireMatchs(json)
    }

    private fun lireMatchs(json: String): List<NbaGame> {
        val events = JSONObject(json).optJSONArray("events") ?: return emptyList()
        val res = ArrayList<NbaGame>()
        for (i in 0 until events.length()) {
            try {
                val e = events.getJSONObject(i)
                val comp = e.getJSONArray("competitions").getJSONObject(0)
                val equipes = comp.getJSONArray("competitors")
                var dom: NbaTeam? = null
                var ext: NbaTeam? = null
                for (j in 0 until equipes.length()) {
                    val c = equipes.getJSONObject(j)
                    val t = c.getJSONObject("team")
                    val nom = t.optString("displayName").ifBlank { t.optString("shortDisplayName") }
                    val equipe = NbaTeam(
                        abbr = t.optString("abbreviation").ifBlank {
                            t.optString("shortDisplayName", nom).take(3).uppercase(Locale.FRENCH)
                        }.ifBlank { "?" },
                        logoUrl = t.optString("logo").ifBlank { null },
                        score = c.optString("score").ifBlank { null },
                        nom = nom,
                    )
                    if (c.optString("homeAway") == "home") dom = equipe else ext = equipe
                }
                val statut = (comp.optJSONObject("status") ?: e.getJSONObject("status")).getJSONObject("type")
                res += NbaGame(
                    id = e.optString("id"),
                    start = dateIso(e.getString("date")),
                    away = ext ?: continue,
                    home = dom ?: continue,
                    state = statut.optString("state", "pre"),
                    detail = statut.optString("shortDetail"),
                )
            } catch (ex: Exception) {
                Log.w(AppRepository.TAG, "Match illisible", ex)
            }
        }
        // Du plus tôt au plus tard
        return res.sortedBy { it.start }
    }

    /** « 2026-10-21T23:30Z » → millisecondes. */
    private fun dateIso(s: String): Long {
        for (motif in listOf("yyyy-MM-dd'T'HH:mmX", "yyyy-MM-dd'T'HH:mm:ssX")) {
            try {
                return SimpleDateFormat(motif, Locale.US).parse(s)!!.time
            } catch (_: Exception) { }
        }
        return 0L
    }

    // ------------------------------------------------------------------ logos des équipes

    private val logos = LruCache<String, ImageBitmap>(40)
    private val dossierLogos = File(context.cacheDir, "logos-nba").apply { mkdirs() }

    fun cachedLogo(url: String): ImageBitmap? = logos.get(url)

    /** Logo d'équipe, mis en cache en mémoire et sur le disque (téléchargé une seule fois). */
    fun loadLogo(url: String): ImageBitmap? {
        logos.get(url)?.let { return it }
        return try {
            val fichier = File(dossierLogos, url.hashCode().toString() + ".png")
            if (!fichier.exists()) {
                val octets = lireOctets(url)
                fichier.writeBytes(octets)
            }
            // Logos fournis en 500 px : décodés réduits (~125 px), largement assez
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bmp = BitmapFactory.decodeFile(fichier.path, opts) ?: return null
            bmp.asImageBitmap().also { logos.put(url, it) }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ réseau

    private fun connexion(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Projo-Launcher")
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

    private fun lireOctets(url: String): ByteArray {
        val c = connexion(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val URL_ESPN = "https://site.api.espn.com/apis/site/v2/sports"
        private const val URL_SCOREBOARD = "$URL_ESPN/basketball/nba/scoreboard"

        /** Date d'une journée NBA, ex. « MER. 22 OCT. ». */
        fun libelleJour(day: String): String {
            val d: Date = SimpleDateFormat("yyyyMMdd", Locale.US).parse(day) ?: return ""
            return SimpleDateFormat("EEE d MMM", Locale.FRENCH).format(d).uppercase(Locale.FRENCH)
        }
    }
}
