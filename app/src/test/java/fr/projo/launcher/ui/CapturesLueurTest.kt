package fr.projo.launcher.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Banc des captures de lueur : sait aller jusqu'à « Toutes les apps ». */
abstract class BancLueur(nomDesign: String) : BancAccueil(nomDesign) {

    /** Depuis la 1re app de « Mes apps », ▶ jusqu'à « Toutes les apps » (au bout de la rangée). */
    protected fun allerALaBibliotheque() {
        donnerFocus(app(0))
        repeat(ui.homeApps.size) { appuyer(Key.DirectionRight) }
        val f = focus()
        if (f?.etiquette != "biblio") fail("[${design.name}] OUTILLAGE : ▶ jusqu'au bout de Mes apps donne ${decrire(f)}")
    }
}

/**
 * Gros plans de la lueur sur chaque type d'élément, et sur des fonds teintés
 * (Aurore : le fond prend la couleur de l'app sélectionnée).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CapturesLueurAuroreTest : BancLueur("AURORE") {

    /** Twitch : carte blanche sur fond teinté de violet (le pire cas pour un halo violet). */
    @Test
    fun favoriTwitch() {
        afficher()
        allerAuFavori(4)
        avancer(1000)
        zoomer(capturer("5_favori_twitch"), "5z_zoom_favori_twitch")
    }

    /** Prime Video : carte bleue sur fond teinté de bleu. */
    @Test
    fun favoriPrime() {
        afficher()
        allerAuFavori(1)
        avancer(1000)
        zoomer(capturer("6_favori_prime"), "6z_zoom_favori_prime")
    }

    /** Carte « Toutes les apps ». */
    @Test
    fun bibliotheque() {
        afficher()
        allerALaBibliotheque()
        zoomer(capturer("7_biblio"), "7z_zoom_biblio")
    }

    /** Netflix, 2 s plus tard : le reflet a tourné (autre position des couleurs). */
    @Test
    fun rotation() {
        afficher()
        avancer(2000)
        zoomer(capturer("8_rotation"), "8z_zoom_rotation")
    }

    /** En plein passage de Netflix à Prime Video (0,1 s après ▶) : l'ancienne lueur s'éteint sur place. */
    @Test
    fun transition() {
        afficher()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        avancer(100)
        zoomer(capturer("9_transition"), "9z_zoom_transition", marge = 160)
    }
}

/** Graphite : favori blanc et carte « Toutes les apps » du dock. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CapturesLueurGraphiteTest : BancLueur("GRAPHITE") {

    @Test
    fun favoriTwitch() {
        afficher()
        allerAuFavori(4)
        avancer(1000)
        zoomer(capturer("5_favori_twitch"), "5z_zoom_favori_twitch")
    }

    @Test
    fun bibliotheque() {
        afficher()
        allerALaBibliotheque()
        zoomer(capturer("7_biblio"), "7z_zoom_biblio")
    }
}
