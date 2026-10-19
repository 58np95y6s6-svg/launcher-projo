package fr.projo.launcher.ui

import androidx.compose.ui.input.key.Key
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Navigation à la télécommande entre les favoris et « Mes apps », pour chacun des 5 designs.
 * Un échec indique où le focus est parti (étiquette de l'élément et position en unités .u).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class NavigationTest(nomDesign: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> = Design.entries.map { arrayOf<Any>(it.name) }
    }

    @Test
    fun basDepuisFavori1() {
        afficher()
        basDepuis(0)
    }

    @Test
    fun basDepuisFavori2() {
        afficher()
        allerAuFavori(1)
        basDepuis(1)
    }

    @Test
    fun basDepuisFavori3() {
        afficher()
        allerAuFavori(2)
        basDepuis(2)
    }

    @Test
    fun basDepuisDernierFavoriVisible() {
        afficher()
        val i = dernierFavoriVisible()
        allerAuFavori(i)
        basDepuis(i, "dernier favori visible (n°${i + 1})")
    }

    @Test
    fun hautDepuisMesApps1() {
        afficher()
        hautDepuisApp(0)
    }

    @Test
    fun hautDepuisMesApps3() {
        afficher()
        hautDepuisApp(2)
    }

    /** Scénario signalé sur Verre, depuis le 1er favori : ▼ puis ▼. */
    @Test
    fun scenarioSignaleDepuisFavori1() {
        afficher()
        scenario(0)
    }

    /** Scénario signalé sur Verre, depuis le 2e favori : ▼ puis ▼. */
    @Test
    fun scenarioSignaleDepuisFavori2() {
        afficher()
        allerAuFavori(1)
        scenario(1)
    }

    /** ▼ depuis le favori [i] (déjà sélectionné) : le focus doit arriver dans « Mes apps ». */
    private fun basDepuis(i: Int, nom: String = "favori n°${i + 1}") {
        val avant = focus()
        appuyer(Key.DirectionDown)
        val apres = focus()
        if (!estDansMesApps(apres)) {
            fail(
                "[${design.name}] $nom ${decrire(avant)} puis ▼ : attendu un élément de « Mes apps », " +
                    "obtenu ${decrire(apres)}. ${rangees()}",
            )
        }
    }

    /** ▲ depuis l'élément [i] de « Mes apps » : le focus doit remonter sur un favori. */
    private fun hautDepuisApp(i: Int) {
        donnerFocus(app(i))
        val avant = focus()
        appuyer(Key.DirectionUp)
        val apres = focus()
        if (!estFavori(apres)) {
            fail(
                "[${design.name}] « Mes apps » n°${i + 1} ${decrire(avant)} puis ▲ : attendu un favori, " +
                    "obtenu ${decrire(apres)}. ${rangees()}",
            )
        }
    }

    /**
     * Scénario de l'utilisateur : sur un des deux premiers favoris, ▼ deux fois. Sur Verre, le
     * focus passe au 2e favori puis revient au 1er au lieu de descendre dans « Mes apps ».
     */
    private fun scenario(i: Int) {
        val etapes = mutableListOf("favori n°${i + 1} ${decrire(focus())}")
        appuyer(Key.DirectionDown)
        val apres1 = focus()
        etapes += "▼ → ${decrire(apres1)}"
        appuyer(Key.DirectionDown)
        etapes += "▼ → ${decrire(focus())}"
        if (!estDansMesApps(apres1)) {
            // Contournement décrit par l'utilisateur : Réglages puis ▼ (pour information)
            val viaReglages = runCatching {
                donnerFocus("rapide:Réglages")
                appuyer(Key.DirectionDown)
                decrire(focus())
            }.getOrElse { "non vérifiable (${it.message})" }
            fail(
                "[${design.name}] scénario signalé : ${etapes.joinToString(" ; ")}. " +
                    "« Mes apps » n'est pas atteint au 1er ▼. Pour info, Réglages puis ▼ → $viaReglages. ${rangees()}",
            )
        }
    }
}
