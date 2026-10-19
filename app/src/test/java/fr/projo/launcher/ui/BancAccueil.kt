package fr.projo.launcher.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import fr.projo.launcher.HomeUi
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.NbaRepository
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File
import kotlin.math.roundToInt

/** Écran de téléviseur 1080p : 1920 × 1080 px, soit 960 × 540 dp (l'écran de référence). */
const val ECRAN_TV = "w960dp-h540dp-land-xhdpi"

/**
 * Banc d'essai de l'écran d'accueil : affiche HomeScreen comme MainActivity (échelle,
 * design, thème), avec des données factices, puis pilote le focus comme une télécommande.
 *
 * L'horloge de Compose avance à la main (autoAdvance = false) : la lueur tourne sans fin
 * et empêcherait sinon le test d'attendre que l'écran soit « au repos ».
 */
@OptIn(ExperimentalTestApi::class)
abstract class BancAccueil(nomDesign: String) {

    protected val design: Design = Design.valueOf(nomDesign)

    /** Robolectric : déclare l'activité vide utilisée par Compose (avec le thème plein écran de l'app). */
    @get:Rule(order = 0)
    val activiteVide = object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            shadowOf(app.packageManager).addActivityIfNotPresent(
                ComponentName(app.packageName, ComponentActivity::class.java.name),
            )
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Données affichées : celles de DonneesFactices, sauf si un test en donne d'autres à [afficher]. */
    private var uiTest: HomeUi? = null
    protected val ui: HomeUi
        get() = uiTest ?: DonneesFactices.ui(design).also { uiTest = it }

    /** Pixels par unité d'interface (.u). */
    protected val pxParU: Float
        get() = compose.density.density * compose.activity.resources.configuration.screenWidthDp / 960f

    // ------------------------------------------------------------------ affichage

    /**
     * Affiche l'accueil chargé et vérifie que le focus initial est bien sur le 1er favori.
     * Variantes : libellé Wi-Fi ([wifiLabel], ex. « Hors ligne »), pastille « Mise à jour »
     * ([majDisponible]) et autres données ([donnees] : sans favori, 30 apps…).
     */
    protected fun afficher(
        wifiLabel: String = "Wi-Fi",
        majDisponible: Boolean = false,
        donnees: HomeUi? = null,
    ) {
        if (donnees != null) uiTest = donnees
        val ui = this.ui
        val contexte = RuntimeEnvironment.getApplication()
        DonneesFactices.installer(contexte)
        val icons = IconLoader(contexte)
        // Visuels préparés d'avance : rien à attendre en arrière-plan pendant le test
        for (p in DonneesFactices.paquets) {
            icons.load(p)
            icons.load(p, fr.projo.launcher.data.FormatVisuel.CARRE)
        }
        val nba = NbaRepository(contexte)
        val nbaData = DonneesFactices.nba()
        val actions = HomeActions(
            launch = {}, openMenu = {}, openLibrary = {}, sources = {}, sourcesLong = {},
            wifi = {}, bluetooth = {}, settings = {}, settingsLong = {}, update = {},
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            // Comme MainActivity : interface dessinée pour 960 dp de large
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            CompositionLocalProvider(LocalScale provides echelle, LocalDesign provides design) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    HomeScreen(
                        ui = ui,
                        icons = icons,
                        wifiLabel = wifiLabel,
                        homePressCount = 0,
                        actions = actions,
                        nbaData = nbaData,
                        nbaErreur = false,
                        nbaRepo = nba,
                        majDisponible = majDisponible,
                        design = design,
                    )
                }
            }
        }
        avancer(2000)
        val racine = compose.onRoot().fetchSemanticsNode().size
        println("[${design.name}] écran ${racine.width} × ${racine.height} px, ${pxParU} px par unité")
        val f = focus()
        if (ui.favorites.isNotEmpty() && f?.etiquette != favori(0)) {
            fail("[${design.name}] OUTILLAGE : focus initial attendu sur ${favori(0)}, obtenu ${decrire(f)} (écran ${racine.width}×${racine.height} px)")
        }
    }

    /** Fait avancer l'horloge de Compose (animations, défilements) puis attend le repos. */
    protected fun avancer(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    /** Appui sur une touche de la télécommande (puis 800 ms pour les animations). */
    protected fun appuyer(touche: Key) {
        compose.onRoot().performKeyInput { pressKey(touche) }
        avancer(800)
    }

    /** Donne le focus directement à un élément (préparation d'un test, pas une navigation). */
    protected fun donnerFocus(etiquette: String) {
        compose.onAllNodes(
            (hasTestTag(etiquette) or hasAnyAncestor(hasTestTag(etiquette))) and
                SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus),
            useUnmergedTree = true,
        ).onFirst().performSemanticsAction(SemanticsActions.RequestFocus)
        avancer(1000)
        val f = focus()
        if (f?.etiquette != etiquette) {
            fail("[${design.name}] OUTILLAGE : impossible de donner le focus à $etiquette, il est sur ${decrire(f)}")
        }
    }

    // ------------------------------------------------------------------ lecture du focus

    /** Élément focalisé : étiquette de l'élément (favori:…, app:…, biblio, rapide:…, nba) et position. */
    protected class Cible(val etiquette: String, val chaine: List<String>, val bornes: Rect)

    protected fun focus(): Cible? {
        val n = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?: return null
        val tags = mutableListOf<String>()
        var p: SemanticsNode? = n
        while (p != null) {
            p.config.getOrNull(SemanticsProperties.TestTag)?.let { tags += it }
            p = p.parent
        }
        val principale = tags.firstOrNull {
            it.startsWith("favori:") || it.startsWith("app:") || it == "biblio" || it.startsWith("rapide:")
        } ?: tags.firstOrNull() ?: "(élément sans étiquette)"
        return Cible(principale, tags, n.bornes())
    }

    /** Position dans la fenêtre, transformations comprises (agrandissement, levée), sans découpe. */
    private fun SemanticsNode.bornes(): Rect {
        val c = layoutInfo.coordinates
        return c.findRootCoordinates().localBoundingBoxOf(c, clipBounds = false)
    }

    private fun bornesDe(etiquette: String): Rect? =
        compose.onAllNodes(hasTestTag(etiquette), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.bornes()

    /** « x 108…254 · y 330…418 u » */
    protected fun Rect.enU(): String {
        val k = pxParU
        fun r(v: Float) = (v / k).roundToInt()
        return "x ${r(left)}…${r(right)} · y ${r(top)}…${r(bottom)} u"
    }

    protected fun decrire(c: Cible?): String =
        if (c == null) "AUCUN élément focalisé" else "« ${c.etiquette} » (${c.bornes.enU()})"

    /** Position des deux rangées, pour situer le focus dans les messages d'échec. */
    protected fun rangees(): String =
        "rangée favoris ${bornesDe("rangee:favoris")?.enU() ?: "?"} ; rangée Mes apps ${bornesDe("rangee:apps")?.enU() ?: "?"}"

    protected fun estDansMesApps(c: Cible?) = c != null && (c.etiquette.startsWith("app:") || c.etiquette == "biblio")
    protected fun estFavori(c: Cible?) = c != null && c.etiquette.startsWith("favori:")

    protected fun favori(i: Int) = "favori:" + ui.favorites[i].packageName
    protected fun app(i: Int) = "app:" + ui.homeApps[i].packageName

    /** Index du dernier favori entièrement visible à l'écran (avant tout défilement). */
    protected fun dernierFavoriVisible(): Int {
        val largeur = compose.onRoot().fetchSemanticsNode().size.width
        var dernier = 0
        for (i in ui.favorites.indices) {
            val b = bornesDe(favori(i)) ?: continue
            if (b.left >= 0f && b.right <= largeur + 0.5f) dernier = i
        }
        return dernier
    }

    /** Depuis le 1er favori, va au favori [i] avec ▶ (échec explicite si la rangée ne suit pas). */
    protected fun allerAuFavori(i: Int) {
        for (k in 1..i) {
            appuyer(Key.DirectionRight)
            val f = focus()
            if (f?.etiquette != favori(k)) {
                fail("[${design.name}] ▶ depuis ${favori(k - 1)} : attendu ${favori(k)}, obtenu ${decrire(f)}")
            }
        }
    }

    // ------------------------------------------------------------------ captures

    /** Capture de tout l'écran (1920 × 1080) dans app/build/captures/. */
    protected fun capturer(nom: String): File {
        val fichier = File("build/captures/${design.name.lowercase()}_$nom.png")
        fichier.parentFile?.mkdirs()
        compose.onRoot().captureRoboImage(fichier.path)
        return fichier
    }

    /** Agrandissement ×3 (pixels nets) autour de l'élément sélectionné, d'après une capture. */
    protected fun zoomer(source: File, nom: String, marge: Int = 50) {
        val c = focus()
        if (c == null) {
            fail("[${design.name}] zoom impossible : aucun élément focalisé")
            return
        }
        val image: Bitmap? = BitmapFactory.decodeFile(source.path)
        if (image == null) {
            fail("[${design.name}] capture illisible : $source")
            return
        }
        val m = marge * pxParU
        val x0 = (c.bornes.left - m).toInt().coerceIn(0, image.width - 1)
        val y0 = (c.bornes.top - m).toInt().coerceIn(0, image.height - 1)
        val x1 = (c.bornes.right + m).toInt().coerceIn(x0 + 1, image.width)
        val y1 = (c.bornes.bottom + m).toInt().coerceIn(y0 + 1, image.height)
        val morceau = Bitmap.createBitmap(image, x0, y0, x1 - x0, y1 - y0)
        val grand = Bitmap.createScaledBitmap(morceau, morceau.width * 3, morceau.height * 3, false)
        File(source.parentFile, "${design.name.lowercase()}_$nom.png").outputStream().use {
            grand.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
