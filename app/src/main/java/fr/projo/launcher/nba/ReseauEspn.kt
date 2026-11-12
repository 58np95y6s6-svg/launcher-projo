package fr.projo.launcher.nba

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Flux ESPN du design NBA (docs/design-nba.md, « Données »). */
internal sealed class RequeteEspn {
    data object Classement : RequeteEspn()
    data object Equipes : RequeteEspn()
    data class Calendrier(val idEquipe: String) : RequeteEspn()
    data class Effectif(val idEquipe: String) : RequeteEspn()

    /** Résumé d'un match terminé (titulaires, blessures). */
    data class Resume(val idMatch: String) : RequeteEspn()

    /** Moyennes de la saison d'un joueur. */
    data class Joueur(val idJoueur: String) : RequeteEspn()
}

/** Réponse brute d'un flux : le texte JSON, ou un échec (code HTTP ; null : délai, réseau, taille). */
internal sealed class ReponseEspn {
    data class Ok(val texte: String) : ReponseEspn()
    data class Echec(val code: Int? = null) : ReponseEspn()
}

/** Requêtes bloquantes (hors du fil principal) ; remplacées par un faux dans les tests. */
internal fun interface ChargeurEspn {
    fun charger(requete: RequeteEspn): ReponseEspn
}

/**
 * Flux publics d'ESPN, sans compte ni clé : délai de 8 s, en-tête `Accept: application/json` et
 * rien d'autre. Aucune adresse n'est écrite dans les journaux.
 */
internal object ReseauEspn : ChargeurEspn {

    private const val SITE = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba"
    private const val CLASSEMENT = "https://site.api.espn.com/apis/v2/sports/basketball/nba/standings"
    private const val JOUEUR = "https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba/athletes/"

    private const val DELAI_MS = 8_000

    /** Le calendrier d'une équipe fait près d'1 Mo : au-delà de 6 Mo, la réponse est refusée. */
    private const val TAILLE_MAX = 6 * 1024 * 1024

    override fun charger(requete: RequeteEspn): ReponseEspn {
        val adresse = when (requete) {
            RequeteEspn.Classement -> CLASSEMENT
            RequeteEspn.Equipes -> "$SITE/teams"
            is RequeteEspn.Calendrier -> propre(requete.idEquipe)?.let { "$SITE/teams/$it/schedule" }
            is RequeteEspn.Effectif -> propre(requete.idEquipe)?.let { "$SITE/teams/$it/roster" }
            is RequeteEspn.Resume -> propre(requete.idMatch)?.let { "$SITE/summary?event=$it" }
            is RequeteEspn.Joueur -> propre(requete.idJoueur)?.let { "$JOUEUR$it/overview" }
        } ?: return ReponseEspn.Echec()
        return lire(adresse)
    }

    /** Identifiant ESPN (chiffres) : tout autre caractère est retiré ; vide → null. */
    private fun propre(id: String): String? = id.filter { it.isLetterOrDigit() }.ifEmpty { null }

    private fun lire(adresse: String): ReponseEspn {
        val c = try {
            URL(adresse).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return ReponseEspn.Echec()
        }
        return try {
            c.connectTimeout = DELAI_MS
            c.readTimeout = DELAI_MS
            c.useCaches = false
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code != 200) ReponseEspn.Echec(code) else ReponseEspn.Ok(c.inputStream.use { texte(it) })
        } catch (e: Exception) {
            // Délai dépassé, réseau coupé, réponse trop grande
            ReponseEspn.Echec()
        } catch (e: OutOfMemoryError) {
            ReponseEspn.Echec()
        } finally {
            c.disconnect()
        }
    }

    private fun texte(entree: InputStream): String {
        val sortie = ByteArrayOutputStream(64 * 1024)
        val tampon = ByteArray(16 * 1024)
        while (true) {
            val n = entree.read(tampon)
            if (n < 0) break
            if (sortie.size() + n > TAILLE_MAX) throw IOException("Réponse trop grande")
            sortie.write(tampon, 0, n)
        }
        return sortie.toString("UTF-8")
    }
}
