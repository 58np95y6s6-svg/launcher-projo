package fr.projo.launcher.modules.infos

import android.util.Log
import android.util.Xml
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.outils.FluxReseau
import fr.projo.launcher.modules.outils.TexteFlux
import fr.projo.launcher.modules.outils.XmlFlux
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Une source d'actualités (identifiant enregistré dans les réglages : ne pas changer). */
class SourceInfos(
    val id: String,
    val nom: String,
    /** Nom court pour la marge des titres. */
    val court: String,
    /** Flux RSS, essayés dans l'ordre (le premier qui répond l'emporte). */
    val urls: List<String>,
)

/**
 * Flux RSS gratuits et publics, sans clé. Adresses « une » / « titres » des sites, stables
 * depuis des années (les secondes adresses sont des replis connus).
 */
val SOURCES_INFOS: List<SourceInfos> = listOf(
    SourceInfos("franceinfo", "franceinfo", "franceinfo", listOf("https://www.franceinfo.fr/titres.rss")),
    SourceInfos("lemonde", "Le Monde", "Le Monde", listOf("https://www.lemonde.fr/rss/une.xml")),
    SourceInfos(
        "lequipe", "L'Équipe", "L'Équipe",
        listOf("https://dwh.lequipe.fr/api/edito/rss?path=/", "https://www.lequipe.fr/rss/actu_rss.xml"),
    ),
    SourceInfos(
        "20minutes", "20 Minutes", "20 Min",
        listOf("https://www.20minutes.fr/feeds/rss-une.xml", "https://www.20minutes.fr/feeds/rss-actu-france.xml"),
    ),
)

val SOURCES_INFOS_PAR_DEFAUT = listOf("franceinfo", "lemonde")

/** Un titre d'actualité, nettoyé. [date] : publication (ms), ou null si inconnue. */
data class TitreInfo(val titre: String, val source: String, val date: Long?)

/** Les derniers titres d'une source. */
data class TitresSource(val source: String, val titres: List<TitreInfo>)

/** Lecture des flux RSS (ou Atom) en flux, sans tout charger en mémoire. */
object InfosReseau {

    private const val TAG = DonneesModules.TAG
    const val MAX_TITRES = 15

    fun charger(source: SourceInfos): TitresSource {
        var derniere: Exception? = null
        for (url in source.urls) {
            try {
                val titres = FluxReseau.ouvrir(url, 7000, "application/rss+xml, application/xml, text/xml, */*").use {
                    lire(it, source.id)
                }
                if (titres.isNotEmpty()) return TitresSource(source.id, titres)
                derniere = IllegalStateException("flux vide : $url")
            } catch (e: Exception) {
                Log.w(TAG, "Infos : $url illisible", e)
                derniere = e
            }
        }
        throw derniere ?: IllegalStateException("aucun flux")
    }

    /** Titres des [MAX_TITRES] premiers articles (item RSS ou entry Atom). */
    fun lire(flux: InputStream, source: String): List<TitreInfo> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(flux, null)
        val titres = ArrayList<TitreInfo>()
        val vus = HashSet<String>()
        while (titres.size < MAX_TITRES) {
            val ev = p.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev != XmlPullParser.START_TAG || (p.name != "item" && p.name != "entry")) continue
            var titre = ""
            var publie: Long? = null
            XmlFlux.lireEnfants(p) { nom ->
                when (nom) {
                    "title" -> titre = TexteFlux.nettoyer(XmlFlux.texte(p))
                    "pubDate", "dc:date", "updated", "published" -> {
                        val d = date(XmlFlux.texte(p))
                        if (publie == null) publie = d
                    }
                    else -> XmlFlux.sauter(p)
                }
            }
            if (titre.isNotEmpty() && vus.add(titre.lowercase(Locale.FRENCH))) titres += TitreInfo(titre, source, publie)
        }
        return titres
    }

    private val FORMATS_RFC822 = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm Z", "EEE, dd MMM yyyy HH:mm:ss",
    )

    /** Dates RSS (« Mon, 19 Oct 2026 18:05:00 +0200 ») ou ISO 8601 (« 2026-10-19T18:05:00+02:00 »). */
    fun date(s: String): Long? {
        val t = s.trim()
        if (t.isEmpty()) return null
        if (t.length >= 19 && t[4] == '-' && t[10] == 'T') {
            // ISO 8601 sans le motif « X » (API 24) : fuseau ramené à « +0200 »
            val base = t.substring(0, 19)
            var fuseau = t.substring(19).replace(Regex("^\\.\\d+"), "")
            fuseau = when {
                fuseau.isEmpty() || fuseau == "Z" -> "+0000"
                else -> fuseau.replace(":", "")
            }
            return try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).parse(base + fuseau)?.time
            } catch (e: Exception) {
                null
            }
        }
        for (f in FORMATS_RFC822) {
            try {
                val format = SimpleDateFormat(f, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                return format.parse(t)?.time ?: continue
            } catch (e: Exception) {
                // format suivant
            }
        }
        return null
    }
}
