package fr.projo.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onRoot
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import fr.projo.launcher.LauncherViewModel
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.system.DeviceSnapshot
import fr.projo.launcher.system.UpdateInfo
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * Design « Néon » : une seule rangée de tuiles carrées (favoris, puis « Mes apps », puis
 * « Toutes les apps ») au-dessus d'une rangée de boutons ronds de couleur.
 *
 * Équivalents des tests communs à deux rangées (NavigationTest) :
 *  - ▼ depuis une tuile → bouton rond VISIBLE le plus aligné ; ▼ encore : on reste ;
 *  - ▲ depuis un bouton → la tuile d'où l'on venait (même après ◀ ▶ dans les boutons) ;
 *  - ◀ ▶ le long de la rangée, des favoris à « Mes apps » puis « Toutes les apps » ;
 *  - sans favori (carte d'aide), 30 favoris, retour du focus après les panneaux, touche Menu ;
 *  - panneau des modules : ouverture par le bouton Modules, sortie par ◀ et par Retour.
 * Chaque test tourne dans les deux thèmes (sombre par défaut, et clair), avec captures.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class NeonTest(private val theme: String) : BancAccueil("NEON") {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf<Any>("sombre"), arrayOf<Any>("clair"))
    }

    init {
        changerTheme(theme == "clair")
    }

    @After
    fun remettreLeTheme() {
        changerTheme(false)
    }

    /**
     * Écrit le thème hors de toute composition : sans sendApplyNotifications(), l'écriture
     * resterait « en attente » dans l'instantané global (aucune vue Compose ne l'observe encore)
     * et la règle de test n'atteindrait plus jamais le repos, pour ce test et tous les suivants.
     */
    private fun changerTheme(clair: Boolean) {
        NeonTheme.clair = clair
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
    }

    private fun capture(nom: String) = capturer("${nom}_$theme")

    // ------------------------------------------------------------------ captures

    @Test
    fun captureTuile1() {
        afficher()
        avancer(1000)
        val f = capture("n1_tuile1")
        zoomer(f, "n1z_zoom_tuile1_$theme", marge = 30)
    }

    @Test
    fun captureBoutonWifi() {
        afficher()
        donnerFocus("rapide:Wi-Fi")
        val f = capture("n2_bouton_wifi")
        zoomer(f, "n2z_zoom_bouton_wifi_$theme", marge = 40)
    }

    /** Bouton Modules puis OK : le panneau glisse du bas, focus dans la zone de modules. */
    @Test
    fun capturePanneauModules() {
        hoteModules = ModulesFactices.hote("meteo", "sport")
        afficher()
        donnerFocus("rapide:Modules")
        appuyer(Key.DirectionCenter)
        avancer(1000)
        val f = focus()
        if (f?.chaine?.contains("nba") != true) fail("[NEON $theme] Modules puis OK : attendu la zone de modules, obtenu ${decrire(f)}")
        capture("n3_panneau_modules")
    }

    /** Toutes les apps (fin de la rangée), mise à jour disponible et hors ligne. */
    @Test
    fun captureToutesLesAppsHorsLigne() {
        afficher(wifiLabel = "Hors ligne", majDisponible = true)
        allerA("biblio")
        capture("n5_toutes_les_apps")
        donnerFocus("rapide:Mise à jour")
        capture("n6_mise_a_jour_hors_ligne")
    }

    @Test
    fun captureSansFavori() {
        val d = DonneesFactices.ui(design)
        afficher(donnees = d.copy(favorites = emptyList(), prefs = d.prefs.copy(favorites = emptyList())))
        capture("n7_sans_favori")
        // ▼ depuis la carte d'aide, puis ▲ : retour sur la carte
        basPuisRetour("carte d'aide")
    }

    /** Panneau Réglages ouvert par-dessus l'accueil (comme MainActivity), puis ▼ jusqu'à la tuile « Fond ». */
    @Test
    fun captureReglagesOuverts() {
        afficherAvecReglages()
        avancer(1500)
        capturerEcran("n4_reglages_ouverts")
        val texte = "Fond · " + if (theme == "clair") "Clair" else "Sombre"
        val hauteur = compose.onRoot().fetchSemanticsNode().size.height
        fun visible(): Boolean = compose.onAllNodes(androidx.compose.ui.test.hasText(texte), useUnmergedTree = true)
            .fetchSemanticsNodes().any { it.boundsInRoot.bottom in 1f..hauteur.toFloat() }
        // ▼ dans le panneau (liste défilante) jusqu'à ce que la tuile soit entièrement à l'écran
        repeat(30) { if (!visible()) appuyer(Key.DirectionDown) }
        appuyer(Key.DirectionDown)
        if (!visible()) fail("[NEON $theme] tuile « $texte » jamais affichée dans le panneau Réglages")
        capturerEcran("n4b_reglages_tuile_fond")
    }

    /** Capture de l'écran entier (panneau Réglages compris). */
    private fun capturerEcran(nom: String) {
        val fichier = File("build/captures/neon_${nom}_$theme.png")
        fichier.parentFile?.mkdirs()
        compose.onRoot().captureRoboImage(fichier.path)
    }

    // ------------------------------------------------------------------ navigation

    /** ◀ ▶ dans la rangée ; ◀ sur la 1re tuile ne bouge pas. */
    @Test
    fun gaucheDroite() {
        afficher()
        attendre(Key.DirectionLeft, favori(0), "1re tuile puis ◀")
        attendre(Key.DirectionRight, favori(1), "${favori(0)} puis ▶")
        attendre(Key.DirectionRight, favori(2), "${favori(1)} puis ▶")
        attendre(Key.DirectionLeft, favori(1), "${favori(2)} puis ◀")
    }

    /** Favoris 1, 2, 3, dernier visible et dernier : ▼ (bouton le plus aligné), ▼ (on reste), ▲ (retour). */
    @Test
    fun basPuisRetourDepuisLesFavoris() {
        afficher()
        val voulus = listOf(0, 1, 2, dernierFavoriVisible(), ui.favorites.lastIndex).distinct().sorted()
        var courant = 0
        for (i in voulus) {
            allerAuFavori(i, depuis = courant)
            courant = i
            basPuisRetour("favori n°${i + 1}")
        }
    }

    /** Le long de toute la rangée (favoris → « Mes apps » → « Toutes les apps ») ; ▶ au bout ne bouge pas. */
    @Test
    fun parcoursDeLaRangee() {
        afficher()
        val ordre = ui.favorites.indices.map { favori(it) } + ui.homeApps.indices.map { app(it) } + "biblio"
        for (k in 1 until ordre.size) attendre(Key.DirectionRight, ordre[k], "${ordre[k - 1]} puis ▶")
        attendre(Key.DirectionRight, "biblio", "« Toutes les apps » puis ▶")
        basPuisRetour("« Toutes les apps »")
        // ▲ sur une tuile : rien au-dessus
        attendre(Key.DirectionUp, "biblio", "« Toutes les apps » puis ▲")
        // Une app de « Mes apps » au milieu de l'écran
        attendre(Key.DirectionLeft, app(ui.homeApps.lastIndex), "« Toutes les apps » puis ◀")
        basPuisRetour("« Mes apps » n°${ui.homeApps.size}")
    }

    /** ▼ puis ◀ ▶ dans les boutons : ▲ revient sur la tuile d'où l'on venait. */
    @Test
    fun hautRevientSurLaTuileDeDepart() {
        afficher()
        allerAuFavori(2)
        appuyer(Key.DirectionDown)
        val bouton = focus()
        if (bouton?.etiquette?.startsWith("rapide:") != true) fail("[NEON $theme] ${favori(2)} puis ▼ : attendu un bouton rond, obtenu ${decrire(bouton)}")
        appuyer(Key.DirectionRight)
        appuyer(Key.DirectionRight)
        appuyer(Key.DirectionLeft)
        val ailleurs = focus()
        if (ailleurs?.etiquette?.startsWith("rapide:") != true) fail("[NEON $theme] ◀ ▶ dans les boutons : obtenu ${decrire(ailleurs)}")
        attendre(Key.DirectionUp, favori(2), "${ailleurs?.etiquette} puis ▲")
        // ◀ sur le 1er bouton et ▶ sur le dernier ne quittent pas la rangée de boutons
        val etiquettes = boutons().map { it.first }
        donnerFocus(etiquettes.first())
        attendre(Key.DirectionLeft, etiquettes.first(), "1er bouton puis ◀")
        donnerFocus(etiquettes.last())
        attendre(Key.DirectionRight, etiquettes.last(), "dernier bouton puis ▶")
    }

    /** 30 favoris : ▶ jusqu'au 15e (la rangée glisse), ▼ ▲ y ramènent. */
    @Test
    fun trenteFavoris() {
        val d = DonneesFactices.ui(design)
        val f = d.favorites + (1..24).map { AppInfo("fr.test.favori$it", "Favori $it", isTvApp = true) }
        afficher(donnees = d.copy(favorites = f, prefs = d.prefs.copy(favorites = f.map { it.packageName })))
        allerAuFavori(14)
        avancer(1000)
        capture("n8_trente_favoris_15e")
        basPuisRetour("favori n°15 sur 30")
    }

    /** Fermeture des panneaux : le focus revient sur Réglages ou « Toutes les apps ». */
    @Test
    fun retourDuFocusApresLesPanneaux() {
        afficher()
        compose.runOnIdle { focusReglages.requestFocus() }
        avancer(800)
        if (focus()?.etiquette != "rapide:Réglages") fail("[NEON $theme] focusReglages : obtenu ${decrire(focus())}")
        // « Toutes les apps » est au bout de la rangée, hors écran : la rangée glisse jusqu'à elle
        compose.runOnIdle { focusBiblio.requestFocus() }
        avancer(1200)
        if (focus()?.etiquette != "biblio") fail("[NEON $theme] focusBiblio : obtenu ${decrire(focus())}")
        basPuisRetour("« Toutes les apps » (retour de la bibliothèque)")
        // Bibliothèque ouverte par le bouton rond vert : le focus revient sur ce bouton
        donnerFocus("rapide:Toutes les apps")
        appuyer(Key.DirectionCenter)
        compose.runOnIdle { focusBiblio.requestFocus() }
        avancer(1200)
        if (focus()?.etiquette != "rapide:Toutes les apps") fail("[NEON $theme] focusBiblio après le bouton rond : obtenu ${decrire(focus())}")
    }

    /** Touche Menu : menu de l'app sélectionnée (favori et « Mes apps »). */
    @Test
    fun toucheMenu() {
        afficher()
        appuyer(Key.Menu)
        repeat(ui.favorites.size + 1) { appuyer(Key.DirectionRight) }
        appuyer(Key.Menu)
        val attendu = listOf(ui.favorites[0].packageName, ui.homeApps[1].packageName)
        if (menus != attendu) fail("[NEON $theme] touche Menu : menus demandés $menus, attendu $attendu")
    }

    /** OK lance l'app sélectionnée ; Maison ramène à la 1re tuile, même depuis le panneau des modules. */
    @Test
    fun okEtMaison() {
        afficher()
        appuyer(Key.DirectionRight)
        appuyer(Key.DirectionCenter)
        if (lances != listOf(ui.favorites[1].packageName)) fail("[NEON $theme] OK sur ${favori(1)} : apps lancées $lances")
        // Maison depuis « Mes apps » (la rangée a glissé)
        repeat(ui.favorites.size + 2) { appuyer(Key.DirectionRight) }
        compose.runOnIdle { appuisMaison.intValue++ }
        avancer(1200)
        if (focus()?.etiquette != favori(0)) fail("[NEON $theme] Maison depuis « Mes apps » : obtenu ${decrire(focus())}")
        // Maison depuis un bouton rond
        donnerFocus("rapide:Wi-Fi")
        compose.runOnIdle { appuisMaison.intValue++ }
        avancer(1200)
        if (focus()?.etiquette != favori(0)) fail("[NEON $theme] Maison depuis Wi-Fi : obtenu ${decrire(focus())}")
        // Maison depuis le panneau des modules : panneau refermé, 1re tuile
        donnerFocus("rapide:Modules")
        appuyer(Key.DirectionCenter)
        avancer(800)
        if (focus()?.chaine?.contains("nba") != true) fail("[NEON $theme] Modules puis OK : obtenu ${decrire(focus())}")
        val notes = mutableListOf("avant : ${decrire(focus())}")
        compose.runOnIdle { appuisMaison.intValue++ }
        for (pas in listOf(16L, 16L, 100L, 1000L)) {
            avancer(pas)
            notes += "+$pas ms : ${decrire(focus())} ${focus()?.chaine}"
        }
        if (focus()?.etiquette != favori(0)) fail("[NEON $theme] Maison depuis les modules : ${notes.joinToString(" | ")}")
        val zone = bornesDe("nba")
        if (zone != null && zone.top < compose.onRoot().fetchSemanticsNode().size.height - 1f) {
            fail("[NEON $theme] panneau des modules encore ouvert après Maison : ${zone.enU()}")
        }
    }

    /** Diagnostic : demandes de focus depuis le panneau des modules ouvert. */
    @Test
    fun diagnosticFocusDepuisLesModules() {
        afficher()
        val notes = mutableListOf<String>()
        fun ouvrir() {
            donnerFocus("rapide:Modules")
            appuyer(Key.DirectionCenter)
            avancer(800)
            notes += "ouvert : ${decrire(focus())}"
        }
        ouvrir()
        compose.runOnIdle { focusReglages.requestFocus() }
        avancer(800)
        notes += "focusReglages → ${decrire(focus())}"
        ouvrir()
        compose.runOnIdle { focusBiblio.requestFocus() }
        avancer(1200)
        notes += "focusBiblio → ${decrire(focus())}"
        ouvrir()
        val ok = compose.runOnIdle {
            runCatching { focusReglages.requestFocus() }.exceptionOrNull()?.toString() ?: "sans exception"
        }
        avancer(800)
        notes += "focusReglages (2) $ok → ${decrire(focus())}"
        if (notes.any { "« nba »" in it && "→" in it }) fail("[NEON $theme] ${notes.joinToString(" | ")}")
    }

    /** Panneau des modules : sortie par ◀ puis par Retour, toujours sur le bouton Modules ; panneau refermé. */
    @Test
    fun sortieDesModules() {
        afficher()
        for (touche in listOf(Key.DirectionLeft, Key.Back)) {
            donnerFocus("rapide:Modules")
            appuyer(Key.DirectionCenter)
            avancer(800)
            if (focus()?.chaine?.contains("nba") != true) fail("[NEON $theme] Modules puis OK : obtenu ${decrire(focus())}")
            appuyer(touche)
            avancer(800)
            if (focus()?.etiquette != "rapide:Modules") fail("[NEON $theme] zone de modules puis $touche : attendu rapide:Modules, obtenu ${decrire(focus())}")
            // Panneau refermé : la zone est sortie de l'écran (glissée vers le bas)
            val zone = bornesDe("nba")
            val hauteur = compose.onRoot().fetchSemanticsNode().size.height
            if (zone != null && zone.top < hauteur - 1f) fail("[NEON $theme] panneau des modules encore ouvert après $touche : ${zone.enU()}")
        }
        // ▲ depuis Modules : retour à la 1re tuile (rangée inchangée)
        attendre(Key.DirectionUp, favori(0), "Modules puis ▲")
    }

    // ------------------------------------------------------------------ outils

    /** Boutons ronds affichés (étiquette, centre x en px), de gauche à droite. */
    private fun boutons(): List<Pair<String, Float>> {
        val m = SemanticsMatcher("bouton rond") { n -> n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("rapide:") == true }
        return compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] to it.boundsInRoot.center.x }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    /** Depuis la tuile sélectionnée : ▼ (bouton le plus aligné), ▼ (on reste), ▲ (retour sur la tuile). */
    private fun basPuisRetour(nom: String) {
        val depart = focus() ?: return fail("[NEON $theme] $nom : aucun élément focalisé")
        val candidats = boutons()
        appuyer(Key.DirectionDown)
        val arrivee = focus()
        val x = depart.bornes.center.x
        val meilleur = candidats.minByOrNull { abs(it.second - x) }
        val choisi = candidats.firstOrNull { it.first == arrivee?.etiquette }
        if (meilleur == null || choisi == null || abs(choisi.second - x) > abs(meilleur.second - x) + 2f * pxParU) {
            fail(
                "[NEON $theme] $nom ${decrire(depart)} puis ▼ : obtenu ${decrire(arrivee)}, attendu le bouton le plus aligné " +
                    "« ${meilleur?.first} ». Boutons : ${candidats.joinToString { it.first }}",
            )
        }
        appuyer(Key.DirectionDown)
        if (focus()?.etiquette != arrivee?.etiquette) fail("[NEON $theme] $nom ▼ ▼ : attendu de rester sur ${arrivee?.etiquette}, obtenu ${decrire(focus())}")
        appuyer(Key.DirectionUp)
        if (focus()?.etiquette != depart.etiquette) fail("[NEON $theme] $nom ▼ ▼ ▲ : attendu le retour sur ${depart.etiquette}, obtenu ${decrire(focus())}")
    }

    /** Va à une tuile de la rangée avec ▶ depuis la 1re. */
    private fun allerA(etiquette: String) {
        repeat(ui.favorites.size + ui.homeApps.size + 1) { if (focus()?.etiquette != etiquette) appuyer(Key.DirectionRight) }
        if (focus()?.etiquette != etiquette) fail("[NEON $theme] ▶ jusqu'à $etiquette : obtenu ${decrire(focus())}")
    }

    private fun attendre(touche: Key, attendu: String, nom: String) {
        appuyer(touche)
        val f = focus()
        if (f?.etiquette != attendu) fail("[NEON $theme] $nom : attendu $attendu, obtenu ${decrire(f)}")
    }

    /** Accueil Néon avec le panneau Réglages ouvert par-dessus (Réglages sélectionné avant l'ouverture). */
    private fun afficherAvecReglages() {
        val contexte = RuntimeEnvironment.getApplication()
        DonneesFactices.installer(contexte)
        val icons = IconLoader(contexte)
        for (p in DonneesFactices.paquets) icons.load(p, fr.projo.launcher.data.FormatVisuel.CARRE)
        val actions = HomeActions(
            launch = {}, openMenu = {}, openLibrary = {}, sources = {}, sourcesLong = {},
            wifi = {}, bluetooth = {}, settings = {}, settingsLong = {}, update = {},
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            val reglages = remember { FocusRequester() }
            var ouvert by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(1200)
                runCatching { reglages.requestFocus() }
                delay(500)
                ouvert = true
            }
            CompositionLocalProvider(LocalScale provides echelle, LocalDesign provides design) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box {
                        HomeScreen(
                            ui = ui, icons = icons, wifiLabel = "Maison-5G", homePressCount = 0,
                            focusReglages = reglages, actions = actions,
                            nbaData = DonneesFactices.nba(), nbaErreur = false, nbaRepo = NbaRepository(contexte),
                            design = design,
                        )
                        if (ouvert) {
                            SettingsPanel(
                                snapshot = DeviceSnapshot(
                                    volume = 10, volumeMax = 15, muet = false, reseau = "Maison-5G", enLigne = true,
                                    signal = 4, bluetoothActif = true, appareilsBluetooth = listOf("Casque"),
                                    stockageLibre = 3_000_000_000L, stockageTotal = 8_000_000_000L,
                                ),
                                maj = LauncherViewModel.MajEtat(info = UpdateInfo("v2.4", "", true)),
                                version = "2.3",
                                hiddenCount = 3,
                                isDefaultHome = true,
                                actions = SettingsActions(
                                    volume = {}, mute = {}, wifi = {}, bluetooth = {}, storage = {}, update = {},
                                    checkUpdate = {}, hiddenApps = {}, sources = {}, homeApp = {}, language = {},
                                    date = {}, sound = {}, allSettings = {}, close = {},
                                ),
                            )
                        }
                    }
                }
            }
        }
        avancer(2000)
    }
}
