package fr.projo.launcher.boxscore

/** Format du box score sur la TV (`nba.affichage`). */
enum class Affichage(val id: String) {
    CACHE("cache"),
    RESUME("resume"),
    DETAILS("details");

    companion object {
        /** Format demandé par le téléphone ; null si inconnu. */
        fun de(id: Any?): Affichage? = entries.firstOrNull { it.id == id }
    }
}

/** Une équipe dans `nba.matchs` : `{code, nom, score, couleur, texte}`. */
data class EquipeEtat(val code: String, val nom: String, val score: Int, val couleur: String, val texte: String)

/** Un match dans `nba.matchs` : `{id, ext, dom, statut, direct}`. */
data class MatchEtat(val id: String, val ext: EquipeEtat, val dom: EquipeEtat, val statut: String, val direct: Boolean)

/** État envoyé au téléphone (`nba`, docs/telecommande.md). */
data class EtatNba(
    val matchs: List<MatchEtat>,
    val choisi: String?,
    val affichage: Affichage,
    val decalage: Int,
    /** `accessibilite`, `source` ou null. */
    val erreur: String?,
) {
    companion object {
        const val ERREUR_ACCESSIBILITE = "accessibilite"
        const val ERREUR_SOURCE = "source"
    }
}

/**
 * Ce que montre la fenêtre de la TV : rien ([affichage] = CACHE), le box score du match choisi
 * ([box]), ou un texte d'attente ([attente] : « Synchronisation avec la vidéo… »…).
 * [equipe] : onglet des détails (0 : extérieur, 1 : domicile).
 */
data class VueTv(val affichage: Affichage, val box: BoxScore?, val attente: String?, val equipe: Int) {
    companion object {
        val CACHEE = VueTv(Affichage.CACHE, null, null, 0)
    }
}

/**
 * Suivi des scores NBA, sans Android (testé sur la JVM) : quoi charger et quand, repli de la
 * source, décalage, choix du match, format affiché. Le pilote (BoxScoreNba) appelle
 * [prochaine] pour savoir quelle requête faire, puis donne la réponse ([tableauRecu],
 * [boxRecu]). Toutes les heures : horloge monotone, en ms. Un seul fil.
 *
 * Rythme (docs/telecommande.md, « Sources (TV) ») : le tableau du jour toutes les 30 s
 * seulement si un téléphone est abonné ou si le box score est affiché ; le box score du match
 * choisi toutes les 15 s seulement s'il est affiché ; sinon rien.
 *
 * Décalage : chaque réponse est gardée avec son heure d'arrivée ; on montre la plus récente
 * arrivée il y a au moins [decalage] secondes. Avant la première, la fenêtre de la TV attend
 * (« Synchronisation avec la vidéo… ») et `nba` garde les dernières valeurs montrées.
 */
class SuiviNba {

    /** Requête à faire maintenant, ou attente. */
    sealed class Etape {
        data class Tableau(val source: SourceNba) : Etape()

        /** Box score de [match] (tel que donné par le dernier tableau de [source]). */
        data class Box(val source: SourceNba, val match: MatchNba) : Etape()
        data class Attendre(val jusqua: Long) : Etape()

        /** Rien à charger : personne n'est abonné et rien n'est affiché. */
        data object Arret : Etape()
    }

    /** Réponse d'une source. */
    sealed class Reponse<out T> {
        data class Ok<T>(val donnees: T) : Reponse<T>()

        /** Box score pas encore publié (403 ou 404 avant l'entre-deux) : pas un échec. */
        data object PasCommence : Reponse<Nothing>()

        /** Erreur HTTP ([code]), délai dépassé ou JSON illisible ([code] null). */
        data class Echec(val code: Int? = null) : Reponse<Nothing>()
    }

    private class Instantane<T>(val arrivee: Long, val donnees: T)

    companion object {
        const val TABLEAU_MS = 30_000L
        const val BOX_MS = 15_000L
        const val ECHECS_AVANT_REPLI = 2
        const val ESSAI_NBA_MS = 10 * 60_000L
        const val MATCHS_MAX = 15

        const val SYNCHRONISATION = "Synchronisation avec la vidéo…"
        const val CHARGEMENT = "Chargement…"
        const val INDISPONIBLE = "Scores NBA indisponibles pour l'instant"
    }

    /** Réglage `nba_decalage` (secondes). */
    var decalage = 0
        set(v) {
            field = v.coerceAtLeast(0)
        }

    /** Un service d'accessibilité d'iBiTV est relié : la fenêtre peut s'afficher par-dessus les applis. */
    var accessibilite = false

    var affichage = Affichage.CACHE
        private set

    /** Onglet des détails : 0 extérieur, 1 domicile. */
    var equipe = 0
        private set

    var source = SourceNba.NBA
        private set

    /** Les deux sources ont échoué (après le repli). */
    var erreurSource = false
        private set

    /** Au moins un téléphone abonné (`nba_etat`). */
    private var abonnes = false

    private var echecsNba = 0
    private var prochainEssaiNba = 0L

    /** Match demandé par le téléphone ; dernier match choisi (gardé tant qu'il reste dans la liste). */
    private var demande: String? = null
    private var memo: String? = null

    /** Dernier tableau reçu (sans décalage) et sa source : identifiants des matchs pour le box score. */
    private var brut: List<MatchNba>? = null
    private var sourceBrut: SourceNba? = null

    /** Réponses pas encore montrées (arrivées il y a moins de [decalage] s), puis la dernière montrée. */
    private val tableaux = ArrayDeque<Instantane<List<MatchNba>>>()
    private var vueTableau: Instantane<List<MatchNba>>? = null
    private var idBox: String? = null
    private val boxs = ArrayDeque<Instantane<BoxScore>>()
    private var vueBox: Instantane<BoxScore>? = null

    private var prochainTableau = 0L
    private var prochainBox = 0L
    private var suiviTableau = false
    private var suiviBox = false

    private val besoinTableau: Boolean get() = abonnes || affichage != Affichage.CACHE

    // ------------------------------------------------------------------ demandes

    /** Un téléphone au moins est abonné, ou plus aucun. */
    fun abonnement(actif: Boolean, t: Long) {
        abonnes = actif
        majSuivi(t)
    }

    /**
     * `nba_afficher` (avec [id] facultatif, choisi d'abord s'il est dans la liste). Sans match ou
     * sans service d'accessibilité, le box score reste caché. Rend le format retenu.
     */
    fun afficher(format: Affichage, id: String?, t: Long): Affichage {
        avancer(t)
        if (id != null) choisir(id, t)
        affichage = when {
            format == Affichage.CACHE -> Affichage.CACHE
            !accessibilite || choisi() == null -> Affichage.CACHE
            else -> format
        }
        majSuivi(t)
        return affichage
    }

    /** Masque le box score (Retour, accueil d'iBiTV, service arrêté, écran éteint). */
    fun cacher(t: Long) {
        affichage = Affichage.CACHE
        majSuivi(t)
    }

    /** `nba_choisir` : ignoré si [id] n'est pas dans la liste. */
    fun choisir(id: String, t: Long): Boolean {
        avancer(t)
        if (liste().none { it.id == id }) return false
        demande = id
        memo = id
        majChoix()
        majSuivi(t)
        return true
    }

    /** ◀ ▶ dans les détails : 0 extérieur, 1 domicile. */
    fun changerEquipe(e: Int) {
        equipe = e.coerceIn(0, 1)
    }

    // ------------------------------------------------------------------ requêtes

    /** Prochaine requête à faire à l'instant [t], ou jusqu'à quand attendre. */
    fun prochaine(t: Long): Etape {
        avancer(t)
        majSuivi(t)
        if (!suiviTableau) return Etape.Arret
        val voulue = sourceVoulue(t)
        if (t >= prochainTableau) return Etape.Tableau(voulue)
        var attente = prochainTableau
        if (suiviBox) {
            val id = idBox
            val m = brut?.firstOrNull { it.id == id }
            // Identifiant du match chez la source actuelle : donné par son dernier tableau (sinon on
            // attend le prochain tableau, déjà avancé au changement de source)
            if (id != null && m != null && sourceBrut == source) {
                if (t >= prochainBox) return Etape.Box(source, m)
                attente = minOf(attente, prochainBox)
            }
        }
        return Etape.Attendre(attente)
    }

    fun tableauRecu(t: Long, src: SourceNba, r: Reponse<List<MatchNba>>) {
        prochainTableau = t + TABLEAU_MS
        when (r) {
            is Reponse.Ok -> {
                reussite(src)
                brut = r.donnees
                sourceBrut = src
                if (suiviTableau) tableaux.addLast(Instantane(t, r.donnees))
            }
            is Reponse.Echec, Reponse.PasCommence -> echec(src, t)
        }
        avancer(t)
    }

    fun boxRecu(t: Long, etape: Etape.Box, r: Reponse<BoxScore>) {
        prochainBox = t + BOX_MS
        val pourLeMatchSuivi = suiviBox && etape.match.id == idBox
        when (r) {
            is Reponse.Ok -> {
                reussite(etape.source)
                if (pourLeMatchSuivi) boxs.addLast(Instantane(t, r.donnees))
            }
            Reponse.PasCommence -> {
                reussite(etape.source)
                if (pourLeMatchSuivi) boxs.addLast(Instantane(t, BoxScore.depuisMatch(etape.match)))
            }
            is Reponse.Echec -> echec(etape.source, t)
        }
        avancer(t)
    }

    /** Instant où la prochaine réponse gardée sera montrée (null : aucune en attente). */
    fun prochaineMaturite(): Long? {
        val d = decalage * 1000L
        return listOfNotNull(tableaux.firstOrNull()?.arrivee, boxs.firstOrNull()?.arrivee).minOrNull()?.plus(d)
    }

    /** [src] a répondu : plus d'erreur ; la source NBA redevient la source principale. */
    private fun reussite(src: SourceNba) {
        erreurSource = false
        if (src == SourceNba.NBA) {
            echecsNba = 0
            source = SourceNba.NBA
        }
    }

    /** Erreur HTTP, délai ou JSON illisible : repli sur ESPN après 2 échecs de suite de la source NBA. */
    private fun echec(src: SourceNba, t: Long) {
        when (src) {
            SourceNba.NBA -> {
                echecsNba++
                if (source == SourceNba.ESPN) {
                    // Nouvel essai de la source NBA raté : encore ESPN, tout de suite
                    prochainEssaiNba = t + ESSAI_NBA_MS
                    prochainTableau = t
                } else if (echecsNba >= ECHECS_AVANT_REPLI) {
                    source = SourceNba.ESPN
                    prochainEssaiNba = t + ESSAI_NBA_MS
                    prochainTableau = t
                }
            }
            SourceNba.ESPN -> erreurSource = true
        }
    }

    /** Sur ESPN, la source NBA est réessayée toutes les 10 minutes (par le tableau du jour). */
    private fun sourceVoulue(t: Long): SourceNba =
        if (source == SourceNba.ESPN && t >= prochainEssaiNba) SourceNba.NBA else source

    // ------------------------------------------------------------------ état montré

    /**
     * Montre les réponses arrivées il y a au moins [decalage] s (la plus récente), puis recalcule
     * le choix du match. Sans réponse assez ancienne, la dernière montrée reste.
     */
    fun avancer(t: Long) {
        val limite = t - decalage * 1000L
        while (tableaux.isNotEmpty() && tableaux.first().arrivee <= limite) {
            val s = tableaux.removeFirst()
            if ((vueTableau?.arrivee ?: Long.MIN_VALUE) <= s.arrivee) vueTableau = s
        }
        while (boxs.isNotEmpty() && boxs.first().arrivee <= limite) {
            val s = boxs.removeFirst()
            if ((vueBox?.arrivee ?: Long.MIN_VALUE) <= s.arrivee) vueBox = s
        }
        majChoix()
        // Plus aucun match à montrer (journée finie) : le box score se cache
        if (affichage != Affichage.CACHE && vueTableau != null && choisi() == null) {
            affichage = Affichage.CACHE
            majSuivi(t)
        }
    }

    /**
     * `nba.matchs` : les matchs en cours par heure de début, plus le match choisi s'il vient de
     * se terminer, tant que le box score est affiché ; 15 au plus.
     */
    private fun liste(): List<MatchNba> {
        val tous = vueTableau?.donnees.orEmpty()
        val fini = memo?.takeIf { affichage != Affichage.CACHE }?.let { id -> tous.firstOrNull { it.id == id && it.etat == EtatMatch.TERMINE } }
        return (tous.filter { it.direct } + listOfNotNull(fini))
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.debut }, { it.id }))
            .take(MATCHS_MAX)
    }

    /** Celui demandé tant qu'il est dans la liste ; sinon le dernier choisi s'il y est encore ; sinon le premier match en cours. */
    private fun choisi(l: List<MatchNba> = liste()): String? {
        val ids = l.map { it.id }
        return demande?.takeIf { it in ids } ?: memo?.takeIf { it in ids } ?: l.firstOrNull()?.id
    }

    /** Le match suivi change : son box score repart de zéro (onglet de l'équipe à l'extérieur). */
    private fun majChoix() {
        val c = choisi()
        if (c != null) memo = c
        if (c != idBox) {
            idBox = c
            boxs.clear()
            vueBox = null
            prochainBox = 0L
            equipe = 0
        }
    }

    /** Début et fin des suivis : un suivi qui (re)commence repart de zéro et charge tout de suite. */
    private fun majSuivi(t: Long) {
        val tableau = besoinTableau
        if (tableau && !suiviTableau) {
            tableaux.clear()
            prochainTableau = t
        }
        suiviTableau = tableau
        val box = affichage != Affichage.CACHE
        if (box && !suiviBox) {
            boxs.clear()
            vueBox = null
            prochainBox = t
            equipe = 0
        }
        suiviBox = box
    }

    /** État `nba` (scores et statuts décalés, comme l'écran de la TV). */
    fun etat(): EtatNba {
        val l = liste()
        val choisi = choisi(l)
        // Le box score (15 s) est plus frais que le tableau (30 s) pour le match choisi
        val frais = if (choisi == null || choisi != idBox) null else
            vueBox?.takeIf { (vueTableau?.arrivee ?: Long.MIN_VALUE) <= it.arrivee }?.donnees?.match
        val matchs = l.map { m ->
            val v = if (frais != null && m.id == choisi) {
                m.copy(
                    ext = m.ext.copy(score = frais.ext.score),
                    dom = m.dom.copy(score = frais.dom.score),
                    etat = frais.etat,
                    periode = frais.periode,
                    horloge = frais.horloge,
                    finDePeriode = frais.finDePeriode,
                )
            } else {
                m
            }
            MatchEtat(v.id, equipeEtat(v.ext), equipeEtat(v.dom), v.statut, v.direct)
        }
        val erreur = when {
            !accessibilite -> EtatNba.ERREUR_ACCESSIBILITE
            erreurSource -> EtatNba.ERREUR_SOURCE
            else -> null
        }
        return EtatNba(matchs, choisi, affichage, decalage, erreur)
    }

    private fun equipeEtat(e: EquipeMatch): EquipeEtat {
        val c = EquipesNba.de(e.code)
        return EquipeEtat(e.code, e.nom.ifEmpty { c.nom }.ifEmpty { e.code }, e.score, c.couleur, c.texte)
    }

    /** Ce que montre la fenêtre de la TV. */
    fun vueTv(): VueTv {
        if (affichage == Affichage.CACHE) return VueTv.CACHEE
        // Gardé seulement pour le match suivi (effacé quand le choix change)
        val box = vueBox?.donnees
        val attente = when {
            box != null -> null
            erreurSource -> INDISPONIBLE
            decalage > 0 -> SYNCHRONISATION
            else -> CHARGEMENT
        }
        return VueTv(affichage, box, attente, equipe)
    }
}
