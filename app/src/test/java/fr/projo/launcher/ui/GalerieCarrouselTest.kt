package fr.projo.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Carrousel de Galerie, logique pure (GalerieCarrousel.kt) : décalages et places de la maquette,
 * glissements, reprise d'un glissement en cours, bouclage en fin de liste sans traversée visible.
 */
class GalerieCarrouselTest {

    private val c = CarrouselGalerie

    /** Côté d'une place à l'écran : -1 à gauche, +1 à droite, 0 au centre ; null si cachée. */
    private fun cote(p: PlaceGalerie): Int? = when {
        p.alpha <= 0f -> null
        p.heros >= 1f -> 0
        p.l + p.w / 2f < 620f -> -1
        p.l + p.w / 2f > 1560f -> 1
        else -> 0
    }

    @Test
    fun decalagesCommeLaMaquette() {
        // 13 titres, le 1er au centre : 1 à droite, 12 à gauche, 6 et 7 aux deux bouts
        assertEquals(0, c.decalage(0, 0, 13))
        assertEquals(1, c.decalage(1, 0, 13))
        assertEquals(-1, c.decalage(12, 0, 13))
        assertEquals(6, c.decalage(6, 0, 13))
        assertEquals(-6, c.decalage(7, 0, 13))
        // Liste paire : la moitié reste à droite (comme o > N / 2 en JavaScript)
        assertEquals(listOf(0, 1, 2, -1), (0 until 4).map { c.decalage(it, 0, 4) })
        assertEquals(listOf(2, -1, 0, 1), (0 until 4).map { c.decalage(it, 2, 4) })
        assertEquals(0, c.decalage(0, 0, 0))
        assertEquals(0, c.decalage(0, 0, 1))
    }

    @Test
    fun placesDeLaMaquette() {
        val centre = c.place(0)
        assertEquals(listOf(620f, 150f, 940f, 560f, 34f), listOf(centre.l, centre.t, centre.w, centre.h, centre.rayon))
        assertEquals(1f, centre.heros)
        assertEquals(PlaceGalerie(300f, 196f, 276f, 232f, flou = 1f, saturation = 1.3f), c.place(-1))
        assertEquals(1f, c.place(-3).gris)
        assertEquals(1f, c.place(-4).affiche)
        // Au-delà des bouts : places cachées (sortie à gauche, pas encore entrée à droite)
        assertEquals(c.place(-5), c.place(-9))
        assertEquals(c.place(3), c.place(7))
        assertEquals(0f, c.place(-5).alpha)
        assertEquals(0f, c.place(3).alpha)
        assertTrue(c.cachee(-6) && c.cachee(4) && !c.cachee(-4) && !c.cachee(2))
        assertEquals((-4..2).toList(), c.VISIBLES.toList())
    }

    @Test
    fun pasEtBouclage() {
        assertEquals(1, c.pas(0, 1, 13))
        assertEquals(12, c.pas(0, -1, 13))
        assertEquals(0, c.pas(12, 1, 13))
        assertEquals(3, c.pas(1, -11, 13))
    }

    /** ▶ : le titre de droite vient au centre, le centre monte en haut à gauche ; rien ne traverse. */
    @Test
    fun glissementSuivant() {
        val a = AnimationGalerie()
        a.installer(13, 0)
        assertTrue(a.deplacer(1, 1f))
        assertEquals(1, a.courant)
        val parIndex = a.trajets.filter { !it.fantome }.associateBy { it.index }
        assertEquals(c.place(1), parIndex.getValue(1).depart)
        assertEquals(c.place(0), parIndex.getValue(1).arrivee)
        assertEquals(c.place(0), parIndex.getValue(0).depart)
        assertEquals(c.place(-1), parIndex.getValue(0).arrivee)
        // Le titre 7 (bout gauche, caché) passe au bout droit sans se voir : saut, sans fantôme
        assertEquals(c.place(3), parIndex.getValue(7).depart)
        assertEquals(c.place(3), parIndex.getValue(7).arrivee)
        assertTrue("aucun fantôme avec 13 titres", a.trajets.none { it.fantome })
        verifierSansTraversee(a, "13 titres ▶")
    }

    /** 13 fois ▶ : retour au premier titre ; à chaque pas, chaque titre arrive à la place de son décalage. */
    @Test
    fun tourCompletSansTraversee() {
        for (sens in listOf(1, -1)) {
            val a = AnimationGalerie()
            a.installer(13, 0)
            repeat(13) { pas ->
                a.deplacer(sens, 1f)
                for (t in a.trajets.filter { !it.fantome }) {
                    assertEquals("pas ${pas + 1}, titre ${t.index}", c.place(c.decalage(t.index, a.courant, 13)), t.arrivee)
                }
                verifierSansTraversee(a, "13 titres, pas ${pas + 1} (sens $sens)")
            }
            assertEquals(0, a.courant)
        }
    }

    /** Petites listes : un titre qui fait le tour sort par un bord (fantôme qui s'efface) et rentre par l'autre. */
    @Test
    fun petitesListesSansTraversee() {
        for (n in 2..9) for (k in listOf(1, -1, 2, -2, 3)) {
            val a = AnimationGalerie()
            a.installer(n, 0)
            val bouge = a.deplacer(k, 1f)
            if (k % n == 0) {
                assertFalse(bouge)
                continue
            }
            val nom = "$n titres, pas de $k"
            // Un pas d'un titre ne traverse jamais l'écran (un saut de plusieurs titres, choisi, peut passer au centre)
            if (kotlin.math.abs(k) == 1) verifierSansTraversee(a, nom)
            // Un seul vrai trajet par titre, vers la place de son nouveau décalage
            val vrais = a.trajets.filter { !it.fantome }
            assertEquals(nom, (0 until n).toList(), vrais.map { it.index }.sorted())
            for (t in vrais) assertEquals(nom, c.place(c.decalage(t.index, a.courant, n)), t.arrivee)
            // Les fantômes finissent cachés, du côté de la sortie
            for (f in a.trajets.filter { it.fantome }) {
                assertEquals("$nom : fantôme caché", 0f, f.arrivee.alpha)
                assertEquals("$nom : sortie du bon côté", if (k > 0) c.place(c.MIN) else c.place(c.MAX), f.arrivee)
            }
            a.terminer()
            assertTrue("$nom : fantômes effacés", a.trajets.none { it.fantome })
        }
    }

    /** Un glissement en cours est repris là où il en est (pas de saut d'image). */
    @Test
    fun repriseDuGlissementEnCours() {
        val a = AnimationGalerie()
        a.installer(13, 0)
        a.deplacer(1, 1f)
        val avant = a.trajets.first { it.index == 1 && !it.fantome }
        val milieu = avant.a(0.5f)
        a.deplacer(1, 0.5f)
        val apres = a.trajets.first { it.index == 1 && !it.fantome }
        assertEquals(milieu, apres.depart)
        assertEquals(c.place(-1), apres.arrivee)
        assertEquals(2, a.courant)
    }

    @Test
    fun rienABougerAvecUnSeulTitre() {
        val a = AnimationGalerie()
        a.installer(1, 0)
        assertFalse(a.deplacer(1, 1f))
        a.installer(0, 5)
        assertEquals(0, a.courant)
        assertFalse(a.deplacer(-1, 1f))
    }

    @Test
    fun interpolation() {
        val p = c.place(1).vers(c.place(0), 0.5f)
        assertEquals((1600f + 620f) / 2f, p.l, 0.01f)
        assertEquals(0.5f, p.heros, 0.001f)
        assertEquals(c.place(1), c.place(1).vers(c.place(0), 0f))
        assertEquals(c.place(0), c.place(1).vers(c.place(0), 1f))
    }

    @Test
    fun courbesEtFondus() {
        assertEquals(0f, c.avancement(0f), 0.001f)
        assertEquals(1f, c.avancement(900f), 0.001f)
        assertEquals(1f, c.avancement(5000f), 0.001f)
        var avant = 0f
        for (ms in 0..900 step 30) {
            val v = c.avancement(ms.toFloat())
            if (v < avant - 0.0001f) fail("courbe non monotone à $ms ms")
            avant = v
        }
        // Départ lent puis arrivée douce (cubic-bezier(.62,.04,.24,1)) : ≈ 8 % à 200 ms, ≈ 90 % à 600 ms
        assertTrue(c.avancement(200f) < 0.1f)
        assertTrue(c.avancement(450f) in 0.6f..0.72f)
        assertTrue(c.avancement(600f) > 0.85f)
        assertEquals(0f, c.apparitionContenu(500f), 0.001f)
        assertEquals(1f, c.apparitionContenu(1000f), 0.001f)
        assertEquals(1f, c.disparition(0f), 0.001f)
        assertEquals(0f, c.disparition(350f), 0.001f)
        assertEquals(0f, c.apparitionSynopsis(400f), 0.001f)
        assertEquals(1f, c.apparitionSynopsis(950f), 0.001f)
    }

    /** Aucun vrai trajet (hors fantômes) ne passe d'un côté visible de l'écran à l'autre. */
    private fun verifierSansTraversee(a: AnimationGalerie, nom: String) {
        for (t in a.trajets) {
            if (t.fantome) continue
            val d = cote(t.depart)
            val f = cote(t.arrivee)
            if (d != null && f != null && d * f < 0) {
                fail("$nom : le titre ${t.index} traverse l'écran (${t.depart} → ${t.arrivee})")
            }
        }
    }
}
