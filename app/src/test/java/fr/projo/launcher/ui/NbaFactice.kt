package fr.projo.launcher.ui

import androidx.compose.ui.graphics.ImageBitmap
import fr.projo.launcher.nba.BilanNba
import fr.projo.launcher.nba.BlessureNba
import fr.projo.launcher.nba.ClassementNba
import fr.projo.launcher.nba.ConferenceNba
import fr.projo.launcher.nba.DivisionNba
import fr.projo.launcher.nba.FicheEquipe
import fr.projo.launcher.nba.JoueurNba
import fr.projo.launcher.nba.MatchEquipe
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Calendar
import java.util.TimeZone

/**
 * Données NBA factices des tests (aucun réseau) : les bilans de la maquette (maquette2.html,
 * objet BILANS) et ses rangs, calculés comme elle ; une fiche réaliste du Thunder.
 */
object NbaFactice {

    /** Bilans de la maquette : victoires, défaites. */
    val BILANS: Map<String, Pair<Int, Int>> = linkedMapOf(
        "BOS" to (10 to 3), "NYK" to (9 to 4), "CLE" to (11 to 2), "MIL" to (7 to 6), "ORL" to (8 to 5), "MIA" to (7 to 6),
        "PHI" to (6 to 7), "IND" to (6 to 6), "ATL" to (7 to 6), "DET" to (8 to 5), "CHI" to (5 to 8), "TOR" to (4 to 9),
        "BKN" to (3 to 10), "CHA" to (4 to 8), "WAS" to (2 to 10),
        "OKC" to (12 to 1), "DEN" to (9 to 4), "MIN" to (8 to 5), "HOU" to (9 to 4), "DAL" to (7 to 6), "LAL" to (8 to 5),
        "GSW" to (9 to 3), "LAC" to (6 to 7), "PHX" to (7 to 6), "SAC" to (5 to 8), "MEM" to (7 to 6), "SAS" to (6 to 7),
        "NOP" to (3 to 10), "POR" to (4 to 9), "UTA" to (2 to 11),
    )

    /** Séries et 10 derniers matchs (inventés) de quelques équipes. */
    private val SERIES = mapOf("OKC" to 3, "CLE" to 5, "BOS" to -1, "WAS" to -6, "UTA" to -4, "GSW" to 2)

    /** 12 novembre 2026 à 21:47 (heure de [fuseau]) : l'heure de la maquette. */
    fun heureMaquette(fuseau: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(fuseau).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 12, 21, 47, 0)
        }.timeInMillis

    /**
     * Classement de la maquette : rang de conférence par pourcentage de victoires puis victoires
     * (ordre des divisions de la maquette en cas d'égalité), rang de division de même.
     */
    fun classement(misAJour: Long, horsLigne: Boolean = false): ClassementNba {
        val pct = { c: String -> BILANS.getValue(c).let { it.first.toDouble() / (it.first + it.second) } }
        val ordre = compareByDescending<String> { pct(it) }.thenByDescending { BILANS.getValue(it).first }
        val rangConf = HashMap<String, Int>()
        val rangDiv = HashMap<String, Int>()
        for (conf in ConferenceNba.entries) {
            val divisions = DivisionNba.entries.filter { it.conference == conf }
            divisions.flatMap { it.equipes }.sortedWith(ordre).forEachIndexed { i, c -> rangConf[c] = i + 1 }
            for (d in divisions) d.equipes.sortedWith(ordre).forEachIndexed { i, c -> rangDiv[c] = i + 1 }
        }
        val equipes = BILANS.mapValues { (c, b) ->
            val serie = SERIES[c] ?: if (b.first >= b.second) 1 else -1
            BilanNba(c, b.first, b.second, rangConf.getValue(c), rangDiv.getValue(c), serie, if (b.first + b.second >= 10) "${minOf(b.first, 7)}-${10 - minOf(b.first, 7)}" else null)
        }
        return ClassementNba(equipes, misAJour, horsLigne)
    }

    private fun utc(mois: Int, jour: Int, heure: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, mois, jour, heure, minute, 0)
        }.timeInMillis

    private fun j(nom: String, numero: String, poste: String, min: Double? = null, pts: Double? = null, reb: Double? = null, pd: Double? = null, blesse: Boolean = false) =
        JoueurNba(nom, numero, poste, blesse, min, pts, reb, pd)

    /** Fiche du Thunder : battu à Portland le 11 novembre, à LA Clippers le 14 (4:30 à Paris). */
    val ficheOkc = FicheEquipe(
        code = "OKC",
        nomComplet = "Oklahoma City Thunder",
        dernier = MatchEquipe("POR", domicile = false, debut = utc(Calendar.NOVEMBER, 11, 3, 0), score = 108, scoreAdversaire = 118),
        prochain = MatchEquipe("LAC", domicile = false, debut = utc(Calendar.NOVEMBER, 14, 3, 30), score = null, scoreAdversaire = null),
        titulaires = listOf(
            j("S. Gilgeous-Alexander", "2", "MJ", 34.1, 31.8, 5.2, 6.4),
            j("J. Williams", "8", "AI", 32.4, 21.3, 5.1, 5.0),
            j("L. Dort", "5", "AR", 30.2, 11.4, 4.1, 1.6),
            j("C. Holmgren", "7", "AF", 29.8, 17.6, 8.5, 2.3),
            j("I. Hartenstein", "55", "P", 27.5, 10.2, 10.8, 3.7),
        ),
        effectif = listOf(
            j("S. Gilgeous-Alexander", "2", "MJ"), j("J. Williams", "8", "AI"), j("L. Dort", "5", "AR"),
            j("C. Holmgren", "7", "AF"), j("I. Hartenstein", "55", "P"), j("A. Caruso", "9", "AR"),
            j("C. Wallace", "22", "MJ"), j("I. Joe", "11", "AR"), j("A. Wiggins", "21", "AR"),
            j("A. Mitchell", "25", "MJ", blesse = true), j("Jay. Williams", "6", "AI", blesse = true), j("O. Dieng", "3", "AI"),
            j("K. Williams", "34", "AI"), j("N. Topić", "44", "MJ", blesse = true), j("B. Fleming", "13", "P"),
        ),
        blessures = listOf(
            BlessureNba("Jay. Williams", "Absent", "ischios"),
            BlessureNba("N. Topić", "Absent", "genou"),
            BlessureNba("A. Mitchell", "Incertain", "cheville"),
        ),
    )
}

/**
 * Source NBA des tests : tout est lu tout de suite ([synchrone]), sans réseau ni autre fil ;
 * [fiches] : fiche de chaque équipe (absente : indisponible) ; [chargementSansFin] : la fiche
 * ne vient jamais (squelette).
 */
class SourceNbaFactice(
    classement: ClassementNba?,
    override val horloge: Long?,
    private val fiches: Map<String, FicheEquipe> = mapOf("OKC" to NbaFactice.ficheOkc),
    private val chargementSansFin: Boolean = false,
    private val logos: (String) -> ImageBitmap? = { null },
) : SourceNba {
    override val classement = MutableStateFlow(classement)

    /** Appels à suivre(), dans l'ordre. */
    val suivis = mutableListOf<Boolean>()

    override fun suivre(actif: Boolean) {
        suivis += actif
    }

    override suspend fun fiche(code: String): FicheEquipe? {
        if (chargementSansFin) awaitCancellation()
        return fiches[code]
    }

    override fun logoEnMemoire(code: String): ImageBitmap? = logos(code)

    override fun logo(code: String): ImageBitmap? = logos(code)

    override val synchrone: Boolean get() = true
}
