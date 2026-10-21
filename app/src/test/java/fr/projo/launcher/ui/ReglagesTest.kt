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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import fr.projo.launcher.LauncherViewModel
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.system.DeviceSnapshot
import fr.projo.launcher.system.UpdateInfo
import kotlinx.coroutines.delay
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Panneau Réglages, pour CHAQUE design (chacun a sa forme : feuille, fenêtre, grille…) :
 * focus initial sur le volume, toutes les tuiles atteignables à la télécommande sans que le
 * focus s'échappe, ◀ et Retour ferment, réglage du volume (◀ ▶ règlent sans fermer).
 * Captures : panneau ouvert, volume en cours de réglage, bas de la liste.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class ReglagesTest(nomDesign: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> = Design.entries.map { arrayOf<Any>(it.name) }
    }

    /** Appels reçus par le panneau. */
    private var fermetures = 0
    private val volumes = mutableListOf<Int>()
    private var ouvert by mutableStateOf(false)

    // ------------------------------------------------------------------ tests

    @Test
    fun ouvertureEtFocusInitial() {
        ouvrir()
        attendreFocus("reglage:volume", "ouverture")
        capturer("r1_reglages_ouverts")
    }

    /**
     * Parcours « en peigne » : ▶ jusqu'au bout de chaque rangée (grilles) puis retour ◀, et ▼
     * vers la rangée suivante, jusqu'en bas. Toutes les tuiles doivent être atteintes, le
     * focus ne doit jamais quitter le panneau, puis ▲ ramène au volume.
     */
    @Test
    fun toutesLesTuilesAtteignables() {
        ouvrir()
        attendreFocus("reglage:volume", "ouverture")
        val attendues = etiquettesTuiles()
        if (attendues.size < 15) fail("[${design.name}] tuiles composées : $attendues")
        val visitees = linkedSetOf(cle())
        val etapes = mutableListOf(cle())
        for (rangee in 0 until 40) {
            val debut = cle()
            // ▶ jusqu'au bout de la rangée
            var pas = 0
            while (pas < 6) {
                val avant = cle()
                appuyer(Key.DirectionRight)
                verifierDansLePanneau("▶ depuis $avant")
                if (cle() == avant) break
                visitees += cle()
                etapes += "▶ ${cle()}"
                pas++
            }
            // ◀ autant de fois : retour au début de la rangée, sans fermer le panneau
            repeat(pas) {
                appuyer(Key.DirectionLeft)
                verifierDansLePanneau("◀ dans la rangée de $debut")
            }
            if (cle() != debut) fail("[${design.name}] ◀ ×$pas : attendu le retour sur $debut, obtenu ${cle()}. Étapes : $etapes")
            // ▼ : rangée suivante
            appuyer(Key.DirectionDown)
            verifierDansLePanneau("▼ depuis $debut")
            if (cle() == debut) break
            visitees += cle()
            etapes += "▼ ${cle()}"
        }
        if (fermetures != 0) fail("[${design.name}] le panneau s'est fermé pendant le parcours. Étapes : $etapes")
        val manquantes = attendues - visitees
        if (manquantes.isNotEmpty()) fail("[${design.name}] tuiles jamais atteintes : $manquantes. Étapes : $etapes")
        capturer("r3_reglages_bas")
        // ▲ jusqu'en haut : retour sur le volume
        repeat(40) { if (cle() != "reglage:volume") appuyer(Key.DirectionUp) }
        verifierDansLePanneau("▲ jusqu'en haut")
        if (cle() != "reglage:volume") fail("[${design.name}] ▲ jusqu'en haut : obtenu ${cle()}")
    }

    @Test
    fun gaucheEtRetourFerment() {
        ouvrir()
        attendreFocus("reglage:volume", "ouverture")
        appuyer(Key.DirectionLeft)
        if (fermetures != 1) fail("[${design.name}] ◀ sur le volume : le panneau devait se fermer (fermetures = $fermetures)")
        if (panneauAffiche()) fail("[${design.name}] ◀ : panneau toujours affiché")
        // Réouverture, puis Retour (BackHandler)
        compose.runOnIdle { ouvert = true }
        avancer(1500)
        attendreFocus("reglage:volume", "réouverture")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        avancer(800)
        if (fermetures != 2) fail("[${design.name}] Retour : le panneau devait se fermer (fermetures = $fermetures)")
    }

    @Test
    fun reglageDuVolume() {
        ouvrir()
        attendreFocus("reglage:volume", "ouverture")
        appuyer(Key.DirectionCenter)
        if (!texteAffiche("Volume · ◀ ▶")) fail("[${design.name}] OK sur le volume : le réglage devait commencer")
        appuyer(Key.DirectionLeft)
        appuyer(Key.DirectionRight)
        appuyer(Key.DirectionRight)
        if (volumes != listOf(-1, 1, 1)) fail("[${design.name}] ◀ ▶ ▶ pendant le réglage : volume $volumes")
        if (fermetures != 0) fail("[${design.name}] ◀ pendant le réglage ne doit pas fermer le panneau")
        attendreFocus("reglage:volume", "pendant le réglage")
        capturer("r2_reglage_volume")
        // Retour quitte le réglage sans fermer
        appuyer(Key.Back)
        if (fermetures != 0) fail("[${design.name}] Retour pendant le réglage ne doit pas fermer le panneau")
        if (texteAffiche("Volume · ◀ ▶")) fail("[${design.name}] Retour : le réglage devait s'arrêter")
        // ◀ ferme de nouveau
        appuyer(Key.DirectionLeft)
        if (fermetures != 1) fail("[${design.name}] ◀ après le réglage : le panneau devait se fermer")
    }

    // ------------------------------------------------------------------ outils

    /** Accueil du design, puis le panneau Réglages ouvert par-dessus (comme MainActivity). */
    private fun ouvrir(maj: Boolean = true) {
        val contexte = RuntimeEnvironment.getApplication()
        DonneesFactices.installer(contexte)
        val icons = IconLoader(contexte)
        for (p in DonneesFactices.paquets) {
            icons.load(p)
            icons.load(p, fr.projo.launcher.data.FormatVisuel.CARRE)
        }
        val actions = HomeActions(
            launch = {}, openMenu = {}, openLibrary = {}, sources = {}, sourcesLong = {},
            wifi = {}, bluetooth = {}, settings = {}, settingsLong = {}, update = {},
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            val reglages = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                // Réglages sélectionné après l'affichage, puis OK ouvre le panneau
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
                            majDisponible = maj, design = design,
                        )
                        if (ouvert) {
                            SettingsPanel(
                                snapshot = DeviceSnapshot(
                                    volume = 10, volumeMax = 15, muet = false, reseau = "Maison-5G", enLigne = true,
                                    signal = 4, bluetoothActif = true, appareilsBluetooth = listOf("Casque"),
                                    stockageLibre = 3_000_000_000L, stockageTotal = 8_000_000_000L,
                                ),
                                maj = if (maj) LauncherViewModel.MajEtat(info = UpdateInfo("v2.4", "", true)) else LauncherViewModel.MajEtat(),
                                version = "2.3",
                                hiddenCount = 3,
                                isDefaultHome = true,
                                telecommande = ResumeTelecommande(telephones = 1),
                                actions = SettingsActions(
                                    volume = { volumes += it }, mute = {}, wifi = {}, bluetooth = {}, storage = {}, update = {},
                                    checkUpdate = {}, hiddenApps = {}, sources = {}, homeApp = {}, language = {},
                                    date = {}, sound = {}, allSettings = {},
                                    close = { fermetures++; ouvert = false },
                                ),
                            )
                        }
                    }
                }
            }
        }
        avancer(2000)
        avancer(1500)
        if (!panneauAffiche()) fail("[${design.name}] OUTILLAGE : panneau Réglages non affiché")
    }

    /** Étiquette « reglage:… » de la tuile sélectionnée (ou description du focus). */
    private fun cle(): String {
        val f = focus() ?: return "(aucun focus)"
        return f.chaine.firstOrNull { it.startsWith("reglage:") } ?: "hors des tuiles : ${decrire(f)}"
    }

    private fun verifierDansLePanneau(nom: String) {
        val f = focus()
        if (f == null || "reglages:panneau" !in f.chaine) fail("[${design.name}] $nom : focus sorti du panneau, ${decrire(f)}")
    }

    private fun attendreFocus(etiquette: String, nom: String) {
        if (cle() != etiquette) fail("[${design.name}] $nom : focus attendu sur $etiquette, obtenu ${cle()}")
    }

    private fun etiquettesTuiles(): Set<String> {
        val matcher = SemanticsMatcher("tuile de réglage") { n ->
            n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("reglage:") == true
        }
        return compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] }.toSet()
    }

    private fun panneauAffiche() =
        compose.onAllNodes(hasTestTag("reglages:panneau"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun texteAffiche(texte: String) =
        compose.onAllNodes(hasText(texte), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
