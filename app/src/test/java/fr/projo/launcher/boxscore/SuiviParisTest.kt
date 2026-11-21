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

    /** Lignes d'un collage (un seul combiné, « g1 », retiré pour comparer). */
    private fun etats(texte: String, d: DonneesParis): List<PariEtat> =
        SuiviParis().apply { ajouter(texte, 0) }.etats(d).lignes.map { it.copy(groupe = null) }

    private val rien = DonneesParis(emptyList(), emptyList())

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
            PariEtat("p1", "Philadelphie 76ers (-2,5) · Handicap 1re mi-temps", NON_SUIVI),
            une("Philadelphie 76ers (-2,5)\nHandicap 1re mi-temps", donnees(boxPhiInd())),
        )
    }

    // ------------------------------------------------------------------ handicap

    @Test
    fun handicapEnCoursPuisALaFin() {
        val texte = "Écart de points (handicap) Philadelphia 76ers -15.5"
        // 71-74 : écart −3, il faut gagner de 16 ; jamais conclu avant la fin
        assertEquals(
            PariEtat("p1", "76ers · handicap \u221215,5", EN_COURS, "PHI-IND", -3, 15.5, handicap = true),
            une(texte, donnees(boxPhiInd())),
        )
        assertEquals(EN_COURS, une(texte, donnees(boxPhiInd(matchPhiInd(120, 90)))).etat)
        // Victoire de 16 : gagné ; de 15 : perdu
        assertEquals(
            PariEtat("p1", "76ers · handicap \u221215,5", GAGNE, "PHI-IND", 16, 15.5, handicap = true),
            une(texte, donnees(boxPhiInd(matchPhiInd(116, 100, EtatMatch.TERMINE, 4, 0.0)))),
        )
        assertEquals(PERDU, une(texte, donnees(boxPhiInd(matchPhiInd(115, 100, EtatMatch.TERMINE, 4, 0.0)))).etat)
        // Prolongations comprises : écart final après la prolongation
        assertEquals(GAGNE, une(texte, donnees(boxPhiInd(matchPhiInd(130, 110, EtatMatch.TERMINE, 5, 0.0)))).etat)
    }

    @Test
    fun handicapPositifEtEquipeADomicile() {
        // Pacers (domicile) +6,5 : perdre de 6 au plus
        val texte = "Indiana Pacers (+6,5)\nHandicap (prolongations incluses)"
        assertEquals(
            PariEtat("p1", "Pacers · handicap +6,5", EN_COURS, "PHI-IND", 3, -6.5, handicap = true),
            une(texte, donnees(boxPhiInd())),
        )
        assertEquals(GAGNE, une(texte, donnees(boxPhiInd(matchPhiInd(106, 100, EtatMatch.TERMINE, 4, 0.0)))).etat)
        assertEquals(PERDU, une(texte, donnees(boxPhiInd(matchPhiInd(107, 100, EtatMatch.TERMINE, 4, 0.0)))).etat)
        // Handicap entier, égalité : remboursé (en attente)
        assertEquals(ATTENTE, une("Indiana Pacers (+7)\nHandicap (prolongations incluses)", donnees(boxPhiInd(matchPhiInd(107, 100, EtatMatch.TERMINE, 4, 0.0)))).etat)
        // Avant le match, ou match inconnu : en attente
        assertEquals(
            PariEtat("p1", "Pacers · handicap +6,5", ATTENTE, "PHI-IND", null, -6.5, handicap = true),
            une(texte, DonneesParis(listOf(matchPhiInd(0, 0, EtatMatch.A_VENIR, 0, null)), emptyList())),
        )
        assertEquals(PariEtat("p1", "Pacers · handicap +6,5", ATTENTE, null, null, -6.5, handicap = true), une(texte, DonneesParis(emptyList(), emptyList())))
    }

    // ------------------------------------------------------------------ double-double, triple-double

    /** Embiid : 16 pts, 10 rebonds, 3 passes (2 catégories à 10 ou plus) ; interceptions et contres comptent. */
    private fun boxEmbiid(pts: Int, reb: Int, pd: Int, int: Int = 0, ctr: Int = 0, match: MatchNba = matchPhiInd(), joue: Boolean = true) = BoxScore(
        match,
        EquipeBox(listOf(joueurPari("J. Embiid", "Joel Embiid", pts = pts, reb = reb, pd = pd, int = int, ctr = ctr, aJoue = joue)), null),
        EquipeBox(listOf(joueurPari("P. Siakam", "Pascal Siakam", pts = 18)), null),
        surTerrainConnu = true,
    )

    @Test
    fun doubleDouble() {
        val texte = "Joel Embiid Oui\nDouble-double"
        assertEquals(PariEtat("p1", "J. Embiid · double-double", EN_COURS, "PHI-IND", 1, 2.0), une(texte, donnees(boxEmbiid(16, 9, 3))))
        // Atteint avant la fin : gagné tout de suite
        assertEquals(PariEtat("p1", "J. Embiid · double-double", GAGNE, "PHI-IND", 2, 2.0), une(texte, donnees(boxEmbiid(16, 10, 3))))
        // Pas atteint à la fin : perdu
        assertEquals(PERDU, une(texte, donnees(boxEmbiid(16, 9, 3, match = matchPhiInd(etat = EtatMatch.TERMINE, periode = 4)))).etat)
        // Interceptions et contres comptent
        assertEquals(GAGNE, une(texte, donnees(boxEmbiid(16, 4, 3, ctr = 10))).etat)
        // Pas joué, match terminé : en attente (remboursé)
        assertEquals(ATTENTE, une(texte, donnees(boxEmbiid(0, 0, 0, match = matchPhiInd(etat = EtatMatch.TERMINE, periode = 4), joue = false))).etat)
    }

    @Test
    fun doubleDoubleNon() {
        val texte = "Joel Embiid Non\nDouble-double"
        assertEquals(PariEtat("p1", "J. Embiid · double-double (non)", EN_COURS, "PHI-IND", 1, 2.0), une(texte, donnees(boxEmbiid(16, 9, 3))))
        // Atteint : perdu tout de suite ; pas atteint à la fin : gagné
        assertEquals(PERDU, une(texte, donnees(boxEmbiid(16, 10, 3))).etat)
        assertEquals(GAGNE, une(texte, donnees(boxEmbiid(16, 9, 3, match = matchPhiInd(etat = EtatMatch.TERMINE, periode = 4)))).etat)
    }

    @Test
    fun tripleDouble() {
        val texte = "Triple-double LeBron James"
        // Pas encore trouvé : en attente, seuil 3
        assertEquals(PariEtat("p1", "L. James · triple-double", ATTENTE, null, null, 3.0), une(texte, donnees(boxPhiInd())))
        val lal = { pts: Int, reb: Int, pd: Int, etat: EtatMatch ->
            BoxScore(
                matchNba("LAL", "BOS", 2_000).copy(etat = etat),
                EquipeBox(listOf(joueurPari("L. James", "LeBron James", pts = pts, reb = reb, pd = pd)), null),
                EquipeBox(emptyList(), null),
                true,
            )
        }
        assertEquals(PariEtat("p1", "L. James · triple-double", EN_COURS, "LAL-BOS", 2, 3.0), une(texte, donnees(lal(24, 10, 9, EtatMatch.EN_COURS))))
        assertEquals(PariEtat("p1", "L. James · triple-double", GAGNE, "LAL-BOS", 3, 3.0), une(texte, donnees(lal(24, 10, 10, EtatMatch.EN_COURS))))
        assertEquals(PERDU, une(texte, donnees(lal(24, 10, 9, EtatMatch.TERMINE))).etat)
    }

    /** Ticket Winamax de l'utilisateur, en plein match PHI-MIA. */
    @Test
    fun ticketWinamaxEnCoursDeMatch() {
        val m = MatchNba("PHI-MIA", "0022600200", 1_000, EquipeMatch("PHI", "76ers", 70, emptyList()), EquipeMatch("MIA", "Heat", 61, emptyList()), EtatMatch.EN_COURS, 3, 300.0, false)
        val box = BoxScore(
            m,
            EquipeBox(
                listOf(
                    joueurPari("J. Embiid", "Joel Embiid", pts = 21, reb = 8, pd = 4),
                    joueurPari("T. Maxey", "Tyrese Maxey", pts = 18),
                    joueurPari("V. Edgecombe", "VJ Edgecombe", pts = 9),
                ),
                null,
            ),
            EquipeBox(listOf(joueurPari("T. Herro", "Tyler Herro", pts = 15)), null),
            true,
        )
        val e = etats(TICKET_WINAMAX, donnees(box))
        assertEquals(
            listOf(
                "L. James · triple-double" to ATTENTE,
                "76ers · handicap \u221215,5" to EN_COURS,
                "76ers · + de 120,5 pts" to EN_COURS,
                "J. Embiid · + de 19,5 pts" to GAGNE,
                "T. Maxey · + de 29,5 pts" to EN_COURS,
                "T. Herro · + de 14,5 pts" to GAGNE,
                "V. Edgecombe · + de 14,5 pts" to EN_COURS,
                "76ers · handicap \u221212,5" to EN_COURS,
                "Total du match · + de 229,5 pts" to EN_COURS,
                "76ers · handicap \u22126,5" to EN_COURS,
                "Total du match · + de 224,5 pts" to EN_COURS,
                "76ers vainqueurs" to EN_COURS,
                "Total du match · - de 243,5 pts" to EN_COURS,
            ),
            e.map { it.libelle to it.etat },
        )
        // Les totaux du match suivent PHI-MIA ; handicap : écart +9, seuil 15,5
        assertEquals(PariEtat("p9", "Total du match · + de 229,5 pts", EN_COURS, "PHI-MIA", 131, 229.5), e[8])
        assertEquals(PariEtat("p2", "76ers · handicap \u221215,5", EN_COURS, "PHI-MIA", 9, 15.5, handicap = true), e[1])
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
        assertEquals(SuiviParis.Ajout(4, 4, 0, "g1"), p.ajouter(TICKET_BETCLIC, 0))
        // Même ticket : rien de nouveau
        assertEquals(SuiviParis.Ajout(4, 0, 0), p.ajouter(TICKET_BETCLIC, 1_000))
        // Autre collage : un autre combiné, même avec une ligne déjà suivie dans le premier
        assertEquals(SuiviParis.Ajout(2, 2, 1, "g2"), p.ajouter("Tyrese Maxey Plus de 19,5 points\nJoel Embiid + de 1,5\nNombre de fautes du joueur", 2_000))
        val e = p.etats(rien).lignes
        assertEquals(listOf("p1", "p2", "p3", "p4", "p5", "p6"), e.map { it.id })
        assertEquals(listOf("g1", "g1", "g1", "g1", "g2", "g2"), e.map { it.groupe })
        assertEquals("Joel Embiid + de 1,5 · Nombre de fautes du joueur", e.last().libelle)
        assertTrue(p.effacer())
        assertFalse(p.effacer())
        assertEquals(0, p.nombre)
        // Les identifiants ne reviennent pas
        p.ajouter("Tyrese Maxey Plus de 19,5 points", 3_000)
        assertEquals("p7", p.etats(rien).lignes.single().id)
        assertEquals("g3", p.etats(rien).groupes.single().id)
    }

    @Test
    fun dixCombinesAuPlusLesPlusAnciensPartent() {
        val p = SuiviParis()
        for (i in 1..12) p.ajouter("Tyrese Maxey Plus de $i,5 points", i.toLong())
        val e = p.etats(rien)
        assertEquals(SuiviParis.GROUPES_MAX, e.groupes.size)
        assertEquals("Combiné 3", e.groupes.first().nom)
        assertEquals("T. Maxey · + de 3,5 pts", e.lignes.first().libelle)
        assertEquals("T. Maxey · + de 12,5 pts", e.lignes.last().libelle)
    }

    @Test
    fun retireesDouzeHeuresApresLeurAjout() {
        val p = SuiviParis()
        p.ajouter("Tyrese Maxey Plus de 19,5 points", 0)
        p.ajouter("Joel Embiid Plus de 9,5 rebonds", 3_600_000)
        assertFalse(p.expirer(SuiviParis.DUREE_MS - 1))
        assertTrue(p.expirer(SuiviParis.DUREE_MS))
        assertEquals(listOf("J. Embiid · + de 9,5 rebonds"), p.etats(rien).lignes.map { it.libelle })
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

    private fun maxey(s: SuiviNba): PariEtat = s.etat().paris.first { it.libelle.startsWith("T. Maxey") }.copy(groupe = null)

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
        assertEquals(SuiviParis.Ajout(4, 4, 0, "g1"), s.ajouterParis(TICKET_BETCLIC, 0))
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
        assertEquals(PariEtat("p1", "76ers vainqueurs", EN_COURS, "PHI-IND", 2, groupe = "g1"), vue.paris[0])
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
