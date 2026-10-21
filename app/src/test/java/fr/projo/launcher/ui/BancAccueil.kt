package fr.projo.launcher.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusRequester
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
import fr.projo.launcher.modules.HoteModules
import fr.projo.launcher.modules.LocalHoteModules
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

    /** Données affichées : celles de DonneesFactices (passées par [preparer]), sauf si un test en donne d'autres à [afficher]. */
    private var uiTest: HomeUi? = null
    protected val ui: HomeUi
        get() = uiTest ?: preparer(DonneesFactices.ui(design)).also { uiTest = it }

    /** Variante des données (sans favori, « Mes apps » vide…) : à redéfinir dans une classe de test. */
    protected open fun preparer(u: HomeUi): HomeUi = u

    /** Comme MainActivity : le focus revient sur Réglages / « Toutes les apps » à la fermeture des panneaux. */
    protected val focusReglages = FocusRequester()
    protected val focusBiblio = FocusRequester()

    /** Paquets dont le menu a été demandé (appui long ou touche Menu). */
    protected val menus = mutableListOf<String>()

    /**
     * Modules affichés à la place du bandeau NBA (voir ModulesTest) ; null : comme avant,
     * NbaPanel affiche les matchs NBA factices.
     */
    protected var hoteModules: HoteModules? = null

    /** Appuis sur la touche Maison (launcher déjà affiché) : à incrémenter pour la simuler. */
    protected val appuisMaison = androidx.compose.runtime.mutableIntStateOf(0)

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
            launch = {}, openMenu = { menus += it.packageName }, openLibrary = {}, sources = {}, sourcesLong = {},
            wifi = {}, bluetooth = {}, settings = {}, settingsLong = {}, update = {},
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            // Comme MainActivity : interface dessinée pour 960 dp de large
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            CompositionLocalProvider(
                LocalScale provides echelle,
                LocalDesign provides design,
                LocalHoteModules provides hoteModules,
            ) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    HomeScreen(
                        ui = ui,
                        icons = icons,
                        wifiLabel = wifiLabel,
                        homePressCount = appuisMaison.intValue,
                        focusReglages = focusReglages,
                        focusBiblio = focusBiblio,
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
        val attendu = if (ui.favorites.isEmpty()) "aide" else favori(0)
        if (f?.etiquette != attendu) {
            fail("[${design.name}] OUTILLAGE : focus initial attendu sur $attendu, obtenu ${decrire(f)} (écran ${racine.width}×${racine.height} px)")
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
        // Chaînes : la zone de modules s'ouvre par l'enveloppe ; une tuile d'une autre page
        // n'est pas composée : on y va à la télécommande, page après page
        if (chaines && etiquette == "nba") {
            // Ouverte par l'enveloppe, la zone reçoit le focus sur son point d'entrée (1er module)
            ouvrirZoneModules()
            return
        }
        if (chaines && etiquette in etiquettesChaines() && !estComposee(etiquette)) {
            allerALaTuile(etiquette)
            return
        }
        // « Toutes les apps » pas encore composée (hors écran) : on y va avec ▶ depuis la 1re app
        // (performScrollToNode attendrait sans fin : la lueur anime en continu)
        if (etiquette == "biblio" && compose.onAllNodes(hasTestTag(etiquette), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
            donnerFocus(app(0))
            repeat(ui.homeApps.size + 2) { if (focus()?.etiquette != "biblio") appuyer(Key.DirectionRight) }
        }
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
            it.startsWith("favori:") || it.startsWith("app:") || it == "biblio" || it == "aide" || it.startsWith("rapide:") ||
                // Zone de modules (ex-bandeau NBA) : « nba », plutôt que l'emplacement « module:… » qu'elle contient
                it == "nba"
        } ?: tags.firstOrNull() ?: "(élément sans étiquette)"
        return Cible(principale, tags, n.bornes())
    }

    /** Position dans la fenêtre, transformations comprises (agrandissement, levée), sans découpe. */
    private fun SemanticsNode.bornes(): Rect {
        val c = layoutInfo.coordinates
        return c.findRootCoordinates().localBoundingBoxOf(c, clipBounds = false)
    }

    protected fun bornesDe(etiquette: String): Rect? =
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

    protected fun estDansMesApps(c: Cible?) = c != null && estEtiquetteMesApps(c.etiquette)
    protected fun estFavori(c: Cible?) = c != null && estEtiquetteFavori(c.etiquette)
    private fun estEtiquetteMesApps(e: String) = e.startsWith("app:") || e == "biblio"
    private fun estEtiquetteFavori(e: String) = e.startsWith("favori:") || e == "aide"

    // ------------------------------------------------------------------ éléments visibles

    /** Largeur de l'écran (px). */
    protected fun largeurEcran(): Float = compose.onRoot().fetchSemanticsNode().size.width.toFloat()

    /** Partie visible d'une rangée : ses bords gauche et droit, limités à l'écran (px). */
    private fun zoneVisible(rangee: String): Pair<Float, Float> {
        val r = bornesDe(rangee) ?: return 0f to largeurEcran()
        return maxOf(r.left, 0f) to minOf(r.right, largeurEcran())
    }

    /** Éléments d'une rangée ENTIÈREMENT visibles à l'écran, de gauche à droite (étiquette, position). */
    protected fun elementsVisibles(mesApps: Boolean): List<Pair<String, Rect>> {
        val (g, d) = zoneVisible(if (mesApps) "rangee:apps" else "rangee:favoris")
        val tolerance = 1.5f
        val matcher = SemanticsMatcher("élément de rangée") { n ->
            val t = n.config.getOrNull(SemanticsProperties.TestTag)
            t != null && (if (mesApps) estEtiquetteMesApps(t) else estEtiquetteFavori(t))
        }
        return compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] to it.bornes() }
            .filter { (_, b) -> b.left >= g - tolerance && b.right <= d + tolerance }
            .distinctBy { it.first }
            .sortedBy { it.second.left }
    }

    /** L'élément [etiquette] est-il visible à l'écran (au moins 85 % de sa largeur, agrandissement compris) ? */
    protected fun estVisible(etiquette: String, mesApps: Boolean): Boolean {
        val b = bornesDe(etiquette) ?: return false
        val (g, d) = zoneVisible(if (mesApps) "rangee:apps" else "rangee:favoris")
        val visible = minOf(b.right, d) - maxOf(b.left, g)
        return visible >= 0.85f * b.width
    }

    /**
     * Vérifie un saut vertical : [apres] doit être dans l'autre rangée, être l'élément visible
     * (avant l'appui, liste [candidats]) le plus aligné avec [source], et rester visible.
     */
    protected fun verifierSaut(
        nom: String,
        avant: Cible?,
        apres: Cible?,
        versMesApps: Boolean,
        candidats: List<Pair<String, Rect>>,
    ) {
        val fleche = if (versMesApps) "▼" else "▲"
        val rangee = if (versMesApps) "un élément de « Mes apps »" else "un favori"
        val bonneRangee = if (versMesApps) estDansMesApps(apres) else estFavori(apres)
        if (avant == null || apres == null || !bonneRangee) {
            fail("[${design.name}] $nom ${decrire(avant)} puis $fleche : attendu $rangee, obtenu ${decrire(apres)}. ${rangees()}")
            return
        }
        val sx = avant.bornes.center.x
        val meilleur = candidats.minByOrNull { kotlin.math.abs(it.second.center.x - sx) }
        val choisi = candidats.firstOrNull { it.first == apres.etiquette }
        val tolerance = 2f * pxParU
        if (meilleur == null || choisi == null ||
            kotlin.math.abs(choisi.second.center.x - sx) > kotlin.math.abs(meilleur.second.center.x - sx) + tolerance
        ) {
            fail(
                "[${design.name}] $nom ${decrire(avant)} puis $fleche : obtenu ${decrire(apres)}, attendu l'élément " +
                    "visible le plus aligné ${meilleur?.let { "« ${it.first} » (${it.second.enU()})" } ?: "(aucun visible ?)"}. " +
                    "Visibles avant l'appui : ${candidats.joinToString { it.first.substringAfterLast('.') }}. ${rangees()}",
            )
        }
        if (!estVisible(apres.etiquette, versMesApps)) {
            fail("[${design.name}] $nom puis $fleche : ${decrire(apres)} n'est pas visible à l'écran. ${rangees()}")
        }
    }

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

    /**
     * Depuis le favori [depuis] (le 1er par défaut), va au favori [i] avec ▶ (échec explicite si la rangée ne suit pas).
     * Chaînes (grille paginée) : chemin ▶ ◀ ▲ ▼ calculé sur la grille, chaque pas vérifié.
     */
    protected fun allerAuFavori(i: Int, depuis: Int = 0) {
        if (chaines) {
            allerALaTuile(favori(i))
            return
        }
        for (k in depuis + 1..i) {
            appuyer(Key.DirectionRight)
            val f = focus()
            if (f?.etiquette != favori(k)) {
                fail("[${design.name}] ▶ depuis ${favori(k - 1)} : attendu ${favori(k)}, obtenu ${decrire(f)}")
            }
        }
    }

    // ------------------------------------------------------------------ Chaînes (grille paginée)

    /** Design « Chaînes » : une grille paginée au lieu des deux rangées favoris / « Mes apps ». */
    protected val chaines: Boolean get() = design == Design.CHAINES

    /** Étiquettes des tuiles de la grille, dans l'ordre (pages successives). */
    protected fun etiquettesChaines(): List<String> = tuilesChaines(ui).map { it.etiquette }

    private fun estComposee(etiquette: String) =
        compose.onAllNodes(hasTestTag(etiquette), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun touche(sens: Sens): Key = when (sens) {
        Sens.GAUCHE -> Key.DirectionLeft
        Sens.DROITE -> Key.DirectionRight
        Sens.HAUT -> Key.DirectionUp
        Sens.BAS -> Key.DirectionDown
    }

    /**
     * Appuie sur la flèche [sens] depuis la tuile sélectionnée et vérifie l'arrivée prévue par
     * la grille (GrilleChaines) : tuile voisine, même colonne pour ▲ ▼, ou bouton de la bande.
     */
    protected fun voisinChaines(sens: Sens, nom: String): Cible? {
        val etiquettes = etiquettesChaines()
        val avant = focus()
        val i = etiquettes.indexOf(avant?.etiquette)
        if (i < 0) {
            fail("[${design.name}] $nom : départ hors de la grille, ${decrire(avant)}")
            return avant
        }
        val v = GrilleChaines.voisin(i, etiquettes.size, sens)
        appuyer(touche(sens))
        val apres = focus()
        if (v == GrilleChaines.BANDE) {
            if (apres == null || !apres.etiquette.startsWith("rapide:")) {
                fail("[${design.name}] $nom ${decrire(avant)} puis ${sens.name} : attendu la bande du bas, obtenu ${decrire(apres)}")
            }
            return apres
        }
        val attendu = etiquettes[v]
        if (apres?.etiquette != attendu) {
            fail("[${design.name}] $nom ${decrire(avant)} puis ${sens.name} : attendu $attendu (tuile ${v + 1}), obtenu ${decrire(apres)}")
        }
        // ▲ ▼ dans la même colonne : la tuile d'arrivée est bien alignée avec celle de départ
        if (avant != null && apres != null && (sens == Sens.HAUT || sens == Sens.BAS) &&
            GrilleChaines.colonne(v) == GrilleChaines.colonne(i) &&
            kotlin.math.abs(apres.bornes.center.x - avant.bornes.center.x) > 2f * pxParU
        ) {
            fail("[${design.name}] $nom puis ${sens.name} : ${decrire(apres)} n'est pas aligné avec ${decrire(avant)}")
        }
        return apres
    }

    /** Va à la tuile [etiquette] à la télécommande depuis la tuile sélectionnée, chaque pas vérifié. */
    protected fun allerALaTuile(etiquette: String) {
        val etiquettes = etiquettesChaines()
        val t = etiquettes.indexOf(etiquette)
        if (t < 0) {
            fail("[${design.name}] OUTILLAGE : $etiquette n'est pas une tuile de la grille")
            return
        }
        // Focus hors de la grille (bande, modules…) : départ d'une tuile de la page affichée
        if (etiquettes.indexOf(focus()?.etiquette) < 0) {
            etiquettes.firstOrNull { estComposee(it) }?.let { donnerFocus(it) }
        }
        repeat(80) {
            val i = etiquettes.indexOf(focus()?.etiquette)
            if (i == t) return
            if (i < 0) {
                fail("[${design.name}] vers $etiquette : focus hors de la grille, ${decrire(focus())}")
                return
            }
            val g = GrilleChaines
            val sens = when {
                g.page(t) > g.page(i) -> Sens.DROITE
                g.page(t) < g.page(i) -> Sens.GAUCHE
                g.rangee(t) > g.rangee(i) -> Sens.BAS
                g.rangee(t) < g.rangee(i) -> Sens.HAUT
                g.colonne(t) > g.colonne(i) -> Sens.DROITE
                else -> Sens.GAUCHE
            }
            voisinChaines(sens, "vers $etiquette :")
        }
        fail("[${design.name}] $etiquette jamais atteinte, focus sur ${decrire(focus())}")
    }

    /**
     * ▼ puis ▲, ou ▲ puis ▼ : retour sur la tuile de départ [etiquette]. On monte d'abord
     * si la grille le permet ; depuis la 1re rangée, on descend (tuile ou bande) puis on remonte.
     */
    protected fun allerRetourChaines(etiquette: String, nom: String) {
        if (focus()?.etiquette != etiquette) donnerFocus(etiquette)
        val etiquettes = etiquettesChaines()
        val i = etiquettes.indexOf(etiquette)
        val monte = GrilleChaines.voisin(i, etiquettes.size, Sens.HAUT) != i
        val etapes = mutableListOf(decrire(focus()))
        for (sens in if (monte) listOf(Sens.HAUT, Sens.BAS) else listOf(Sens.BAS, Sens.HAUT)) {
            appuyer(touche(sens))
            etapes += "${sens.name} → ${decrire(focus())}"
        }
        if (focus()?.etiquette != etiquette) {
            fail("[${design.name}] $nom : ${etapes.joinToString(" ; ")} : attendu le retour sur $etiquette")
        }
    }

    /** Chaînes : ouvre la zone de modules par le bouton enveloppe (OK), comme à la télécommande. */
    protected fun ouvrirZoneModules() {
        if (!chaines || estComposee("nba")) return
        donnerFocus("rapide:Modules")
        appuyer(Key.DirectionCenter)
        avancer(500)
        val f = focus()
        if (f == null || "nba" !in f.chaine) fail("[${design.name}] OUTILLAGE : enveloppe puis OK, zone de modules attendue, obtenu ${decrire(f)}")
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
