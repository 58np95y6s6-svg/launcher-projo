package fr.projo.launcher.boxscore

/*
 * Paris suivis (docs/telecommande.md, « Paris suivis » et « Combinés ») : combinés et leurs lignes
 * gardés en mémoire sur la TV, et leur état d'après les scores montrés (décalés). Sans Android,
 * testé sur la JVM.
 */

/** État d'une ligne (`nba.paris[].etat`) ou d'un combiné (`nba.groupes[].etat`, jamais `non_suivi`). */
enum class EtatPari(val id: String) {
    ATTENTE("attente"),
    EN_COURS("en_cours"),
    GAGNE("gagne"),
    PERDU("perdu"),
    NON_SUIVI("non_suivi"),
}

/**
 * Une ligne dans `nba.paris` : `{id, libelle, etat, match?, valeur?, seuil?, groupe}`. [valeur] :
 * points du joueur, écart de l'équipe (vainqueur, handicap), total, ou catégories à 10 ou plus
 * (double-double) ; [seuil] : absent pour « vainqueur ». [handicap] (TV seulement, jamais envoyé) :
 * [valeur] est l'écart signé et [seuil] l'écart à dépasser (montrés « +3 / 15,5 »). [groupe] :
 * identifiant du combiné de la ligne (« g1 »).
 */
data class PariEtat(
    val id: String,
    val libelle: String,
    val etat: EtatPari,
    val match: String? = null,
    val valeur: Int? = null,
    val seuil: Double? = null,
    val handicap: Boolean = false,
    val groupe: String? = null,
)

/**
 * Un combiné dans `nba.groupes` : `{id, nom, cote?, etat, gagnees, suivies, nonSuivies, ouvert}`.
 * [cote] : cote totale telle que lue sur le ticket (« 5,10 »), null sinon ; [gagnees] / [suivies] :
 * lignes gagnées / lignes suivies (« 2/5 ») ; [ouvert] : le combiné ouvert (un seul).
 */
data class GroupeEtat(
    val id: String,
    val nom: String,
    val cote: String?,
    val etat: EtatPari,
    val gagnees: Int,
    val suivies: Int,
    val nonSuivies: Int,
    val ouvert: Boolean,
)

/** Lignes de pari (ordre d'ajout, combiné par combiné) et combinés (ordre d'ajout), évalués ensemble. */
data class EtatParis(val lignes: List<PariEtat>, val groupes: List<GroupeEtat>) {
    companion object {
        val VIDE = EtatParis(emptyList(), emptyList())
    }
}

/**
 * Ce que la TV montre (décalé) pour juger les lignes : [matchs] du jour (le match affiché avec
 * le score de son box score s'il est plus récent), [boxs] derniers box scores montrés, le plus
 * récent d'abord.
 */
class DonneesParis(val matchs: List<MatchNba>, val boxs: List<BoxScore>) {
    /** Le match [id] est terminé, d'après le tableau du jour ou un box score gardé. */
    fun termine(id: String): Boolean =
        matchs.any { it.id == id && it.etat == EtatMatch.TERMINE } || boxs.any { it.match.id == id && it.match.etat == EtatMatch.TERMINE }
}

/**
 * Paris de la TV (docs/telecommande.md, « Combinés ») : chaque collage est un combiné, ses lignes
 * restent groupées. En mémoire seulement (jamais sur disque) : 10 combinés et 40 lignes au plus
 * dans l'ordre d'ajout (au-delà, le plus ancien combiné part), retirés 12 h après leur ajout. Un
 * seul combiné ouvert (le dernier ajouté, ou celui choisi par [ouvrir]). Identifiants « p1 »…
 * (lignes) et « g1 »… (combinés, nommés « Combiné 1 »…) jamais réutilisés. Horloge monotone en
 * ms, un seul fil.
 */
class SuiviParis {

    companion object {
        const val GROUPES_MAX = 10
        const val LIGNES_MAX = 40
        const val DUREE_MS = 12 * 60 * 60_000L

        /** Ligne qui commence un nouveau combiné dans un collage. */
        private val R_SEPARATEUR = Regex("""^\s*(?:combin[éeÉ]e?\s*(?:n\s*°|no\.?|#)?\s*\d+\b|[-=_]{3,}\s*$)""", RegexOption.IGNORE_CASE)
    }

    /**
     * Résultat d'un ajout (journal : nombres seulement) : lignes lues, ajoutées, dont non suivies ;
     * [groupe] : le combiné créé, null si rien n'est ajouté (aucune ligne lue, ou combiné identique
     * déjà présent).
     */
    data class Ajout(val lues: Int, val ajoutees: Int, val nonSuivies: Int, val groupe: String? = null)

    private class Suivie(val id: String, val ligne: LignePari)

    private class Combine(val id: String, val nom: String, val cote: String?, val lignes: List<Suivie>, val ajout: Long) {
        val cles: Set<String> = lignes.map { it.ligne.cle }.toSet()
    }

    private val combines = ArrayList<Combine>()
    private var numeroLigne = 0
    private var numeroCombine = 0

    /** Identifiant du combiné ouvert ; null sans combiné. */
    var ouvert: String? = null
        private set

    /** Nombre de lignes gardées (tous combinés). */
    val nombre: Int get() = combines.sumOf { it.lignes.size }

    /** Nombre de combinés gardés. */
    val nombreCombines: Int get() = combines.size

    /**
     * Lit [texte] et en fait un nouveau combiné, qui devient le combiné ouvert : lignes reconnues
     * sans doublon dans ce combiné (une même ligne peut être dans deux combinés). Rien n'est ajouté
     * sans ligne reconnue, ni si un combiné a déjà exactement ces lignes.
     */
    fun ajouter(texte: String, t: Long): Ajout {
        // Plusieurs combinés collés d'un coup, séparés par « Combiné 1 », « Combiné 2 »… ou « --- » :
        // un combiné par morceau, dans l'ordre
        val morceaux = decouper(texte)
        if (morceaux.size <= 1) return ajouterUn(texte, t)
        var lues = 0
        var ajoutees = 0
        var nonSuivies = 0
        var groupe: String? = null
        for (m in morceaux) {
            val a = ajouterUn(m, t)
            lues += a.lues
            ajoutees += a.ajoutees
            nonSuivies += a.nonSuivies
            if (a.groupe != null) groupe = a.groupe
        }
        return Ajout(lues, ajoutees, nonSuivies, groupe)
    }

    /**
     * Morceaux d'un collage : une ligne « Combiné 2 » (« Combiné n°2 · cote 3,10 »…) ou « --- »
     * commence un nouveau combiné (elle reste en tête de son morceau : sa cote est lue). Pas
     * « Combiné (2) » de Betclic, qui est l'en-tête d'un seul ticket.
     */
    internal fun decouper(texte: String): List<String> {
        val morceaux = mutableListOf<MutableList<String>>(mutableListOf())
        for (ligne in texte.lines()) {
            if (R_SEPARATEUR.containsMatchIn(ligne) && morceaux.last().any { it.isNotBlank() }) morceaux += mutableListOf<String>()
            morceaux.last() += ligne
        }
        return morceaux.map { it.joinToString("\n") }.filter { it.isNotBlank() }
    }

    private fun ajouterUn(texte: String, t: Long): Ajout {
        expirer(t)
        val lues = ParisNba.lire(texte).distinctBy { it.cle }
        if (lues.isEmpty()) return Ajout(0, 0, 0)
        val cles = lues.map { it.cle }.toSet()
        if (combines.any { it.cles == cles }) return Ajout(lues.size, 0, 0)
        numeroCombine++
        val lignes = lues.map { l ->
            numeroLigne++
            Suivie("p$numeroLigne", l)
        }
        val c = Combine("g$numeroCombine", "Combiné $numeroCombine", ParisNba.cote(texte), lignes, t)
        combines += c
        ouvert = c.id
        // Au-delà des limites, les plus anciens partent (jamais celui-ci : 20 lignes au plus)
        while (combines.size > 1 && (combines.size > GROUPES_MAX || nombre > LIGNES_MAX)) combines.removeAt(0)
        return Ajout(lues.size, lignes.size, lues.count { it is LignePari.NonSuivie }, c.id)
    }

    /** Retire le combiné [groupe] (inconnu : rien), ou tous sans [groupe] ; true si un combiné est parti. */
    fun effacer(groupe: String? = null): Boolean {
        val avant = combines.size
        if (groupe == null) combines.clear() else combines.removeAll { it.id == groupe }
        majOuvert()
        return combines.size != avant
    }

    /** [groupe] devient le combiné ouvert ; false s'il est inconnu (rien ne change). */
    fun ouvrir(groupe: String): Boolean {
        if (combines.none { it.id == groupe }) return false
        ouvert = groupe
        return true
    }

    /** Retire les combinés ajoutés il y a 12 h ou plus ; true si un combiné est parti. */
    fun expirer(t: Long): Boolean {
        val parti = combines.removeAll { t - it.ajout >= DUREE_MS }
        majOuvert()
        return parti
    }

    /** Le combiné ouvert a été retiré : le plus récent devient le combiné ouvert. */
    private fun majOuvert() {
        if (combines.none { it.id == ouvert }) ouvert = combines.lastOrNull()?.id
    }

    /** Lignes (ordre d'ajout) et combinés pour la TV et pour `nba`. */
    fun etats(d: DonneesParis): EtatParis {
        if (combines.isEmpty()) return EtatParis.VIDE
        val lignes = ArrayList<PariEtat>(nombre)
        val groupes = ArrayList<GroupeEtat>(combines.size)
        for (c in combines) {
            val e = c.lignes.map { EvaluationParis.evaluer(it.id, it.ligne, d).copy(groupe = c.id) }
            lignes += e
            groupes += GroupeEtat(
                id = c.id,
                nom = c.nom,
                cote = c.cote,
                etat = etatCombine(e, d),
                gagnees = e.count { it.etat == EtatPari.GAGNE },
                suivies = e.count { it.etat != EtatPari.NON_SUIVI },
                nonSuivies = e.count { it.etat == EtatPari.NON_SUIVI },
                ouvert = c.id == ouvert,
            )
        }
        return EtatParis(lignes, groupes)
    }

    /**
     * État d'un combiné d'après ses lignes suivies (les lignes `non_suivi` ne comptent pas) :
     * perdu dès qu'une ligne est perdue ; gagné quand toutes sont gagnées ; en attente si toutes
     * sont en attente (ou sans ligne suivie) ; en cours sinon. Une ligne remboursée (en attente,
     * son match terminé : joueur qui n'a pas joué, handicap nul) ne compte pas, comme dans les
     * applis de paris ; toutes remboursées : en attente.
     */
    private fun etatCombine(lignes: List<PariEtat>, d: DonneesParis): EtatPari {
        val suivies = lignes.filter { it.etat != EtatPari.NON_SUIVI }
        if (suivies.any { it.etat == EtatPari.PERDU }) return EtatPari.PERDU
        val comptees = suivies.filterNot { it.etat == EtatPari.ATTENTE && it.match != null && d.termine(it.match) }
        return when {
            comptees.isEmpty() -> EtatPari.ATTENTE
            comptees.all { it.etat == EtatPari.GAGNE } -> EtatPari.GAGNE
            comptees.all { it.etat == EtatPari.ATTENTE } -> EtatPari.ATTENTE
            else -> EtatPari.EN_COURS
        }
    }
}

/**
 * État d'une ligne d'après les données montrées (docs/telecommande.md) :
 * - « plus de N » : gagnée dès que la valeur dépasse N, perdue si le match se termine sans ;
 * - « moins de N » : perdue dès que la valeur atteint N (dépasse N,5), gagnée à la fin sinon ;
 * - « vainqueur » : en cours pendant le match (valeur : écart, positif si l'équipe mène), gagnée
 *   ou perdue à la fin ; « temps réglementaire » : perdue dès la prolongation ;
 * - handicap : en cours pendant le match (valeur : écart ; seuil : écart à dépasser, -h), gagné
 *   si l'écart final plus le handicap est positif, perdu s'il est négatif, en attente s'il est
 *   nul (handicap entier, remboursé) ; jamais conclu avant la fin ;
 * - double-double, triple-double : gagné dès 2 (3) catégories à 10 ou plus, perdu à la fin sinon ;
 *   « Non » : l'inverse ;
 * - joueur ou équipe pas encore trouvé dans un match, match pas commencé : en attente ;
 * - joueur qui n'a pas joué, match terminé : en attente (pari remboursé par les applis).
 */
object EvaluationParis {

    fun evaluer(id: String, l: LignePari, d: DonneesParis): PariEtat = when (l) {
        is LignePari.NonSuivie -> PariEtat(id, l.texte, EtatPari.NON_SUIVI)
        is LignePari.Joueur -> joueur(id, l, d)
        is LignePari.Vainqueur -> vainqueur(id, l, d)
        is LignePari.TotalEquipe -> totalEquipe(id, l, d)
        is LignePari.TotalMatch -> totalMatch(id, l, d)
        is LignePari.Handicap -> handicap(id, l, d)
        is LignePari.DoubleDouble -> doubles(id, l, d)
    }

    /** Joueur [nom] dans les box scores montrés (celui du match du ticket d'abord) ; null : pas encore trouvé. */
    private fun trouver(nom: String, contexte: Set<String>, d: DonneesParis): Pair<BoxScore, JoueurNba>? {
        val match = matchDuContexte(contexte, d.matchs)
        val trouves = d.boxs.mapNotNull { b -> NomsJoueurs.trouver(nom, b.ext.joueurs + b.dom.joueurs)?.let { b to it } }
        return trouves.firstOrNull { it.first.match.id == match?.id } ?: trouves.firstOrNull()
    }

    /** Match d'un joueur pas encore trouvé : celui du ticket, sauf si son box score (chargé) ne le compte pas. */
    private fun matchEnAttente(contexte: Set<String>, d: DonneesParis): String? {
        val match = matchDuContexte(contexte, d.matchs) ?: return null
        val charge = d.boxs.any { it.match.id == match.id && (it.ext.joueurs.isNotEmpty() || it.dom.joueurs.isNotEmpty()) }
        return match.id.takeUnless { charge }
    }

    private fun joueur(id: String, l: LignePari.Joueur, d: DonneesParis): PariEtat {
        val (b, j) = trouver(l.nom, l.contexte, d)
            ?: return PariEtat(id, libelleJoueur(NomsJoueurs.court(l.nom), l), EtatPari.ATTENTE, matchEnAttente(l.contexte, d), null, l.seuil)
        val libelle = libelleJoueur(j.nom, l)
        val fini = b.match.etat == EtatMatch.TERMINE
        if (fini && !j.aJoue) return PariEtat(id, libelle, EtatPari.ATTENTE, b.match.id, null, l.seuil)
        val v = l.stat.de(j)
        return PariEtat(id, libelle, issue(v, l.sens, l.seuil, fini), b.match.id, v, l.seuil)
    }

    private fun vainqueur(id: String, l: LignePari.Vainqueur, d: DonneesParis): PariEtat {
        val libelle = nomEquipe(l.equipe) + " vainqueurs" + if (l.reglementaire) " (temps régl.)" else ""
        val m = matchDe(l.equipe, d.matchs) ?: return PariEtat(id, libelle, EtatPari.ATTENTE)
        if (m.etat == EtatMatch.A_VENIR) return PariEtat(id, libelle, EtatPari.ATTENTE, m.id)
        val ecart = if (m.ext.code == l.equipe) m.ext.score - m.dom.score else m.dom.score - m.ext.score
        val etat = when {
            // Égalité à la fin du temps réglementaire : pari perdu dès la prolongation
            l.reglementaire && m.periode >= 5 -> EtatPari.PERDU
            m.etat == EtatMatch.TERMINE -> if (ecart > 0) EtatPari.GAGNE else EtatPari.PERDU
            else -> EtatPari.EN_COURS
        }
        return PariEtat(id, libelle, etat, m.id, ecart)
    }

    private fun totalEquipe(id: String, l: LignePari.TotalEquipe, d: DonneesParis): PariEtat {
        val libelle = nomEquipe(l.equipe) + " · " + l.sens.texte + " " + texteNombre(l.seuil) + " pts"
        val m = matchDe(l.equipe, d.matchs) ?: return PariEtat(id, libelle, EtatPari.ATTENTE, null, null, l.seuil)
        if (m.etat == EtatMatch.A_VENIR) return PariEtat(id, libelle, EtatPari.ATTENTE, m.id, null, l.seuil)
        val v = if (m.ext.code == l.equipe) m.ext.score else m.dom.score
        return PariEtat(id, libelle, issue(v, l.sens, l.seuil, m.etat == EtatMatch.TERMINE), m.id, v, l.seuil)
    }

    private fun totalMatch(id: String, l: LignePari.TotalMatch, d: DonneesParis): PariEtat {
        val libelle = "Total du match · " + l.sens.texte + " " + texteNombre(l.seuil) + " pts"
        val m = matchDuContexte(l.contexte, d.matchs) ?: return PariEtat(id, libelle, EtatPari.ATTENTE, null, null, l.seuil)
        if (m.etat == EtatMatch.A_VENIR) return PariEtat(id, libelle, EtatPari.ATTENTE, m.id, null, l.seuil)
        val v = m.ext.score + m.dom.score
        return PariEtat(id, libelle, issue(v, l.sens, l.seuil, m.etat == EtatMatch.TERMINE), m.id, v, l.seuil)
    }

    private fun handicap(id: String, l: LignePari.Handicap, d: DonneesParis): PariEtat {
        val libelle = nomEquipe(l.equipe) + " · handicap " + texteSigne(l.handicap)
        val seuil = -l.handicap
        val m = matchDe(l.equipe, d.matchs) ?: return PariEtat(id, libelle, EtatPari.ATTENTE, null, null, seuil, handicap = true)
        if (m.etat == EtatMatch.A_VENIR) return PariEtat(id, libelle, EtatPari.ATTENTE, m.id, null, seuil, handicap = true)
        val ecart = if (m.ext.code == l.equipe) m.ext.score - m.dom.score else m.dom.score - m.ext.score
        val couvert = ecart + l.handicap
        val etat = when {
            m.etat != EtatMatch.TERMINE -> EtatPari.EN_COURS
            couvert > 0.0 -> EtatPari.GAGNE
            couvert < 0.0 -> EtatPari.PERDU
            else -> EtatPari.ATTENTE
        }
        return PariEtat(id, libelle, etat, m.id, ecart, seuil, handicap = true)
    }

    private fun doubles(id: String, l: LignePari.DoubleDouble, d: DonneesParis): PariEtat {
        val seuil = l.categories.toDouble()
        val (b, j) = trouver(l.nom, l.contexte, d)
            ?: return PariEtat(id, libelleDoubles(NomsJoueurs.court(l.nom), l), EtatPari.ATTENTE, matchEnAttente(l.contexte, d), null, seuil)
        val libelle = libelleDoubles(j.nom, l)
        val fini = b.match.etat == EtatMatch.TERMINE
        if (fini && !j.aJoue) return PariEtat(id, libelle, EtatPari.ATTENTE, b.match.id, null, seuil)
        val v = categoriesADix(j)
        val atteint = v >= l.categories
        val etat = when {
            atteint -> if (l.non) EtatPari.PERDU else EtatPari.GAGNE
            fini -> if (l.non) EtatPari.GAGNE else EtatPari.PERDU
            else -> EtatPari.EN_COURS
        }
        return PariEtat(id, libelle, etat, b.match.id, v, seuil)
    }

    /** Catégories à 10 ou plus : points, rebonds, passes, interceptions, contres. */
    fun categoriesADix(j: JoueurNba): Int = listOf(j.points, j.rebonds, j.passes, j.interceptions, j.contres).count { it >= 10 }

    /** « plus de N » gagnée dès que la valeur dépasse N ; « moins de N » perdue dès qu'elle l'atteint. */
    private fun issue(v: Int, sens: Sens, seuil: Double, fini: Boolean): EtatPari = when (sens) {
        Sens.PLUS -> when {
            v > seuil -> EtatPari.GAGNE
            fini -> EtatPari.PERDU
            else -> EtatPari.EN_COURS
        }
        Sens.MOINS -> when {
            v >= seuil -> EtatPari.PERDU
            fini -> EtatPari.GAGNE
            else -> EtatPari.EN_COURS
        }
    }

    /** Match du jour d'une équipe (celui en cours d'abord). */
    private fun matchDe(code: String, matchs: List<MatchNba>): MatchNba? =
        matchs.filter { it.ext.code == code || it.dom.code == code }.minByOrNull { ordre(it) }

    /** Match des équipes du ticket : les deux si connues, sinon celle qui l'est ; null sans contexte. */
    private fun matchDuContexte(contexte: Set<String>, matchs: List<MatchNba>): MatchNba? {
        if (contexte.isEmpty()) return null
        return matchs.filter { m -> contexte.all { it == m.ext.code || it == m.dom.code } || (contexte.size == 1 && contexte.first().let { it == m.ext.code || it == m.dom.code }) }
            .minByOrNull { ordre(it) }
    }

    private fun ordre(m: MatchNba): Int = when (m.etat) {
        EtatMatch.EN_COURS -> 0
        EtatMatch.TERMINE -> 1
        EtatMatch.A_VENIR -> 2
    }

    private fun libelleJoueur(nom: String, l: LignePari.Joueur) =
        nom + " · " + l.sens.texte + " " + texteNombre(l.seuil) + " " + l.stat.unite

    private fun libelleDoubles(nom: String, l: LignePari.DoubleDouble) =
        nom + " · " + (if (l.categories >= 3) "triple-double" else "double-double") + if (l.non) " (non)" else ""

    /** Handicap avec son signe : « −15,5 » (vrai signe moins), « +6,5 ». */
    private fun texteSigne(h: Double): String = if (h < 0) "\u2212" + texteNombre(-h) else "+" + texteNombre(h)

    private fun nomEquipe(code: String): String = EquipesNba.de(code).nom.ifEmpty { code }
}
