package fr.projo.launcher.modules.outils

import fr.projo.launcher.modules.Reseau
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.xmlpull.v1.XmlPullParser

/**
 * Accès réseau en flux pour les modules Ce soir à la TV, Infos et Photo du jour (fichiers XML,
 * images) : HTTPS, délais courts, redirections suivies (même d'un domaine à l'autre), taille
 * plafonnée. À appeler hors du thread principal.
 */
object FluxReseau {

    private const val AGENT = "Mozilla/5.0 (Linux; Android 11; Projo-Launcher) AppleWebKit/537.36"

    /**
     * Ouvre [url] et renvoie le flux de la réponse (à fermer : ferme aussi la connexion).
     * Lève [Reseau.ErreurHttp] si le code n'est pas 200, ou une IOException.
     */
    fun ouvrir(url: String, delai: Int = 8000, accepte: String = "*/*"): InputStream {
        var adresse = url
        repeat(5) {
            val c = (URL(adresse).openConnection() as HttpURLConnection).apply {
                connectTimeout = delai
                readTimeout = delai
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", AGENT)
                setRequestProperty("Accept", accepte)
                setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9")
            }
            val code = try {
                c.responseCode
            } catch (e: IOException) {
                c.disconnect()
                throw e
            }
            when (code) {
                200 -> return object : FilterInputStream(c.inputStream) {
                    override fun close() {
                        try { super.close() } finally { c.disconnect() }
                    }
                }
                301, 302, 303, 307, 308 -> {
                    val cible = c.getHeaderField("Location")
                    c.disconnect()
                    if (cible.isNullOrBlank()) throw Reseau.ErreurHttp(code)
                    adresse = URL(URL(adresse), cible).toString()
                }
                else -> {
                    c.disconnect()
                    throw Reseau.ErreurHttp(code)
                }
            }
        }
        throw IOException("Trop de redirections : $url")
    }

    /**
     * Télécharge [url] dans [fichier] (au plus [tailleMax] octets, sinon IOException). En cas
     * d'échec, le fichier est supprimé.
     */
    fun telecharger(url: String, fichier: File, tailleMax: Long, delai: Int = 10_000) {
        try {
            ouvrir(url, delai).use { entree ->
                fichier.outputStream().buffered().use { sortie ->
                    val tampon = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = entree.read(tampon)
                        if (n < 0) break
                        total += n
                        if (total > tailleMax) throw IOException("Fichier trop gros : $url")
                        sortie.write(tampon, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            fichier.delete()
            throw e
        }
    }
}

/** Nettoyage des textes venus d'un flux (titres RSS, XMLTV, page HTML). */
object TexteFlux {

    private val ENTITES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "laquo" to "«", "raquo" to "»",
        "hellip" to "…", "ndash" to "–", "mdash" to "—", "euro" to "€", "eacute" to "é", "egrave" to "è",
        "ecirc" to "ê", "agrave" to "à", "acirc" to "â", "ccedil" to "ç", "ocirc" to "ô", "ucirc" to "û",
        "ugrave" to "ù", "icirc" to "î", "iuml" to "ï", "euml" to "ë", "Eacute" to "É", "Agrave" to "À",
        "Ccedil" to "Ç", "oelig" to "œ", "OElig" to "Œ", "deg" to "°", "times" to "×", "middot" to "·",
        "shy" to "", "thinsp" to " ", "ensp" to " ", "emsp" to " ", "bull" to "•", "trade" to "™",
        "copy" to "©", "reg" to "®",
    )

    private val ENTITE = Regex("&(#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);")
    private val BALISE = Regex("<[^>]*>")
    private val ESPACES = Regex("\\s+")

    /** Décode les entités HTML (deux passes : « &amp;eacute; » arrive parfois). */
    fun entites(s: String): String {
        var r = s
        repeat(2) {
            if ('&' !in r) return r
            r = ENTITE.replace(r) { m ->
                val e = m.groupValues[1]
                when {
                    e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { codePoint(it) }
                    e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { codePoint(it) }
                    else -> ENTITES[e]
                } ?: m.value
            }
        }
        return r
    }

    private fun codePoint(c: Int): String? =
        if (c in 1..0x10FFFF && c !in 0xD800..0xDFFF) String(Character.toChars(c)) else null

    /** Texte d'affichage : sans balises ni entités, espaces simplifiés, sur une ligne. */
    fun nettoyer(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val sansBalises = entites(BALISE.replace(entites(s), " "))
        return ESPACES.replace(sansBalises, " ").trim()
    }
}

/** Lecture en flux d'un document XML (XmlPullParser), sans tout charger en mémoire. */
object XmlFlux {

    /** Parcourt les enfants directs de l'élément courant ; [enfant] doit consommer l'élément. */
    inline fun lireEnfants(p: XmlPullParser, enfant: (String) -> Unit) {
        val profondeur = p.depth
        while (true) {
            val ev = p.next()
            if (ev == XmlPullParser.END_DOCUMENT) return
            if (ev == XmlPullParser.END_TAG && p.depth == profondeur) return
            if (ev == XmlPullParser.START_TAG) {
                enfant(p.name)
            }
        }
    }

    /** Texte de l'élément courant (balises internes ignorées) ; curseur laissé sur sa balise de fin. */
    fun texte(p: XmlPullParser): String {
        val sb = StringBuilder()
        var niveau = 1
        while (niveau > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> niveau++
                XmlPullParser.END_TAG -> niveau--
                XmlPullParser.TEXT -> if (sb.length < 2000) sb.append(p.text)
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
        return sb.toString()
    }

    /** Saute l'élément courant (curseur sur sa balise ouvrante) et tout son contenu. */
    fun sauter(p: XmlPullParser) {
        var niveau = 1
        while (niveau > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> niveau++
                XmlPullParser.END_TAG -> niveau--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }
}
