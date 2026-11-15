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
 * objet BILANS) et ses rangs, calculés comme elle ; des fiches réalistes du Thunder, des Spurs
 * et des Celtics (leurs 3 derniers matchs, le prochain, le cinq de départ).
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

    private fun m(adversaire: String, domicile: Boolean, jour: Int, heure: Int, minute: Int, score: Int? = null, eux: Int? = null) =
        MatchEquipe(adversaire, domicile, utc(Calendar.NOVEMBER, jour, heure, minute), score, eux)

    /** Fiche du Thunder : battu à Portland le 11 novembre, à LA Clippers le 14 (4:30 à Paris). */
    val ficheOkc = FicheEquipe(
        code = "OKC",
        nomComplet = "Oklahoma City Thunder",
        derniers = listOf(
            m("POR", domicile = false, 11, 3, 0, 108, 118),
            m("DEN", domicile = true, 9, 1, 0, 121, 112),
            m("MEM", domicile = true, 7, 1, 0, 115, 101),
        ),
        prochain = m("LAC", domicile = false, 14, 3, 30),
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

    /** Fiche des Spurs : battus à Chicago le 12 novembre (heure de Paris), contre Golden State le 14 (3:30). */
    val ficheSas = FicheEquipe(
        code = "SAS",
        nomComplet = "San Antonio Spurs",
        derniers = listOf(
            m("CHI", domicile = false, 12, 1, 0, 119, 124),
            m("PHX", domicile = false, 10, 2, 0, 122, 96),
            m("POR", domicile = false, 8, 3, 0, 125, 103),
        ),
        prochain = m("GSW", domicile = true, 14, 2, 30),
        titulaires = listOf(
            j("D. Fox", "4", "MJ", 33.6, 23.4, 4.1, 6.3),
            j("S. Castle", "5", "AR", 30.9, 15.2, 4.4, 4.8),
            j("D. Vassell", "24", "AR", 28.7, 13.1, 3.6, 2.5),
            j("H. Barnes", "40", "AI", 26.4, 10.4, 4.2, 1.9),
            j("V. Wembanyama", "1", "P", 33.2, 30.3, 11.7, 3.6),
        ),
        effectif = listOf(
            j("V. Wembanyama", "1", "P"), j("D. Fox", "4", "MJ"), j("S. Castle", "5", "AR"), j("D. Vassell", "24", "AR"),
            j("H. Barnes", "40", "AI"), j("K. Johnson", "3", "AI"), j("J. Sochan", "10", "AF"), j("D. Harper", "2", "MJ"),
            j("L. Kornet", "7", "P", blesse = true), j("B. Wesley", "14", "AR"),
        ),
        blessures = listOf(BlessureNba("L. Kornet", "Au jour le jour", "cheville")),
    )

    /** Fiche des Celtics : battus à New York le 12 novembre, contre Miami le 14 (1:30). */
    val ficheBos = FicheEquipe(
        code = "BOS",
        nomComplet = "Boston Celtics",
        derniers = listOf(
            m("NYK", domicile = false, 12, 0, 30, 108, 112),
            m("TOR", domicile = true, 10, 0, 30, 121, 104),
            m("ORL", domicile = false, 8, 0, 0, 117, 109),
        ),
        prochain = m("MIA", domicile = true, 14, 0, 30),
        titulaires = listOf(
            j("J. Brown", "7", "AR", 35.1, 27.6, 6.2, 4.4),
            j("D. White", "9", "MJ", 32.8, 17.9, 4.5, 5.1),
            j("P. Pritchard", "11", "MJ", 29.4, 15.3, 3.6, 3.9),
            j("S. Hauser", "30", "AI", 25.2, 9.6, 3.9, 1.4),
            j("N. Queta", "88", "P", 24.7, 8.8, 8.9, 1.2),
        ),
        effectif = listOf(
            j("J. Brown", "7", "AR"), j("D. White", "9", "MJ"), j("P. Pritchard", "11", "MJ"), j("S. Hauser", "30", "AI"),
            j("N. Queta", "88", "P"), j("J. Tatum", "0", "AI", blesse = true), j("A. Simons", "4", "AR"), j("J. Walsh", "27", "AI"),
        ),
        blessures = listOf(BlessureNba("J. Tatum", "Absent", "tendon d'Achille")),
    )

    /** Toutes les fiches factices, par équipe. */
    val fiches: Map<String, FicheEquipe> = mapOf("OKC" to ficheOkc, "SAS" to ficheSas, "BOS" to ficheBos)
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

    /** Fiches demandées, dans l'ordre. */
    val demandes = mutableListOf<String>()

    override fun suivre(actif: Boolean) {
        suivis += actif
    }

    override suspend fun fiche(code: String): FicheEquipe? {
        demandes += code
        if (chargementSansFin) awaitCancellation()
        return fiches[code]
    }

    override fun logoEnMemoire(code: String): ImageBitmap? = logos(code)

    override fun logo(code: String): ImageBitmap? = logos(code)

    override val synchrone: Boolean get() = true
}
