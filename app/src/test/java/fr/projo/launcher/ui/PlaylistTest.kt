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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import fr.projo.launcher.LauncherViewModel
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.modules.LocalHoteModules
import fr.projo.launcher.system.DeviceSnapshot
import fr.projo.launcher.system.UpdateInfo
import kotlinx.coroutines.delay
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design « Playlist » : navigation entre les zones (barre du haut, bibliothèque à gauche,
 * contenu central, zone de modules à droite) et captures des états qui ne sont pas couverts
 * par CapturesTest (bibliothèque, filtre, modules, bouton rond, panneau Réglages ouvert).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class PlaylistTest : BancAccueil("PLAYLIST") {

    private fun ligne(i: Int) = "ligne:" + (ui.favorites + ui.homeApps)[i].packageName

    private fun attendre(attendu: String, etape: String) {
        val f = focus()
        if (f?.etiquette != attendu) fail("[PLAYLIST] $etape : attendu $attendu, obtenu ${decrire(f)}")
    }

    private fun attendreQue(etape: String, condition: (Cible) -> Boolean) {
        val f = focus()
        if (f == null || !condition(f)) fail("[PLAYLIST] $etape : obtenu ${decrire(f)} (étiquettes ${f?.chaine})")
    }

    // ------------------------------------------------------------------ navigation

    /** ◀ sur la 1re carte : la ligne de la bibliothèque la plus alignée ; ▶ : retour sur la carte. */
    @Test
    fun gaucheVersLaBibliothequePuisRetour() {
        afficher()
        appuyer(Key.DirectionLeft)
        attendreQue("◀ depuis ${favori(0)}") { it.etiquette.startsWith("ligne:") }
        appuyer(Key.DirectionRight)
        attendre(favori(0), "◀ puis ▶")
    }

    /** ▶ depuis une ligne de la bibliothèque : le contenu central, son premier élément de la ligne la plus alignée. */
    @Test
    fun droiteDepuisLaBibliotheque() {
        afficher()
        donnerFocus(ligne(1))
        appuyer(Key.DirectionRight)
        attendreQue("▶ depuis ${ligne(1)}") {
            it.etiquette.startsWith("tuile:") || it.etiquette.startsWith("favori:") || it.etiquette.startsWith("app:")
        }
        // ◀ : retour sur la même ligne
        appuyer(Key.DirectionLeft)
        attendre(ligne(1), "▶ puis ◀")
    }

    /** ▲ depuis une carte de « Vos apps » : la tuile la plus alignée ; ▼ : retour sur la carte. */
    @Test
    fun hautVersLesTuilesPuisRetour() {
        for (i in listOf(0, 1)) {
            if (i == 0) afficher() else allerAuFavori(1)
            appuyer(Key.DirectionUp)
            attendreQue("▲ depuis ${favori(i)}") { it.etiquette.startsWith("tuile:") }
            appuyer(Key.DirectionDown)
            attendre(favori(i), "▲ puis ▼ depuis ${favori(i)}")
        }
    }

    /** Tuiles : ▲ ▲ ▲ jusqu'à la barre du haut, puis ▼ revient sur la tuile. */
    @Test
    fun tuilesVersLaBarreDuHaut() {
        afficher()
        appuyer(Key.DirectionUp)
        repeat(3) { if (focus()?.etiquette?.startsWith("tuile:") == true) appuyer(Key.DirectionUp) }
        val haut = focus()
        if (haut == null || !(haut.etiquette.startsWith("rapide:") || haut.etiquette == "recherche")) {
            fail("[PLAYLIST] ▲ depuis la 1re ligne de tuiles : attendu la barre du haut, obtenu ${decrire(haut)}")
        }
        appuyer(Key.DirectionDown)
        attendreQue("▼ depuis la barre du haut") { it.etiquette.startsWith("tuile:") }
    }

    /** ▶ au bout de « Vos apps » : la zone de modules ; ◀ : retour à la 1re carte. */
    @Test
    fun droiteAuBoutDesFavorisVersLesModules() {
        afficher()
        allerAuFavori(ui.favorites.lastIndex)
        appuyer(Key.DirectionRight)
        attendreQue("▶ sur le dernier favori") { "nba" in it.chaine }
        appuyer(Key.DirectionLeft)
        attendre(favori(0), "zone de modules puis ◀")
    }

    /** ▼ depuis Réglages (au-dessus du panneau de droite) : la zone de modules ; ▲ : un bouton rond. */
    @Test
    fun reglagesVersLesModulesEtRetour() {
        afficher()
        donnerFocus("rapide:Réglages")
        appuyer(Key.DirectionDown)
        attendreQue("▼ depuis Réglages") { "nba" in it.chaine }
        appuyer(Key.DirectionUp)
        attendreQue("▲ depuis la zone de modules") { it.etiquette.startsWith("rapide:") }
    }

    /** ▼ dans « Toutes les apps » : on reste ; Retour : 1re carte de « Vos apps ». */
    @Test
    fun buteeEtRetour() {
        afficher()
        donnerFocus(app(3))
        appuyer(Key.DirectionDown)
        attendre(app(3), "▼ dans « Toutes les apps »")
        appuyer(Key.Back)
        attendre(favori(0), "Retour depuis ${app(3)}")
    }

    /** Pastille « Musique » : la bibliothèque ne montre plus que les apps de musique ; OK à nouveau : tout. */
    @Test
    fun filtreMusique() {
        afficher()
        val toutes = lignesAffichees().size
        donnerFocus("filtre:Musique")
        appuyer(Key.DirectionCenter)
        val musique = lignesAffichees()
        if (musique != listOf("ligne:com.spotify.tv.android")) fail("[PLAYLIST] filtre Musique : lignes $musique")
        capturer("p3_filtre_musique")
        appuyer(Key.DirectionCenter)
        if (lignesAffichees().size != toutes) fail("[PLAYLIST] filtre retiré : ${lignesAffichees().size} lignes au lieu de $toutes")
    }

    private fun lignesAffichees(): List<String> =
        compose.onAllNodes(
            SemanticsMatcher("ligne") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("ligne:") == true },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }.distinct()

    // ------------------------------------------------------------------ captures

    @Test
    fun captureBibliotheque() {
        afficher()
        donnerFocus(ligne(3))
        capturer("p1_bibliotheque_ligne")
    }

    @Test
    fun captureTuile() {
        afficher()
        appuyer(Key.DirectionUp)
        capturer("p2_tuile")
    }

    @Test
    fun captureModules() {
        hoteModules = ModulesFactices.hote("meteo", "sport")
        afficher()
        allerAuFavori(ui.favorites.lastIndex)
        appuyer(Key.DirectionRight)
        capturer("p4_modules")
    }

    @Test
    fun captureModuleSeul() {
        hoteModules = ModulesFactices.hote("meteo")
        afficher()
        donnerFocus("rapide:Réglages")
        appuyer(Key.DirectionDown)
        capturer("p5_module_meteo_grand")
    }

    @Test
    fun captureBoutonRondEtMiseAJour() {
        afficher(majDisponible = true, wifiLabel = "Maison-5G")
        donnerFocus("rapide:Maison-5G")
        capturer("p6_bouton_wifi_maj")
    }

    @Test
    fun captureToutesLesApps() {
        afficher()
        donnerFocus("biblio")
        capturer("p7_toutes_les_apps")
    }

    /** Comme dans l'app : Réglages sélectionné, puis OK ouvre le panneau par-dessus l'accueil. */
    @Test
    fun captureReglagesOuverts() {
        val contexte = RuntimeEnvironment.getApplication()
        DonneesFactices.installer(contexte)
        val icons = IconLoader(contexte)
        for (p in DonneesFactices.paquets) {
            icons.load(p)
            icons.load(p, fr.projo.launcher.data.FormatVisuel.CARRE)
        }
        val donnees = ui
        val actions = HomeActions(
            launch = {}, openMenu = {}, openLibrary = {}, sources = {}, sourcesLong = {},
            wifi = {}, bluetooth = {}, settings = {}, settingsLong = {}, update = {},
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            val reglages = remember { FocusRequester() }
            var panneauOuvert by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(1200)
                runCatching { reglages.requestFocus() }
                delay(500)
                panneauOuvert = true
            }
            CompositionLocalProvider(
                LocalScale provides echelle,
                LocalDesign provides design,
                LocalHoteModules provides null,
            ) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box {
                        HomeScreen(
                            ui = donnees,
                            icons = icons,
                            wifiLabel = "Maison-5G",
                            homePressCount = 0,
                            focusReglages = reglages,
                            actions = actions,
                            nbaData = DonneesFactices.nba(),
                            nbaErreur = false,
                            nbaRepo = NbaRepository(contexte),
                            design = design,
                        )
                        if (panneauOuvert) {
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
        avancer(1500)
        capturer("p8_panneau_reglages")
    }
}
