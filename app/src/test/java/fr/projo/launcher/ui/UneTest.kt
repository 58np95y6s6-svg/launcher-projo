package fr.projo.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import fr.projo.launcher.LauncherViewModel
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.NbaData
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.system.DeviceSnapshot
import fr.projo.launcher.system.UpdateInfo
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design « À la une » : navigation entre les étages (pilules, NBA, favoris, « Mes apps »),
 * butées des rangées, et captures des états qui ne sont pas couverts par CapturesTest
 * (NBA, Réglages, mise à jour, sans favori, NBA indisponible, panneau Réglages ouvert).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class UneTest : BancAccueil("UNE") {

    // ------------------------------------------------------------------ navigation

    @Test
    fun hautDepuisFavoriVersPilules() {
        afficher()
        allerAuFavori(2)
        appuyer(Key.DirectionUp)
        val f = focus()
        if (f == null || !f.etiquette.startsWith("rapide:")) fail("[UNE] ▲ depuis ${favori(2)} : attendu une pilule, obtenu ${decrire(f)}")
    }

    @Test
    fun basDepuisPiluleVersNbaPuisFavori() {
        afficher()
        donnerFocus("rapide:Réglages")
        appuyer(Key.DirectionDown)
        val nba = focus()
        if (nba == null || "nba" !in nba.chaine) fail("[UNE] ▼ depuis Réglages : attendu le bloc NBA, obtenu ${decrire(nba)}")
        // ▶ : onglet Résultats, ◀ : retour à Cette nuit
        appuyer(Key.DirectionRight)
        val res = focus()
        if (res?.chaine?.contains("nba:onglet1") != true) fail("[UNE] ▶ dans le NBA : attendu « Résultats », obtenu ${decrire(res)}")
        appuyer(Key.DirectionLeft)
        val nuit = focus()
        if (nuit?.chaine?.contains("nba:onglet0") != true) fail("[UNE] ◀ sur « Résultats » : attendu « Cette nuit », obtenu ${decrire(nuit)}")
        // ▼ : retour au favori mémorisé (le 1er)
        appuyer(Key.DirectionDown)
        val fav = focus()
        if (fav?.etiquette != favori(0)) fail("[UNE] ▼ depuis le NBA : attendu ${favori(0)}, obtenu ${decrire(fav)}")
    }

    @Test
    fun gaucheEtRetourQuittentLeNba() {
        afficher()
        donnerFocus("nba:onglet0")
        appuyer(Key.DirectionLeft)
        val f = focus()
        if (!estFavori(f)) fail("[UNE] ◀ dans le NBA : attendu un favori, obtenu ${decrire(f)}")
        donnerFocus("nba:onglet1")
        appuyer(Key.Back)
        val g = focus()
        if (!estFavori(g)) fail("[UNE] Retour dans le NBA : attendu un favori, obtenu ${decrire(g)}")
    }

    @Test
    fun hautDepuisNbaVersPilules() {
        afficher()
        donnerFocus("nba:onglet0")
        appuyer(Key.DirectionUp)
        val f = focus()
        if (f == null || !f.etiquette.startsWith("rapide:")) fail("[UNE] ▲ depuis le NBA : attendu une pilule, obtenu ${decrire(f)}")
    }

    @Test
    fun butees() {
        afficher()
        // ◀ sur le 1er favori, ▶ sur le dernier : on reste
        appuyer(Key.DirectionLeft)
        if (focus()?.etiquette != favori(0)) fail("[UNE] ◀ sur le 1er favori : obtenu ${decrire(focus())}")
        val dernier = ui.favorites.lastIndex
        allerAuFavori(dernier)
        appuyer(Key.DirectionRight)
        if (focus()?.etiquette != favori(dernier)) fail("[UNE] ▶ sur le dernier favori : obtenu ${decrire(focus())}")
        // ▼ dans « Mes apps » : on reste ; ▶ sur « Toutes les apps » : on reste
        donnerFocus(app(0))
        appuyer(Key.DirectionDown)
        if (focus()?.etiquette != app(0)) fail("[UNE] ▼ dans « Mes apps » : obtenu ${decrire(focus())}")
        donnerFocus("biblio")
        appuyer(Key.DirectionRight)
        if (focus()?.etiquette != "biblio") fail("[UNE] ▶ sur « Toutes les apps » : obtenu ${decrire(focus())}")
        // ▲ depuis « Toutes les apps » : un favori
        appuyer(Key.DirectionUp)
        if (!estFavori(focus())) fail("[UNE] ▲ depuis « Toutes les apps » : obtenu ${decrire(focus())}")
    }

    /** ▼ depuis chaque favori : « Mes apps » ; puis ▲ : un favori ; le tout sans jamais sortir de l'index. */
    @Test
    fun basPuisHautDepuisChaqueFavori() {
        afficher()
        for (i in ui.favorites.indices) {
            if (i > 0) {
                donnerFocus(favori(i))
            }
            appuyer(Key.DirectionDown)
            val bas = focus()
            if (!estDansMesApps(bas)) fail("[UNE] ▼ depuis ${favori(i)} : obtenu ${decrire(bas)}. ${rangees()}")
            appuyer(Key.DirectionUp)
            val haut = focus()
            if (!estFavori(haut)) fail("[UNE] ▲ depuis ${decrire(bas)} : obtenu ${decrire(haut)}. ${rangees()}")
        }
    }

    @Test
    fun retourDepuisMesAppsVersPremierFavori() {
        afficher()
        donnerFocus(app(3))
        appuyer(Key.Back)
        val f = focus()
        if (f?.etiquette != favori(0)) fail("[UNE] Retour depuis « Mes apps » : attendu ${favori(0)}, obtenu ${decrire(f)}")
    }

    // ------------------------------------------------------------------ captures

    @Test
    fun captureMesApps() {
        afficher()
        donnerFocus(app(2))
        capturer("3_mesapps_app3")
    }

    @Test
    fun captureNba() {
        afficher()
        donnerFocus("nba:onglet0")
        capturer("4_nba")
        donnerFocus("nba:onglet1")
        capturer("4b_nba_resultats")
    }

    @Test
    fun captureRaccourciReglages() {
        afficherVariante(maj = true)
        donnerFocus("rapide:Réglages")
        capturer("5_raccourci_reglages_maj")
    }

    @Test
    fun captureBibliotheque() {
        afficher()
        donnerFocus("biblio")
        capturer("6_biblio")
    }

    @Test
    fun captureSansFavoriSansNba() {
        afficherVariante(sansFavoris = true, nba = null, nbaErreur = true)
        capturer("7_sans_favori_sans_nba")
    }

    @Test
    fun captureReglagesOuverts() {
        afficherVariante(reglages = true)
        avancer(1000)
        capturer("8_panneau_reglages")
    }

    /**
     * Affichage avec d'autres données que le banc commun : mise à jour disponible, pas de
     * favori, NBA indisponible, panneau Réglages ouvert par-dessus (comme MainActivity).
     */
    private fun afficherVariante(
        maj: Boolean = false,
        sansFavoris: Boolean = false,
        nba: NbaData? = DonneesFactices.nba(),
        nbaErreur: Boolean = false,
        reglages: Boolean = false,
    ) {
        val contexte = RuntimeEnvironment.getApplication()
        DonneesFactices.installer(contexte)
        val icons = IconLoader(contexte)
        for (p in DonneesFactices.paquets) {
            icons.load(p)
            icons.loadIcon(p)
        }
        val donnees = if (sansFavoris) ui.copy(favorites = emptyList()) else ui
        val actions = HomeActions(
            launch = {}, openMenu = {}, openLibrary = {}, sources = {}, sourcesLong = {},
            wifi = {}, bluetooth = {}, settings = {}, settingsLong = {}, update = {},
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            CompositionLocalProvider(LocalScale provides echelle, LocalDesign provides design) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box {
                        HomeScreen(
                            ui = donnees,
                            icons = icons,
                            wifiLabel = "Maison-5G",
                            homePressCount = 0,
                            actions = actions,
                            nbaData = nba,
                            nbaErreur = nbaErreur,
                            nbaRepo = NbaRepository(contexte),
                            majDisponible = maj,
                            design = design,
                        )
                        if (reglages) {
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
