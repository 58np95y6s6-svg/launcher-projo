package fr.projo.launcher.ui

import androidx.compose.ui.input.key.Key
import fr.projo.launcher.HomeUi
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

    // ------------------------------------------------------------------ ▼ depuis les favoris

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

    /** ▶ jusqu'au bout des favoris (la rangée défile) puis ▼. */
    @Test
    fun basApresDefilementDesFavoris() {
        afficher()
        val dernier = ui.favorites.lastIndex
        allerAuFavori(dernier)
        basDepuis(dernier, "favori n°${dernier + 1} après défilement")
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

    // ------------------------------------------------------------------ ▲ depuis « Mes apps »

    @Test
    fun hautDepuisMesApps1() {
        afficher()
        hautDepuis(app(0), "« Mes apps » n°1")
    }

    @Test
    fun hautDepuisMesApps3() {
        afficher()
        hautDepuis(app(2), "« Mes apps » n°3")
    }

    @Test
    fun hautDepuisMesAppsMilieu() {
        afficher()
        val i = ui.homeApps.size / 2
        hautDepuis(app(i), "« Mes apps » n°${i + 1}")
    }

    @Test
    fun hautDepuisToutesLesApps() {
        afficher()
        hautDepuis("biblio", "« Toutes les apps »")
    }

    // ------------------------------------------------------------------ allers-retours

    /** ▼ puis ▲ : retour sur le favori de départ. */
    @Test
    fun allerRetourBasHaut() {
        afficher()
        for (i in listOf(0, 1, 2)) {
            if (i > 0) allerAuFavori(i, depuis = i - 1)
            val depart = focus()
            appuyer(Key.DirectionDown)
            val milieu = focus()
            appuyer(Key.DirectionUp)
            val fin = focus()
            if (fin?.etiquette != favori(i)) {
                fail(
                    "[${design.name}] favori n°${i + 1} ${decrire(depart)} ▼ → ${decrire(milieu)} ▲ → ${decrire(fin)} : " +
                        "attendu le retour sur ${favori(i)}. ${rangees()}",
                )
            }
        }
    }

    /** ▲ puis ▼ : retour sur l'app de départ. */
    @Test
    fun allerRetourHautBas() {
        afficher()
        for (etiquette in listOf(app(0), app(3), "biblio")) {
            donnerFocus(etiquette)
            val depart = focus()
            appuyer(Key.DirectionUp)
            val milieu = focus()
            appuyer(Key.DirectionDown)
            val fin = focus()
            if (fin?.etiquette != etiquette) {
                fail(
                    "[${design.name}] ${decrire(depart)} ▲ → ${decrire(milieu)} ▼ → ${decrire(fin)} : " +
                        "attendu le retour sur $etiquette. ${rangees()}",
                )
            }
        }
    }

    // ------------------------------------------------------------------ autres directions

    /** ▼ depuis « Mes apps » (rangée du bas) : le focus ne part pas sur une voisine. */
    @Test
    fun basDepuisMesAppsResteSurPlace() {
        afficher()
        for (etiquette in listOf(app(0), app(2))) {
            donnerFocus(etiquette)
            appuyer(Key.DirectionDown)
            val f = focus()
            if (f?.etiquette != etiquette) {
                fail("[${design.name}] $etiquette puis ▼ : attendu de rester sur place, obtenu ${decrire(f)}. ${rangees()}")
            }
        }
    }

    /** ◀ / ▶ dans les deux rangées. */
    @Test
    fun gaucheDroiteDansLesRangees() {
        afficher()
        allerAuFavori(2)
        attendreApres(Key.DirectionLeft, favori(1), "${favori(2)} puis ◀")
        donnerFocus(app(1))
        attendreApres(Key.DirectionLeft, app(0), "${app(1)} puis ◀")
        attendreApres(Key.DirectionRight, app(1), "${app(0)} puis ▶")
        attendreApres(Key.DirectionRight, app(2), "${app(1)} puis ▶")
    }

    /** Sortie du bandeau NBA par ◀ puis par Retour : retour au 1er favori (rangée remise au début). */
    @Test
    fun sortieDuBandeauNba() {
        afficher()
        allerAuFavori(3)
        donnerFocus("nba")
        attendreApres(Key.DirectionLeft, favori(0), "bandeau NBA puis ◀")
        donnerFocus("nba")
        attendreApres(Key.Back, favori(0), "bandeau NBA puis Retour")
    }

    /** Fermeture des panneaux : le focus revient sur Réglages ou « Toutes les apps ». */
    @Test
    fun retourDuFocusApresLesPanneaux() {
        afficher()
        donnerFocus("biblio")
        compose.runOnIdle { focusReglages.requestFocus() }
        avancer(800)
        val f1 = focus()
        if (f1?.etiquette != "rapide:Réglages") fail("[${design.name}] focusReglages : obtenu ${decrire(f1)}")
        compose.runOnIdle { focusBiblio.requestFocus() }
        avancer(800)
        val f2 = focus()
        if (f2?.etiquette != "biblio") fail("[${design.name}] focusBiblio : obtenu ${decrire(f2)}")
        // Et la navigation repart bien de là
        hautDepuis("biblio", "« Toutes les apps » (retour de la bibliothèque)")
    }

    /** Touche Menu : menu de l'app sélectionnée (favori et « Mes apps »). */
    @Test
    fun toucheMenu() {
        afficher()
        appuyer(Key.Menu)
        donnerFocus(app(1))
        appuyer(Key.Menu)
        val attendu = listOf(ui.favorites[0].packageName, ui.homeApps[1].packageName)
        if (menus != attendu) fail("[${design.name}] touche Menu : menus demandés $menus, attendu $attendu")
    }

    // ------------------------------------------------------------------ outils

    /** ▼ depuis le favori [i] (déjà sélectionné) : élément VISIBLE de « Mes apps » le plus aligné. */
    private fun basDepuis(i: Int, nom: String = "favori n°${i + 1}") {
        val avant = focus()
        val candidats = elementsVisibles(mesApps = true)
        appuyer(Key.DirectionDown)
        verifierSaut(nom, avant, focus(), versMesApps = true, candidats = candidats)
    }

    /** ▲ depuis un élément de « Mes apps » : favori VISIBLE le plus aligné. */
    private fun hautDepuis(etiquette: String, nom: String) {
        if (focus()?.etiquette != etiquette) donnerFocus(etiquette)
        val avant = focus()
        val candidats = elementsVisibles(mesApps = false)
        appuyer(Key.DirectionUp)
        verifierSaut(nom, avant, focus(), versMesApps = false, candidats = candidats)
    }

    private fun attendreApres(touche: Key, attendu: String, nom: String) {
        appuyer(touche)
        val f = focus()
        if (f?.etiquette != attendu) fail("[${design.name}] $nom : attendu $attendu, obtenu ${decrire(f)}. ${rangees()}")
    }

    /**
     * Scénario de l'utilisateur : sur un des deux premiers favoris, ▼ deux fois. Sur Verre, le
     * focus passait au 2e favori puis revenait au 1er au lieu de descendre dans « Mes apps ».
     */
    private fun scenario(i: Int) {
        val etapes = mutableListOf("favori n°${i + 1} ${decrire(focus())}")
        appuyer(Key.DirectionDown)
        val apres1 = focus()
        etapes += "▼ → ${decrire(apres1)}"
        appuyer(Key.DirectionDown)
        val apres2 = focus()
        etapes += "▼ → ${decrire(apres2)}"
        if (!estDansMesApps(apres1) || !estDansMesApps(apres2)) {
            fail(
                "[${design.name}] scénario signalé : ${etapes.joinToString(" ; ")}. " +
                    "Attendu : « Mes apps » au 1er ▼, et on y reste au 2e. ${rangees()}",
            )
        }
    }
}

/** Sans aucun favori : la carte d'aide prend la place de la rangée. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class NavigationSansFavoriTest(nomDesign: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> = Design.entries.map { arrayOf<Any>(it.name) }
    }

    override fun preparer(u: HomeUi): HomeUi =
        u.copy(favorites = emptyList(), prefs = u.prefs.copy(favorites = emptyList()))

    @Test
    fun basDepuisLaCarteAide() {
        afficher()
        val avant = focus()
        val candidats = elementsVisibles(mesApps = true)
        appuyer(Key.DirectionDown)
        verifierSaut("carte d'aide", avant, focus(), versMesApps = true, candidats = candidats)
    }

    @Test
    fun hautVersLaCarteAide() {
        afficher()
        for (etiquette in listOf(app(0), app(3), "biblio")) {
            donnerFocus(etiquette)
            appuyer(Key.DirectionUp)
            val f = focus()
            if (f?.etiquette != "aide") fail("[${design.name}] $etiquette puis ▲ : attendu la carte d'aide, obtenu ${decrire(f)}. ${rangees()}")
        }
    }
}

/** « Mes apps » vide : il ne reste que « Toutes les apps ». */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class NavigationSeuleBibliothequeTest(nomDesign: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> = Design.entries.map { arrayOf<Any>(it.name) }
    }

    override fun preparer(u: HomeUi): HomeUi =
        u.copy(homeApps = emptyList(), prefs = u.prefs.copy(homeApps = emptyList()))

    @Test
    fun basVersToutesLesApps() {
        afficher()
        for (i in listOf(0, 1)) {
            if (i > 0) allerAuFavori(i, depuis = i - 1)
            appuyer(Key.DirectionDown)
            val f = focus()
            if (f?.etiquette != "biblio") fail("[${design.name}] favori n°${i + 1} puis ▼ : attendu « Toutes les apps », obtenu ${decrire(f)}. ${rangees()}")
            appuyer(Key.DirectionUp)
            val g = focus()
            if (g?.etiquette != favori(i)) fail("[${design.name}] « Toutes les apps » puis ▲ : attendu ${favori(i)}, obtenu ${decrire(g)}. ${rangees()}")
        }
    }
}
