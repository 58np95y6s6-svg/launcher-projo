package fr.projo.launcher.ui

import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import fr.projo.launcher.LauncherViewModel
import fr.projo.launcher.remote.EtatAppairage
import fr.projo.launcher.system.DeviceSnapshot
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Outils du pointeur (souris, tactile) pour les bancs de l'accueil : clic, toucher, appui
 * long, clic droit, glissement, molette, sur l'élément cliquable d'une étiquette.
 */
@OptIn(ExperimentalTestApi::class)
abstract class BancPointeur(nomDesign: String) : BancAccueil(nomDesign) {

    init {
        // Studio : suggestions factices (repli, sans réseau), comme dans StudioTest
        if (design == Design.STUDIO) sourceStudio = SourceStudioFactice()
    }

    protected val d get() = "[${design.name}]"

    /** Élément cliquable (Surface, ligne…) portant l'étiquette [etiquette] ou posé dans un élément qui la porte. */
    protected fun noeud(etiquette: String): SemanticsNodeInteraction {
        val m = hasClickAction() and (hasTestTag(etiquette) or hasAnyAncestor(hasTestTag(etiquette)))
        val n = compose.onAllNodes(m, useUnmergedTree = true)
        if (n.fetchSemanticsNodes().isEmpty()) fail("$d OUTILLAGE : aucun élément cliquable pour « $etiquette »")
        return n.onFirst()
    }

    /** Élément cliquable qui contient le texte [texte] (ligne de menu, bouton de dialogue). */
    protected fun noeudTexte(texte: String): SemanticsNodeInteraction {
        val n = compose.onAllNodes(hasClickAction() and hasAnyDescendant(hasText(texte, substring = true)), useUnmergedTree = true)
        if (n.fetchSemanticsNodes().isEmpty()) fail("$d OUTILLAGE : aucun élément cliquable avec le texte « $texte »")
        return n.onFirst()
    }

    /** État modifié pendant le geste (horloge du test arrêtée) : signalé à Compose, puis quelques images. */
    protected fun apres(ms: Long = 800) {
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        avancer(ms)
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        avancer(200)
    }

    protected fun toucher(n: SemanticsNodeInteraction) {
        n.performTouchInput { click() }
        apres()
    }

    protected fun cliquer(n: SemanticsNodeInteraction) {
        n.performMouseInput { click() }
        apres()
    }

    /** Appui long tactile : doigt posé 1 s (plus que le délai d'appui long), puis levé. */
    protected fun appuiLong(n: SemanticsNodeInteraction) {
        n.performTouchInput { down(center) }
        apres(1000)
        n.performTouchInput { up() }
        apres(500)
    }

    protected fun clicDroit(n: SemanticsNodeInteraction) {
        n.performMouseInput { rightClick() }
        apres()
    }

    /** Glissement du doigt (px, repère de l'écran), en 300 ms. */
    protected fun glisser(depart: Offset, arrivee: Offset) {
        compose.onRoot().performTouchInput { swipe(depart, arrivee, 300) }
        apres(1200)
    }

    /**
     * Glissement lent puis arrêt avant de lever le doigt : la liste suit le doigt sans élan
     * (pour amener un élément à l'écran sans le dépasser).
     */
    protected fun glisserSansElan(depart: Offset, arrivee: Offset) {
        compose.onRoot().performTouchInput {
            down(depart)
            for (k in 1..10) moveTo(depart + (arrivee - depart) * (k / 10f))
            advanceEventTime(300)
            move()
            up()
        }
        apres(800)
    }

    private var tempsMolette = 10_000L

    /**
     * Crans de molette à [position] (px, repère de l'écran) : > 0 vers le bas, comme une vraie
     * souris (AXIS_VSCROLL négatif). Événements Android bruts, précédés d'un survol.
     */
    protected fun molette(position: Offset, crans: Float) {
        val vue = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        tempsMolette += 1000
        fun evenement(action: Int, vscroll: Float): MotionEvent {
            val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
            val coords = MotionEvent.PointerCoords().apply {
                x = position.x
                y = position.y
                setAxisValue(MotionEvent.AXIS_VSCROLL, vscroll)
            }
            return MotionEvent.obtain(
                tempsMolette, tempsMolette, action, 1, arrayOf(props), arrayOf(coords),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
            )
        }
        compose.runOnUiThread {
            vue.dispatchGenericMotionEvent(evenement(MotionEvent.ACTION_HOVER_MOVE, 0f))
            vue.dispatchGenericMotionEvent(evenement(MotionEvent.ACTION_SCROLL, -crans))
        }
        apres()
    }

    protected fun largeur() = compose.onRoot().fetchSemanticsNode().size.width.toFloat()
    protected fun hauteur() = compose.onRoot().fetchSemanticsNode().size.height.toFloat()

    /** L'élément est-il à l'écran (au moins 90 %, découpes des listes comprises) ? */
    protected fun visible(n: SemanticsNode): Boolean {
        val c = n.layoutInfo.coordinates
        val entier = c.findRootCoordinates().localBoundingBoxOf(c, clipBounds = false)
        val vu = c.boundsInRoot()
        return entier.width > 0f && vu.width >= entier.width * 0.9f && vu.height >= entier.height * 0.9f &&
            vu.left >= -1f && vu.right <= largeur() + 1f && vu.top >= -1f && vu.bottom <= hauteur() + 1f
    }

    /**
     * Amène l'élément à l'écran AU DOIGT : la liste ou la rangée [zone] glisse (vers le haut,
     * ou vers la gauche si [horizontal]) jusqu'à ce qu'il soit visible. Pas de performScrollTo :
     * il attend sans fin la fin d'un défilement animé quand l'horloge du test est arrêtée.
     */
    protected fun amener(etiquette: String, zone: String? = null, horizontal: Boolean = false): SemanticsNodeInteraction {
        val m = hasClickAction() and (hasTestTag(etiquette) or hasAnyAncestor(hasTestTag(etiquette)))
        for (essai in 0..8) {
            val n = compose.onAllNodes(m, useUnmergedTree = true)
            val premier = n.fetchSemanticsNodes().firstOrNull()
            if (premier != null && visible(premier)) return n.onFirst()
            val b = zone?.let { bornesDe(it) } ?: break
            if (horizontal) glisserSansElan(Offset(b.left + b.width * 0.8f, b.center.y), Offset(b.left + b.width * 0.2f, b.center.y))
            else glisserSansElan(Offset(b.center.x, b.top + b.height * 0.75f), Offset(b.center.x, b.top + b.height * 0.4f))
        }
        fail("$d OUTILLAGE : « $etiquette » jamais à l'écran (zone ${zone ?: "aucune"})")
        return noeud(etiquette)
    }

    /** Rangée qui dépasse de l'écran (favoris, sinon « Mes apps ») et ses éléments, pour la faire défiler. */
    protected fun rangeeQuiDepasse(): Pair<String, List<String>> {
        val rangees = listOf("rangee:favoris" to ui.favorites.indices.map { favori(it) }, "rangee:apps" to ui.homeApps.indices.map { app(it) })
        return rangees.firstOrNull { (r, e) ->
            val b = bornesDe(r)
            val dernier = e.lastOrNull()?.let { bornesDe(it) }
            b != null && (dernier == null || dernier.right > minOf(b.right, largeur()) + 2f)
        } ?: rangees.first()
    }

    /** Le focus est sur [etiquette] (élément principal ou une étiquette de sa chaîne). */
    protected fun focusSur(etiquette: String, etape: String) {
        val f = focus()
        if (f == null || (f.etiquette != etiquette && etiquette !in f.chaine)) {
            fail("$d $etape : focus attendu sur « $etiquette », obtenu ${decrire(f)} (étiquettes ${f?.chaine})")
        }
    }

    protected fun present(etiquette: String) =
        compose.onAllNodes(hasTestTag(etiquette), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Premier élément de « Mes apps » entièrement à l'écran (rangée, grille ou barre). */
    protected fun appVisible(): String {
        for (i in ui.homeApps.indices) {
            val e = app(i)
            val n = compose.onAllNodes(hasClickAction() and (hasTestTag(e) or hasAnyAncestor(hasTestTag(e))), useUnmergedTree = true)
                .fetchSemanticsNodes().firstOrNull() ?: continue
            if (visible(n)) return e
        }
        fail("$d OUTILLAGE : aucune app de « Mes apps » à l'écran")
        return ""
    }

    /** Point de l'écran hors de l'élément [etiquette] (voile autour d'un panneau), ou null. */
    protected fun pointHors(etiquette: String): Offset? {
        val b = bornesDe(etiquette) ?: return null
        val l = largeur()
        val h = hauteur()
        return when {
            b.left > 20f -> Offset(b.left / 2f, h / 2f)
            b.top > 20f -> Offset(l / 2f, b.top / 2f)
            b.right < l - 20f -> Offset((b.right + l) / 2f, h / 2f)
            b.bottom < h - 20f -> Offset(l / 2f, (b.bottom + h) / 2f)
            else -> null
        }
    }
}

/**
 * Souris et tactile, pour CHACUN des 6 designs (version Fire TV) : favori et app de
 * « Mes apps » lancés au toucher et au clic, le focus suit le clic, appui long et clic droit
 * ouvrent le menu, un glissement fait défiler sans rien lancer, Réglages (bouton rapide,
 * tuiles, jauge du volume, dialogues, fermeture) et zone de modules au pointeur.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class PointeurTest(nomDesign: String) : BancPointeur(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> = Design.entries.map { arrayOf<Any>(it.name) }

        private const val T0 = 1_760_000_000_000L
    }

    init {
        fireTv = true
    }

    // ------------------------------------------------------------------ apps

    /** Toucher un favori le lance (une seule fois) et lui donne le focus ; clic souris sur une app de « Mes apps », idem. */
    @Test
    fun favoriEtMesApps() {
        afficher()
        val f = favori(1)
        toucher(noeud(f))
        if (lances != listOf(ui.favorites[1].packageName)) fail("$d toucher $f : lancé(s) $lances")
        focusSur(f, "toucher $f")
        val a = appVisible()
        cliquer(noeud(a))
        if (lances != listOf(ui.favorites[1].packageName, a.removePrefix("app:"))) fail("$d clic sur $a : lancé(s) $lances")
        focusSur(a, "clic sur $a")
        if (menus.isNotEmpty()) fail("$d aucun menu attendu, obtenu $menus")
    }

    /** Appui long (tactile) sur un favori et clic droit sur une app : menu de l'app, rien de lancé, focus sur l'app. */
    @Test
    fun appuiLongEtClicDroit() {
        afficher()
        val f = favori(2)
        appuiLong(noeud(f))
        if (menus != listOf(ui.favorites[2].packageName)) fail("$d appui long sur $f : menus $menus")
        focusSur(f, "appui long sur $f")
        val a = appVisible()
        clicDroit(noeud(a))
        if (menus != listOf(ui.favorites[2].packageName, a.removePrefix("app:"))) fail("$d clic droit sur $a : menus $menus")
        focusSur(a, "clic droit sur $a")
        if (lances.isNotEmpty()) fail("$d rien ne devait se lancer, obtenu $lances")
    }

    /** Glisser le doigt sur la rangée des favoris la fait défiler sans rien lancer (Chaînes : page suivante). */
    @Test
    fun glisserNeLancePas() {
        afficher()
        if (chaines) {
            val g = bornesDe("chaines:grille")!!
            glisser(Offset(g.right * 0.8f, g.center.y), Offset(g.left + g.width * 0.2f, g.center.y))
            val attendu = etiquettesChaines()[GrilleChaines.PAR_PAGE]
            focusSur(attendu, "glisser vers la gauche sur la grille")
        } else {
            // Rangée qui dépasse de l'écran (favoris, sinon « Mes apps ») : elle doit défiler
            val (rangee, elements) = rangeeQuiDepasse()
            val r = bornesDe(rangee) ?: return fail("$d OUTILLAGE : $rangee introuvable")
            val avant = bornesDe(elements[0])!!.left
            glisser(Offset(r.left + r.width * 0.85f, r.center.y), Offset(r.left + r.width * 0.15f, r.center.y))
            val apres = bornesDe(elements[0])?.left ?: Float.NEGATIVE_INFINITY
            if (apres > avant - 20f) fail("$d glisser sur $rangee : la rangée n'a pas défilé ($avant → $apres px)")
        }
        if (lances.isNotEmpty() || menus.isNotEmpty()) fail("$d un glissement n'est pas un clic : lancés $lances, menus $menus")
    }

    // ------------------------------------------------------------------ Réglages

    private var dialogue by mutableStateOf<String?>(null)
    private val choix = mutableListOf<String>()
    private val volumes = mutableListOf<Int>()
    private var fermetures = 0

    /** Comme MainActivity : le bouton Réglages ouvre le panneau, dont les tuiles ouvrent des dialogues. */
    override fun surReglages() {
        pardessus.value = { PanneauEssai() }
    }

    @Composable
    private fun PanneauEssai() {
        SettingsPanel(
            snapshot = DeviceSnapshot(
                volume = 10, volumeMax = 15, muet = false, reseau = "Maison-5G", enLigne = true,
                signal = 4, bluetoothActif = true, appareilsBluetooth = listOf("Casque"),
                stockageLibre = 3_000_000_000L, stockageTotal = 8_000_000_000L,
            ),
            maj = LauncherViewModel.MajEtat(),
            version = "2.3",
            hiddenCount = 3,
            isDefaultHome = true,
            actions = SettingsActions(
                volume = { volumes += it }, mute = { choix += "muet" }, wifi = { choix += "wifi" }, bluetooth = {},
                storage = {}, update = {}, checkUpdate = {}, hiddenApps = {}, sources = {}, homeApp = {},
                language = {}, date = {}, sound = {}, allSettings = {},
                close = { fermetures++; pardessus.value = null },
                ouvertureAuto = { choix += "auto" },
                design = { dialogue = "design" },
                appairer = { dialogue = "appairage" },
            ),
        )
        when (dialogue) {
            "design" -> DesignDialog(current = design, onChoose = { choix += "design:" + it.name }, onDismiss = { dialogue = null })
            // Code expiré : boutons « Nouveau code » et « Fermer »
            "appairage" -> AppairageDialog(
                EtatAppairage.Expire,
                onNouveauCode = { choix += "nouveau code" },
                onFermer = { choix += "fermer"; dialogue = null },
                horloge = { T0 },
            )
        }
    }

    private fun ouvrirReglagesAuToucher() {
        toucher(noeud("rapide:Réglages"))
        if (rapides != listOf("reglages")) fail("$d toucher Réglages : actions $rapides")
        if (!present("reglages:panneau")) fail("$d toucher Réglages : panneau non affiché")
        focusSur("reglage:volume", "ouverture du panneau au toucher")
    }

    /** Bouton Réglages au toucher, puis tuiles au clic et au toucher (focus qui suit), jauge du volume, Fire TV. */
    @Test
    fun reglagesTuilesEtJauge() {
        afficher()
        ouvrirReglagesAuToucher()
        cliquer(amener("reglage:wifi", "reglages:panneau"))
        if (choix != listOf("wifi")) fail("$d clic sur Wi-Fi : actions $choix")
        focusSur("reglage:wifi", "clic sur Wi-Fi")
        // Fire TV : « Ouverture automatique », en haut du panneau
        toucher(amener("reglage:auto", "reglages:panneau"))
        if (choix != listOf("wifi", "auto")) fail("$d toucher « Ouverture automatique » : actions $choix")
        focusSur("reglage:auto", "toucher « Ouverture automatique »")
        // Jauge du volume : 80 % de 15 crans = 12, depuis 10 → +2 ; la tuile du volume reprend le focus
        val jauge = compose.onAllNodes(hasTestTag("jauge:volume"), useUnmergedTree = true)
        if (jauge.fetchSemanticsNodes().isEmpty()) fail("$d jauge du volume absente")
        jauge.onFirst().performTouchInput { click(Offset(width * 0.8f, height / 2f)) }
        apres()
        if (volumes.size != 1 || volumes[0] !in 1..3) fail("$d toucher la jauge à 80 % : réglages du volume $volumes (attendu ≈ +2)")
        focusSur("reglage:volume", "toucher la jauge du volume")
        if (fermetures != 0) fail("$d le panneau ne devait pas se fermer")
    }

    /** Réglages : tuile qui ouvre un dialogue, ligne du dialogue au toucher, boutons de dialogue à la souris, fermeture. */
    @Test
    fun reglagesDialoguesEtFermeture() {
        afficher()
        ouvrirReglagesAuToucher()
        // Design : dialogue en liste (MenuDialog)
        cliquer(amener("reglage:design", "reglages:panneau"))
        if (dialogue != "design") fail("$d clic sur « Design » : dialogue $dialogue")
        val autre = if (design == Design.PASTEL) Design.VERRE else Design.PASTEL
        toucher(noeudTexte(autre.nom + " — " + autre.resume))
        if (choix != listOf("design:" + autre.name)) fail("$d toucher « ${autre.nom} » : choix $choix")
        if (dialogue != null) fail("$d le dialogue Design devait se fermer après le choix")
        // Appairage : boutons de dialogue (BoutonDialogue)
        toucher(amener("reglage:appairer", "reglages:panneau"))
        if (dialogue != "appairage") fail("$d toucher « Appairer » : dialogue $dialogue")
        cliquer(noeudTexte("Nouveau code"))
        if (choix.lastOrNull() != "nouveau code") fail("$d clic sur « Nouveau code » : choix $choix")
        toucher(noeudTexte("Fermer"))
        if (choix.lastOrNull() != "fermer" || dialogue != null) fail("$d toucher « Fermer » : choix $choix, dialogue $dialogue")
        if (fermetures != 0) fail("$d le panneau ne devait pas se fermer avec les dialogues")
        // Fermeture : × ou « Retour » s'ils sont dessinés, sinon le voile autour du panneau
        if (present("reglages:fermer")) {
            toucher(compose.onAllNodes(hasTestTag("reglages:fermer"), useUnmergedTree = true).onFirst())
        } else {
            val p = pointHors("reglages:panneau") ?: return fail("$d ni bouton de fermeture ni voile à toucher")
            compose.onRoot().performTouchInput { click(p) }
            apres()
        }
        if (fermetures != 1 || present("reglages:panneau")) fail("$d fermeture au pointeur : fermetures $fermetures")
        if (lances.isNotEmpty()) fail("$d un clic dans les Réglages a lancé $lances")
    }

    // ------------------------------------------------------------------ modules

    /** Zone de modules (Météo + Sport) : toucher et clic y mettent le focus (Studio : écran 2 à la molette ; Chaînes : enveloppe). */
    @Test
    fun moduleAuPointeur() {
        hoteModules = ModulesFactices.hote("meteo", "sport")
        afficher()
        when (design) {
            Design.CHAINES -> {
                toucher(noeud("rapide:Modules"))
                if (!present("chaines:panneau")) fail("$d toucher l'enveloppe : panneau des modules non affiché")
            }
            Design.STUDIO -> {
                molette(Offset(largeur() / 2f, hauteur() * 0.12f), 1f)
                if (!present("module:meteo") || bornesDe("module:meteo")!!.top > hauteur()) fail("$d molette : écran 2 attendu")
            }
            else -> Unit
        }
        toucher(noeud("module:meteo"))
        focusSur("module:meteo", "toucher la Météo")
        cliquer(noeud("module:sport"))
        focusSur("module:sport", "clic dans le Sport")
        if (chaines) {
            // Toucher le voile blanc autour de la zone ferme le panneau, comme ◀ ou Retour
            compose.onRoot().performTouchInput { click(Offset(20f, 20f)) }
            apres()
            if (present("chaines:panneau")) fail("$d toucher le voile : le panneau des modules devait se fermer")
        }
        if (lances.isNotEmpty()) fail("$d un clic dans les modules a lancé $lances")
    }
}

/** Studio : écran 2 (atteint seulement par ▼ à la télécommande) à la molette et au glisser. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class PointeurStudioTest : BancPointeur("STUDIO") {

    /** Haut de la barre des apps (u) : ≈ 422 sur l'écran 1, ≈ 28 sur l'écran 2. */
    private fun ecran(): Int = if ((bornesDe("rangee:favoris")?.top ?: 0f) / pxParU < 120f) 2 else 1

    private fun verifier(ecran: Int, bandes: List<String>, etape: String) {
        if (ecran() != ecran) fail("[STUDIO] $etape : écran $ecran attendu")
        val f = focus()?.etiquette.orEmpty()
        if (bandes.none { f.startsWith(it) }) fail("[STUDIO] $etape : focus attendu dans $bandes, obtenu ${decrire(focus())}")
    }

    @Test
    fun moletteEtGlisser() {
        afficher()
        val l = largeur()
        val h = hauteur()
        // Molette vers le bas sur l'en-tête : écran 2, focus sur un raccourci
        molette(Offset(l / 2f, h * 0.12f), 1f)
        verifier(2, listOf("raccourci:", "biblio", "app:projo:"), "molette vers le bas")
        // Molette vers le haut : écran 1, focus sur une suggestion (ou l'en-tête)
        // (sur les raccourcis : pas de liste qui défile sous la souris)
        molette(Offset(l / 2f, h * 0.36f), -1f)
        verifier(1, listOf("suggestion:", "onglet:", "rapide:"), "molette vers le haut")
        // Glisser vers le haut en partant des suggestions : écran 2, sans rien lancer
        glisser(Offset(l * 0.3f, h * 0.55f), Offset(l * 0.3f, h * 0.15f))
        verifier(2, listOf("raccourci:", "biblio", "app:projo:"), "glisser vers le haut")
        glisser(Offset(l * 0.5f, h * 0.38f), Offset(l * 0.5f, h * 0.85f))
        verifier(1, listOf("suggestion:", "onglet:", "rapide:"), "glisser vers le bas")
        if (lances.isNotEmpty() || menus.isNotEmpty()) fail("[STUDIO] gestes de page : lancés $lances, menus $menus")
        // Écran 2 au pointeur, puis toucher « Toutes les apps »
        molette(Offset(l / 2f, h * 0.12f), 1f)
        toucher(noeud("biblio"))
        if (rapides != listOf("bibliotheque")) fail("[STUDIO] toucher « Toutes les apps » : actions $rapides")
        focusSur("biblio", "toucher « Toutes les apps »")
    }
}

/** Chaînes : pages (atteintes par ◀ ▶ aux bords à la télécommande) aux flèches de page, au glisser et à la molette. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class PointeurChainesTest : BancPointeur("CHAINES") {

    @Test
    fun pagesAuPointeur() {
        afficher()
        val tuiles = etiquettesChaines()
        val page2 = tuiles[GrilleChaines.PAR_PAGE]
        // Flèche de page dessinée au bord : page suivante, sa 1re tuile sélectionnée
        toucher(compose.onAllNodes(hasTestTag("chaines:page-suivante"), useUnmergedTree = true).onFirst())
        focusSur(page2, "toucher la flèche de page")
        // Glisser vers la droite : page précédente
        val g = bornesDe("chaines:grille")!!
        glisser(Offset(g.left + g.width * 0.2f, g.center.y), Offset(g.left + g.width * 0.8f, g.center.y))
        focusSur(tuiles[0], "glisser vers la droite")
        // Molette vers le bas : page suivante ; « Toutes les apps » au clic
        molette(g.center, 1f)
        focusSur(page2, "molette vers le bas")
        cliquer(noeud("biblio"))
        if (rapides != listOf("bibliotheque")) fail("[CHAINES] clic sur « Toutes les apps » : actions $rapides")
        focusSur("biblio", "clic sur « Toutes les apps »")
        if (lances.isNotEmpty()) fail("[CHAINES] gestes de page : lancés $lances")
    }
}

/** Bibliothèque et molette sur une rangée (Verre). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class PointeurBibliothequeTest : BancPointeur("VERRE") {

    /** « Toutes les apps » au toucher ; dans la bibliothèque : toucher lance, appui long ouvre le menu, un clic à vide ne traverse pas. */
    @Test
    fun bibliotheque() {
        afficher()
        // « Toutes les apps » au bout de « Mes apps » : la rangée glisse au doigt jusqu'à elle
        toucher(amener("biblio", "rangee:apps", horizontal = true))
        if (rapides != listOf("bibliotheque")) fail("[VERRE] toucher « Toutes les apps » : actions $rapides")
        if (lances.isNotEmpty()) fail("[VERRE] glisser « Mes apps » a lancé $lances")
        val lancesBiblio = mutableListOf<String>()
        val menusBiblio = mutableListOf<String>()
        compose.runOnIdle {
            pardessus.value = {
                LibraryScreen(
                    ui = ui, icons = fr.projo.launcher.data.IconLoader(compose.activity),
                    onLaunch = { lancesBiblio += it.packageName }, onMenu = { menusBiblio += it.packageName },
                    onOpenHidden = {}, onClose = {},
                )
            }
        }
        apres(1500)
        // Clic sur le sous-titre (partie vide) : rien, et surtout pas l'élément de l'accueil caché derrière
        compose.onAllNodes(hasText("Maintiens OK sur une app", substring = true), useUnmergedTree = true).onFirst()
            .performTouchInput { click() }
        apres()
        val a = ui.favorites[0].packageName
        val b = ui.favorites[1].packageName
        toucher(noeud("bibliotheque:$a"))
        focusSur("bibliotheque:$a", "toucher une app de la bibliothèque")
        appuiLong(noeud("bibliotheque:$b"))
        if (lancesBiblio != listOf(a) || menusBiblio != listOf(b)) fail("[VERRE] bibliothèque : lancés $lancesBiblio, menus $menusBiblio")
        if (lances.isNotEmpty() || menus.isNotEmpty()) fail("[VERRE] un clic dans la bibliothèque a atteint l'accueil : $lances $menus")
    }

    /** Molette sur une rangée qui dépasse de l'écran : elle défile, le focus ne bouge pas, rien n'est lancé. */
    @Test
    fun moletteSurRangee() {
        afficher()
        val (rangee, elements) = rangeeQuiDepasse()
        val r = bornesDe(rangee)!!
        val avant = bornesDe(elements[0])!!.left
        molette(Offset(r.center.x, r.center.y), 2f)
        val apres = bornesDe(elements[0])?.left ?: Float.NEGATIVE_INFINITY
        if (apres > avant - 20f) fail("[VERRE] molette sur $rangee : la rangée n'a pas défilé ($avant → $apres px)")
        focusSur(favori(0), "molette sur « Mes apps »")
        if (lances.isNotEmpty()) fail("[VERRE] molette : lancés $lances")
    }
}
