package fr.projo.launcher.modules.sport

import android.content.Context
import fr.projo.launcher.data.NbaData
import fr.projo.launcher.data.NbaGame
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.modules.DonneesModules
import fr.projo.launcher.modules.EtatDonnees
import fr.projo.launcher.modules.HoteModules
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Une compétition du module Sport, lue sur les tableaux des scores publics d'ESPN (sans clé) :
 * https://site.api.espn.com/apis/site/v2/sports/<chemin>/scoreboard
 */
class Competition(
    /** Identifiant enregistré dans les réglages (ne pas changer). */
    val id: String,
    /** Sport (titre de section dans les réglages). */
    val sport: String,
    val nom: String,
    /** Libellé d'onglet, en capitales et court. */
    val court: String,
    /** Chemins ESPN (plusieurs pour l'équipe de France : amicaux, Ligue des nations…). */
    val chemins: List<String>,
    /** Ne garder que les matchs de cette équipe (nom complet ESPN). */
    val equipe: String? = null,
)

/**
 * Compétitions proposées, par sport. Identifiants ESPN :
 *  - basket : basketball/nba ;
 *  - football : soccer/fra.1 (Ligue 1), soccer/uefa.champions, soccer/eng.1, soccer/esp.1,
 *    soccer/fra.coupe_de_france ; équipe de France : soccer/fifa.friendly, uefa.nations,
 *    fifa.worldq.uefa, uefa.euroq filtrés sur « France » ;
 *  - rugby (identifiants numériques) : 270559 = Top 14, 180659 = Six Nations,
 *    271937 = Champions Cup européenne.
 */
val COMPETITIONS: List<Competition> = listOf(
    Competition("nba", "Basket", "NBA", "NBA", listOf("basketball/nba")),
    Competition("fra.1", "Football", "Ligue 1", "LIGUE 1", listOf("soccer/fra.1")),
    Competition("uefa.champions", "Football", "Ligue des champions", "LDC", listOf("soccer/uefa.champions")),
    Competition("eng.1", "Football", "Premier League", "PREMIER LEAGUE", listOf("soccer/eng.1")),
    Competition("esp.1", "Football", "Liga", "LIGA", listOf("soccer/esp.1")),
    Competition("fra.coupe_de_france", "Football", "Coupe de France", "COUPE DE FRANCE", listOf("soccer/fra.coupe_de_france")),
    Competition(
        "bleus", "Football", "Équipe de France", "BLEUS",
        listOf("soccer/fifa.friendly", "soccer/uefa.nations", "soccer/fifa.worldq.uefa", "soccer/uefa.euroq"),
        equipe = "France",
    ),
    Competition("top14", "Rugby", "Top 14", "TOP 14", listOf("rugby/270559")),
    Competition("six.nations", "Rugby", "Six Nations", "6 NATIONS", listOf("rugby/180659")),
    Competition("champions.cup", "Rugby", "Champions Cup", "CHAMPIONS CUP", listOf("rugby/271937")),
)

/** Par défaut : NBA seule (le bandeau d'origine). */
val COMPETITIONS_PAR_DEFAUT = listOf("nba")

/** Matchs d'une compétition. Pour la NBA, [nba] garde les deux onglets d'origine. */
class MatchsCompetition(val jeux: List<NbaGame>, val nba: NbaData? = null) {
    val enDirect: Boolean get() = jeux.any { it.state == "in" }
}

/** Chargement des matchs (dépôt NBA partagé : mêmes logos en cache pour tous les sports). */
object SportReseau {

    @Volatile private var depot: NbaRepository? = null

    /** Reprend le dépôt NBA de l'application (cache de logos commun). */
    fun adopter(repo: NbaRepository) {
        if (depot == null) depot = repo
    }

    fun depot(context: Context): NbaRepository =
        depot ?: synchronized(this) { depot ?: NbaRepository(context.applicationContext).also { depot = it } }

    /** Hôte de repli de NbaPanel : module Sport (NBA) avec les données NBA fournies. */
    fun hoteNba(data: NbaData?, erreur: Boolean) = HoteModules(
        choix = listOf(ModuleSport.id),
        donnees = DonneesModules(factices = { k ->
            if (k == cle(COMPETITIONS[0])) {
                EtatDonnees(data?.let { matchsNba(it) }, erreur = data == null && erreur, date = System.currentTimeMillis())
            } else null
        }),
    )

    fun cle(c: Competition) = "sport:" + c.id

    fun matchsNba(d: NbaData) = MatchsCompetition(
        (d.results?.games.orEmpty() + d.tonight.games).distinctBy { it.id }.sortedBy { it.start },
        d,
    )

    /** À appeler hors du thread principal. */
    fun charger(context: Context, c: Competition): MatchsCompetition {
        val repo = depot(context)
        if (c.id == "nba") return matchsNba(repo.fetchAll())
        // Fenêtre : 2 jours en arrière (résultats), 6 jours en avant (prochains matchs)
        val paris = TimeZone.getTimeZone("Europe/Paris")
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = paris }
        val cal = Calendar.getInstance(paris)
        cal.add(Calendar.DAY_OF_MONTH, -2)
        val debut = fmt.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 8)
        val fin = fmt.format(cal.time)
        var reussites = 0
        var derniereErreur: Exception? = null
        val jeux = ArrayList<NbaGame>()
        for (chemin in c.chemins) {
            try {
                jeux += repo.fetchScoreboard(chemin, "$debut-$fin")
                reussites++
            } catch (e: Exception) {
                derniereErreur = e
            }
        }
        if (reussites == 0) throw derniereErreur ?: IllegalStateException("source indisponible")
        val maintenant = System.currentTimeMillis()
        val gardes = jeux
            .filter { c.equipe == null || it.home.nom == c.equipe || it.away.nom == c.equipe }
            .filter { it.state == "in" || (it.start > maintenant - 2 * JOUR && it.start < maintenant + 7 * JOUR) }
            .distinctBy { it.id }
            .sortedBy { it.start }
        return MatchsCompetition(gardes)
    }

    /** Prochain rafraîchissement : 1 min en direct, juste avant un coup d'envoi proche, sinon 10 à 15 min. */
    fun delai(c: Competition, m: MatchsCompetition): Long {
        if (m.enDirect) return 60_000L
        if (c.id == "nba") return 10 * 60_000L
        val maintenant = System.currentTimeMillis()
        val prochain = m.jeux.filter { it.state == "pre" && it.start > maintenant }.minOfOrNull { it.start }
        val avantDebut = prochain?.let { it - maintenant + 30_000L }
        return if (avantDebut != null && avantDebut < 15 * 60_000L) maxOf(60_000L, avantDebut) else 15 * 60_000L
    }

    private const val JOUR = 86_400_000L
}
