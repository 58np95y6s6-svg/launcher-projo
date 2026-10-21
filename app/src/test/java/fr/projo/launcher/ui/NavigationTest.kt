package fr.projo.launcher.ui

import androidx.compose.ui.input.key.Key
import fr.projo.launcher.HomeUi
import fr.projo.launcher.data.AppInfo
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Navigation à la télécommande entre les favoris et « Mes apps », pour chacun des 5 designs.
 * Un échec indique où le focus est parti (étiquette de l'élément et position en unités .u).
 *
 * Néon n'a qu'une rangée de tuiles (favoris, puis « Mes apps », puis « Toutes les apps ») au-dessus
 * de boutons ronds : les mêmes vérifications, transposées (▼ vers le bouton le plus aligné, ▲ retour
 * sur la tuile, ◀ ▶ le long de la rangée, sans favori, 30 favoris…), sont dans NeonTest.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class NavigationTest(nomDesign: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> = designsDeuxRangeesNeon()
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

    /**
     * Sortie du bandeau NBA par ◀ puis par Retour : retour au 1er favori (rangée remise au début).
     * Graphite : ◀ revient sur Réglages, d'où l'on y entre par ▶.
     */
    @Test
    fun sortieDuBandeauNba() {
        afficher()
        allerAuFavori(3)
        donnerFocus("nba")
        val gauche = if (design == Design.GRAPHITE) "rapide:Réglages" else favori(0)
        attendreApres(Key.DirectionLeft, gauche, "bandeau NBA puis ◀")
        donnerFocus("nba")
        attendreApres(Key.Back, favori(0), "bandeau NBA puis Retour")
    }

    /**
     * Le bandeau NBA s'atteint encore à la télécommande (sans lui donner le focus directement) :
     * ▲ depuis le dernier favori visible puis ▶ ; Console : ▶ au bout des favoris.
     */
    @Test
    fun entreeDansLaNba() {
        afficher()
        val etapes = mutableListOf<String>()
        fun dansNba() = focus()?.chaine?.contains("nba") == true
        fun touche(k: Key, nom: String) {
            appuyer(k)
            etapes += "$nom → ${decrire(focus())}"
        }
        if (design == Design.CONSOLE) {
            allerAuFavori(ui.favorites.lastIndex)
            touche(Key.DirectionRight, "▶")
        } else {
            val i = dernierFavoriVisible()
            allerAuFavori(i)
            etapes += "favori n°${i + 1} ${decrire(focus())}"
            touche(Key.DirectionUp, "▲")
            repeat(8) { if (!dansNba()) touche(Key.DirectionRight, "▶") }
        }
        if (!dansNba()) fail("[${design.name}] bandeau NBA jamais atteint : ${etapes.joinToString(" ; ")}")
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
        fun designs(): List<Array<Any>> = designsDeuxRangeesNeon()
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
        fun designs(): List<Array<Any>> = designsDeuxRangeesNeon()
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

/** Console : la carte NBA est entre les deux rangées, à droite ; on y entre par ▶ sur la dernière tuile. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class NavigationConsoleTest : BancAccueil("CONSOLE") {

    /** ▶ sur « Toutes les apps » (sous la carte NBA) : dans la carte, puis ◀ → 1er favori. */
    @Test
    fun droiteSurToutesLesAppsVersLaCarteNba() {
        afficher()
        donnerFocus("biblio")
        appuyer(Key.DirectionRight)
        val f = focus()
        if (f?.chaine?.contains("nba") != true) fail("[CONSOLE] « Toutes les apps » puis ▶ : attendu la carte NBA, obtenu ${decrire(f)}")
        appuyer(Key.DirectionLeft)
        val g = focus()
        if (g?.etiquette != favori(0)) fail("[CONSOLE] carte NBA puis ◀ : attendu ${favori(0)}, obtenu ${decrire(g)}")
    }

    @Test
    fun droiteSurLaDerniereTuileVersLaCarteNba() {
        afficher()
        allerAuFavori(ui.favorites.lastIndex)
        appuyer(Key.DirectionRight)
        val f = focus()
        if (f?.chaine?.contains("nba") != true) fail("[CONSOLE] dernière tuile puis ▶ : attendu la carte NBA, obtenu ${decrire(f)}")
        // ◀ : retour au 1er favori
        appuyer(Key.DirectionLeft)
        val g = focus()
        if (g?.etiquette != favori(0)) fail("[CONSOLE] carte NBA puis ◀ : attendu ${favori(0)}, obtenu ${decrire(g)}")
    }
}

/**
 * Rangées de tailles variées (1 app, 2 apps, 30 apps, 30 favoris), pour chacun des 5 designs :
 * ▼ / ▲ sautent toujours vers l'élément visible le plus aligné de l'autre rangée,
 * ▼ ne bouge plus une fois dans « Mes apps », et ▼ ▲ / ▲ ▼ ramènent au départ.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class NavigationVariantesTest(nomDesign: String, private val variante: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun cas(): List<Array<Any>> = Design.entries.filter { it != Design.NEON }.flatMap { d ->
            listOf("1 app", "2 apps", "30 apps", "30 favoris").map { arrayOf<Any>(d.name, it) }
        }

        /** Apps factices (sans visuel : initiale) pour allonger une rangée jusqu'à [total]. */
        private fun completer(liste: List<AppInfo>, total: Int, nom: String): List<AppInfo> =
            liste + (1..total - liste.size).map { AppInfo("fr.test.$nom$it", "${nom.replaceFirstChar { c -> c.uppercase() }} $it", isTvApp = true) }
    }

    override fun preparer(u: HomeUi): HomeUi {
        fun apps(a: List<AppInfo>) = u.copy(homeApps = a, prefs = u.prefs.copy(homeApps = a.map { it.packageName }))
        return when (variante) {
            "1 app" -> apps(u.homeApps.take(1))
            "2 apps" -> apps(u.homeApps.take(2))
            "30 apps" -> apps(completer(u.homeApps, 30, "appli"))
            else -> completer(u.favorites, 30, "favori").let { f ->
                u.copy(favorites = f, prefs = u.prefs.copy(favorites = f.map { it.packageName }))
            }
        }
    }

    /** Favoris 1, 2, dernier visible et dernier : ▼ (le plus aligné), ▼ (on reste), ▲ (retour au favori). */
    @Test
    fun basPuisRetourDepuisLesFavoris() {
        afficher()
        val voulus = listOf(0, 1, dernierFavoriVisible(), ui.favorites.lastIndex).distinct().sorted()
        var courant = 0
        for (i in voulus) {
            allerAuFavori(i, depuis = courant)
            courant = i
            val nom = "[$variante] favori n°${i + 1}"
            val avant = focus()
            val candidats = elementsVisibles(mesApps = true)
            appuyer(Key.DirectionDown)
            val arrivee = focus()
            verifierSaut(nom, avant, arrivee, versMesApps = true, candidats = candidats)
            appuyer(Key.DirectionDown)
            val encore = focus()
            if (encore?.etiquette != arrivee?.etiquette) {
                fail("[${design.name}] $nom ▼ → ${decrire(arrivee)} ▼ → ${decrire(encore)} : attendu de rester dans « Mes apps ». ${rangees()}")
            }
            appuyer(Key.DirectionUp)
            val retour = focus()
            if (retour?.etiquette != favori(i)) {
                fail("[${design.name}] $nom ▼ ▼ ▲ → ${decrire(retour)} : attendu le retour sur ${favori(i)}. ${rangees()}")
            }
        }
    }

    /** Le long de « Mes apps » (▶ d'app en app) : ▲ (le plus aligné) puis ▼ (retour sur l'app). */
    @Test
    fun hautPuisRetourLeLongDeMesApps() {
        afficher()
        val n = ui.homeApps.size
        val voulus = setOf(0, 1, n / 2, n - 1, n)
        donnerFocus(app(0))
        for (k in 0..n) {
            val etiquette = if (k == n) "biblio" else app(k)
            if (k > 0) {
                appuyer(Key.DirectionRight)
                val f = focus()
                if (f?.etiquette != etiquette) fail("[${design.name}] [$variante] ▶ dans « Mes apps » : attendu $etiquette, obtenu ${decrire(f)}")
            }
            if (k !in voulus) continue
            val nom = "[$variante] ${if (k == n) "« Toutes les apps »" else "« Mes apps » n°${k + 1}"}"
            val avant = focus()
            val candidats = elementsVisibles(mesApps = false)
            appuyer(Key.DirectionUp)
            verifierSaut(nom, avant, focus(), versMesApps = false, candidats = candidats)
            appuyer(Key.DirectionDown)
            val retour = focus()
            if (retour?.etiquette != etiquette) {
                fail("[${design.name}] $nom ▲ ▼ → ${decrire(retour)} : attendu le retour sur $etiquette. ${rangees()}")
            }
        }
    }
}

/** Designs à deux rangées « favoris » / « Mes apps » (Néon : une seule rangée, voir NeonTest). */
fun designsDeuxRangeesNeon(): List<Array<Any>> = Design.entries.filter { it != Design.NEON }.map { arrayOf<Any>(it.name) }
