package fr.projo.launcher.boxscore

import fr.projo.launcher.boxscore.EtatPari.ATTENTE
import fr.projo.launcher.boxscore.EtatPari.EN_COURS
import fr.projo.launcher.boxscore.EtatPari.GAGNE
import fr.projo.launcher.boxscore.EtatPari.NON_SUIVI
import fr.projo.launcher.boxscore.EtatPari.PERDU
import fr.projo.launcher.boxscore.SuiviNba.Etape
import fr.projo.launcher.boxscore.SuiviNba.Reponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paris suivis : état des lignes d'après les scores montrés (gagné dès le seuil franchi, perdu
 * pour de bon, fin du match, écart du vainqueur, attente), lignes gardées (doublons, 20 au plus,
 * 12 h), puis avec SuiviNba : décalage respecté, lignes du match affiché pour la TV, dernier box
 * score gardé quand le match n'est plus affiché.
 */
class SuiviParisTest {

    private fun donnees(vararg b: BoxScore, matchs: List<MatchNba> = b.map { it.match }) = DonneesParis(matchs, b.toList())

    private fun etats(texte: String, d: DonneesParis): List<PariEtat> = SuiviParis().apply { ajouter(texte, 0) }.etats(d)

    private fun une(texte: String, d: DonneesParis): PariEtat = etats(texte, d).single()

    // ------------------------------------------------------------------ états

    @Test
    fun ticketDeLUtilisateurEnCoursDeMatch() {
        assertEquals(
            listOf(
                PariEtat("p1", "76ers vainqueurs", EN_COURS, "PHI-IND", -3, null),
                // Seuil franchi avant la fin : gagné tout de suite
                PariEtat("p2", "T. Maxey · + de 19,5 pts", GAGNE, "PHI-IND", 23, 19.5),
                PariEtat("p3", "K. Caldwell-Pope · + de 9,5 pts", EN_COURS, "PHI-IND", 6, 9.5),
                PariEtat("p4", "I. Zubac · + de 2,5 passes", EN_COURS, "PHI-IND", 2, 2.5),
            ),
            etats(TICKET_BETCLIC, donnees(boxPhiInd())),
        )
    }

    @Test
    fun ticketDeLUtilisateurALaFin() {
        val fin = boxPhiInd(matchPhiInd(112, 108, EtatMatch.TERMINE, 4, 0.0), maxey = 31, kcp = 9, zubacPd = 4)
        assertEquals(
            listOf(
                PariEtat("p1", "76ers vainqueurs", GAGNE, "PHI-IND", 4, null),
                PariEtat("p2", "T. Maxey · + de 19,5 pts", GAGNE, "PHI-IND", 31, 19.5),
                // 9 points pour « plus de 9,5 » : perdu à la fin
                PariEtat("p3", "K. Caldwell-Pope · + de 9,5 pts", PERDU, "PHI-IND", 9, 9.5),
                PariEtat("p4", "I. Zubac · + de 2,5 passes", GAGNE, "PHI-IND", 4, 2.5),
            ),
            etats(TICKET_BETCLIC, donnees(fin)),
        )
        // Défaite : vainqueur perdu, écart négatif
        val defaite = boxPhiInd(matchPhiInd(101, 108, EtatMatch.TERMINE, 4, 0.0))
        assertEquals(PariEtat("p1", "76ers vainqueurs", PERDU, "PHI-IND", -7, null), etats(TICKET_BETCLIC, donnees(defaite)).first())
    }

    @Test
    fun moinsDe() {
        val texte = "Joel Embiid Moins de 9,5 rebonds"
        // Seuil dépassé en cours de match : perdu pour de bon
        assertEquals(PariEtat("p1", "J. Embiid · - de 9,5 rebonds", PERDU, "PHI-IND", 10, 9.5), une(texte, donnees(boxPhiInd(embiidReb = 10))))
        assertEquals(EN_COURS, une(texte, donnees(boxPhiInd(embiidReb = 8))).etat)
        // Pas dépassé à la fin : gagné
        val fin = boxPhiInd(matchPhiInd(etat = EtatMatch.TERMINE, periode = 4), embiidReb = 8)
        assertEquals(GAGNE, une(texte, donnees(fin)).etat)
        // Seuil entier : « moins de 10 » perdu à 10
        assertEquals(PERDU, une("Joel Embiid Moins de 10 rebonds", donnees(boxPhiInd(embiidReb = 10))).etat)
        assertEquals(EN_COURS, une("Joel Embiid Plus de 10 rebonds", donnees(boxPhiInd(embiidReb = 10))).etat)
    }

    @Test
    fun vainqueurEcartEtTempsReglementaire() {
        assertEquals(PariEtat("p1", "Pacers vainqueurs", EN_COURS, "PHI-IND", 3), une("Indiana Pacers\nVainqueur du match", donnees(boxPhiInd())))
        val regl = "Philadelphie 76ers\nRésultat du match (t. rég)"
        assertEquals(PariEtat("p1", "76ers vainqueurs (temps régl.)", EN_COURS, "PHI-IND", -3), une(regl, donnees(boxPhiInd())))
        // Égalité à la fin du temps réglementaire : perdu dès la prolongation, même si l'équipe gagne ensuite
        val prolongation = boxPhiInd(matchPhiInd(118, 115, EtatMatch.EN_COURS, 5, 40.0))
        assertEquals(PERDU, une(regl, donnees(prolongation)).etat)
        assertEquals(EN_COURS, une("Philadelphie 76ers\nVainqueur du match", donnees(prolongation)).etat)
        val finProlongation = boxPhiInd(matchPhiInd(118, 115, EtatMatch.TERMINE, 5, 0.0))
        assertEquals(PERDU, une(regl, donnees(finProlongation)).etat)
        assertEquals(GAGNE, une("Philadelphie 76ers\nVainqueur du match", donnees(finProlongation)).etat)
    }

    @Test
    fun totaux() {
        val texte = "Plus de 221,5\nTotal de points du match\nPhiladelphie 76ers + de 70,5\nTotal de points de l'équipe\nSixers - Pacers"
        assertEquals(
            listOf(
                PariEtat("p1", "Total du match · + de 221,5 pts", EN_COURS, "PHI-IND", 145, 221.5),
                PariEtat("p2", "76ers · + de 70,5 pts", GAGNE, "PHI-IND", 71, 70.5),
            ),
            etats(texte, donnees(boxPhiInd())),
        )
        val fin = boxPhiInd(matchPhiInd(112, 108, EtatMatch.TERMINE, 4))
        // 220 points à la fin : « plus de 221,5 » perdu
        assertEquals(listOf(PERDU, GAGNE), etats(texte, donnees(fin)).map { it.etat })
        assertEquals(PERDU, une("Total de points du match : Moins de 215,5\nSixers - Pacers", donnees(fin)).etat)
        // Total du match sans match connu : en attente, sans match
        assertEquals(PariEtat("p1", "Total du match · + de 221,5 pts", ATTENTE, null, null, 221.5), une("Total de points du match : Plus de 221,5", donnees(boxPhiInd())))
    }

    @Test
    fun attenteAvantLeMatchEtSansBoxScore() {
        // Tableau du jour seulement (match à venir, aucun box score) : lignes en attente, match connu par le ticket
        val aVenir = matchPhiInd(0, 0, EtatMatch.A_VENIR, 0, null)
        val e = etats(TICKET_BETCLIC, DonneesParis(listOf(aVenir), emptyList()))
        assertEquals(PariEtat("p1", "76ers vainqueurs", ATTENTE, "PHI-IND"), e[0])
        assertEquals(PariEtat("p2", "T. Maxey · + de 19,5 pts", ATTENTE, "PHI-IND", null, 19.5), e[1])
        assertTrue(e.all { it.etat == ATTENTE && it.valeur == null && it.match == "PHI-IND" })
        // Box score « pas encore commencé » (sans joueurs) : pareil
        assertTrue(etats(TICKET_BETCLIC, donnees(BoxScore.depuisMatch(aVenir))).all { it.etat == ATTENTE && it.match == "PHI-IND" })
        // Rien du tout : en attente, match inconnu
        assertTrue(etats(TICKET_BETCLIC, DonneesParis(emptyList(), emptyList())).all { it.etat == ATTENTE && it.match == null })
    }

    @Test
    fun joueurAbsentDuMatch() {
        // Box score chargé sans ce joueur : pas de ce match
        val e = une("LeBron James + de 25,5\nNombre de points du joueur (paliers)\nSixers 29 8 37\nPacers 35 4 39", donnees(boxPhiInd()))
        assertEquals(PariEtat("p1", "L. James · + de 25,5 pts", ATTENTE, null, null, 25.5), e)
        // Sans contexte, trouvé dans le box score montré
        assertEquals("PHI-IND", une("Tyrese Maxey + de 19,5 points", donnees(boxPhiInd())).match)
        // Trouvé dans un autre box score gardé
        val lalBos = BoxScore(matchNba("LAL", "BOS", 2_000), EquipeBox(listOf(joueurPari("L. James", "LeBron James", pts = 30)), null), EquipeBox(emptyList(), null), true)
        val trouve = une("LeBron James + de 25,5\nNombre de points du joueur (paliers)", DonneesParis(listOf(matchPhiInd(), lalBos.match), listOf(boxPhiInd(), lalBos)))
        assertEquals(PariEtat("p1", "L. James · + de 25,5 pts", GAGNE, "LAL-BOS", 30, 25.5), trouve)
    }

    @Test
    fun joueurQuiNAPasJoue() {
        val texte = "Joel Embiid Moins de 9,5 rebonds"
        // En cours : pas encore entré, 0
        assertEquals(EN_COURS, une(texte, donnees(boxPhiInd(embiidReb = 0, embiidJoue = false))).etat)
        // Fin du match sans avoir joué : en attente (pari remboursé), sans valeur
        val fin = boxPhiInd(matchPhiInd(etat = EtatMatch.TERMINE, periode = 4), embiidReb = 0, embiidJoue = false)
        assertEquals(PariEtat("p1", "J. Embiid · - de 9,5 rebonds", ATTENTE, "PHI-IND", null, 9.5), une(texte, donnees(fin)))
    }

    @Test
    fun nonSuivi() {
        assertEquals(
            PariEtat("p1", "Philadelphie 76ers (-4,5) · Handicap (prolongations incluses)", NON_SUIVI),
            une("Philadelphie 76ers (-4,5)\nHandicap (prolongations incluses)", donnees(boxPhiInd())),
        )
    }

    @Test
    fun libellesEnMotsEntiers() {
        val texte = listOf(
            "Plus de 19,5 points", "Plus de 5,5 rebonds", "Plus de 4,5 passes décisives", "Plus de 2,5 paniers à 3 points",
            "Plus de 30,5 points + rebonds + passes", "Plus de 25,5 points + rebonds", "Plus de 24,5 points + passes",
            "Plus de 9,5 rebonds + passes",
        ).joinToString("\n") { "Tyrese Maxey $it" }
        assertEquals(
            listOf(
                "T. Maxey · + de 19,5 pts", "T. Maxey · + de 5,5 rebonds", "T. Maxey · + de 4,5 passes",
                "T. Maxey · + de 2,5 paniers à 3 pts", "T. Maxey · + de 30,5 pts + reb. + passes", "T. Maxey · + de 25,5 pts + reb.",
                "T. Maxey · + de 24,5 pts + passes", "T. Maxey · + de 9,5 reb. + passes",
            ),
            etats(texte, DonneesParis(emptyList(), emptyList())).map { it.libelle },
        )
        // Jamais « pd »
        assertTrue(StatPari.entries.none { Regex("""\bpd\b""").containsMatchIn(it.unite) })
    }

    // ------------------------------------------------------------------ lignes gardées

    @Test
    fun doublonsIdentifiantsEtEffacement() {
        val p = SuiviParis()
        assertEquals(SuiviParis.Ajout(4, 4, 0), p.ajouter(TICKET_BETCLIC, 0))
        // Même ticket : rien de nouveau
        assertEquals(SuiviParis.Ajout(4, 0, 0), p.ajouter(TICKET_BETCLIC, 1_000))
        assertEquals(SuiviParis.Ajout(2, 1, 1), p.ajouter("Tyrese Maxey Plus de 19,5 points\nJoel Embiid + de 1,5\nNombre de fautes du joueur", 2_000))
        val e = p.etats(DonneesParis(emptyList(), emptyList()))
        assertEquals(listOf("p1", "p2", "p3", "p4", "p5"), e.map { it.id })
        assertEquals("Joel Embiid + de 1,5 · Nombre de fautes du joueur", e.last().libelle)
        assertTrue(p.effacer())
        assertFalse(p.effacer())
        assertEquals(0, p.nombre)
        // Les identifiants ne reviennent pas
        p.ajouter("Tyrese Maxey Plus de 19,5 points", 3_000)
        assertEquals("p6", p.etats(DonneesParis(emptyList(), emptyList())).single().id)
    }

    @Test
    fun vingtLignesAuPlusLesPlusAnciennesPartent() {
        val p = SuiviParis()
        for (i in 1..25) p.ajouter("Tyrese Maxey Plus de $i,5 points", i.toLong())
        val e = p.etats(DonneesParis(emptyList(), emptyList()))
        assertEquals(SuiviParis.LIGNES_MAX, e.size)
        assertEquals("T. Maxey · + de 6,5 pts", e.first().libelle)
        assertEquals("T. Maxey · + de 25,5 pts", e.last().libelle)
    }

    @Test
    fun retireesDouzeHeuresApresLeurAjout() {
        val p = SuiviParis()
        p.ajouter("Tyrese Maxey Plus de 19,5 points", 0)
        p.ajouter("Joel Embiid Plus de 9,5 rebonds", 3_600_000)
        assertFalse(p.expirer(SuiviParis.DUREE_MS - 1))
        assertTrue(p.expirer(SuiviParis.DUREE_MS))
        assertEquals(listOf("J. Embiid · + de 9,5 rebonds"), p.etats(DonneesParis(emptyList(), emptyList())).map { it.libelle })
        // Ajouté de nouveau : nouvelle ligne
        p.ajouter("Tyrese Maxey Plus de 19,5 points", SuiviParis.DUREE_MS)
        assertEquals(2, p.nombre)
        assertTrue(p.expirer(SuiviParis.DUREE_MS + 3_600_000))
        assertEquals(1, p.nombre)
    }

    // ------------------------------------------------------------------ avec SuiviNba

    private val lalBos = matchNba("LAL", "BOS", 2_000)

    /** Exécute les requêtes dues à [t] avec les réponses données (une étape « Attendre » arrête). */
    private fun SuiviNba.servirAvec(t: Long, tableau: List<MatchNba>, box: (MatchNba) -> Reponse<BoxScore>) {
        repeat(10) {
            when (val e = prochaine(t)) {
                is Etape.Tableau -> tableauRecu(t, e.source, Reponse.Ok(tableau))
                is Etape.Box -> boxRecu(t, e, box(e.match))
                else -> return
            }
        }
        throw AssertionError("requêtes sans fin")
    }

    private fun SuiviNba.servir(t: Long, tableau: List<MatchNba>, box: (MatchNba) -> BoxScore) =
        servirAvec(t, tableau) { Reponse.Ok(box(it)) }

    private fun maxey(s: SuiviNba): PariEtat = s.etat().paris.first { it.libelle.startsWith("T. Maxey") }

    @Test
    fun decalageRespecte() {
        val s = SuiviNba().apply {
            accessibilite = true
            decalage = 30
        }
        s.ajouterParis(TICKET_BETCLIC, 0)
        s.abonnement(true, 0)
        s.servir(0, listOf(matchPhiInd(), lalBos)) { boxPhiInd(maxey = 15) }
        // Rien de montré avant 30 s : en attente, match inconnu
        assertTrue(s.etat().paris.all { it.etat == ATTENTE && it.match == null })
        s.avancer(30_000)
        // Tableau montré : vainqueur en cours ; joueurs en attente (box score pas chargé), match du ticket
        assertEquals(EN_COURS, s.etat().paris[0].etat)
        assertEquals(PariEtat("p2", "T. Maxey · + de 19,5 pts", ATTENTE, "PHI-IND", null, 19.5), maxey(s))
        assertEquals(Affichage.PARI, s.afficher(Affichage.PARI, "PHI-IND", 30_000))
        s.servir(30_000, listOf(matchPhiInd(), lalBos)) { boxPhiInd(maxey = 15) }
        s.avancer(59_999)
        assertEquals(ATTENTE, maxey(s).etat)
        s.avancer(60_000)
        assertEquals(PariEtat("p2", "T. Maxey · + de 19,5 pts", EN_COURS, "PHI-IND", 15, 19.5), maxey(s))
        // 21 points arrivés à 65 s : montrés à 95 s seulement
        s.servir(65_000, listOf(matchPhiInd(), lalBos)) { boxPhiInd(maxey = 21) }
        s.avancer(94_999)
        assertEquals(15, maxey(s).valeur)
        s.avancer(95_000)
        assertEquals(PariEtat("p2", "T. Maxey · + de 19,5 pts", GAGNE, "PHI-IND", 21, 19.5), maxey(s))
    }

    @Test
    fun pariCollEAvantLeCoupDEnvoi() {
        val s = SuiviNba().apply { accessibilite = true }
        // Collé avant tout : ni tableau du jour, ni box score, ni match en cours
        assertEquals(SuiviParis.Ajout(4, 4, 0), s.ajouterParis(TICKET_BETCLIC, 0))
        val avant = s.etat().paris
        assertTrue(avant.all { it.etat == ATTENTE && it.match == null && it.valeur == null })
        assertEquals(listOf(null, 19.5, 9.5, 2.5), avant.map { it.seuil })
        assertEquals(
            listOf("76ers vainqueurs", "T. Maxey · + de 19,5 pts", "K. Caldwell-Pope · + de 9,5 pts", "I. Zubac · + de 2,5 passes"),
            avant.map { it.libelle },
        )
        // Tableau du jour : match à venir, en attente avec le match du ticket ; pas de format pari possible
        val aVenir = matchPhiInd(0, 0, EtatMatch.A_VENIR, 0, null)
        s.abonnement(true, 1_000)
        s.servirAvec(1_000, listOf(aVenir)) { Reponse.PasCommence }
        assertTrue(s.etat().paris.all { it.etat == ATTENTE && it.match == "PHI-IND" && it.valeur == null })
        assertEquals(Affichage.CACHE, s.afficher(Affichage.PARI, "PHI-IND", 1_000))
        // Coup d'envoi deux heures plus tard, box score pas encore publié : joueurs en attente, vainqueur en cours
        val t = 2 * 3_600_000L
        val debut = matchPhiInd(2, 0, EtatMatch.EN_COURS, 1, 700.0)
        s.servirAvec(t, listOf(debut)) { Reponse.PasCommence }
        assertEquals(Affichage.PARI, s.afficher(Affichage.PARI, "PHI-IND", t))
        s.servirAvec(t, listOf(debut)) { Reponse.PasCommence }
        val vue = s.vueTv(t)
        assertEquals(listOf("p1", "p2", "p3", "p4"), vue.paris.map { it.id })
        assertEquals(PariEtat("p1", "76ers vainqueurs", EN_COURS, "PHI-IND", 2), vue.paris[0])
        assertTrue(vue.paris.drop(1).all { it.etat == ATTENTE && it.match == "PHI-IND" })
        // Box score publié : les joueurs sont trouvés
        s.servirAvec(t + 5_000, listOf(debut)) { Reponse.Ok(boxPhiInd(debut, maxey = 2, kcp = 0, zubacPd = 0)) }
        assertEquals(listOf(EN_COURS, EN_COURS, EN_COURS, EN_COURS), s.vueTv(t + 5_000).paris.map { it.etat })
        assertEquals(PariEtat("p2", "T. Maxey · + de 19,5 pts", EN_COURS, "PHI-IND", 2, 19.5), maxey(s))
    }

    @Test
    fun formatPariLignesDuMatchAffiche() {
        val s = SuiviNba().apply { accessibilite = true }
        s.ajouterParis(TICKET_BETCLIC + "\nLeBron James + de 25,5 points", 0)
        s.abonnement(true, 0)
        s.servir(0, listOf(matchPhiInd(), lalBos)) { if (it.id == "PHI-IND") boxPhiInd() else BoxScore.depuisMatch(it) }
        assertEquals(Affichage.PARI, s.afficher(Affichage.PARI, "PHI-IND", 0))
        s.servir(0, listOf(matchPhiInd(), lalBos)) { if (it.id == "PHI-IND") boxPhiInd() else BoxScore.depuisMatch(it) }
        val vue = s.vueTv(0)
        assertEquals(Affichage.PARI, vue.affichage)
        assertEquals("PHI-IND", vue.box?.match?.id)
        // Les 4 lignes du match ; LeBron James (pas dans ce match) absent
        assertEquals(listOf("p1", "p2", "p3", "p4"), vue.paris.map { it.id })
        assertEquals(5, s.etat().paris.size)
        assertEquals("pari", s.etat().affichage.id)
        // Autre match affiché : aucune ligne ; les lignes de PHI-IND gardent leurs dernières valeurs
        s.choisir("LAL-BOS", 1_000)
        s.servir(1_000, listOf(matchPhiInd(), lalBos)) { if (it.id == "PHI-IND") boxPhiInd() else BoxScore.depuisMatch(it) }
        assertEquals("LAL-BOS", s.vueTv(1_000).box?.match?.id)
        assertTrue(s.vueTv(1_000).paris.isEmpty())
        assertEquals(PariEtat("p2", "T. Maxey · + de 19,5 pts", GAGNE, "PHI-IND", 23, 19.5), maxey(s))
        // Résumé : pas de lignes dans la vue
        s.afficher(Affichage.RESUME, "PHI-IND", 2_000)
        assertTrue(s.vueTv(2_000).paris.isEmpty())
        // Effacées
        assertTrue(s.effacerParis())
        assertTrue(s.etat().paris.isEmpty())
    }

    @Test
    fun formatPariHuitLignesAuPlus() {
        val s = SuiviNba().apply { accessibilite = true }
        s.ajouterParis((1..12).joinToString("\n") { "Tyrese Maxey Plus de $it,5 points" }, 0)
        s.abonnement(true, 0)
        s.servir(0, listOf(matchPhiInd())) { boxPhiInd() }
        s.afficher(Affichage.PARI, null, 0)
        s.servir(0, listOf(matchPhiInd())) { boxPhiInd() }
        assertEquals(SuiviNba.PARIS_TV_MAX, s.vueTv(0).paris.size)
        assertEquals(12, s.etat().paris.size)
    }

    @Test
    fun formatPariSansMatchResteCache() {
        val s = SuiviNba().apply { accessibilite = true }
        s.ajouterParis(TICKET_BETCLIC, 0)
        assertEquals(Affichage.CACHE, s.afficher(Affichage.PARI, null, 0))
        assertNull(s.vueTv(0).box)
        assertEquals(Affichage.PARI, Affichage.de("pari"))
    }

    @Test
    fun lignesRetireesApresDouzeHeures() {
        val s = SuiviNba()
        s.ajouterParis(TICKET_BETCLIC, 0)
        assertEquals(4, s.nombreParis)
        s.avancer(SuiviParis.DUREE_MS)
        assertEquals(0, s.nombreParis)
        assertTrue(s.etat().paris.isEmpty())
    }
}
