package fr.projo.launcher.nba

import fr.projo.launcher.boxscore.EquipesNba
import fr.projo.launcher.boxscore.LectureNba
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Équipe de la liste ESPN : identifiant, nom complet, logo (`https://…`, null si absent). */
internal data class EquipeEspn(val code: String, val id: String, val nom: String, val logo: String?)

/** Un match du calendrier et son identifiant ESPN (résumé du match). */
internal data class MatchLu(val idMatch: String, val match: MatchEquipe)

/** Calendrier d'une équipe : dernier match terminé, premier match pas encore terminé. */
internal data class CalendrierLu(val nomEquipe: String?, val dernier: MatchLu?, val prochain: MatchLu?)

/** Joueur de l'effectif ; [blessure] : statut d'origine (« Out »…), null s'il n'est pas blessé. */
internal data class JoueurLu(
    val id: String?,
    val nom: String,
    val numero: String?,
    val poste: String?,
    val blessure: String?,
    val zone: String?,
)

internal data class EffectifLu(val nomEquipe: String?, val joueurs: List<JoueurLu>)

/** Blessure lue dans un résumé de match : statut et zone d'origine (« Out », « Ankle »). */
internal data class BlessureLue(val id: String?, val nom: String, val statut: String?, val zone: String?)

/** Résumé d'un match pour une équipe : ses titulaires (5) et ses blessés du moment. */
internal data class ResumeLu(val titulaires: List<JoueurLu>, val blessures: List<BlessureLue>)

/** Moyennes de la saison régulière ; toutes null si le joueur n'a pas encore joué. */
internal data class MoyennesLues(val minutes: Double?, val points: Double?, val rebonds: Double?, val passes: Double?) {
    companion object {
        val AUCUNE = MoyennesLues(null, null, null, null)
    }
}

/**
 * Lecture des flux ESPN du design NBA (docs/design-nba.md, « Données »), dans le style de
 * [LectureNba] : champ absent ou null, nombre écrit en texte, score en objet (`{value,
 * displayValue}`) ou en texte, effectif à plat ou groupé par poste. Une entrée illisible est
 * sautée sans gêner les autres ; un document sans la partie attendue lève [FormatInconnu].
 */
internal object LectureEspn {

    class FormatInconnu(message: String) : Exception(message)

    // ------------------------------------------------------------------ classement

    private class BilanLu(
        val code: String,
        val victoires: Int,
        val defaites: Int,
        val pourcentage: Double,
        val tete: Int?,
        val serie: Int,
        val dixDerniers: String?,
    )

    /**
     * Classement (`children[]` → `standings.entries[]`) : rang de conférence = `playoffSeed`
     * (calculé comme le rang de division s'il manque), rang de division calculé : % de victoires,
     * puis victoires, puis rang de conférence.
     */
    fun classement(json: String, misAJour: Long): ClassementNba {
        val entrees = ArrayList<JSONObject>()
        collecterEntrees(JSONObject(json), entrees, 0)
        if (entrees.isEmpty()) throw FormatInconnu("children[].standings.entries")
        val lus = LinkedHashMap<String, BilanLu>()
        for (e in entrees) {
            val b = sansErreur { bilan(e) } ?: continue
            if (b.code !in lus) lus[b.code] = b
        }
        if (lus.isEmpty()) throw FormatInconnu("entries[].team")
        return ClassementNba(rangs(lus.values.toList()), misAJour, horsLigne = false)
    }

    /** Entrées du classement, à n'importe quel niveau (conférences, ou conférences puis divisions). */
    private fun collecterEntrees(o: JSONObject, sortie: MutableList<JSONObject>, profondeur: Int) {
        if (profondeur > 4) return
        o.optJSONObject("standings")?.optJSONArray("entries")?.let { sortie += objets(it) }
        for (enfant in objets(o.optJSONArray("children"))) collecterEntrees(enfant, sortie, profondeur + 1)
    }

    private fun bilan(e: JSONObject): BilanLu? {
        val code = EquipesNba.codeNba(texte(e.optJSONObject("team"), "abbreviation") ?: return null)
        if (DivisionNba.de(code) == null) return null
        // Statistiques par nom (« wins », « Last Ten Games ») et par type (« lasttengames »)
        val stats = HashMap<String, JSONObject>()
        for (s in objets(e.optJSONArray("stats"))) {
            // (pas de putIfAbsent : API 24)
            texte(s, "type")?.lowercase()?.let { if (it !in stats) stats[it] = s }
            texte(s, "name")?.let { stats[it.lowercase()] = s }
        }
        fun stat(vararg cles: String): JSONObject? = cles.firstNotNullOfOrNull { stats[it.lowercase()] }
        fun nombreStat(vararg cles: String): Double? = stat(*cles)?.let { decimal(it, "value") ?: decimal(it, "displayValue") }
        val v = nombreStat("wins")?.roundToInt() ?: return null
        val d = nombreStat("losses")?.roundToInt() ?: return null
        val pct = if (v + d > 0) v.toDouble() / (v + d) else nombreStat("winPercent")?.let { if (it > 1.0) it / 100.0 else it } ?: 0.0
        val serie = stat("streak")?.let { s -> lireSerie(texte(s, "displayValue")) ?: decimal(s, "value")?.roundToInt() } ?: 0
        val dix = texte(stat("Last Ten Games", "lasttengames", "L10"), "displayValue")?.let { lireDix(it) }
        return BilanLu(code, v, d, pct, nombreStat("playoffSeed")?.roundToInt()?.takeIf { it > 0 }, serie, dix)
    }

    /** « W3 » → +3, « L8 » → −8 ; illisible → null. */
    fun lireSerie(s: String?): Int? {
        val m = Regex("""^([WLwl])\s*(\d+)$""").matchEntire(s?.trim().orEmpty()) ?: return null
        val n = m.groupValues[2].toInt()
        return if (m.groupValues[1].equals("W", ignoreCase = true)) n else -n
    }

    /** « 7-3 » (ou « 7–3 ») → « 7-3 » ; illisible → null. */
    private fun lireDix(s: String): String? {
        val m = Regex("""^(\d+)\s*[-–]\s*(\d+)$""").matchEntire(s.trim()) ?: return null
        return "${m.groupValues[1]}-${m.groupValues[2]}"
    }

    private fun rangs(bilans: List<BilanLu>): Map<String, BilanNba> {
        val meilleur = compareByDescending<BilanLu> { it.pourcentage }.thenByDescending { it.victoires }.thenBy { it.defaites }
        // Rang de conférence : celui d'ESPN ; s'il manque à une équipe, toute la conférence est recalculée
        val rangConf = HashMap<String, Int>()
        for ((_, equipes) in bilans.groupBy { DivisionNba.de(it.code)!!.conference }) {
            if (equipes.all { it.tete != null }) {
                equipes.forEach { rangConf[it.code] = it.tete!! }
            } else {
                equipes.sortedWith(meilleur.thenBy { it.code }).forEachIndexed { i, b -> rangConf[b.code] = i + 1 }
            }
        }
        val rangDiv = HashMap<String, Int>()
        for ((_, equipes) in bilans.groupBy { DivisionNba.de(it.code)!! }) {
            equipes.sortedWith(
                compareByDescending<BilanLu> { it.pourcentage }.thenByDescending { it.victoires }.thenBy { rangConf.getValue(it.code) }.thenBy { it.code },
            ).forEachIndexed { i, b -> rangDiv[b.code] = i + 1 }
        }
        return bilans.associate { b ->
            b.code to BilanNba(b.code, b.victoires, b.defaites, rangConf.getValue(b.code), rangDiv.getValue(b.code), b.serie, b.dixDerniers)
        }
    }

    // ------------------------------------------------------------------ équipes

    /** Liste des équipes (`sports[].leagues[].teams[].team`) par code NBA. */
    fun equipes(json: String): Map<String, EquipeEspn> {
        val sortie = LinkedHashMap<String, EquipeEspn>()
        for (sport in objets(JSONObject(json).optJSONArray("sports"))) {
            for (ligue in objets(sport.optJSONArray("leagues"))) {
                for (t in objets(ligue.optJSONArray("teams"))) {
                    val e = sansErreur { equipe(t.optJSONObject("team") ?: t) } ?: continue
                    if (e.code !in sortie) sortie[e.code] = e
                }
            }
        }
        if (sortie.isEmpty()) throw FormatInconnu("sports[].leagues[].teams")
        return sortie
    }

    private fun equipe(t: JSONObject): EquipeEspn? {
        val code = EquipesNba.codeNba(texte(t, "abbreviation") ?: return null)
        if (DivisionNba.de(code) == null) return null
        val id = texte(t, "id") ?: return null
        val nom = texte(t, "displayName") ?: listOfNotNull(texte(t, "location"), texte(t, "name")).joinToString(" ").ifEmpty { return null }
        return EquipeEspn(code, id, nom, logo(t.optJSONArray("logos")))
    }

    /** Logo par défaut (`rel` « default »), sinon le premier ; seulement en https. */
    private fun logo(logos: JSONArray?): String? {
        val liste = objets(logos)
        val choisi = liste.firstOrNull { l -> chaines(l.optJSONArray("rel")).any { it == "default" } } ?: liste.firstOrNull()
        return texte(choisi, "href")?.takeIf { it.startsWith("https://") }
    }

    // ------------------------------------------------------------------ calendrier

    /**
     * Calendrier d'une équipe ([code] NBA ; [idEquipe] ESPN en secours) : dernier match terminé
     * (`status.type.completed`) avec les scores, premier match pas encore terminé (ni annulé ni
     * reporté). [MatchEquipe.debut] : `date` (UTC).
     */
    fun calendrier(json: String, code: String, idEquipe: String? = null): CalendrierLu {
        val r = JSONObject(json)
        val evenements = r.optJSONArray("events") ?: throw FormatInconnu("events")
        var dernier: MatchLu? = null
        var prochain: MatchLu? = null
        for (e in objets(evenements)) {
            sansErreur {
                val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return@sansErreur null
                val type = (comp.optJSONObject("status") ?: e.optJSONObject("status"))?.optJSONObject("type")
                val etat = texte(type, "state")
                val nomStatut = texte(type, "name").orEmpty().uppercase()
                val termine = drapeau(type, "completed") ?: (etat == "post")
                val concurrents = objets(comp.optJSONArray("competitors"))
                val nous = concurrents.firstOrNull { codeConcurrent(it) == code }
                    ?: concurrents.firstOrNull { idEquipe != null && texte(it.optJSONObject("team"), "id") == idEquipe }
                    ?: return@sansErreur null
                val eux = concurrents.firstOrNull { it !== nous } ?: return@sansErreur null
                val m = MatchLu(
                    idMatch = texte(e, "id") ?: texte(comp, "id").orEmpty(),
                    match = MatchEquipe(
                        adversaire = codeConcurrent(eux) ?: return@sansErreur null,
                        domicile = texte(nous, "homeAway") == "home",
                        debut = LectureNba.dateUtc(texte(e, "date") ?: texte(comp, "date")),
                        score = if (termine) score(nous) else null,
                        scoreAdversaire = if (termine) score(eux) else null,
                    ),
                )
                val ecarte = etat == "post" || nomStatut.contains("CANCEL") || nomStatut.contains("POSTPONE")
                when {
                    termine -> if (dernier.let { it == null || m.match.debut >= it.match.debut }) dernier = m
                    !ecarte -> if (prochain.let { it == null || m.match.debut < it.match.debut }) prochain = m
                }
                Unit
            }
        }
        return CalendrierLu(texte(r.optJSONObject("team"), "displayName"), dernier, prochain)
    }

    private fun codeConcurrent(c: JSONObject): String? =
        texte(c.optJSONObject("team"), "abbreviation")?.let { EquipesNba.codeNba(it) }

    /** Score : `{value, displayValue}`, texte (« 118 ») ou nombre. */
    private fun score(c: JSONObject): Int? {
        if (!c.has("score") || c.isNull("score")) return null
        return when (val s = c.opt("score")) {
            is JSONObject -> entier(s, "value") ?: entier(s, "displayValue")
            is Number -> s.toDouble().takeIf { it.isFinite() }?.roundToInt()
            is String -> nombre(s)
            else -> null
        }
    }

    // ------------------------------------------------------------------ effectif

    /** Effectif (`athletes[]`, à plat ou groupé par poste dans `items[]`) avec les blessures (`injuries[]`). */
    fun effectif(json: String): EffectifLu {
        val r = JSONObject(json)
        val athletes = r.optJSONArray("athletes") ?: throw FormatInconnu("athletes")
        val aPlat = objets(athletes).flatMap { a -> a.optJSONArray("items")?.let { objets(it) } ?: listOf(a) }
        val joueurs = aPlat.mapNotNull { a -> sansErreur { joueur(a) } }
        return EffectifLu(texte(r.optJSONObject("team"), "displayName"), joueurs)
    }

    private fun joueur(a: JSONObject): JoueurLu? {
        val nom = texte(a, "shortName") ?: texte(a, "displayName") ?: return null
        val blessure = objets(a.optJSONArray("injuries")).firstOrNull()
        return JoueurLu(
            id = texte(a, "id"),
            nom = nom,
            numero = texte(a, "jersey"),
            poste = texte(a.optJSONObject("position"), "abbreviation"),
            blessure = blessure?.let { texte(it, "status") ?: texte(it.optJSONObject("type"), "description") ?: "" },
            zone = blessure?.let { zoneLue(it) },
        )
    }

    /** Zone d'une blessure : `details.type`, sinon le terme entre parenthèses du commentaire (« Newell (toe) … »). */
    private fun zoneLue(b: JSONObject): String? =
        texte(b.optJSONObject("details"), "type")
            ?: listOfNotNull(texte(b, "shortComment"), texte(b, "longComment")).firstNotNullOfOrNull { c ->
                Regex("""\(([A-Za-z][A-Za-z \-]{1,30})\)""").find(c)?.groupValues?.get(1)
            }

    // ------------------------------------------------------------------ résumé d'un match

    /**
     * Résumé d'un match pour l'équipe [code] : titulaires (`boxscore.players[].statistics[0]
     * .athletes[]`, `starter`) et blessés (`injuries[]`, avec `details.type`).
     */
    fun resume(json: String, code: String, idEquipe: String? = null): ResumeLu {
        val r = JSONObject(json)
        val box = r.optJSONObject("boxscore")
        val blesses = r.optJSONArray("injuries")
        if (box == null && blesses == null) throw FormatInconnu("boxscore")
        fun estNous(o: JSONObject): Boolean {
            val t = o.optJSONObject("team")
            return texte(t, "abbreviation")?.let { EquipesNba.codeNba(it) } == code || (idEquipe != null && texte(t, "id") == idEquipe)
        }
        val feuille = objets(box?.optJSONArray("players")).firstOrNull { estNous(it) }
        val titulaires = objets(feuille?.optJSONArray("statistics")?.optJSONObject(0)?.optJSONArray("athletes"))
            .filter { drapeau(it, "starter") == true }
            .mapNotNull { a ->
                sansErreur {
                    val ath = a.optJSONObject("athlete") ?: return@sansErreur null
                    JoueurLu(
                        id = texte(ath, "id"),
                        nom = texte(ath, "shortName") ?: texte(ath, "displayName") ?: return@sansErreur null,
                        numero = texte(ath, "jersey"),
                        poste = texte(ath.optJSONObject("position"), "abbreviation"),
                        blessure = null,
                        zone = null,
                    )
                }
            }
            .take(5)
        val blessures = objets(objets(blesses).firstOrNull { estNous(it) }?.optJSONArray("injuries")).mapNotNull { b ->
            sansErreur {
                val ath = b.optJSONObject("athlete")
                BlessureLue(
                    id = texte(ath, "id"),
                    nom = texte(ath, "shortName") ?: texte(ath, "displayName") ?: return@sansErreur null,
                    statut = texte(b, "status") ?: texte(b.optJSONObject("type"), "description"),
                    zone = zoneLue(b),
                )
            }
        }
        return ResumeLu(titulaires, blessures)
    }

    // ------------------------------------------------------------------ moyennes d'un joueur

    /**
     * Moyennes de la saison régulière (`statistics.names`/`labels`, `splits[]` « Regular
     * Season ») ; [MoyennesLues.AUCUNE] si le joueur n'a pas encore joué cette saison.
     */
    fun moyennes(json: String): MoyennesLues {
        val s = JSONObject(json).optJSONObject("statistics") ?: throw FormatInconnu("statistics")
        val noms = chaines(s.optJSONArray("names"))
        val libelles = chaines(s.optJSONArray("labels")).map { it.uppercase() }
        val saison = objets(s.optJSONArray("splits")).firstOrNull { texte(it, "displayName").equals("Regular Season", ignoreCase = true) }
            ?: return MoyennesLues.AUCUNE
        val valeurs = chaines(saison.optJSONArray("stats"))
        fun valeur(nom: String, libelle: String): Double? {
            val i = noms.indexOf(nom).takeIf { it >= 0 } ?: libelles.indexOf(libelle)
            return valeurs.getOrNull(i)?.let { decimalTexte(it) }
        }
        return MoyennesLues(
            minutes = valeur("avgMinutes", "MIN"),
            points = valeur("avgPoints", "PTS"),
            rebonds = valeur("avgRebounds", "REB"),
            passes = valeur("avgAssists", "AST"),
        )
    }

    // ------------------------------------------------------------------ traductions

    /** Statut de blessure en français ; inconnu : le terme d'origine (« Blessé » s'il est vide). */
    fun statut(brut: String?): String {
        val s = brut?.trim().orEmpty()
        val cle = s.lowercase().filter { it.isLetter() }
        return when {
            cle.isEmpty() -> "Blessé"
            cle.startsWith("out") -> "Absent"
            cle == "daytoday" || cle == "dtd" -> "Au jour le jour"
            cle == "questionable" || cle == "gtd" || cle == "gametimedecision" -> "Incertain"
            cle == "doubtful" -> "Peu probable"
            cle == "probable" -> "Probable"
            cle.startsWith("suspen") -> "Suspendu"
            else -> s
        }
    }

    private val zones = mapOf(
        "knee" to "genou", "ankle" to "cheville", "toe" to "orteil", "hamstring" to "ischios", "foot" to "pied",
        "back" to "dos", "lower back" to "bas du dos", "hip" to "hanche", "calf" to "mollet", "groin" to "aine",
        "shoulder" to "épaule", "wrist" to "poignet", "hand" to "main", "illness" to "maladie", "rest" to "repos",
        "concussion" to "commotion", "achilles" to "tendon d'Achille", "head" to "tête", "elbow" to "coude",
        "heel" to "talon", "leg" to "jambe", "lower leg" to "jambe", "abdomen" to "abdomen", "finger" to "doigt",
        "thumb" to "pouce", "quadriceps" to "quadriceps", "quad" to "quadriceps", "thigh" to "cuisse", "neck" to "cou",
        "face" to "visage", "nose" to "nez", "eye" to "œil", "chest" to "poitrine", "ribs" to "côtes", "rib" to "côte",
        "oblique" to "oblique", "shin" to "tibia", "forearm" to "avant-bras", "arm" to "bras", "jaw" to "mâchoire",
        "pelvis" to "bassin", "adductor" to "adducteur", "personal" to "raisons personnelles",
        "not injury related" to "hors blessure", "plantar fasciitis" to "fasciite plantaire", "conditioning" to "remise en forme",
    )

    /** Zone d'une blessure en français (« Knee » → « genou ») ; inconnue : le terme d'origine, en minuscules. */
    fun zone(brut: String?): String? {
        val s = brut?.trim()?.replace(Regex("""\s+"""), " ")?.ifEmpty { null } ?: return null
        return zones[s.lowercase()] ?: s.lowercase()
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

    private fun entier(o: JSONObject?, cle: String): Int? = decimal(o, cle)?.roundToInt()

    private fun decimal(o: JSONObject?, cle: String): Double? {
        if (o == null || !o.has(cle) || o.isNull(cle)) return null
        return when (val v = o.opt(cle)) {
            is Number -> v.toDouble().takeIf { it.isFinite() }
            is String -> decimalTexte(v)
            else -> null
        }
    }

    /** « 24.9 », « .833 », « +5 », « 49,4 % » ; illisible → null. */
    private fun decimalTexte(s: String): Double? =
        s.trim().removeSuffix("%").trim().replace(',', '.').removePrefix("+").toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun nombre(s: String?): Int? = s?.let { decimalTexte(it) }?.roundToInt()

    /** true / false, 1 / 0, « true » / « false ». */
    private fun drapeau(o: JSONObject?, cle: String): Boolean? {
        if (o == null || !o.has(cle) || o.isNull(cle)) return null
        return when (val v = o.opt(cle)) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> when (v.trim().lowercase()) {
                "1", "true" -> true
                "0", "false", "" -> false
                else -> null
            }
            else -> null
        }
    }
}
