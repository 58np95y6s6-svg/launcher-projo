package fr.projo.launcher.ui

import androidx.compose.ui.input.key.Key
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

    @Test
    fun gaucheDepuisNba() {
        afficher()
        donnerFocus("nba")
        appuyer(Key.DirectionLeft)
        attendre("NBA puis ◀", favori(0))
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
