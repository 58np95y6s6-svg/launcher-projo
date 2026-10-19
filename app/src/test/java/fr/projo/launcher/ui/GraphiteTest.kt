package fr.projo.launcher.ui

import androidx.compose.ui.input.key.Key
import fr.projo.launcher.data.AppInfo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Design Graphite (maquette t8, tout en pleine largeur) : captures des états sélectionnés
 * et navigation à la télécommande entre les zones.
 *
 * Règle vérifiée : ▲ / ▼ mènent à l'élément VISIBLE de la zone voisine dont le centre est le
 * plus proche horizontalement (barre des raccourcis ↔ favoris ↔ « Mes apps »).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class GraphiteTest : BancAccueil("GRAPHITE") {

    // ------------------------------------------------------------------ captures

    /** Bandeau NBA sélectionné (onglet « Cette nuit »). */
    @Test
    fun captureNba() {
        afficher()
        donnerFocus("nba")
        val f = capturer("5_nba")
        zoomer(f, "5z_zoom_nba", marge = 30)
    }

    /** 4e favori sélectionné (sous le bandeau NBA) : la grande carte au milieu de la rangée. */
    @Test
    fun captureFavori4() {
        afficher()
        allerAuFavori(3)
        val f = capturer("6_favori4")
        zoomer(f, "6z_zoom_favori4")
    }

    /** Barre des raccourcis : Réglages sélectionné (au bout de la barre, à côté du bandeau NBA). */
    @Test
    fun captureReglages() {
        afficher()
        donnerFocus("rapide:Réglages")
        val f = capturer("7_raccourci_reglages")
        zoomer(f, "7z_zoom_raccourci_reglages")
    }

    /** Pastille « Mise à jour » en tête de la barre : 5 segments, aucun libellé coupé. */
    @Test
    fun captureMiseAJour() {
        afficher(majDisponible = true)
        donnerFocus("rapide:Mise à jour")
        val f = capturer("9_mise_a_jour")
        zoomer(f, "9z_zoom_mise_a_jour", marge = 20)
    }

    /** Hors connexion et mise à jour disponible (le cas le plus chargé de la barre). */
    @Test
    fun captureHorsLigne() {
        afficher(wifiLabel = "Hors ligne", majDisponible = true)
        donnerFocus("rapide:Hors ligne")
        val f = capturer("10_hors_ligne")
        zoomer(f, "10z_zoom_hors_ligne", marge = 20)
    }

    /** Aucun favori : la carte d'aide. */
    @Test
    fun captureSansFavori() {
        val d = DonneesFactices.ui(design)
        afficher(donnees = d.copy(favorites = emptyList(), prefs = d.prefs.copy(favorites = emptyList())))
        capturer("11_sans_favori")
    }

    /** Un seul favori. */
    @Test
    fun captureUnFavori() {
        val d = DonneesFactices.ui(design)
        val un = d.favorites.take(1)
        afficher(donnees = d.copy(favorites = un, prefs = d.prefs.copy(favorites = un.map { it.packageName })))
        capturer("12_un_favori")
    }

    /** 30 apps dans « Mes apps », focus sur la 15e : la barre défile, rien n'est coupé net. */
    @Test
    fun captureTrenteApps() {
        val d = DonneesFactices.ui(design)
        val plus = (1..30 - d.homeApps.size).map { AppInfo("fr.test.app$it", "Application $it", isTvApp = true) }
        val apps = d.homeApps + plus
        afficher(donnees = d.copy(homeApps = apps, prefs = d.prefs.copy(homeApps = apps.map { it.packageName })))
        capturer("13a_trente_apps_repos")
        donnerFocus(app(0))
        for (k in 1..14) {
            appuyer(Key.DirectionRight)
            attendre("« Mes apps » ▶ n°$k", app(k))
        }
        // Les pilules nouvellement composées ne sont dessinées par Robolectric qu'après quelques images
        avancer(3000)
        capturer("13_trente_apps_15e")
        verifierSaut(Key.DirectionUp, "favoris", ::estElementFavori)
        avancer(3000)
        capturer("13b_trente_apps_puis_favori")
    }

    /** Onglet « Résultats » du bandeau NBA. */
    @Test
    fun captureResultats() {
        afficher()
        donnerFocus("nba")
        appuyer(Key.DirectionRight)
        capturer("14_nba_resultats")
        // ◀ depuis « Résultats » : « Cette nuit » (on reste dans le bandeau)
        appuyer(Key.DirectionLeft)
        attendre("Résultats puis ◀", "nba")
    }

    // ------------------------------------------------------------------ navigation

    @Test
    fun basDepuisChaqueFavoriVisible() {
        afficher()
        val n = dernierFavoriVisible()
        if (n < 4) fail("[GRAPHITE] seulement ${n + 1} favoris entièrement visibles (attendu ≥ 5)")
        for (i in 0..n) {
            donnerFocus(favori(i))
            verifierSaut(Key.DirectionDown, "« Mes apps »", ::estElementApps)
        }
    }

    @Test
    fun hautDepuisChaqueAppVisible() {
        afficher()
        val visibles = noeuds(::estElementApps).filter { it.visible() }.map { it.tag() }
        if (visibles.size < 6) fail("[GRAPHITE] seulement ${visibles.size} éléments de « Mes apps » visibles (attendu ≥ 6) : $visibles")
        for (t in visibles) {
            donnerFocus(t)
            verifierSaut(Key.DirectionUp, "favoris", ::estElementFavori)
        }
    }

    @Test
    fun hautDepuisChaqueFavoriVisible() {
        afficher()
        for (i in 0..dernierFavoriVisible()) {
            donnerFocus(favori(i))
            verifierSaut(Key.DirectionUp, "barre des raccourcis", ::estRaccourci)
        }
    }

    @Test
    fun basDepuisChaqueRaccourci() {
        afficher()
        val raccourcis = noeuds(::estRaccourci).map { it.tag() }
        if (raccourcis.size < 3) fail("[GRAPHITE] raccourcis introuvables : $raccourcis")
        for (t in raccourcis) {
            donnerFocus(t)
            verifierSaut(Key.DirectionDown, "favoris", ::estElementFavori)
        }
    }

    @Test
    fun droiteDepuisReglagesVersNba() {
        afficher()
        donnerFocus("rapide:Réglages")
        appuyer(Key.DirectionRight)
        attendre("rapide:Réglages puis ▶", "nba")
    }

    /** ◀ depuis le bandeau NBA : retour sur Réglages, d'où l'on y entre par ▶ (on ne perd pas sa place). */
    @Test
    fun gaucheDepuisNba() {
        afficher()
        donnerFocus("rapide:Réglages")
        appuyer(Key.DirectionRight)
        attendre("Réglages puis ▶", "nba")
        appuyer(Key.DirectionLeft)
        attendre("NBA puis ◀", "rapide:Réglages")
    }

    @Test
    fun retourDepuisNba() {
        afficher()
        donnerFocus("nba")
        appuyer(Key.Back)
        attendre("NBA puis Retour", favori(0))
    }

    @Test
    fun basDepuisMesAppsReste() {
        afficher()
        donnerFocus(app(0))
        appuyer(Key.DirectionDown)
        attendre("« Mes apps » n°1 puis ▼", app(0))
    }

    @Test
    fun parcoursFavorisPuisMesApps() {
        afficher()
        // ▶ jusqu'au dernier favori, puis ▶ encore : on reste sur le dernier
        val dernier = ui.favorites.lastIndex
        allerAuFavori(dernier)
        appuyer(Key.DirectionRight)
        attendre("dernier favori puis ▶", favori(dernier))
        // ▼ : un élément de « Mes apps », ▲ : de nouveau un favori
        verifierSaut(Key.DirectionDown, "« Mes apps »", ::estElementApps)
        verifierSaut(Key.DirectionUp, "favoris", ::estElementFavori)
    }

    /** ▶ d'app en app jusqu'à « Toutes les apps » (fixé au bout de la barre), puis ◀ revient sur la dernière app. */
    @Test
    fun mesAppsJusquaToutesLesApps() {
        afficher()
        donnerFocus(app(0))
        for (k in 1 until ui.homeApps.size) {
            appuyer(Key.DirectionRight)
            attendre("« Mes apps » ▶ n°$k", app(k))
        }
        appuyer(Key.DirectionRight)
        attendre("dernière app puis ▶", "biblio")
        val f = capturer("8_toutes_les_apps")
        zoomer(f, "8z_zoom_toutes_les_apps")
        appuyer(Key.DirectionRight)
        attendre("« Toutes les apps » puis ▶", "biblio")
        appuyer(Key.DirectionLeft)
        attendre("« Toutes les apps » puis ◀", app(ui.homeApps.lastIndex))
    }

    // ------------------------------------------------------------------ outils

    private fun estElementApps(t: String) = t.startsWith("app:") || t == "biblio"
    private fun estElementFavori(t: String) = t.startsWith("favori:")
    private fun estRaccourci(t: String) = t.startsWith("rapide:")

    private fun SemanticsNode.tag(): String = config.getOrNull(SemanticsProperties.TestTag) ?: ""

    /**
     * Réellement visible : au moins à moitié à l'écran (bornes découpées par la rangée) et, pour
     * les apps de la barre, dans la partie non effacée (du bord du panneau, x = 48, jusqu'à
     * « Toutes les apps », fixé au bout de la barre).
     */
    private fun SemanticsNode.visible(): Boolean {
        if (size.width <= 0 || boundsInRoot.width < size.width * 0.5f) return false
        if (!tag().startsWith("app:")) return true
        val fin = noeuds { it == "biblio" }.firstOrNull()?.positionInRoot?.x ?: return true
        val debut = 48f * pxParU
        val gauche = positionInRoot.x
        val commun = minOf(gauche + size.width, fin) - maxOf(gauche, debut)
        return commun >= size.width * 0.5f
    }

    private fun SemanticsNode.centreX(): Float = positionInRoot.x + size.width / 2f

    private fun noeuds(filtre: (String) -> Boolean): List<SemanticsNode> =
        compose.onAllNodes(
            SemanticsMatcher("étiquette") { n -> n.config.getOrNull(SemanticsProperties.TestTag)?.let(filtre) == true },
            useUnmergedTree = true,
        ).fetchSemanticsNodes()

    /** Appuie sur [touche] et vérifie que le focus arrive sur l'élément visible de la zone le plus aligné. */
    private fun verifierSaut(touche: Key, zone: String, filtre: (String) -> Boolean) {
        val avant = focus()
        if (avant == null) {
            fail("[GRAPHITE] aucun élément focalisé avant ${nomTouche(touche)}")
            return
        }
        val x = avant.bornes.center.x
        val candidats = noeuds(filtre).filter { it.visible() }
        val attendu = candidats.minByOrNull { abs(it.centreX() - x) }?.tag()
        appuyer(touche)
        val apres = focus()
        if (apres?.etiquette != attendu) {
            fail(
                "[GRAPHITE] ${decrire(avant)} puis ${nomTouche(touche)} : attendu « $attendu » ($zone, le plus aligné), " +
                    "obtenu ${decrire(apres)}. Candidats visibles : ${candidats.map { it.tag() }}",
            )
        }
    }

    private fun attendre(etape: String, etiquette: String) {
        val f = focus()
        if (f?.etiquette != etiquette) fail("[GRAPHITE] $etape : attendu « $etiquette », obtenu ${decrire(f)}")
    }

    private fun nomTouche(k: Key) = when (k) {
        Key.DirectionDown -> "▼"
        Key.DirectionUp -> "▲"
        Key.DirectionLeft -> "◀"
        Key.DirectionRight -> "▶"
        else -> k.toString()
    }
}
