package fr.projo.launcher.nba

import fr.projo.launcher.boxscore.EquipesNba
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Données du design NBA, sans Android (testées sur la JVM) : classement (cache disque, rythme
 * de [PlanClassement]), équipes (identifiants ESPN, noms, logos ; une fois par jour, cache
 * disque) et fiches d'équipe (caches en mémoire : calendrier 30 min, effectif 1 h, résumé du
 * dernier match et moyennes des joueurs 6 h). [horloge] : ms (epoch) ; [chargeur] : requêtes
 * bloquantes, faites sur [filReseau].
 *
 * Journal : types d'événements seulement (« nba carte : classement indisponible (403) »), jamais
 * d'adresse ni de nom de joueur.
 */
internal class EntrepotNba(
    private val chargeur: ChargeurEspn,
    private val horloge: () -> Long,
    private val etat: MutableStateFlow<ClassementNba?>,
    private val journal: (String) -> Unit,
    private val filReseau: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val EQUIPES_MS = 24 * 3_600_000L
        const val EQUIPES_REPRISE_MS = 3_600_000L
        const val CALENDRIER_MS = 30 * 60_000L
        const val EFFECTIF_MS = 3_600_000L
        const val RESUME_MS = 6 * 3_600_000L
        const val MOYENNES_MS = 6 * 3_600_000L

        const val FICHIER_CLASSEMENT = "classement.json"
        const val FICHIER_EQUIPES = "equipes.json"

        /** Identifiants ESPN des 30 équipes (stables), en attendant la liste des équipes. */
        val ID_ESPN = mapOf(
            "ATL" to "1", "BOS" to "2", "NOP" to "3", "CHI" to "4", "CLE" to "5", "DAL" to "6", "DEN" to "7",
            "DET" to "8", "GSW" to "9", "HOU" to "10", "IND" to "11", "LAC" to "12", "LAL" to "13", "MIA" to "14",
            "MIL" to "15", "MIN" to "16", "BKN" to "17", "NYK" to "18", "ORL" to "19", "PHI" to "20", "PHX" to "21",
            "POR" to "22", "SAC" to "23", "SAS" to "24", "OKC" to "25", "UTA" to "26", "WAS" to "27", "TOR" to "28",
            "MEM" to "29", "CHA" to "30",
        )
    }

    val plan = PlanClassement()

    @Volatile
    private var stockage: StockageNba? = null

    @Volatile
    private var equipes: Map<String, EquipeEspn> = emptyMap()

    @Volatile
    private var equipesMisAJour: Long? = null
    private var equipesEchec: Long? = null

    private val calendriers = CacheTemporise<String, CalendrierLu>(CALENDRIER_MS, horloge)
    private val effectifs = CacheTemporise<String, EffectifLu>(EFFECTIF_MS, horloge)
    private val resumes = CacheTemporise<String, ResumeLu>(RESUME_MS, horloge)
    private val moyennes = CacheTemporise<String, MoyennesLues>(MOYENNES_MS, horloge)

    // ------------------------------------------------------------------ cache disque

    /** Branche le cache disque et relit le dernier classement et la liste des équipes. */
    fun brancher(s: StockageNba) {
        stockage = s
        if (etat.value == null) {
            s.lire(FICHIER_CLASSEMENT)?.let { texte -> lireSansErreur { classementDisque(texte) } }?.let { etat.compareAndSet(null, it) }
        }
        if (equipesMisAJour == null) {
            s.lire(FICHIER_EQUIPES)?.let { texte -> lireSansErreur { equipesDisque(texte) } }?.let { (t, liste) ->
                equipes = liste
                equipesMisAJour = t
            }
        }
    }

    private inline fun <T> lireSansErreur(bloc: () -> T): T? = try {
        bloc()
    } catch (e: Exception) {
        journal("nba carte : cache disque illisible")
        null
    }

    private fun classementDisque(texte: String): ClassementNba {
        val o = JSONObject(texte)
        val liste = o.getJSONArray("equipes")
        val bilans = (0 until liste.length()).map { i ->
            val b = liste.getJSONObject(i)
            BilanNba(
                code = b.getString("code"),
                victoires = b.getInt("v"),
                defaites = b.getInt("d"),
                rangConference = b.getInt("rc"),
                rangDivision = b.getInt("rd"),
                serie = b.getInt("s"),
                dixDerniers = if (b.has("dix") && !b.isNull("dix")) b.getString("dix") else null,
            )
        }
        return ClassementNba(bilans.associateBy { it.code }, o.getLong("misAJour"), horsLigne = false)
    }

    private fun texteClassement(c: ClassementNba): String {
        val liste = JSONArray()
        for (b in c.equipes.values) {
            liste.put(
                JSONObject().put("code", b.code).put("v", b.victoires).put("d", b.defaites).put("rc", b.rangConference)
                    .put("rd", b.rangDivision).put("s", b.serie).put("dix", b.dixDerniers ?: JSONObject.NULL),
            )
        }
        return JSONObject().put("version", 1).put("misAJour", c.misAJour).put("equipes", liste).toString()
    }

    private fun equipesDisque(texte: String): Pair<Long, Map<String, EquipeEspn>> {
        val o = JSONObject(texte)
        val liste = o.getJSONArray("equipes")
        val equipes = (0 until liste.length()).map { i ->
            val e = liste.getJSONObject(i)
            EquipeEspn(e.getString("code"), e.getString("id"), e.getString("nom"), if (e.has("logo") && !e.isNull("logo")) e.getString("logo") else null)
        }
        return o.getLong("misAJour") to equipes.associateBy { it.code }
    }

    private fun texteEquipes(t: Long, liste: Collection<EquipeEspn>): String {
        val a = JSONArray()
        for (e in liste) a.put(JSONObject().put("code", e.code).put("id", e.id).put("nom", e.nom).put("logo", e.logo ?: JSONObject.NULL))
        return JSONObject().put("version", 1).put("misAJour", t).put("equipes", a).toString()
    }

    // ------------------------------------------------------------------ classement et équipes

    /** Étape du rafraîchissement à cet instant (voir [PlanClassement]). */
    fun etape(): PlanClassement.Etape = plan.etape(horloge(), etat.value?.misAJour)

    /**
     * Charge le classement (bloquant), puis la liste des équipes si elle a plus d'un jour. Échec :
     * le classement affiché reste, marqué [ClassementNba.horsLigne].
     */
    fun charger() {
        val ok = chargerClassement()
        val t = horloge()
        if (ok) plan.reussite(t) else plan.echec(t)
        if (equipesDues(t)) chargerEquipes()
    }

    private fun chargerClassement(): Boolean {
        val t = horloge()
        val c = lire(RequeteEspn.Classement, "classement") { LectureEspn.classement(it, t) }
        if (c == null) {
            etat.value?.let { if (!it.horsLigne) etat.value = it.copy(horsLigne = true) }
            return false
        }
        etat.value = c
        stockage?.ecrire(FICHIER_CLASSEMENT, texteClassement(c))
        return true
    }

    private fun equipesDues(t: Long): Boolean {
        val echec = equipesEchec
        if (echec != null && t - echec in 0 until EQUIPES_REPRISE_MS) return false
        val maj = equipesMisAJour ?: return true
        return t - maj !in 0 until EQUIPES_MS
    }

    private fun chargerEquipes() {
        val liste = lire(RequeteEspn.Equipes, "équipes") { LectureEspn.equipes(it) }
        val t = horloge()
        if (liste == null) {
            equipesEchec = t
            return
        }
        equipesEchec = null
        equipes = liste
        equipesMisAJour = t
        stockage?.ecrire(FICHIER_EQUIPES, texteEquipes(t, liste.values))
    }

    /** Adresse du logo : celle de la liste des équipes, sinon l'adresse ESPN habituelle. */
    fun logo(code: String): String =
        equipes[EquipesNba.codeNba(code)]?.logo ?: "https://a.espncdn.com/i/teamlogos/nba/500/${DonneesNba.codeEspn(code).lowercase()}.png"

    /** Identifiant ESPN d'une équipe (code NBA). */
    fun idEspn(code: String): String? = equipes[code]?.id ?: ID_ESPN[code]

    // ------------------------------------------------------------------ fiche d'une équipe

    /**
     * Fiche d'une équipe : calendrier et effectif en parallèle, puis le résumé du dernier match
     * (titulaires) et les moyennes des 5 titulaires en parallèle. Chaque morceau est facultatif ;
     * null seulement si ni le calendrier ni l'effectif n'ont pu être lus.
     */
    suspend fun fiche(code: String): FicheEquipe? {
        val c = EquipesNba.codeNba(code)
        if (DivisionNba.de(c) == null) return null
        val id = idEspn(c) ?: return null
        return coroutineScope {
            val enCoursEffectif = async { obtenir(effectifs, id, RequeteEspn.Effectif(id), "effectif") { LectureEspn.effectif(it) } }
            val calendrier = obtenir(calendriers, id, RequeteEspn.Calendrier(id), "calendrier") { LectureEspn.calendrier(it, c, id) }
            val resume = calendrier?.dernier?.idMatch?.takeIf { it.isNotEmpty() }?.let { m ->
                obtenir(resumes, "$m/$c", RequeteEspn.Resume(m), "résumé") { LectureEspn.resume(it, c, id) }
            }
            val titulaires = resume?.titulaires.orEmpty()
            val stats = titulaires.map { j ->
                async { j.id?.let { idJ -> obtenir(moyennes, idJ, RequeteEspn.Joueur(idJ), "moyennes") { LectureEspn.moyennes(it) } } }
            }.awaitAll()
            val effectif = enCoursEffectif.await()
            if (calendrier == null && effectif == null) {
                journal("nba carte : fiche indisponible")
                return@coroutineScope null
            }
            assembler(c, calendrier, effectif, resume, stats)
        }
    }

    private fun assembler(
        code: String,
        calendrier: CalendrierLu?,
        effectif: EffectifLu?,
        resume: ResumeLu?,
        stats: List<MoyennesLues?>,
    ): FicheEquipe {
        val joueurs = effectif?.joueurs.orEmpty()
        val parId = joueurs.filter { it.id != null }.associateBy { it.id }
        // Zones des blessures : effectif (rarement détaillé), sinon résumé du dernier match, par
        // identifiant (par nom seulement sans identifiant : deux « J. Williams » chez le Thunder)
        val blessuresResume = resume?.blessures.orEmpty()
        fun zoneResume(id: String?, nom: String): String? {
            val trouvee = if (id != null) blessuresResume.firstOrNull { it.id == id } else null
            return (trouvee ?: blessuresResume.firstOrNull { (id == null || it.id == null) && it.nom == nom })?.zone
        }
        val blessures = if (effectif != null) {
            joueurs.filter { it.blessure != null }.map { j ->
                BlessureNba(j.nom, LectureEspn.statut(j.blessure), LectureEspn.zone(j.zone ?: zoneResume(j.id, j.nom)))
            }
        } else {
            blessuresResume.map { BlessureNba(it.nom, LectureEspn.statut(it.statut), LectureEspn.zone(it.zone)) }
        }
        val titulaires = resume?.titulaires.orEmpty().mapIndexed { i, t ->
            val m = stats.getOrNull(i)
            val membre = t.id?.let { parId[it] }
            JoueurNba(
                nom = t.nom,
                numero = t.numero ?: membre?.numero,
                poste = t.poste ?: membre?.poste,
                blesse = membre?.blessure != null,
                minutes = m?.minutes,
                points = m?.points,
                rebonds = m?.rebonds,
                passes = m?.passes,
            )
        }
        return FicheEquipe(
            code = code,
            nomComplet = equipes[code]?.nom ?: calendrier?.nomEquipe ?: effectif?.nomEquipe ?: EquipesNba.de(code).nom.ifEmpty { code },
            dernier = calendrier?.dernier?.match,
            prochain = calendrier?.prochain?.match,
            titulaires = titulaires,
            effectif = joueurs.map { JoueurNba(it.nom, it.numero, it.poste, it.blessure != null, null, null, null, null) },
            blessures = blessures,
        )
    }

    /** Valeur en cache, sinon requête et lecture sur [filReseau] ; échec : null (rien n'est gardé). */
    private suspend fun <V : Any> obtenir(
        cache: CacheTemporise<String, V>,
        cle: String,
        requete: RequeteEspn,
        nom: String,
        lecture: (String) -> V,
    ): V? {
        cache.lire(cle)?.let { return it }
        val v = withContext(filReseau) { lire(requete, nom, lecture) } ?: return null
        cache.mettre(cle, v)
        return v
    }

    /** Requête puis lecture (bloquant) ; échec noté dans le journal (type d'événement et code HTTP). */
    private fun <V : Any> lire(requete: RequeteEspn, nom: String, lecture: (String) -> V): V? {
        val s = if (nom.endsWith("s")) "s" else ""
        return when (val r = chargeur.charger(requete)) {
            is ReponseEspn.Echec -> {
                journal("nba carte : $nom indisponible$s" + (r.code?.let { " ($it)" } ?: ""))
                null
            }
            is ReponseEspn.Ok -> try {
                lecture(r.texte)
            } catch (e: Exception) {
                journal("nba carte : $nom illisible$s")
                null
            } catch (e: OutOfMemoryError) {
                journal("nba carte : $nom trop grand$s")
                null
            }
        }
    }

    /** Tests : oublie les fiches en mémoire. */
    fun viderFiches() {
        calendriers.vider()
        effectifs.vider()
        resumes.vider()
        moyennes.vider()
    }
}
