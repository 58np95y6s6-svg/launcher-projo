package fr.projo.launcher.ui

import fr.projo.launcher.nba.BilanNba
import fr.projo.launcher.nba.ConferenceNba
import fr.projo.launcher.nba.DivisionNba
import fr.projo.launcher.nba.MatchEquipe
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/*
 * Design « NBA » : textes affichés (rangs, saison, bilans, matchs), en français et à l'heure locale.
 */
object TextesNba {
    private val FR = Locale.FRENCH

    /** Rang : 1 → (« 1 », « er »), 6 → (« 6 », « e ») ; le suffixe s'écrit en exposant. */
    fun rang(n: Int): Pair<String, String> = n.toString() to if (n == 1) "er" else "e"

    /** Rang à plat : « 1er », « 6e ». */
    fun rangTexte(n: Int): String = rang(n).let { it.first + it.second }

    /** « EST » / « OUEST ». */
    fun conference(c: ConferenceNba): String = if (c == ConferenceNba.EST) "EST" else "OUEST"

    /** Ligne des rangs d'une pastille, à plat : « 1er OUEST · 1er DIV. ». */
    fun ligneRangs(b: BilanNba): String {
        val c = DivisionNba.de(b.code)?.conference ?: return ""
        return "${rangTexte(b.rangConference)} ${conference(c)} · ${rangTexte(b.rangDivision)} DIV."
    }

    /** Rangs de la fiche, à plat : « 6e OUEST · 2e NORD-OUEST ». */
    fun rangsFiche(b: BilanNba): String {
        val d = DivisionNba.de(b.code) ?: return ""
        return "${rangTexte(b.rangConference)} ${conference(d.conference)} · ${rangTexte(b.rangDivision)} ${d.nom.uppercase(FR)}"
    }

    /** « 12–1 » ; sans bilan : « –– ». */
    fun bilan(b: BilanNba?): String = if (b == null) "––" else "${b.victoires}–${b.defaites}"

    /**
     * Saison en cours à la date [t] : d'octobre à décembre, celle qui commence (« 2026-27 ») ;
     * de janvier à septembre, celle commencée l'année d'avant.
     */
    fun saison(t: Long, fuseau: TimeZone = TimeZone.getDefault()): String {
        val c = Calendar.getInstance(fuseau).apply { timeInMillis = t }
        val an = c.get(Calendar.YEAR)
        val debut = if (c.get(Calendar.MONTH) >= Calendar.OCTOBER) an else an - 1
        return "$debut-" + ((debut + 1) % 100).toString().padStart(2, '0')
    }

    /** Série : « 3 victoires de suite », « 2 défaites de suite », « Victoire au dernier match »… ; aucune : null. */
    fun serie(n: Int): String? = when {
        n >= 2 -> "$n victoires de suite"
        n == 1 -> "Victoire au dernier match"
        n <= -2 -> "${-n} défaites de suite"
        n == -1 -> "Défaite au dernier match"
        else -> null
    }

    /** « 7-3 » → « 7–3 sur les 10 derniers » ; illisible : null. */
    fun dixDerniers(s: String?): String? {
        val m = s?.trim()?.let { Regex("^(\\d+)\\s*[-–]\\s*(\\d+)$").find(it) } ?: return null
        return "${m.groupValues[1]}–${m.groupValues[2]} sur les 10 derniers"
    }

    /** « il y a 4 min » (« à l'instant », « il y a 2 h ») : bilans mis à jour à [misAJour]. */
    fun depuis(misAJour: Long, maintenant: Long): String {
        val min = ((maintenant - misAJour) / 60_000L).coerceAtLeast(0L)
        return when {
            min < 1 -> "à l'instant"
            min < 60 -> "il y a $min min"
            else -> "il y a ${min / 60} h"
        }
    }

    /** « 21:12 » (même jour) ou « 11 nov. » : heure des derniers bilans connus (hors ligne). */
    fun heureBilans(misAJour: Long, maintenant: Long, fuseau: TimeZone = TimeZone.getDefault()): String {
        val a = Calendar.getInstance(fuseau).apply { timeInMillis = misAJour }
        val b = Calendar.getInstance(fuseau).apply { timeInMillis = maintenant }
        val memeJour = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return if (memeJour) "de " + format("HH:mm", misAJour, fuseau) else "du " + format("d MMM", misAJour, fuseau)
    }

    /** « ven. 14 nov. » */
    fun jour(t: Long, fuseau: TimeZone = TimeZone.getDefault()): String = format("EEE d MMM", t, fuseau)

    /** « 4:30 » (heure locale, sans zéro devant). */
    fun heure(t: Long, fuseau: TimeZone = TimeZone.getDefault()): String = format("H:mm", t, fuseau)

    /** « MERCREDI 12 NOVEMBRE » */
    fun dateEnTete(t: Long, fuseau: TimeZone = TimeZone.getDefault()): String = format("EEEE d MMMM", t, fuseau).uppercase(FR)

    /** « 21:47 » */
    fun horloge(t: Long, fuseau: TimeZone = TimeZone.getDefault()): String = format("HH:mm", t, fuseau)

    private fun format(motif: String, t: Long, fuseau: TimeZone): String =
        SimpleDateFormat(motif, FR).apply { timeZone = fuseau }.format(Date(t))

    /** « à Portland » (à l'extérieur) ou « contre Portland » (à domicile). */
    fun lieu(m: MatchEquipe): String = (if (m.domicile) "contre " else "à ") + ville(m.adversaire)

    /** Dernier match : (« Défaite », « 108–118 à Portland · mar. 11 nov. ») ; score inconnu : (null, …). */
    fun dernier(m: MatchEquipe, fuseau: TimeZone = TimeZone.getDefault()): Pair<String?, String> {
        val issue = when (m.victoire) {
            true -> "Victoire"
            false -> "Défaite"
            null -> null
        }
        val score = if (m.score != null && m.scoreAdversaire != null) "${m.score}–${m.scoreAdversaire} " else ""
        return issue to "$score${lieu(m)} · ${jour(m.debut, fuseau)}"
    }

    /** Prochain match : « ven. 14 nov. · 4:30 · à LA Clippers ». */
    fun prochain(m: MatchEquipe, fuseau: TimeZone = TimeZone.getDefault()): String =
        "${jour(m.debut, fuseau)} · ${heure(m.debut, fuseau)} · ${lieu(m)}"

    /** Moyenne avec une décimale, virgule française (« 32,1 ») ; inconnue : « – ». */
    fun moyenne(v: Double?): String = if (v == null || v.isNaN()) "–" else String.format(FR, "%.1f", v)

    /** Nom court d'une équipe adverse, comme on le dit : « Portland », « LA Clippers ». */
    fun ville(code: String): String = VILLES[code] ?: code

    /** Nom complet (en attendant la fiche) : « Oklahoma City Thunder ». */
    fun nomComplet(code: String): String = NOMS[code] ?: code

    private val VILLES = mapOf(
        "ATL" to "Atlanta", "BOS" to "Boston", "BKN" to "Brooklyn", "CHA" to "Charlotte", "CHI" to "Chicago",
        "CLE" to "Cleveland", "DAL" to "Dallas", "DEN" to "Denver", "DET" to "Detroit", "GSW" to "Golden State",
        "HOU" to "Houston", "IND" to "Indiana", "LAC" to "LA Clippers", "LAL" to "LA Lakers", "MEM" to "Memphis",
        "MIA" to "Miami", "MIL" to "Milwaukee", "MIN" to "Minnesota", "NOP" to "New Orleans", "NYK" to "New York",
        "OKC" to "Oklahoma City", "ORL" to "Orlando", "PHI" to "Philadelphie", "PHX" to "Phoenix", "POR" to "Portland",
        "SAC" to "Sacramento", "SAS" to "San Antonio", "TOR" to "Toronto", "UTA" to "Utah", "WAS" to "Washington",
    )

    private val NOMS = mapOf(
        "ATL" to "Atlanta Hawks", "BOS" to "Boston Celtics", "BKN" to "Brooklyn Nets", "CHA" to "Charlotte Hornets",
        "CHI" to "Chicago Bulls", "CLE" to "Cleveland Cavaliers", "DAL" to "Dallas Mavericks", "DEN" to "Denver Nuggets",
        "DET" to "Detroit Pistons", "GSW" to "Golden State Warriors", "HOU" to "Houston Rockets", "IND" to "Indiana Pacers",
        "LAC" to "LA Clippers", "LAL" to "Los Angeles Lakers", "MEM" to "Memphis Grizzlies", "MIA" to "Miami Heat",
        "MIL" to "Milwaukee Bucks", "MIN" to "Minnesota Timberwolves", "NOP" to "New Orleans Pelicans",
        "NYK" to "New York Knicks", "OKC" to "Oklahoma City Thunder", "ORL" to "Orlando Magic",
        "PHI" to "Philadelphia 76ers", "PHX" to "Phoenix Suns", "POR" to "Portland Trail Blazers",
        "SAC" to "Sacramento Kings", "SAS" to "San Antonio Spurs", "TOR" to "Toronto Raptors", "UTA" to "Utah Jazz",
        "WAS" to "Washington Wizards",
    )
}
