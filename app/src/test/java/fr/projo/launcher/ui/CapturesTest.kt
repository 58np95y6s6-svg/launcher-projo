package fr.projo.launcher.ui

import androidx.compose.ui.input.key.Key
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures d'écran 1920 × 1080 de chaque design (rendu graphique réel de Robolectric),
 * enregistrées dans app/build/captures/.
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

    /** Accueil, 1er favori sélectionné (animations terminées : 1 s après l'affichage). */
    @Test
    fun accueilFavori1() {
        afficher()
        avancer(1000)
        capturer("1_accueil_favori1")
    }

    /** 1er élément de « Mes apps » sélectionné. */
    @Test
    fun mesAppsPremier() {
        afficher()
        donnerFocus(app(0))
        capturer("2_mesapps_app1")
    }

    /** 2e favori puis ▼ : le focus est descendu dans « Mes apps ». */
    @Test
    fun basVersMesApps() {
        afficher()
        appuyer(Key.DirectionRight)
        appuyer(Key.DirectionDown)
        avancer(1000)
        capturer("5_favori2_puis_bas")
    }
}
