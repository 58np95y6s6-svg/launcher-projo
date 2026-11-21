package fr.projo.launcher.boxscore

import fr.projo.launcher.boxscore.EtatPari.ATTENTE
import fr.projo.launcher.boxscore.EtatPari.EN_COURS
import fr.projo.launcher.boxscore.EtatPari.GAGNE
import fr.projo.launcher.boxscore.EtatPari.PERDU
import fr.projo.launcher.boxscore.SuiviNba.Etape
import fr.projo.launcher.boxscore.SuiviNba.Reponse
import fr.projo.launcher.boxscore.SuiviParis.Ajout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Combinés (docs/telecommande.md, « Combinés ») : un combiné par collage (doublons dans le
 * combiné seulement, combiné identique ignoré), identifiants et noms jamais réutilisés, limites,
 * combiné ouvert, effacement d'un combiné, état d'un combiné, cote lue ; puis avec SuiviNba :
 * combinés concernés par le match affiché en accordéon, ◀ ▶ seulement avec au moins deux
 * combinés concernés.
 */
class CombinesTest {

    private val rien = DonneesParis(emptyList(), emptyList())

    private fun donnees(vararg b: BoxScore, matchs: List<MatchNba> = b.map { it.match }) = DonneesParis(matchs, b.toList())

    private fun SuiviParis.groupes(d: DonneesParis = rien) = etats(d).groupes

    private fun SuiviParis.ids() = groupes().map { it.id }

    /** Le combiné d'un seul collage. */
    private fun combine(texte: String, d: DonneesParis): GroupeEtat = SuiviParis().apply { ajouter(texte, 0) }.groupes(d).single()

    // ------------------------------------------------------------------ combinés gardés

    /** Plusieurs combinés collés d'un coup, séparés par « Combiné N » ou « --- » : un combiné chacun, dans l'ordre. */
    @Test
    fun plusieursCombinesEnUnSeulCollage() {
        val p = SuiviParis()
        val a = p.ajouter(
            listOf(
                "Combiné 1 · cote 5,10",
                "Tyrese Maxey - Points : Plus de 24,5",
                "Tyrese Maxey - Rebonds : Plus de 2,5",
                "COMBINÉ 2 · cote 3,10",
                "Écart de points (handicap) Philadelphia 76ers -12.5",
                "Nombre de points Plus de 229,5",
                "---",
                "Joel Embiid + de 19,5",
                "Nombre de points du joueur (paliers)",
            ).joinToString("\n"),
            0,
        )
        val g = p.groupes()
        assertEquals(listOf("Combiné 1", "Combiné 2", "Combiné 3"), g.map { it.nom })
        assertEquals(listOf("5,10", "3,10", null), g.map { it.cote })
        assertEquals(listOf(2, 2, 1), g.map { it.suivies + it.nonSuivies })
        assertEquals(5, a.ajoutees)
        assertEquals(g.last().id, a.groupe)
        assertTrue(g.last().ouvert)
        // Recollé : rien de nouveau
        assertEquals(0, p.ajouter("Combiné 1 · cote 5,10\nTyrese Maxey - Points : Plus de 24,5\nTyrese Maxey - Rebonds : Plus de 2,5", 1).ajoutees)
    }

    /** « Combiné (2) » de Betclic est l'en-tête d'un seul ticket : pas de découpage. */
    @Test
    fun enTeteBetclicNeDecoupePas() {
        assertEquals(1, SuiviParis().decouper("Combiné (2)\nMYCOMBI\n8,75\nPhiladelphie 76ers\nVainqueur du match").size)
        assertEquals(2, SuiviParis().decouper("Combiné 1\nA\nCombiné 2\nB").size)
        assertEquals(1, SuiviParis().decouper("Combiné 1\nA").size)
    }

    @Test
    fun chaqueCollageEstUnCombine() {
        val p = SuiviParis()
        assertEquals(Ajout(5, 5, 0, "g1"), p.ajouter(COMBINE_WINAMAX_PHI_MIL, 0))
        assertEquals(Ajout(2, 2, 0, "g2"), p.ajouter(COMBINE_BETCLIC_PHI_MIL, 1_000))
        val e = p.etats(rien)
        assertEquals(listOf("g1", "g2"), e.groupes.map { it.id })
        assertEquals(listOf("Combiné 1", "Combiné 2"), e.groupes.map { it.nom })
        assertEquals(listOf("5,10", "10,06"), e.groupes.map { it.cote })
        // Le dernier ajouté est ouvert, un seul
        assertEquals(listOf(false, true), e.groupes.map { it.ouvert })
        assertEquals("g2", p.ouvert)
        assertEquals((1..7).map { "p$it" }, e.lignes.map { it.id })
        assertEquals(List(5) { "g1" } + List(2) { "g2" }, e.lignes.map { it.groupe })
        assertEquals(7, p.nombre)
        assertEquals(2, p.nombreCombines)
    }

    @Test
    fun doublonsDansLeCombineSeulement() {
        val p = SuiviParis()
        // Même ligne deux fois dans un collage : une seule
        assertEquals(Ajout(1, 1, 0, "g1"), p.ajouter("Tyrese Maxey Plus de 19,5 points\nTyrese Maxey Plus de 19,5 points", 0))
        // Déjà dans un autre combiné : gardée dans celui-ci aussi
        assertEquals(Ajout(2, 2, 0, "g2"), p.ajouter("Tyrese Maxey Plus de 19,5 points\nJoel Embiid Plus de 9,5 rebonds", 0))
        assertEquals(3, p.nombre)
        // Exactement les lignes d'un combiné présent (autre ordre, autre appli, cote) : rien n'est ajouté ni ouvert
        assertTrue(p.ouvrir("g1"))
        assertEquals(Ajout(2, 0, 0), p.ajouter("Joel Embiid + de 9,5\nNombre de rebonds du joueur (paliers)\nTyrese Maxey Plus de 19,5 points\nCote 3,10", 0))
        assertEquals("g1", p.ouvert)
        assertEquals(listOf("g1", "g2"), p.ids())
        // Une partie seulement d'un combiné : un nouveau combiné
        assertEquals(Ajout(1, 1, 0, "g3"), p.ajouter("Joel Embiid Plus de 9,5 rebonds", 0))
        // Aucune ligne reconnue : aucun combiné
        assertEquals(Ajout(0, 0, 0), p.ajouter("Mise 2,00 €\nCote 3,10", 0))
        assertEquals(Ajout(0, 0, 0), p.ajouter("", 0))
        assertEquals(listOf("g1", "g2", "g3"), p.ids())
        assertEquals("g3", p.ouvert)
    }

    @Test
    fun identifiantsEtNomsJamaisReutilises() {
        val p = SuiviParis()
        p.ajouter("Tyrese Maxey Plus de 19,5 points", 0)
        p.ajouter("Joel Embiid Plus de 9,5 rebonds", 0)
        assertTrue(p.effacer("g2"))
        // Le même collage, une fois son combiné retiré : un nouveau combiné
        p.ajouter("Joel Embiid Plus de 9,5 rebonds", 0)
        val e = p.etats(rien)
        assertEquals(listOf("g1", "g3"), e.groupes.map { it.id })
        assertEquals(listOf("Combiné 1", "Combiné 3"), e.groupes.map { it.nom })
        assertEquals(listOf("p1", "p3"), e.lignes.map { it.id })
        // Tout effacé, puis expiré : les numéros continuent
        assertTrue(p.effacer())
        p.ajouter("Tyrese Maxey Plus de 19,5 points", 1_000)
        assertEquals(GroupeEtat("g4", "Combiné 4", null, ATTENTE, 0, 1, 0, true), p.groupes().single())
        assertTrue(p.expirer(1_000 + SuiviParis.DUREE_MS))
        assertNull(p.ouvert)
        p.ajouter("Tyrese Maxey Plus de 19,5 points", 2_000 + SuiviParis.DUREE_MS)
        assertEquals("g5", p.groupes().single().id)
        assertEquals("p5", p.etats(rien).lignes.single().id)
    }

    @Test
    fun limitesLePlusAncienCombinePart() {
        val p = SuiviParis()
        // 40 lignes au plus : trois combinés de 15 lignes, le plus ancien part
        for (k in 0..2) p.ajouter((1..15).joinToString("\n") { "Tyrese Maxey Plus de ${k * 15 + it},5 points" }, k.toLong())
        assertEquals(listOf("g2", "g3"), p.ids())
        assertEquals(30, p.nombre)
        assertEquals("T. Maxey · + de 16,5 pts", p.etats(rien).lignes.first().libelle)
        // 10 combinés au plus
        for (i in 1..9) p.ajouter("Joel Embiid Plus de $i,5 rebonds", 10L + i)
        assertEquals(SuiviParis.GROUPES_MAX, p.nombreCombines)
        assertEquals(listOf("g3") + (4..12).map { "g$it" }, p.ids())
        assertEquals(24, p.nombre)
        assertEquals("g12", p.ouvert)
        assertTrue(p.etats(rien).lignes.size <= SuiviParis.LIGNES_MAX)
    }

    @Test
    fun ouvrirEtEffacerUnCombine() {
        val p = SuiviParis()
        p.ajouter(COMBINE_SANS_COTE_PHI_MIL, 0)
        p.ajouter(COMBINE_WINAMAX_PHI_MIL, 0)
        p.ajouter(COMBINE_BETCLIC_PHI_MIL, 0)
        assertEquals("g3", p.ouvert)
        assertTrue(p.ouvrir("g1"))
        assertEquals(listOf(true, false, false), p.groupes().map { it.ouvert })
        // Inconnu : ignoré
        assertFalse(p.ouvrir("g9"))
        assertFalse(p.ouvrir(""))
        assertEquals("g1", p.ouvert)
        assertFalse(p.effacer("g9"))
        assertEquals(3, p.nombreCombines)
        // Un combiné replié : l'ouvert le reste
        assertTrue(p.effacer("g2"))
        assertEquals(listOf("g1", "g3"), p.ids())
        assertEquals(2 + 2, p.nombre)
        assertEquals("g1", p.ouvert)
        // L'ouvert : le plus récent devient l'ouvert
        assertTrue(p.effacer("g1"))
        assertEquals("g3", p.ouvert)
        assertEquals(listOf(true), p.groupes().map { it.ouvert })
        assertTrue(p.effacer("g3"))
        assertNull(p.ouvert)
        assertTrue(p.etats(rien).groupes.isEmpty())
        assertTrue(p.etats(rien).lignes.isEmpty())
    }

    // ------------------------------------------------------------------ état d'un combiné

    @Test
    fun etatDUnCombine() {
        val d = donnees(boxPhiMil())
        // Handicap +7 et Embiid 21 pts en cours, Maxey 27 pts gagné, Giannis 8 rebonds, total 169 : en cours, 1/5
        assertEquals(GroupeEtat("g1", "Combiné 1", "5,10", EN_COURS, 1, 5, 0, true), combine(COMBINE_WINAMAX_PHI_MIL, d))
        // Perdu dès qu'une ligne est perdue (Embiid 9 rebonds pour « moins de 8,5 »), l'autre en cours
        assertEquals(GroupeEtat("g1", "Combiné 1", null, PERDU, 0, 2, 0, true), combine(COMBINE_SANS_COTE_PHI_MIL, d))
        // Gagné quand toutes les lignes suivies sont gagnées, avant la fin du match
        assertEquals(GroupeEtat("g1", "Combiné 1", "10,06", GAGNE, 2, 2, 0, true), combine(COMBINE_BETCLIC_PHI_MIL, d))
        // En attente : toutes en attente (match à venir, ou rien de connu)
        val avant = DonneesParis(listOf(matchPhiMil(0, 0, EtatMatch.A_VENIR, 0, null)), emptyList())
        assertEquals(ATTENTE, combine(COMBINE_WINAMAX_PHI_MIL, avant).etat)
        assertEquals(ATTENTE, combine(COMBINE_WINAMAX_PHI_MIL, rien).etat)
        // Une gagnée, une en attente (joueur d'un autre match) : en cours
        assertEquals(EN_COURS, combine("Tyrese Maxey Plus de 19,5 points\nLeBron James Plus de 25,5 points", d).etat)
        // Lignes non suivies ignorées (comptées à part)
        val fautes = "Tyrese Maxey Plus de 19,5 points\nJoel Embiid + de 1,5\nNombre de fautes du joueur"
        assertEquals(GroupeEtat("g1", "Combiné 1", null, GAGNE, 1, 1, 1, true), combine(fautes, d))
        assertEquals(GroupeEtat("g1", "Combiné 1", null, ATTENTE, 0, 0, 1, true), combine("Joel Embiid + de 1,5\nNombre de fautes du joueur", d))
        // Perdu à la fin
        val fin = donnees(boxPhiMil(matchPhiMil(101, 104, EtatMatch.TERMINE, 4, 0.0)))
        assertEquals(PERDU, combine(COMBINE_WINAMAX_PHI_MIL, fin).etat)
    }

    @Test
    fun ligneRembourseeNeComptePas() {
        // Match terminé, Embiid n'a pas joué : sa ligne est remboursée, le combiné est gagné avec la ligne restante
        val fin = boxPhiInd(matchPhiInd(112, 108, EtatMatch.TERMINE, 4, 0.0), maxey = 31, embiidJoue = false)
        val g = combine("Tyrese Maxey Plus de 19,5 points\nJoel Embiid Plus de 9,5 rebonds", donnees(fin))
        assertEquals(GroupeEtat("g1", "Combiné 1", null, GAGNE, 1, 2, 0, true), g)
        // Handicap entier nul à la fin (gagné de 4 avec −4) : remboursé aussi
        assertEquals(GAGNE, combine("Écart de points (handicap) Philadelphia 76ers -4\nTyrese Maxey Plus de 19,5 points", donnees(fin)).etat)
        // Toutes remboursées : en attente
        assertEquals(ATTENTE, combine("Joel Embiid Plus de 9,5 rebonds", donnees(fin)).etat)
        // Pendant le match, un joueur pas encore entré n'est pas remboursé : en cours
        val enCours = boxPhiInd(maxey = 31, embiidReb = 0, embiidJoue = false)
        assertEquals(EN_COURS, combine("Tyrese Maxey Plus de 19,5 points\nJoel Embiid Plus de 9,5 rebonds", donnees(enCours)).etat)
    }

    // ------------------------------------------------------------------ format pari (SuiviNba)

    private val lalBos = matchNba("LAL", "BOS", 2_000)

    private fun SuiviNba.servir(t: Long, tableau: List<MatchNba>, box: BoxScore) {
        repeat(10) {
            when (val e = prochaine(t)) {
                is Etape.Tableau -> tableauRecu(t, e.source, Reponse.Ok(tableau))
                is Etape.Box -> boxRecu(t, e, Reponse.Ok(if (e.match.id == box.match.id) box else BoxScore.depuisMatch(e.match)))
                else -> return
            }
        }
        throw AssertionError("requêtes sans fin")
    }

    /** Suivi avec ces collages, format pari sur le match de [box] (tableau du jour : [tableau]). */
    private fun suivi(textes: List<String>, box: BoxScore = boxPhiMil(), tableau: List<MatchNba> = listOf(box.match, lalBos)): SuiviNba {
        val s = SuiviNba().apply { accessibilite = true }
        for (t in textes) s.ajouterParis(t, 0)
        s.abonnement(true, 0)
        s.servir(0, tableau, box)
        assertEquals(Affichage.PARI, s.afficher(Affichage.PARI, box.match.id, 0))
        s.servir(0, tableau, box)
        return s
    }

    private val lakers = "Los Angeles Lakers\nVainqueur du match\nNombre de points de Boston Celtics Plus de 110,5"

    @Test
    fun formatPariCombinesConcernesEnAccordeon() {
        // g1, g2, g3 : PHI-MIL ; g4 : LAL-BOS seulement (le dernier ajouté, donc l'ouvert)
        val s = suivi(listOf(COMBINE_SANS_COTE_PHI_MIL, COMBINE_WINAMAX_PHI_MIL, COMBINE_BETCLIC_PHI_MIL, lakers))
        val e = s.etat()
        assertEquals(listOf("g1", "g2", "g3", "g4"), e.groupes.map { it.id })
        assertEquals("g4", e.groupes.single { it.ouvert }.id)
        assertEquals(11, e.paris.size)
        assertEquals(listOf("LAL-BOS", "LAL-BOS"), e.paris.filter { it.groupe == "g4" }.map { it.match })
        // L'ouvert n'est pas concerné : la TV ouvre le plus récent des concernés, sans changer `ouvert`
        var vue = s.vueTv(0)
        assertEquals(listOf("g1", "g2", "g3"), vue.combines.map { it.groupe.id })
        assertEquals(listOf(false, false, true), vue.combines.map { it.ouvert })
        assertEquals(listOf("p8", "p9"), vue.paris.map { it.id })
        assertTrue(vue.combines.filter { !it.ouvert }.all { it.lignes.isEmpty() })
        assertTrue(s.touchesGardees())
        // `nba_paris_ouvrir` : la TV suit
        assertTrue(s.ouvrirParis("g2"))
        vue = s.vueTv(0)
        assertEquals(listOf(false, true, false), vue.combines.map { it.ouvert })
        val ouvert = vue.combines[1]
        assertEquals(GroupeEtat("g2", "Combiné 2", "5,10", EN_COURS, 1, 5, 0, true), ouvert.groupe)
        assertEquals(listOf("p3", "p4", "p5", "p6", "p7"), ouvert.lignes.map { it.id })
        assertEquals(0, ouvert.autres)
        // Replié : le résumé seulement (état, nom, cote, 2/2)
        assertEquals(GroupeEtat("g3", "Combiné 3", "10,06", GAGNE, 2, 2, 0, false), vue.combines[2].groupe)
        assertEquals(PERDU, vue.combines[0].groupe.etat)
        // Effacé : la TV suit
        assertTrue(s.effacerParis("g1"))
        assertEquals(listOf("g2", "g3"), s.vueTv(0).combines.map { it.groupe.id })
    }

    @Test
    fun formatPariLignesDUnAutreMatchResumees() {
        val texte = "Tyrese Maxey Plus de 19,5 points\nÉcart de points (handicap) Philadelphia 76ers -4.5\n$lakers"
        val s = suivi(listOf(texte))
        val c = s.vueTv(0).combines.single()
        assertTrue(c.ouvert)
        assertEquals(listOf("T. Maxey · + de 19,5 pts", "76ers · handicap −4,5"), c.lignes.map { it.libelle })
        assertEquals(2, c.autres)
        assertTrue(c.unAutreMatch)
        assertEquals("+ 2 lignes d'un autre match", c.texteAutres)
        // Un seul combiné concerné : aucune touche gardée
        assertFalse(s.touchesGardees())
        assertFalse(s.parcourirCombines(1))
        // Lignes de plusieurs autres matchs (ou de match inconnu), non suivies exclues
        val s2 = suivi(listOf("$texte\nLeBron James Plus de 25,5 points\nJoel Embiid + de 1,5\nNombre de fautes du joueur"))
        val c2 = s2.vueTv(0).combines.single()
        assertEquals(3, c2.autres)
        assertFalse(c2.unAutreMatch)
        assertEquals("+ 3 lignes d'autres matchs", c2.texteAutres)
        assertEquals("+ 1 ligne d'un autre match", c2.copy(autres = 1, unAutreMatch = true).texteAutres)
    }

    @Test
    fun flechesSeulementAvecDeuxCombinesConcernes() {
        val s = suivi(listOf(COMBINE_SANS_COTE_PHI_MIL, COMBINE_WINAMAX_PHI_MIL, lakers, COMBINE_BETCLIC_PHI_MIL))
        assertTrue(s.touchesGardees())
        fun ouvert() = s.etat().groupes.single { it.ouvert }.id
        assertEquals("g4", ouvert())
        // ▶ en boucle, parmi les concernés seulement (g3 : LAL-BOS)
        assertTrue(s.parcourirCombines(1))
        assertEquals("g1", ouvert())
        assertTrue(s.parcourirCombines(1))
        assertEquals("g2", ouvert())
        assertTrue(s.parcourirCombines(1))
        assertEquals("g4", ouvert())
        assertTrue(s.parcourirCombines(-1))
        assertEquals("g2", ouvert())
        assertEquals("g2", s.vueTv(0).combines.single { it.ouvert }.groupe.id)
        // Autres formats : rien en résumé, ◀ ▶ des équipes en détails
        s.afficher(Affichage.RESUME, null, 0)
        assertFalse(s.touchesGardees())
        assertFalse(s.parcourirCombines(1))
        assertTrue(s.vueTv(0).combines.isEmpty())
        s.afficher(Affichage.DETAILS, null, 0)
        assertTrue(s.touchesGardees())
        assertFalse(s.parcourirCombines(1))
        // Autre match affiché (LAL-BOS) en pari : un seul combiné concerné
        s.afficher(Affichage.PARI, "LAL-BOS", 0)
        s.servir(0, listOf(matchPhiMil(), lalBos), boxPhiMil())
        assertEquals(listOf("g3"), s.vueTv(0).combines.map { it.groupe.id })
        assertTrue(s.vueTv(0).combines.single().ouvert)
        assertFalse(s.touchesGardees())
        // Sans combiné : rien
        s.effacerParis()
        assertFalse(s.touchesGardees())
        assertTrue(s.vueTv(0).combines.isEmpty())
        assertTrue(s.etat().groupes.isEmpty())
    }

    @Test
    fun auPlusCinqCombinesRepliesAutourDeLOuvert() {
        val s = suivi((1..8).map { "Tyrese Maxey Plus de $it,5 points" })
        fun visibles() = s.vueTv(0).combines.map { it.groupe.id }
        // Ouvert en dernier : les 5 précédents
        assertEquals((3..8).map { "g$it" }, visibles())
        assertTrue(s.ouvrirParis("g1"))
        assertEquals((1..6).map { "g$it" }, visibles())
        // Au milieu : 2 avant, 3 après
        assertTrue(s.ouvrirParis("g4"))
        assertEquals((2..7).map { "g$it" }, visibles())
        assertEquals(listOf(false, false, true, false, false, false), s.vueTv(0).combines.map { it.ouvert })
        assertTrue(s.ouvrirParis("g7"))
        assertEquals((3..8).map { "g$it" }, visibles())
    }

    @Test
    fun huitLignesAuPlusDansLOuvert() {
        val s = suivi(listOf((1..12).joinToString("\n") { "Tyrese Maxey Plus de $it,5 points" }))
        assertEquals(SuiviNba.PARIS_TV_MAX, s.vueTv(0).paris.size)
        assertEquals(0, s.vueTv(0).combines.single().autres)
        assertEquals(12, s.etat().paris.size)
    }
}
