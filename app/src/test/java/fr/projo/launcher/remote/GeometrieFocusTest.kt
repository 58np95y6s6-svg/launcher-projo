package fr.projo.launcher.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/** Navigation par le service d'accessibilité : choix de l'élément voisin. */
class GeometrieFocusTest {

    // Grille 3 × 2 de tuiles 100 × 60, espacées de 20
    private fun tuile(col: Int, ligne: Int) = Boite(col * 120, ligne * 80, col * 120 + 100, ligne * 80 + 60)
    private val grille = listOf(tuile(0, 0), tuile(1, 0), tuile(2, 0), tuile(0, 1), tuile(1, 1), tuile(2, 1))

    @Test
    fun voisinsDansLaGrille() {
        val centre = tuile(1, 0)
        assertEquals(2, GeometrieFocus.voisin(centre, grille, Direction.DROITE))
        assertEquals(0, GeometrieFocus.voisin(centre, grille, Direction.GAUCHE))
        assertEquals(4, GeometrieFocus.voisin(centre, grille, Direction.BAS))
        assertEquals(-1, GeometrieFocus.voisin(centre, grille, Direction.HAUT))
        assertEquals(-1, GeometrieFocus.voisin(tuile(2, 1), grille, Direction.DROITE))
    }

    /** Un élément dans le faisceau passe avant un élément plus proche mais décalé. */
    @Test
    fun faisceauPrioritaire() {
        val depuis = Boite(0, 0, 100, 100)
        val decalePres = Boite(110, 150, 200, 250)
        val alignéLoin = Boite(400, 20, 500, 80)
        assertEquals(1, GeometrieFocus.voisin(depuis, listOf(decalePres, alignéLoin), Direction.DROITE))
    }

    @Test
    fun boitesVidesIgnorees() {
        val depuis = Boite(0, 0, 100, 100)
        assertEquals(1, GeometrieFocus.voisin(depuis, listOf(Boite(150, 0, 150, 100), Boite(300, 0, 400, 100)), Direction.DROITE))
    }

    @Test
    fun premierElement() {
        assertEquals(0, GeometrieFocus.premier(grille))
        assertEquals(1, GeometrieFocus.premier(listOf(tuile(1, 1), tuile(2, 0))))
        assertEquals(-1, GeometrieFocus.premier(emptyList()))
    }
}
