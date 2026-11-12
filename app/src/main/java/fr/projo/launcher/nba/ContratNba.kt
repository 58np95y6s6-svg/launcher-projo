package fr.projo.launcher.nba

/*
 * Design « NBA » (docs/design-nba.md) : ce que l'affichage utilise. Les données (ESPN) le
 * remplissent ; l'affichage ne dépend de rien d'autre.
 */

enum class ConferenceNba { EST, OUEST }

enum class DivisionNba(val id: String, val conference: ConferenceNba, val nom: String, val equipes: List<String>) {
    ATLANTIQUE("atlantique", ConferenceNba.EST, "Atlantique", listOf("BOS", "BKN", "NYK", "PHI", "TOR")),
    CENTRALE("centrale", ConferenceNba.EST, "Centrale", listOf("CHI", "CLE", "DET", "IND", "MIL")),
    SUD_EST("sudest", ConferenceNba.EST, "Sud-Est", listOf("ATL", "CHA", "MIA", "ORL", "WAS")),
    NORD_OUEST("nordouest", ConferenceNba.OUEST, "Nord-Ouest", listOf("DEN", "MIN", "OKC", "POR", "UTA")),
    PACIFIQUE("pacifique", ConferenceNba.OUEST, "Pacifique", listOf("GSW", "LAC", "LAL", "PHX", "SAC")),
    SUD_OUEST("sudouest", ConferenceNba.OUEST, "Sud-Ouest", listOf("DAL", "HOU", "MEM", "NOP", "SAS"));

    companion object {
        /** Division d'une équipe (code NBA à 3 lettres), null si inconnue. */
        fun de(code: String): DivisionNba? = entries.firstOrNull { code in it.equipes }

        /** Division par identifiant de la carte (« atlantique »…), null si inconnue. */
        fun parId(id: String): DivisionNba? = entries.firstOrNull { it.id == id }
    }
}

/** Bilan d'une équipe : [serie] +3 = 3 victoires de suite, −2 = 2 défaites ; [dixDerniers] « 7-3 ». */
data class BilanNba(
    val code: String,
    val victoires: Int,
    val defaites: Int,
    val rangConference: Int,
    val rangDivision: Int,
    val serie: Int,
    val dixDerniers: String?,
)

/** Classement des 30 équipes ; [misAJour] en ms (epoch) ; [horsLigne] : dernier rafraîchissement raté. */
data class ClassementNba(val equipes: Map<String, BilanNba>, val misAJour: Long, val horsLigne: Boolean)

/** Match d'une équipe (dernier ou prochain) ; [debut] en ms (epoch). */
data class MatchEquipe(
    val adversaire: String,
    val domicile: Boolean,
    val debut: Long,
    val score: Int?,
    val scoreAdversaire: Int?,
) {
    val victoire: Boolean? get() = if (score == null || scoreAdversaire == null) null else score > scoreAdversaire
}

/** Joueur ; moyennes de la saison quand elles sont connues. */
data class JoueurNba(
    val nom: String,
    val numero: String?,
    val poste: String?,
    val blesse: Boolean,
    val minutes: Double?,
    val points: Double?,
    val rebonds: Double?,
    val passes: Double?,
)

/** Blessure : [statut] déjà traduit (« Absent »…), [zone] traduite si connue (« genou »). */
data class BlessureNba(val nom: String, val statut: String, val zone: String?)

/** Fiche d'une équipe (sélection sur la carte). */
data class FicheEquipe(
    val code: String,
    val nomComplet: String,
    val dernier: MatchEquipe?,
    val prochain: MatchEquipe?,
    val titulaires: List<JoueurNba>,
    val effectif: List<JoueurNba>,
    val blessures: List<BlessureNba>,
)
