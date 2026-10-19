package fr.projo.launcher.ui

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures d'écran 1920 × 1080 de chaque design (rendu graphique réel de Robolectric),
 * enregistrées dans app/build/captures/. Pour les designs à lueur, un agrandissement ×3
 * autour de l'élément sélectionné permet de juger la finition de la lueur.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CapturesTest(nomDesign: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> = Design.entries.map { arrayOf<Any>(it.name) }
    }

    /** Accueil, 1er favori sélectionné (lueur installée : 1 s après l'affichage). */
    @Test
    fun accueilFavori1() {
        afficher()
        avancer(1000)
        val f = capturer("1_accueil_favori1")
        if (design.avecLueur) zoomer(f, "1z_zoom_favori1")
    }

    /** 1er élément de « Mes apps » sélectionné. */
    @Test
    fun mesAppsPremier() {
        afficher()
        donnerFocus(app(0))
        val f = capturer("2_mesapps_app1")
        if (design.avecLueur) zoomer(f, "2z_zoom_mesapps_app1")
    }
}

/** Graphite : la lueur sur un raccourci de la colonne et sur une tuile du dock. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CapturesGraphiteTest : BancAccueil("GRAPHITE") {

    @Test
    fun raccourci() {
        afficher()
        donnerFocus("rapide:Wi-Fi")
        val f = capturer("3_raccourci_wifi")
        zoomer(f, "3z_zoom_raccourci_wifi")
    }

    @Test
    fun tuileDock() {
        afficher()
        donnerFocus(app(1))
        val f = capturer("4_dock_app2")
        zoomer(f, "4z_zoom_dock_app2")
    }
}
