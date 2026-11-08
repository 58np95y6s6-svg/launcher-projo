package fr.projo.launcher.boxscore

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Requêtes vers les sources de scores (remplacées par un faux dans les tests). */
internal interface ChargeurNba {
    fun tableau(source: SourceNba): SuiviNba.Reponse<List<MatchNba>>
    fun box(source: SourceNba, match: MatchNba): SuiviNba.Reponse<BoxScore>
}

/**
 * Requêtes vers les flux publics (sans compte ni clé) : délai de 8 s, en-tête `Accept:
 * application/json` et rien d'autre. Appels bloquants, hors du fil principal. Aucune adresse
 * n'est écrite dans les journaux.
 */
internal object ReseauNba : ChargeurNba {

    private const val NBA_TABLEAU = "https://cdn.nba.com/static/json/liveData/scoreboard/todaysScoreboard_00.json"
    private const val NBA_BOX = "https://cdn.nba.com/static/json/liveData/boxscore/boxscore_"
    private const val ESPN_TABLEAU = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard"
    private const val ESPN_RESUME = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event="

    private const val DELAI_MS = 8_000

    /** Un box score fait une centaine de Ko : au-delà de 4 Mo, la réponse est refusée. */
    private const val TAILLE_MAX = 4 * 1024 * 1024

    override fun tableau(source: SourceNba): SuiviNba.Reponse<List<MatchNba>> = when (source) {
        SourceNba.NBA -> lire(NBA_TABLEAU, pasCommence = false) { LectureNba.tableauNba(it) }
        SourceNba.ESPN -> lire(ESPN_TABLEAU, pasCommence = false) { LectureNba.tableauEspn(it) }
    }

    override fun box(source: SourceNba, match: MatchNba): SuiviNba.Reponse<BoxScore> {
        val id = match.idSource.filter { it.isLetterOrDigit() }
        if (id.isEmpty()) return SuiviNba.Reponse.Echec()
        return when (source) {
            // Avant l'entre-deux, le box score NBA répond 403 ou 404 : « pas encore commencé »
            SourceNba.NBA -> lire("$NBA_BOX$id.json", pasCommence = true) { LectureNba.boxScoreNba(it, match) }
            SourceNba.ESPN -> lire(ESPN_RESUME + URLEncoder.encode(id, "UTF-8"), pasCommence = false) { LectureNba.boxScoreEspn(it, match) }
        }
    }

    private fun <T> lire(adresse: String, pasCommence: Boolean, lecture: (String) -> T): SuiviNba.Reponse<T> {
        val c = try {
            URL(adresse).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return SuiviNba.Reponse.Echec()
        }
        return try {
            c.connectTimeout = DELAI_MS
            c.readTimeout = DELAI_MS
            c.useCaches = false
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            when {
                pasCommence && (code == 403 || code == 404) -> SuiviNba.Reponse.PasCommence
                code != 200 -> SuiviNba.Reponse.Echec(code)
                else -> SuiviNba.Reponse.Ok(lecture(c.inputStream.use { texte(it) }))
            }
        } catch (e: Exception) {
            // Délai dépassé, réseau coupé, JSON illisible
            SuiviNba.Reponse.Echec()
        } catch (e: OutOfMemoryError) {
            SuiviNba.Reponse.Echec()
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
