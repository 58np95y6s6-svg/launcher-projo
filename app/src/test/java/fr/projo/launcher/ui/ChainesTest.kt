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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import fr.projo.launcher.HomeUi
import fr.projo.launcher.LauncherViewModel
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.system.DeviceSnapshot
import fr.projo.launcher.system.UpdateInfo
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Grille « Chaînes » sans affichage : ordre des tuiles et voisins de chaque flèche. */
class ChainesGrilleTest {

    private fun app(p: String) = AppInfo(p, p, isTvApp = true)

    @Test
    fun ordreDesTuiles() {
        val ui = HomeUi(loaded = true, favorites = listOf(app("a"), app("b")), homeApps = listOf(app("b"), app("c")))
        // Favoris, puis « Mes apps » sans doublon, puis « Toutes les apps »
        assertEquals(listOf("favori:a", "favori:b", "app:c", "biblio"), tuilesChaines(ui).map { it.etiquette })
        val sansFavori = HomeUi(loaded = true, homeApps = listOf(app("c")))
        assertEquals(listOf("aide", "app:c", "biblio"), tuilesChaines(sansFavori).map { it.etiquette })
    }

    @Test
    fun pages() {
        val g = GrilleChaines
        assertEquals(1, g.pages(0))
        assertEquals(1, g.pages(12))
        assertEquals(2, g.pages(13))
        assertEquals(4, g.pages(40))
    }

    /** 16 tuiles : page 1 pleine (0…11), page 2 avec 4 tuiles (12…15) et 8 cases vides. */
    @Test
    fun voisinsSurDeuxPages() {
        val g = GrilleChaines
        val n = 16
        fun v(i: Int, s: Sens) = g.voisin(i, n, s)
        // Dans la page
        assertEquals(1, v(0, Sens.DROITE))
        assertEquals(4, v(0, Sens.BAS))
        assertEquals(2, v(6, Sens.HAUT))
        assertEquals(5, v(6, Sens.GAUCHE))
        // Bords de la page 1 : ◀ ▲ ne font rien, ▶ passe à la page 2 (même rangée)
        assertEquals(0, v(0, Sens.GAUCHE))
        assertEquals(0, v(0, Sens.HAUT))
        assertEquals(12, v(3, Sens.DROITE))
        // Rangée 2 ou 3 absente de la page 2 : dernière tuile de la page
        assertEquals(15, v(7, Sens.DROITE))
        assertEquals(15, v(11, Sens.DROITE))
        // Page 2 : ◀ au bord revient à la page 1, même rangée, colonne de droite
        assertEquals(3, v(12, Sens.GAUCHE))
        assertEquals(14, v(15, Sens.GAUCHE))
        // Dernière tuile : ▶ ne fait rien
        assertEquals(15, v(15, Sens.DROITE))
        // ▼ depuis la dernière rangée (ou au-dessus d'une rangée vide) : la bande du bas
        assertEquals(g.BANDE, v(8, Sens.BAS))
        assertEquals(g.BANDE, v(11, Sens.BAS))
        assertEquals(g.BANDE, v(12, Sens.BAS))
    }

    @Test
    fun rangeeIncomplete() {
        // 7 tuiles : ▼ depuis la 1re rangée vise la tuile du dessous, ou la dernière si la case est vide
        assertEquals(6, GrilleChaines.voisin(2, 7, Sens.BAS))
        assertEquals(6, GrilleChaines.voisin(3, 7, Sens.BAS))
        assertEquals(GrilleChaines.BANDE, GrilleChaines.voisin(6, 7, Sens.BAS))
    }

    @Test
    fun basDeColonnes() {
        assertEquals(listOf(0 to 8, 1 to 9, 2 to 10, 3 to 11), GrilleChaines.basDeColonnes(0, 16))
        assertEquals(listOf(0 to 12, 1 to 13, 2 to 14, 3 to 15), GrilleChaines.basDeColonnes(1, 16))
        assertEquals(listOf(0 to 4, 1 to 5, 2 to 6, 3 to 3), GrilleChaines.basDeColonnes(0, 7))
    }
}

/**
 * Design « Chaînes » à l'écran : pages et flèches, bords de la grille, bande du bas,
 * touche Maison, retours de focus, zone de modules (enveloppe), et captures.
 * Données : 6 favoris + 9 « Mes apps » + « Toutes les apps » = 16 tuiles, 2 pages.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class ChainesTest : BancAccueil("CHAINES") {

    private fun composee(etiquette: String) =
        compose.onAllNodes(hasTestTag(etiquette), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun attendre(etiquette: String, etape: String) {
        val f = focus()
        if (f?.etiquette != etiquette) fail("[CHAINES] $etape : attendu $etiquette, obtenu ${decrire(f)}")
    }

    // ------------------------------------------------------------------ pages

    /** TEMPORAIRE : diagnostic (résultat dans le message d'échec). */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test
    fun diagnosticFocus() {
        val l = mutableListOf<String>()
        afficher()
        val e = etiquettesChaines()
        fun etat(nom: String) {
            l += "$nom: focus=${focus()?.etiquette} p1=${composee(e[0])} p2=${composee(e[12])} prec=${composee("chaines:page-precedente")} panneau=${composee("chaines:panneau")}"
        }
        // B. Maison depuis la tuile 2
        appuyer(Key.DirectionRight)
        compose.runOnIdle { appuisMaison.intValue++ }
        avancer(1500)
        etat("B maison")
        // A. ▶ au bord
        allerAuFavori(3)
        etat("A avant")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        avancer(50); etat("A 50")
        avancer(200); etat("A 250")
        avancer(800); etat("A 1050")
        // E. focus relais premierFocus via Maison depuis la page 2 (si on y est)
        compose.runOnIdle { appuisMaison.intValue++ }
        avancer(1500)
        etat("E maison")
        // C. panneau + Maison
        ouvrirZoneModules()
        etat("C ouvert")
        compose.runOnIdle { appuisMaison.intValue++ }
        avancer(1500)
        etat("C maison")
        // D. panneau + focusReglages
        ouvrirZoneModules()
        compose.runOnIdle { focusReglages.requestFocus() }
        avancer(800)
        etat("D reglages")
        fail("DIAG " + l.joinToString(" | "))
    }

    @Test
    fun pagesEtFleches() {
        afficher()
        val e = etiquettesChaines()
        assertEquals(16, e.size)
        // Page 1 : 12 tuiles, flèche ▶ seulement
        val absentes = e.take(12).filterNot { composee(it) }
        if (absentes.isNotEmpty()) fail("[CHAINES] page 1 : tuiles absentes $absentes")
        if (composee(e[12])) fail("[CHAINES] page 1 : ${e[12]} (page 2) ne devrait pas être affichée")
        if (!composee("chaines:page-suivante") || composee("chaines:page-precedente")) fail("[CHAINES] page 1 : attendu ▶ seule")
        capturer("c1_page1")

        // ▶ au bord droit : page 2 (glissement), même rangée
        allerAuFavori(3)
        voisinChaines(Sens.DROITE, "4e favori puis ▶")
        avancer(600)
        if (composee(e[0])) fail("[CHAINES] page 2 : la page 1 est encore affichée")
        if (composee("chaines:page-suivante") || !composee("chaines:page-precedente")) fail("[CHAINES] page 2 : attendu ◀ seule")
        // 4 tuiles et « Toutes les apps » en dernier
        val absentes2 = e.drop(12).filterNot { composee(it) }
        if (absentes2.isNotEmpty()) fail("[CHAINES] page 2 : tuiles absentes $absentes2")
        capturer("c2_page2")

        // ◀ au bord gauche : retour page 1, sur la tuile de départ
        voisinChaines(Sens.GAUCHE, "page 2, 1re tuile puis ◀")
        attendre(favori(3), "retour page 1")
    }

    @Test
    fun bordsDeLaGrille() {
        afficher()
        voisinChaines(Sens.GAUCHE, "1re tuile puis ◀")
        voisinChaines(Sens.HAUT, "1re tuile puis ▲")
        attendre(favori(0), "1re tuile, ◀ ▲")
        allerALaTuile("biblio")
        voisinChaines(Sens.DROITE, "dernière tuile puis ▶")
        voisinChaines(Sens.HAUT, "page 2, 1re rangée puis ▲")
        attendre("biblio", "dernière tuile, ▶ ▲")
        // Rien sous « Toutes les apps » : la bande du bas
        voisinChaines(Sens.BAS, "« Toutes les apps » puis ▼")
    }

    // ------------------------------------------------------------------ bande du bas

    @Test
    fun bandeDuBas() {
        afficher(majDisponible = true)
        val boutons = listOf("rapide:Réglages", "rapide:Wi-Fi", "rapide:Bluetooth", "rapide:Sources", "rapide:Mise à jour", "rapide:Modules")
        val manquants = boutons.filterNot { composee(it) }
        if (manquants.isNotEmpty()) fail("[CHAINES] bande du bas : boutons absents $manquants")
        // Dans l'ordre de l'écran, de gauche à droite
        val ordre = boutons.sortedBy { bornesDe(it)!!.left }
        if (ordre != boutons) fail("[CHAINES] ordre des boutons à l'écran : $ordre")

        // ▼ ▼ ▼ depuis la 1re tuile : jusqu'au bouton de la bande le plus aligné
        voisinChaines(Sens.BAS, "1re tuile ▼")
        voisinChaines(Sens.BAS, "5e tuile ▼")
        val avant = focus()!!
        val plusProche = boutons.minBy { kotlin.math.abs(bornesDe(it)!!.center.x - avant.bornes.center.x) }
        appuyer(Key.DirectionDown)
        attendre(plusProche, "dernière rangée puis ▼")
        // ▲ : retour sur la tuile de départ
        appuyer(Key.DirectionUp)
        attendre(avant.etiquette, "bande puis ▲")

        // ◀ ▶ parcourent la bande, butée aux deux bouts ; ▼ n'en sort pas
        appuyer(Key.DirectionDown)
        repeat(boutons.size) { appuyer(Key.DirectionLeft) }
        attendre(boutons.first(), "◀ jusqu'au bout")
        capturer("c3_bande_reglages")
        for (k in 1 until boutons.size) {
            appuyer(Key.DirectionRight)
            attendre(boutons[k], "▶ dans la bande")
        }
        appuyer(Key.DirectionRight)
        attendre(boutons.last(), "▶ au bout de la bande")
        appuyer(Key.DirectionDown)
        attendre(boutons.last(), "▼ dans la bande")
        // ▲ depuis l'enveloppe (à droite) : la tuile la plus basse de la colonne de droite
        val e = etiquettesChaines()
        appuyer(Key.DirectionUp)
        attendre(e[11], "enveloppe puis ▲")
    }

    // ------------------------------------------------------------------ retours de focus

    @Test
    fun toucheMaisonRameneAPage1() {
        afficher()
        allerALaTuile("biblio")
        compose.runOnIdle { appuisMaison.intValue++ }
        avancer(1500)
        attendre(favori(0), "page 2 puis Maison")
        if (composee("chaines:page-precedente")) fail("[CHAINES] Maison : la page 1 devrait être affichée")
    }

    @Test
    fun retourDeLaBibliothequeDepuisUneAutrePage() {
        afficher()
        // « Toutes les apps » est en page 2 : son demandeur de focus passe par un relais
        compose.runOnIdle { focusBiblio.requestFocus() }
        avancer(1500)
        attendre("biblio", "focusBiblio depuis la page 1")
        compose.runOnIdle { focusReglages.requestFocus() }
        avancer(800)
        attendre("rapide:Réglages", "focusReglages")
    }

    @Test
    fun menuSurUneTuileDeLaPage2() {
        afficher()
        allerALaTuile(app(6))
        appuyer(Key.Menu)
        if (menus != listOf(ui.homeApps[6].packageName)) fail("[CHAINES] touche Menu en page 2 : menus $menus")
    }

    // ------------------------------------------------------------------ zone de modules

    @Test
    fun zoneDeModules() {
        hoteModules = ModulesFactices.hote("meteo", "sport")
        afficher()
        if (composee("nba")) fail("[CHAINES] la zone de modules ne s'affiche qu'à l'ouverture de l'enveloppe")
        ouvrirZoneModules()
        avancer(800)
        capturer("c4_modules")
        // Le focus ne s'échappe pas du panneau
        for (k in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionRight, Key.DirectionDown, Key.DirectionUp)) {
            appuyer(k)
            if (focus()?.chaine?.contains("nba") != true) fail("[CHAINES] panneau des modules puis $k : focus sorti, ${decrire(focus())}")
        }
        // Retour : panneau fermé, 1re tuile
        appuyer(Key.Back)
        attendre(favori(0), "modules puis Retour")
        if (composee("chaines:panneau")) fail("[CHAINES] Retour : le panneau des modules devrait être fermé")
        // Maison depuis le panneau : fermé aussi
        ouvrirZoneModules()
        compose.runOnIdle { appuisMaison.intValue++ }
        avancer(1500)
        attendre(favori(0), "modules puis Maison")
        if (composee("chaines:panneau")) fail("[CHAINES] Maison : le panneau des modules devrait être fermé")
    }

    // ------------------------------------------------------------------ Réglages ouverts

    /** Comme dans l'app : « iBi » sélectionné, puis le panneau Réglages (clair) s'ouvre par-dessus. */
    @Test
    fun captureReglagesOuverts() {
        val contexte = RuntimeEnvironment.getApplication()
        DonneesFactices.installer(contexte)
        val icons = IconLoader(contexte)
        for (p in DonneesFactices.paquets) icons.load(p)
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
                            majDisponible = true, design = design,
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
        avancer(1500)
        capturer("c5_reglages_ouverts")
    }
}
