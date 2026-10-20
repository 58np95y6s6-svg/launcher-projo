package fr.projo.launcher.modules.tv

import android.content.Context
import android.util.Log
import android.util.Xml
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.outils.FluxReseau
import fr.projo.launcher.modules.outils.TexteFlux
import fr.projo.launcher.modules.outils.XmlFlux
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

/** Une chaîne proposée par le module (identifiant enregistré dans les réglages : ne pas changer). */
class ChaineTv(
    val id: String,
    val nom: String,
    /** Libellé court de la pastille (« F2 »). */
    val court: String,
    /** Noms normalisés (voir [SoireeTv.normaliser]) reconnus dans le fichier XMLTV. */
    val alias: List<String>,
)

/** Grandes chaînes de la TNT, dans l'ordre des numéros. */
val CHAINES_TV: List<ChaineTv> = listOf(
    ChaineTv("tf1", "TF1", "TF1", listOf("tf1")),
    ChaineTv("france2", "France 2", "F2", listOf("france2")),
    ChaineTv("france3", "France 3", "F3", listOf("france3")),
    ChaineTv("canalplus", "Canal+", "C+", listOf("canalplus", "canal")),
    ChaineTv("france5", "France 5", "F5", listOf("france5")),
    ChaineTv("m6", "M6", "M6", listOf("m6")),
    ChaineTv("arte", "Arte", "ARTE", listOf("arte", "artefr")),
    ChaineTv("c8", "C8", "C8", listOf("c8")),
    ChaineTv("w9", "W9", "W9", listOf("w9")),
    ChaineTv("tmc", "TMC", "TMC", listOf("tmc")),
    ChaineTv("tfx", "TFX", "TFX", listOf("tfx")),
    ChaineTv("france4", "France 4", "F4", listOf("france4")),
    ChaineTv("6ter", "6ter", "6TER", listOf("6ter")),
    ChaineTv("lcp", "LCP", "LCP", listOf("lcp", "lcppublicsenat", "lcpan")),
    ChaineTv("gulli", "Gulli", "GULLI", listOf("gulli")),
)

val CHAINES_PAR_DEFAUT = listOf("tf1", "france2", "france3", "canalplus", "france5", "m6", "arte", "tmc")

/** Programme de première partie de soirée d'une chaîne. */
data class ProgrammeTv(
    val chaine: String,
    val titre: String,
    val sousTitre: String?,
    val debut: Long,
    val fin: Long,
    val genre: String?,
)

/** Soirée d'un jour : [jour] au format yyyyMMdd ; programmes dans l'ordre de [CHAINES_TV]. */
data class SoireeTvDonnees(val jour: String, val programmes: List<ProgrammeTv>)

/**
 * Programmes de la soirée, d'après le guide XMLTV public de XML TV Fr (xmltvfr.fr, projet libre
 * racacax/XML-TV-Fr, sans clé), fichier « TNT » compressé.
 *
 * Mémoire (1 Go sur le projecteur) : le fichier est téléchargé dans un fichier temporaire
 * (supprimé ensuite), puis lu en flux (XmlPullParser) : seuls les programmes de la soirée des
 * chaînes connues sont gardés. Le résultat (quelques Ko) est mis en cache sur le disque pour
 * la journée : un seul téléchargement par jour.
 */
object SoireeTv {

    private const val TAG = DonneesModules.TAG

    /** Sources, essayées dans l'ordre (même contenu, compressé ou non). */
    val SOURCES = listOf(
        "https://xmltvfr.fr/xmltv/xmltv_tnt.xml.gz",
        "https://xmltvfr.fr/xmltv/xmltv_tnt.zip",
        "https://xmltvfr.fr/xmltv/xmltv_tnt.xml",
    )

    private const val TAILLE_MAX = 60L * 1024 * 1024

    /** Fenêtre de la première partie de soirée (heure locale, en minutes depuis minuit). */
    private const val DEBUT_MIN = 20 * 60 + 45
    private const val DEBUT_MAX = 22 * 60 + 30
    private const val COUVRE = 21 * 60 + 15
    private const val DUREE_MIN = 25 * 60_000L

    fun jour(t: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(t)

    /** Soirée du jour [jour] : cache disque, sinon téléchargement. */
    fun charger(context: Context, jour: String): SoireeTvDonnees {
        val dossier = File(context.cacheDir, "soiree_tv").apply { mkdirs() }
        val cache = File(dossier, "soiree_$jour.json")
        lireCache(cache)?.let { return it }
        // Ménage : anciennes soirées et fichiers temporaires abandonnés
        dossier.listFiles()?.forEach { if (it.name != cache.name) it.delete() }

        var derniere: Exception? = null
        for (url in SOURCES) {
            val temp = File(dossier, "guide.tmp")
            try {
                FluxReseau.telecharger(url, temp, TAILLE_MAX, delai = 12_000)
                val soiree = ouvrirGuide(temp).use { lire(it, jour) }
                if (soiree.programmes.isNotEmpty()) {
                    runCatching { cache.writeText(versJson(soiree).toString()) }
                }
                return soiree
            } catch (e: Exception) {
                Log.w(TAG, "Ce soir à la TV : $url illisible", e)
                derniere = e
            } finally {
                temp.delete()
            }
        }
        throw derniere ?: IllegalStateException("guide indisponible")
    }

    /** Flux XML du fichier téléchargé : gzip, zip (première entrée .xml) ou XML brut. */
    private fun ouvrirGuide(f: File): InputStream {
        val entree = BufferedInputStream(f.inputStream(), 64 * 1024)
        entree.mark(4)
        val b0 = entree.read()
        val b1 = entree.read()
        entree.reset()
        return when {
            b0 == 0x1f && b1 == 0x8b -> BufferedInputStream(GZIPInputStream(entree, 32 * 1024), 64 * 1024)
            b0 == 'P'.code && b1 == 'K'.code -> {
                val zip = ZipInputStream(entree)
                while (true) {
                    val e = zip.nextEntry ?: run { zip.close(); error("archive sans XML") }
                    if (!e.isDirectory && e.name.endsWith(".xml", ignoreCase = true)) break
                }
                BufferedInputStream(zip, 64 * 1024)
            }
            else -> entree
        }
    }

    /** « TF1.fr », « Canal+ », « France 2 HD » → « tf1 », « canalplus », « france2hd ». */
    fun normaliser(s: String): String {
        val base = s.trim().lowercase(Locale.ROOT).removeSuffix(".fr").replace("+", "plus")
        val sansAccents = Normalizer.normalize(base, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return sansAccents.filter { it in 'a'..'z' || it in '0'..'9' }
    }

    private val PAR_ALIAS: Map<String, String> =
        CHAINES_TV.flatMap { c -> c.alias.map { it to c.id } }.toMap()

    private fun chaineDe(nom: String): String? {
        val n = normaliser(nom)
        return PAR_ALIAS[n] ?: PAR_ALIAS[n.removeSuffix("hd")]
    }

    /**
     * Lit un guide XMLTV en flux et garde, pour chaque chaîne connue, le programme de première
     * partie de soirée du [jour] (yyyyMMdd, heure locale de l'appareil).
     */
    fun lire(flux: InputStream, jour: String): SoireeTvDonnees {
        val cal = Calendar.getInstance().apply {
            time = SimpleDateFormat("yyyyMMdd", Locale.US).parse(jour) ?: error("jour invalide")
        }
        val minuit = cal.timeInMillis
        // Programmes commençant entre 19 h et 0 h 30 (le choix fin se fait ensuite)
        val fenetreDebut = minuit + 19 * 3_600_000L
        val fenetreFin = minuit + 24 * 3_600_000L + 30 * 60_000L

        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(flux, null)
        val idsChaines = HashMap<String, String?>()
        val candidats = HashMap<String, MutableList<ProgrammeTv>>()
        val formatHeure = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

        fun chaineProgramme(id: String): String? =
            if (idsChaines.containsKey(id)) idsChaines[id] else chaineDe(id).also { idsChaines[id] = it }

        while (true) {
            val ev = p.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev != XmlPullParser.START_TAG) continue
            when (p.name) {
                "channel" -> {
                    val id = p.getAttributeValue(null, "id") ?: continue
                    var trouve = chaineDe(id)
                    XmlFlux.lireEnfants(p) { nom ->
                        if (nom == "display-name") {
                            val t = XmlFlux.texte(p)
                            if (trouve == null) trouve = chaineDe(t)
                        } else XmlFlux.sauter(p)
                    }
                    idsChaines[id] = trouve
                }
                "programme" -> {
                    val chaine = p.getAttributeValue(null, "channel")?.let { chaineProgramme(it) }
                    val debut = heure(formatHeure, p.getAttributeValue(null, "start"))
                    if (chaine == null || debut == null || debut < fenetreDebut || debut >= fenetreFin) {
                        XmlFlux.sauter(p)
                        continue
                    }
                    val fin = heure(formatHeure, p.getAttributeValue(null, "stop")) ?: (debut + 60 * 60_000L)
                    var titre: String? = null
                    var sousTitre: String? = null
                    var genre: String? = null
                    var genreFr = false
                    XmlFlux.lireEnfants(p) { nom ->
                        when (nom) {
                            "title" -> {
                                val t = TexteFlux.nettoyer(XmlFlux.texte(p))
                                if (titre == null && t.isNotEmpty()) titre = t
                            }
                            "sub-title" -> {
                                val t = TexteFlux.nettoyer(XmlFlux.texte(p))
                                if (sousTitre == null && t.isNotEmpty()) sousTitre = t
                            }
                            "category" -> {
                                val fr = p.getAttributeValue(null, "lang")?.startsWith("fr") == true
                                val t = TexteFlux.nettoyer(XmlFlux.texte(p))
                                if (t.isNotEmpty() && (genre == null || (fr && !genreFr))) {
                                    genre = t
                                    genreFr = fr
                                }
                            }
                            else -> XmlFlux.sauter(p)
                        }
                    }
                    val t = titre ?: continue
                    candidats.getOrPut(chaine) { ArrayList() } +=
                        ProgrammeTv(chaine, t, sousTitre, debut, fin, genre?.replaceFirstChar { it.titlecase(Locale.FRENCH) })
                }
            }
        }
        val programmes = CHAINES_TV.mapNotNull { c -> candidats[c.id]?.let { choisir(it, minuit) } }
        return SoireeTvDonnees(jour, programmes)
    }

    /**
     * Première partie de soirée : le premier programme d'au moins 25 min qui commence entre
     * 20 h 45 et 22 h 30 ; à défaut, celui qui est à l'antenne à 21 h 15.
     */
    fun choisir(liste: List<ProgrammeTv>, minuit: Long): ProgrammeTv? {
        val tries = liste.sortedBy { it.debut }
        val min = minuit + DEBUT_MIN * 60_000L
        val max = minuit + DEBUT_MAX * 60_000L
        val couvre = minuit + COUVRE * 60_000L
        return tries.firstOrNull { it.debut in min until max && it.fin - it.debut >= DUREE_MIN }
            ?: tries.firstOrNull { it.debut <= couvre && it.fin > couvre }
    }

    private fun heure(f: SimpleDateFormat, s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        return try {
            // « 20261019211000 +0200 » ; parfois sans fuseau (heure de Paris)
            if (t.length >= 20) f.parse(t)?.time
            else SimpleDateFormat("yyyyMMddHHmmss", Locale.US).parse(t.take(14))?.time
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ cache disque

    private fun versJson(s: SoireeTvDonnees): JSONObject = JSONObject().apply {
        put("jour", s.jour)
        put("programmes", JSONArray().apply {
            s.programmes.forEach { pr ->
                put(JSONObject().apply {
                    put("chaine", pr.chaine)
                    put("titre", pr.titre)
                    pr.sousTitre?.let { put("sousTitre", it) }
                    put("debut", pr.debut)
                    put("fin", pr.fin)
                    pr.genre?.let { put("genre", it) }
                })
            }
        })
    }

    private fun lireCache(f: File): SoireeTvDonnees? {
        if (!f.isFile) return null
        return try {
            val j = JSONObject(f.readText())
            val a = j.getJSONArray("programmes")
            val liste = (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                ProgrammeTv(
                    o.getString("chaine"), o.getString("titre"), o.optString("sousTitre").ifEmpty { null },
                    o.getLong("debut"), o.getLong("fin"), o.optString("genre").ifEmpty { null },
                )
            }
            SoireeTvDonnees(j.getString("jour"), liste)
        } catch (e: Exception) {
            f.delete()
            null
        }
    }
}
