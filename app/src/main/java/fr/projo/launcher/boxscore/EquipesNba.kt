package fr.projo.launcher.boxscore

/**
 * Les 30 équipes NBA : nom court, couleurs du badge (cercle de la couleur de l'équipe avec son
 * code, aucun logo) et [accent], couleur claire de l'équipe pour les chiffres écrits sur le verre
 * sombre (contraste d'au moins 4,5:1 sur #0c0e14, vérifié par les tests).
 *
 * Couleurs officielles, éclaircies quand elles se perdraient sur le panneau sombre (bleu marine,
 * noir) ; Nets et Spurs en badge clair. Code inconnu : badge gris.
 */
object EquipesNba {

    /** Couleurs d'une équipe (`#rrggbb`) : [couleur] fond du badge, barres et onglet ; [texte] lettres du badge. */
    data class Equipe(val code: String, val nom: String, val couleur: String, val texte: String, val accent: String)

    private val liste = listOf(
        Equipe("ATL", "Hawks", "#e03a3e", "#ffffff", "#c8d64a"),
        Equipe("BOS", "Celtics", "#0d8a4f", "#ffffff", "#7be0a8"),
        Equipe("BKN", "Nets", "#ffffff", "#000000", "#e6e6e6"),
        Equipe("CHA", "Hornets", "#00788c", "#ffffff", "#5ed0e0"),
        Equipe("CHI", "Bulls", "#ce1141", "#ffffff", "#ff7a8f"),
        Equipe("CLE", "Cavaliers", "#860038", "#fdbb30", "#fdbb30"),
        Equipe("DAL", "Mavericks", "#0064b1", "#ffffff", "#72b7f2"),
        Equipe("DEN", "Nuggets", "#1d428a", "#fec524", "#fec524"),
        Equipe("DET", "Pistons", "#c8102e", "#ffffff", "#ff8091"),
        Equipe("GSW", "Warriors", "#1d428a", "#ffc72c", "#ffc72c"),
        Equipe("HOU", "Rockets", "#ce1141", "#ffffff", "#ff7d8f"),
        Equipe("IND", "Pacers", "#1d4e9b", "#fdbb30", "#fdbb30"),
        Equipe("LAC", "Clippers", "#c8102e", "#ffffff", "#7fa8f0"),
        Equipe("LAL", "Lakers", "#6c3fb5", "#f2b631", "#f2b631"),
        Equipe("MEM", "Grizzlies", "#5d76a9", "#ffffff", "#a9bde6"),
        Equipe("MIA", "Heat", "#98002e", "#f9a01b", "#f9a01b"),
        Equipe("MIL", "Bucks", "#1d6b3a", "#eee1c6", "#eee1c6"),
        Equipe("MIN", "Timberwolves", "#236192", "#ffffff", "#8fd14f"),
        Equipe("NOP", "Pelicans", "#1f3f73", "#c9ad7a", "#d8bf8f"),
        Equipe("NYK", "Knicks", "#006bb6", "#ffffff", "#f58426"),
        Equipe("OKC", "Thunder", "#007ac1", "#ffffff", "#ff7a5c"),
        Equipe("ORL", "Magic", "#0077c0", "#ffffff", "#6cc3ff"),
        Equipe("PHI", "76ers", "#006bb6", "#ffffff", "#ff6b86"),
        Equipe("PHX", "Suns", "#e56020", "#1d1160", "#ff9a5c"),
        Equipe("POR", "Trail Blazers", "#e03a3e", "#ffffff", "#ff8285"),
        Equipe("SAC", "Kings", "#6b3fa0", "#ffffff", "#c7a6f2"),
        Equipe("SAS", "Spurs", "#c4ced4", "#000000", "#c4ced4"),
        Equipe("TOR", "Raptors", "#ce1141", "#ffffff", "#ff7088"),
        Equipe("UTA", "Jazz", "#f9a01b", "#000000", "#f9a01b"),
        Equipe("WAS", "Wizards", "#e31837", "#ffffff", "#ff8095"),
    )

    private val parCode = liste.associateBy { it.code }

    /** Toutes les équipes connues. */
    val toutes: List<Equipe> get() = liste

    /** Couleurs d'une équipe inconnue (match amical, All-Star…). */
    fun inconnue(code: String) = Equipe(code, "", "#4a4f5c", "#ffffff", "#d0d4dc")

    fun de(code: String): Equipe = parCode[code] ?: inconnue(code)

    fun connue(code: String): Boolean = code in parCode

    /** Codes ESPN qui diffèrent des codes NBA. */
    private val codesEspn = mapOf("GS" to "GSW", "NY" to "NYK", "SA" to "SAS", "NO" to "NOP", "UTAH" to "UTA", "WSH" to "WAS")

    /** Code de la source (ESPN ou NBA) → code NBA à 3 lettres, en majuscules. */
    fun codeNba(code: String): String {
        val c = code.trim().uppercase()
        return codesEspn[c] ?: c
    }
}
