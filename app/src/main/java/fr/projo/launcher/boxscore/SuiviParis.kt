package fr.projo.launcher.boxscore

/*
 * Paris suivis (docs/telecommande.md, « Paris suivis ») : lignes gardées en mémoire sur la TV et
 * leur état d'après les scores montrés (décalés). Sans Android, testé sur la JVM.
 */

/** État d'une ligne (`nba.paris[].etat`). */
enum class EtatPari(val id: String) {
    ATTENTE("attente"),
    EN_COURS("en_cours"),
    GAGNE("gagne"),
    PERDU("perdu"),
    NON_SUIVI("non_suivi"),
}

/**
 * Une ligne dans `nba.paris` : `{id, libelle, etat, match?, valeur?, seuil?}`. [valeur] : points du
 * joueur, écart de l'équipe (vainqueur, handicap), total, ou catégories à 10 ou plus
 * (double-double) ; [seuil] : absent pour « vainqueur ». [handicap] (TV seulement, jamais envoyé) :
 * [valeur] est l'écart signé et [seuil] l'écart à dépasser (montrés « +3 / 15,5 »).
 */
data class PariEtat(
    val id: String,
    val libelle: String,
    val etat: EtatPari,
    val match: String? = null,
    val valeur: Int? = null,
    val seuil: Double? = null,
    val handicap: Boolean = false,
)

/**
 * Ce que la TV montre (décalé) pour juger les lignes : [matchs] du jour (le match affiché avec
 * le score de son box score s'il est plus récent), [boxs] derniers box scores montrés, le plus
 * récent d'abord.
 */
class DonneesParis(val matchs: List<MatchNba>, val boxs: List<BoxScore>)

/**
 * Lignes de pari de la TV : en mémoire seulement (jamais sur disque), 20 au plus dans l'ordre
 * d'ajout (au-delà, les plus anciennes partent), sans doublon, retirées 12 h après leur ajout.
 * Horloge monotone en ms, un seul fil.
 */
class SuiviParis {

    companion object {
        const val LIGNES_MAX = 20
        const val DUREE_MS = 12 * 60 * 60_000L
    }

    /** Résultat d'un ajout (journal : nombres seulement) : lignes lues, ajoutées, dont non suivies. */
    data class Ajout(val lues: Int, val ajoutees: Int, val nonSuivies: Int)

    private class Suivie(val id: String, val ligne: LignePari, val ajout: Long)

    private val lignes = ArrayList<Suivie>()
    private var numero = 0

    val nombre: Int get() = lignes.size

    /** Lit [texte] et ajoute les lignes reconnues qui ne sont pas déjà là. */
    fun ajouter(texte: String, t: Long): Ajout {
        expirer(t)
        val lues = ParisNba.lire(texte)
        var ajoutees = 0
        var nonSuivies = 0
        for (l in lues) {
            if (lignes.any { it.ligne.cle == l.cle }) continue
            numero++
            lignes += Suivie("p$numero", l, t)
            ajoutees++
            if (l is LignePari.NonSuivie) nonSuivies++
        }
        while (lignes.size > LIGNES_MAX) lignes.removeAt(0)
        return Ajout(lues.size, ajoutees, nonSuivies)
    }

    /** Retire toutes les lignes ; true s'il y en avait. */
    fun effacer(): Boolean {
        val avant = lignes.isNotEmpty()
        lignes.clear()
        return avant
    }

    /** Retire les lignes ajoutées il y a 12 h ou plus ; true si une ligne est partie. */
    fun expirer(t: Long): Boolean = lignes.removeAll { t - it.ajout >= DUREE_MS }

    /** Lignes de pari pour la TV et pour `nba` (ordre d'ajout). */
    fun etats(d: DonneesParis): List<PariEtat> = lignes.map { EvaluationParis.evaluer(it.id, it.ligne, d) }
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
