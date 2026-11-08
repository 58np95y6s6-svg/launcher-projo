package fr.projo.launcher.boxscore

import java.util.Locale
import kotlin.math.floor

/*
 * Box score NBA par-dessus la vidéo (docs/telecommande.md, « Box score NBA ») : le modèle commun
 * aux deux sources (NBA, ESPN) et le texte du statut en français. Sans Android, testé sur la JVM.
 */

/** Sources des scores : NBA (principale), ESPN (repli). */
enum class SourceNba { NBA, ESPN }

/** Où en est un match. */
enum class EtatMatch { A_VENIR, EN_COURS, TERMINE }

/** Une équipe dans un match : code NBA à 3 lettres, nom donné par la source (« Lakers »), points par période. */
data class EquipeMatch(
    val code: String,
    val nom: String,
    val score: Int,
    /** Points de chaque période jouée (4 quarts-temps, puis prolongations). */
    val periodes: List<Int> = emptyList(),
)

/**
 * Un match, quelle que soit la source. [id] : « EXT-DOM » (« LAL-BOS »), stable d'une source à
 * l'autre ; [idSource] : identifiant propre à la source (gameId NBA, événement ESPN).
 */
data class MatchNba(
    val id: String,
    val idSource: String,
    /** Heure de début (ms, UTC) ; 0 si inconnue. */
    val debut: Long,
    val ext: EquipeMatch,
    val dom: EquipeMatch,
    val etat: EtatMatch,
    /** Période en cours (1 à 4, puis 5 = 1re prolongation…) ; 0 avant le match. */
    val periode: Int,
    /** Temps restant dans la période (s) ; null si inconnu. */
    val horloge: Double?,
    /** Entre deux périodes (fin de quart-temps, mi-temps). */
    val finDePeriode: Boolean,
) {
    val direct: Boolean get() = etat == EtatMatch.EN_COURS
    val statut: String get() = Statuts.texte(etat, periode, horloge, finDePeriode)
}

/** Ligne d'un joueur. [surTerrain] : null si la source ne le dit pas (ESPN). */
data class JoueurNba(
    val nom: String,
    val titulaire: Boolean,
    val surTerrain: Boolean?,
    val aJoue: Boolean,
    val minutes: Int,
    val points: Int,
    val rebonds: Int,
    val passes: Int,
    val tirsReussis: Int,
    val tirsTentes: Int,
    val troisReussis: Int,
    val troisTentes: Int,
    val plusMoins: Int?,
)

/** Statistiques d'une équipe ; pourcentages entre 0 et 1, null si inconnus. */
data class StatsEquipe(
    val pctTirs: Double? = null,
    val pctTrois: Double? = null,
    val rebonds: Int? = null,
    val passes: Int? = null,
    val pertes: Int? = null,
    val tirsReussis: Int? = null,
    val tirsTentes: Int? = null,
    val troisReussis: Int? = null,
    val troisTentes: Int? = null,
) {
    companion object {
        /**
         * Complète [s] avec les totaux des joueurs quand la source ne donne pas une valeur
         * (tirs, 3 points, rebonds, passes ; les pertes ne se déduisent pas).
         */
        fun completer(s: StatsEquipe?, joueurs: List<JoueurNba>): StatsEquipe? {
            val joues = joueurs.filter { it.aJoue }
            if (s == null && joues.isEmpty()) return null
            val b = s ?: StatsEquipe()
            val tr = b.tirsReussis ?: joues.takeIf { it.isNotEmpty() }?.sumOf { it.tirsReussis }
            val tt = b.tirsTentes ?: joues.takeIf { it.isNotEmpty() }?.sumOf { it.tirsTentes }
            val r3 = b.troisReussis ?: joues.takeIf { it.isNotEmpty() }?.sumOf { it.troisReussis }
            val t3 = b.troisTentes ?: joues.takeIf { it.isNotEmpty() }?.sumOf { it.troisTentes }
            return b.copy(
                tirsReussis = tr,
                tirsTentes = tt,
                troisReussis = r3,
                troisTentes = t3,
                pctTirs = b.pctTirs ?: pourcentage(tr, tt),
                pctTrois = b.pctTrois ?: pourcentage(r3, t3),
                rebonds = b.rebonds ?: joues.takeIf { it.isNotEmpty() }?.sumOf { it.rebonds },
                passes = b.passes ?: joues.takeIf { it.isNotEmpty() }?.sumOf { it.passes },
            )
        }

        private fun pourcentage(reussis: Int?, tentes: Int?): Double? =
            if (reussis == null || tentes == null || tentes <= 0) null else reussis.toDouble() / tentes
    }
}

/** Joueurs (titulaires d'abord, dans l'ordre de la source) et statistiques d'une équipe. */
data class EquipeBox(val joueurs: List<JoueurNba>, val stats: StatsEquipe?)

/**
 * Box score d'un match : en-tête ([match] : scores, statut, périodes), puis les deux équipes.
 * [surTerrainConnu] : la source dit qui est sur le terrain (NBA oui, ESPN non).
 */
data class BoxScore(
    val match: MatchNba,
    val ext: EquipeBox,
    val dom: EquipeBox,
    val surTerrainConnu: Boolean,
) {
    companion object {
        /** Box score pas encore publié (« pas encore commencé ») : l'en-tête seul, d'après le tableau du jour. */
        fun depuisMatch(m: MatchNba) = BoxScore(m, EquipeBox(emptyList(), null), EquipeBox(emptyList(), null), surTerrainConnu = false)
    }
}

/** Texte du statut d'un match, en français (docs/telecommande.md, `nba.statut`). */
object Statuts {

    /** « 1er QT », « 2e QT »… ; « Prol. », « 2e prol. »… */
    fun periode(p: Int): String = when {
        p <= 1 -> "1er QT"
        p <= 4 -> "${p}e QT"
        p == 5 -> "Prol."
        else -> "${p - 4}e prol."
    }

    /**
     * « 4e QT · 2:31 », « 1er QT · 0:45.3 » (moins d'une minute : dixièmes), « Fin du 3e QT »,
     * « Mi-temps », « Prol. · 1:12 », « 2e prol. · 3:05 », « Fin de la prol. », « Terminé ».
     */
    fun texte(etat: EtatMatch, periode: Int, horloge: Double?, finDePeriode: Boolean): String = when (etat) {
        EtatMatch.TERMINE -> "Terminé"
        EtatMatch.A_VENIR -> "À venir"
        EtatMatch.EN_COURS -> {
            val p = periode.coerceAtLeast(1)
            when {
                finDePeriode && p == 2 -> "Mi-temps"
                finDePeriode && p <= 4 -> "Fin du ${periode(p)}"
                finDePeriode && p == 5 -> "Fin de la prol."
                finDePeriode -> "Fin de la ${p - 4}e prol."
                horloge == null -> periode(p)
                else -> periode(p) + " · " + horloge(horloge)
            }
        }
    }

    /** 151 → « 2:31 » ; 45.36 → « 0:45.3 » (dixièmes tronqués, jamais « 0:60.0 »). */
    fun horloge(secondes: Double): String {
        val s = secondes.coerceAtLeast(0.0)
        if (s < 60.0) {
            val dixiemes = floor(s * 10.0 + 1e-6).toInt().coerceAtMost(599)
            return String.format(Locale.ROOT, "0:%02d.%d", dixiemes / 10, dixiemes % 10)
        }
        val total = floor(s + 1e-6).toInt()
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
    }

    /**
     * Horloge de la source en secondes : « PT02M31.00S » (NBA), « 2:31 » ou « 45.3 » (ESPN) ;
     * null si vide ou illisible.
     */
    fun lireHorloge(texte: String?): Double? {
        val t = texte?.trim().orEmpty()
        if (t.isEmpty()) return null
        Regex("""^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?$""", RegexOption.IGNORE_CASE).matchEntire(t)?.let { m ->
            if (m.groupValues.drop(1).all { it.isEmpty() }) return null
            val h = m.groupValues[1].toDoubleOrNull() ?: 0.0
            val min = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val s = m.groupValues[3].toDoubleOrNull() ?: 0.0
            return h * 3600 + min * 60 + s
        }
        Regex("""^(\d+):(\d{1,2}(?:\.\d+)?)$""").matchEntire(t)?.let { m ->
            return m.groupValues[1].toDouble() * 60 + m.groupValues[2].toDouble()
        }
        return t.toDoubleOrNull()?.takeIf { it >= 0 && !it.isNaN() && !it.isInfinite() }
    }

    /** Minutes jouées : « PT38M12.00S » → 38, « 38:12 » → 38, « 38 » → 38 ; 0 si illisible. */
    fun minutes(texte: String?): Int {
        val t = texte?.trim().orEmpty()
        if (t.isEmpty()) return 0
        if (t.startsWith("PT", ignoreCase = true)) return ((lireHorloge(t) ?: 0.0) / 60.0).toInt()
        if (':' in t) return t.substringBefore(':').trim().toIntOrNull() ?: 0
        return t.toDoubleOrNull()?.toInt() ?: 0
    }
}
