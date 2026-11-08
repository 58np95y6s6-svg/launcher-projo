package fr.projo.launcher.boxscore

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Lecture des flux publics (docs/telecommande.md, « Sources (TV) ») vers le modèle commun :
 *  - NBA : tableau du jour (`scoreboard.games[]`) et box score (`game`) ;
 *  - ESPN : tableau (`events[]`) et résumé (`boxscore`, `header`).
 *
 * Tolérant : champ absent ou null, nombre écrit en texte, `turnovers` ou `turnoversTotal`,
 * pourcentage entre 0 et 1 ou entre 0 et 100, horloge vide, code d'équipe inconnu. Un match ou
 * un joueur illisible est sauté sans gêner les autres. Un document sans la partie attendue lève
 * [FormatInconnu] (compté comme un échec de la source).
 */
object LectureNba {

    class FormatInconnu(message: String) : Exception(message)

    // ------------------------------------------------------------------ NBA

    /** Tableau du jour NBA → matchs (à venir, en cours, terminés). */
    fun tableauNba(json: String): List<MatchNba> {
        val jeux = JSONObject(json).optJSONObject("scoreboard")?.optJSONArray("games") ?: throw FormatInconnu("scoreboard.games")
        return objets(jeux).mapNotNull { g -> sansErreur { matchNba(g) } }
    }

    /** Box score NBA d'un match ; [repli] : le match du tableau (heure de début). */
    fun boxScoreNba(json: String, repli: MatchNba? = null): BoxScore {
        val g = JSONObject(json).optJSONObject("game") ?: throw FormatInconnu("game")
        val lu = matchNba(g) ?: repli ?: throw FormatInconnu("game.teams")
        val m = if (lu.debut == 0L && repli != null) lu.copy(debut = repli.debut) else lu
        return BoxScore(
            match = m,
            ext = equipeBoxNba(g.optJSONObject("awayTeam")),
            dom = equipeBoxNba(g.optJSONObject("homeTeam")),
            surTerrainConnu = true,
        )
    }

    private fun matchNba(g: JSONObject): MatchNba? {
        val ext = equipeNba(g.optJSONObject("awayTeam")) ?: return null
        val dom = equipeNba(g.optJSONObject("homeTeam")) ?: return null
        val etat = when (entier(g, "gameStatus")) {
            2 -> EtatMatch.EN_COURS
            3 -> EtatMatch.TERMINE
            else -> EtatMatch.A_VENIR
        }
        val periode = entier(g, "period") ?: 0
        val texteStatut = texte(g, "gameStatusText").orEmpty()
        // « PT02M31.00S » ; vide entre deux quarts-temps
        val horlogeBrute = if (g.has("gameClock") && !g.isNull("gameClock")) g.opt("gameClock").toString() else null
        val horloge = Statuts.lireHorloge(horlogeBrute)
        val fin = etat == EtatMatch.EN_COURS && periode >= 1 && (
            (horlogeBrute != null && (horloge == null || horloge <= 0.0)) ||
                texteStatut.contains("half", ignoreCase = true) || texteStatut.trim().startsWith("end", ignoreCase = true)
            )
        return MatchNba(
            id = "${ext.code}-${dom.code}",
            idSource = texte(g, "gameId").orEmpty(),
            debut = dateUtc(texte(g, "gameTimeUTC")),
            ext = ext,
            dom = dom,
            etat = etat,
            periode = periode,
            horloge = if (fin) null else horloge,
            finDePeriode = fin,
        )
    }

    private fun equipeNba(t: JSONObject?): EquipeMatch? {
        if (t == null) return null
        val code = EquipesNba.codeNba(texte(t, "teamTricode") ?: return null)
        if (code.isEmpty()) return null
        val periodes = objets(t.optJSONArray("periods"))
            .mapNotNull { p -> sansErreur { (entier(p, "period") ?: 0) to (entier(p, "score") ?: 0) } }
            .sortedBy { it.first }
            .map { it.second }
        return EquipeMatch(code, texte(t, "teamName") ?: EquipesNba.de(code).nom, entier(t, "score") ?: 0, periodes)
    }

    private fun equipeBoxNba(t: JSONObject?): EquipeBox {
        if (t == null) return EquipeBox(emptyList(), null)
        val joueurs = objets(t.optJSONArray("players")).mapNotNull { p -> sansErreur { joueurNba(p) } }
            // Titulaires d'abord, puis le banc, chacun dans l'ordre de la source
            .sortedBy { !it.titulaire }
        val s = t.optJSONObject("statistics")
        val stats = s?.let {
            StatsEquipe(
                pctTirs = pourcentage(decimal(it, "fieldGoalsPercentage")),
                pctTrois = pourcentage(decimal(it, "threePointersPercentage")),
                rebonds = entier(it, "reboundsTotal"),
                passes = entier(it, "assists"),
                pertes = entier(it, "turnovers") ?: entier(it, "turnoversTotal"),
                tirsReussis = entier(it, "fieldGoalsMade"),
                tirsTentes = entier(it, "fieldGoalsAttempted"),
                troisReussis = entier(it, "threePointersMade"),
                troisTentes = entier(it, "threePointersAttempted"),
            )
        }
        return EquipeBox(joueurs, StatsEquipe.completer(stats, joueurs))
    }

    private fun joueurNba(p: JSONObject): JoueurNba? {
        val nom = texte(p, "nameI") ?: texte(p, "name") ?: return null
        val s = p.optJSONObject("statistics")
        val minutes = Statuts.minutes(texte(s, "minutes") ?: texte(s, "minutesCalculated"))
        return JoueurNba(
            nom = nom,
            titulaire = drapeau(p, "starter") ?: false,
            surTerrain = drapeau(p, "oncourt"),
            aJoue = drapeau(p, "played") ?: (minutes > 0),
            minutes = minutes,
            points = entier(s, "points") ?: 0,
            rebonds = entier(s, "reboundsTotal") ?: 0,
            passes = entier(s, "assists") ?: 0,
            tirsReussis = entier(s, "fieldGoalsMade") ?: 0,
            tirsTentes = entier(s, "fieldGoalsAttempted") ?: 0,
            troisReussis = entier(s, "threePointersMade") ?: 0,
            troisTentes = entier(s, "threePointersAttempted") ?: 0,
            plusMoins = entier(s, "plusMinusPoints"),
        )
    }

    // ------------------------------------------------------------------ ESPN

    /** Tableau ESPN → matchs. */
    fun tableauEspn(json: String): List<MatchNba> {
        val evenements = JSONObject(json).optJSONArray("events") ?: throw FormatInconnu("events")
        return objets(evenements).mapNotNull { e ->
            sansErreur {
                val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return@sansErreur null
                matchEspn(
                    idSource = texte(e, "id") ?: texte(comp, "id").orEmpty(),
                    date = texte(e, "date") ?: texte(comp, "date"),
                    concurrents = comp.optJSONArray("competitors"),
                    statut = comp.optJSONObject("status") ?: e.optJSONObject("status"),
                )
            }
        }
    }

    /**
     * Résumé ESPN d'un match : joueurs et statistiques (`boxscore`), en-tête (`header`, sinon
     * [repli] : le match du tableau). Pas d'info « sur le terrain » chez ESPN.
     */
    fun boxScoreEspn(json: String, repli: MatchNba? = null): BoxScore {
        val r = JSONObject(json)
        val box = r.optJSONObject("boxscore") ?: throw FormatInconnu("boxscore")
        val tete = r.optJSONObject("header")
        val comp = tete?.optJSONArray("competitions")?.optJSONObject(0)
        val lu = comp?.let {
            sansErreur {
                matchEspn(
                    idSource = texte(tete, "id") ?: texte(it, "id") ?: repli?.idSource.orEmpty(),
                    date = texte(it, "date"),
                    concurrents = it.optJSONArray("competitors"),
                    statut = it.optJSONObject("status") ?: tete.optJSONObject("status"),
                )
            }
        }
        val m = when {
            lu == null -> repli ?: throw FormatInconnu("header")
            lu.debut == 0L && repli != null -> lu.copy(debut = repli.debut)
            else -> lu
        }
        // Une entrée par équipe, reconnue par son code (sinon : extérieur puis domicile)
        val joueurs = objets(box.optJSONArray("players")).mapNotNull { p ->
            sansErreur { codeEquipe(p) to joueursEspn(p.optJSONArray("statistics")?.optJSONObject(0)) }
        }
        val stats = objets(box.optJSONArray("teams")).mapNotNull { t ->
            sansErreur { codeEquipe(t) to statsEspn(t.optJSONArray("statistics")) }
        }
        fun <T> pour(liste: List<Pair<String?, T>>, code: String, rang: Int): T? =
            liste.firstOrNull { it.first == code }?.second ?: liste.takeIf { l -> l.none { it.first == m.ext.code || it.first == m.dom.code } }?.getOrNull(rang)?.second
        val jExt = pour(joueurs, m.ext.code, 0).orEmpty()
        val jDom = pour(joueurs, m.dom.code, 1).orEmpty()
        return BoxScore(
            match = m,
            ext = EquipeBox(jExt, StatsEquipe.completer(pour(stats, m.ext.code, 0), jExt)),
            dom = EquipeBox(jDom, StatsEquipe.completer(pour(stats, m.dom.code, 1), jDom)),
            surTerrainConnu = false,
        )
    }

    private fun codeEquipe(o: JSONObject): String? = texte(o.optJSONObject("team"), "abbreviation")?.let { EquipesNba.codeNba(it) }

    private fun matchEspn(idSource: String, date: String?, concurrents: JSONArray?, statut: JSONObject?): MatchNba? {
        val equipes = objets(concurrents)
        if (equipes.size < 2) return null
        val dom = equipes.firstOrNull { it.optString("homeAway") == "home" } ?: equipes[0]
        val ext = equipes.firstOrNull { it.optString("homeAway") == "away" } ?: equipes.first { it !== dom }
        val eExt = equipeEspn(ext) ?: return null
        val eDom = equipeEspn(dom) ?: return null
        val type = statut?.optJSONObject("type")
        val etat = when (texte(type, "state")) {
            "in" -> EtatMatch.EN_COURS
            "post" -> EtatMatch.TERMINE
            "pre" -> EtatMatch.A_VENIR
            else -> if (drapeau(type, "completed") == true) EtatMatch.TERMINE else EtatMatch.A_VENIR
        }
        val periode = entier(statut, "period") ?: 0
        val affichee = texte(statut, "displayClock")
        val horloge = Statuts.lireHorloge(affichee) ?: decimal(statut, "clock")
        val nom = texte(type, "name").orEmpty()
        val fin = etat == EtatMatch.EN_COURS && periode >= 1 && (
            nom.contains("HALFTIME", ignoreCase = true) || nom.contains("END_PERIOD", ignoreCase = true) ||
                (horloge != null && horloge <= 0.0)
            )
        return MatchNba(
            id = "${eExt.code}-${eDom.code}",
            idSource = idSource,
            debut = dateUtc(date),
            ext = eExt,
            dom = eDom,
            etat = etat,
            periode = periode,
            horloge = if (fin) null else horloge,
            finDePeriode = fin,
        )
    }

    private fun equipeEspn(c: JSONObject): EquipeMatch? {
        val t = c.optJSONObject("team") ?: return null
        val code = EquipesNba.codeNba(texte(t, "abbreviation") ?: return null)
        if (code.isEmpty()) return null
        val periodes = objets(c.optJSONArray("linescores")).map { l -> entier(l, "value") ?: entier(l, "displayValue") ?: 0 }
        val nom = texte(t, "name") ?: texte(t, "shortDisplayName") ?: EquipesNba.de(code).nom
        return EquipeMatch(code, nom, entier(c, "score") ?: 0, periodes)
    }

    private fun joueursEspn(s: JSONObject?): List<JoueurNba> {
        if (s == null) return emptyList()
        val libelles = chaines(s.optJSONArray("labels") ?: s.optJSONArray("names")).map { it.uppercase() }
        val cles = chaines(s.optJSONArray("keys"))
        fun colonne(libelle: String, cle: String): Int = libelles.indexOf(libelle).takeIf { it >= 0 } ?: cles.indexOf(cle)
        val iMin = colonne("MIN", "minutes")
        val iTirs = colonne("FG", "fieldGoalsMade-fieldGoalsAttempted")
        val iTrois = colonne("3PT", "threePointFieldGoalsMade-threePointFieldGoalsAttempted")
        val iReb = colonne("REB", "rebounds")
        val iPd = colonne("AST", "assists")
        val iPm = colonne("+/-", "plusMinus")
        val iPts = colonne("PTS", "points")
        return objets(s.optJSONArray("athletes")).mapNotNull { a ->
            sansErreur {
                val ath = a.optJSONObject("athlete")
                val nom = texte(ath, "shortName") ?: texte(ath, "displayName") ?: return@sansErreur null
                val stats = chaines(a.optJSONArray("stats"))
                val v = { i: Int -> if (i >= 0) stats.getOrNull(i) else null }
                val (tr, tt) = paire(v(iTirs))
                val (r3, t3) = paire(v(iTrois))
                JoueurNba(
                    nom = nom,
                    titulaire = drapeau(a, "starter") ?: false,
                    surTerrain = null,
                    aJoue = drapeau(a, "didNotPlay") != true && stats.isNotEmpty(),
                    minutes = Statuts.minutes(v(iMin)),
                    points = nombre(v(iPts)) ?: 0,
                    rebonds = nombre(v(iReb)) ?: 0,
                    passes = nombre(v(iPd)) ?: 0,
                    tirsReussis = tr,
                    tirsTentes = tt,
                    troisReussis = r3,
                    troisTentes = t3,
                    plusMoins = nombre(v(iPm)),
                )
            }
        }.sortedBy { !it.titulaire }
    }

    private fun statsEspn(liste: JSONArray?): StatsEquipe? {
        val valeurs = HashMap<String, String>()
        for (o in objets(liste)) {
            val nom = texte(o, "name") ?: continue
            valeurs[nom] = texte(o, "displayValue") ?: continue
        }
        if (valeurs.isEmpty()) return null
        val tirs = valeurs["fieldGoalsMade-fieldGoalsAttempted"]?.let { paire(it) }
        val trois = valeurs["threePointFieldGoalsMade-threePointFieldGoalsAttempted"]?.let { paire(it) }
        return StatsEquipe(
            pctTirs = pourcentage(valeurs["fieldGoalPct"]?.let { decimalTexte(it) }),
            pctTrois = pourcentage(valeurs["threePointFieldGoalPct"]?.let { decimalTexte(it) }),
            rebonds = nombre(valeurs["totalRebounds"]) ?: nombre(valeurs["rebounds"]),
            passes = nombre(valeurs["assists"]),
            pertes = nombre(valeurs["turnovers"]) ?: nombre(valeurs["totalTurnovers"]),
            tirsReussis = tirs?.first,
            tirsTentes = tirs?.second,
            troisReussis = trois?.first,
            troisTentes = trois?.second,
        )
    }

    // ------------------------------------------------------------------ outils

    /** Un élément illisible est sauté (null) sans gêner les autres. */
    private inline fun <T> sansErreur(bloc: () -> T?): T? = try {
        bloc()
    } catch (e: Exception) {
        null
    }

    private fun objets(a: JSONArray?): List<JSONObject> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    private fun chaines(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { i -> if (a.isNull(i)) "" else a.opt(i).toString() }
    }

    private fun texte(o: JSONObject?, cle: String): String? {
        if (o == null || !o.has(cle) || o.isNull(cle)) return null
        return when (val v = o.opt(cle)) {
            is String -> v.trim().ifEmpty { null }
            is Number, is Boolean -> v.toString()
            else -> null
        }
    }

    /** Entier, qu'il soit écrit en nombre (4.0 compris) ou en texte (« 102 », « +5 »). */
    private fun entier(o: JSONObject?, cle: String): Int? {
        if (o == null || !o.has(cle) || o.isNull(cle)) return null
        return when (val v = o.opt(cle)) {
            is Number -> v.toDouble().takeIf { it.isFinite() }?.roundToInt()
            is String -> nombre(v)
            else -> null
        }
    }

    private fun decimal(o: JSONObject?, cle: String): Double? {
        if (o == null || !o.has(cle) || o.isNull(cle)) return null
        return when (val v = o.opt(cle)) {
            is Number -> v.toDouble().takeIf { it.isFinite() }
            is String -> decimalTexte(v)
            else -> null
        }
    }

    private fun decimalTexte(s: String): Double? =
        s.trim().removeSuffix("%").trim().replace(',', '.').removePrefix("+").toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun nombre(s: String?): Int? = s?.let { decimalTexte(it) }?.roundToInt()

    /** « 1 » / « 0 », 1 / 0, true / false. */
    private fun drapeau(o: JSONObject?, cle: String): Boolean? {
        if (o == null || !o.has(cle) || o.isNull(cle)) return null
        return when (val v = o.opt(cle)) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> when (v.trim().lowercase()) {
                "1", "true", "oui", "y", "yes" -> true
                "0", "false", "non", "n", "no", "" -> false
                else -> null
            }
            else -> null
        }
    }

    /** « 12-24 » → (12, 24) ; illisible → (0, 0). */
    private fun paire(s: String?): Pair<Int, Int> {
        val morceaux = s?.split('-', '/')?.map { it.trim() } ?: return 0 to 0
        if (morceaux.size != 2) return 0 to 0
        return (morceaux[0].toIntOrNull() ?: 0) to (morceaux[1].toIntOrNull() ?: 0)
    }

    /** Pourcentage entre 0 et 1, qu'il soit donné entre 0 et 1 (NBA) ou entre 0 et 100 (ESPN « 48.1 »). */
    fun pourcentage(v: Double?): Double? {
        if (v == null || v.isNaN() || v < 0) return null
        return (if (v > 1.0) v / 100.0 else v).coerceIn(0.0, 1.0)
    }

    /** « 2024-01-16T00:30:00Z », « 2024-01-16T00:30Z », « …+01:00 » → ms (UTC) ; 0 si illisible. */
    fun dateUtc(s: String?): Long {
        val m = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?(Z|[+-]\d{2}:?\d{2})?$""")
            .matchEntire(s?.trim().orEmpty()) ?: return 0L
        val g = m.groupValues
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toIntOrNull() ?: 0)
        var t = c.timeInMillis
        val z = g[7]
        if (z.isNotEmpty() && z != "Z") {
            val chiffres = z.substring(1).replace(":", "")
            val decalage = (chiffres.substring(0, 2).toInt() * 60 + chiffres.substring(2, 4).toInt()) * 60_000L
            t += if (z[0] == '+') -decalage else decalage
        }
        return t
    }
}
