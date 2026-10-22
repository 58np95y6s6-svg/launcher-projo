package fr.projo.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
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
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.projo.launcher.LauncherViewModel
import fr.projo.launcher.data.IconLoader
import fr.projo.launcher.data.NbaRepository
import fr.projo.launcher.data.ReglageNouveautes
import fr.projo.launcher.remote.EtatAppairage
import fr.projo.launcher.remote.RemoteConfig
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
import java.io.File

/**
 * Panneau Réglages, pour CHACUN des 6 designs (chacun a sa forme : feuille, fenêtre, grille…) :
 * focus initial sur le volume, toutes les tuiles présentes (Suggestions pour Studio seulement)
 * et atteignables à la télécommande sans que le focus s'échappe, ◀ et Retour ferment, réglage
 * du volume (◀ ▶ règlent sans fermer), actions des tuiles « Affiches des nouveautés » et
 * « Suggestions ». Captures : panneau ouvert, volume en cours de réglage, bas de la liste, et
 * dialogues ouverts depuis le panneau (Design, Modules, clé TMDB, appairage) aux couleurs du design.
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
    private var suggestions = 0
    private var ambiances = 0
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
                touche(Key.DirectionRight)
                verifierDansLePanneau("▶ depuis $avant")
                if (cle() == avant) break
                visitees += cle()
                etapes += "▶ ${cle()}"
                pas++
            }
            // ◀ autant de fois : retour au début de la rangée, sans fermer le panneau
            repeat(pas) {
                touche(Key.DirectionLeft)
                verifierDansLePanneau("◀ dans la rangée de $debut")
            }
            if (cle() != debut) fail("[${design.name}] ◀ ×$pas : attendu le retour sur $debut, obtenu ${cle()}. Étapes : $etapes")
            // ▼ : rangée suivante
            touche(Key.DirectionDown)
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
        repeat(40) { if (cle() != "reglage:volume") touche(Key.DirectionUp) }
        verifierDansLePanneau("▲ jusqu'en haut")
        if (cle() != "reglage:volume") fail("[${design.name}] ▲ jusqu'en haut : obtenu ${cle()}")
    }

    /** Toutes les tuiles de l'app, celles ajoutées depuis comprises ; « Suggestions » pour Studio seulement. */
    @Test
    fun tuilesDuPanneau() {
        ouvrir()
        attendreFocus("reglage:volume", "ouverture")
        val attendues = listOfNotNull(
            "volume", "maj", "wifi", "bluetooth", "stockage",
            "accueil", "hdmi", "design", "modules", "suggestions".takeIf { design == Design.STUDIO }, "masquees", "nouveautes", "ambiance",
            "appairer", "telephones", "aide-telecommande",
            "langue", "date", "son", "tous",
        ).map { "reglage:$it" }.toSet()
        val composees = etiquettesTuiles()
        if (composees != attendues) {
            fail("[${design.name}] tuiles en trop : ${composees - attendues}, manquantes : ${attendues - composees}")
        }
    }

    /**
     * OK sur « Affiches des nouveautés » bascule le réglage (et son libellé) sans fermer le
     * panneau ; Studio : OK sur « Suggestions de films et séries » ouvre la saisie de la clé TMDB.
     */
    @Test
    fun actionsDesNouvellesTuiles() {
        val contexte = RuntimeEnvironment.getApplication()
        ReglageNouveautes.ecrire(contexte, true)
        try {
            ouvrir()
            attendreFocus("reglage:volume", "ouverture")
            donnerFocus("reglage:nouveautes")
            touche(Key.DirectionCenter)
            if (ReglageNouveautes.actif) fail("[${design.name}] OK sur « Affiches des nouveautés » : le réglage devait se désactiver")
            if (!texteAffiche("Désactivées · pas de photo")) fail("[${design.name}] « Affiches des nouveautés » : libellé « Désactivées » absent")
            attendreFocus("reglage:nouveautes", "après OK")
            touche(Key.DirectionCenter)
            if (!ReglageNouveautes.actif) fail("[${design.name}] 2e OK sur « Affiches des nouveautés » : le réglage devait se réactiver")
            if (design == Design.STUDIO) {
                donnerFocus("reglage:suggestions")
                touche(Key.DirectionCenter)
                if (suggestions != 1) fail("[${design.name}] OK sur « Suggestions » : saisie de la clé TMDB attendue (appels : $suggestions)")
            }
            if (fermetures != 0) fail("[${design.name}] le panneau ne devait pas se fermer")
        } finally {
            ReglageNouveautes.ecrire(contexte, true)
        }
    }

    /** « Mode Ambiance » : présente dans tous les designs, OK ouvre ses réglages sans fermer le panneau. */
    @Test
    fun tuileModeAmbiance() {
        fr.projo.launcher.ambiance.ModeAmbiance.oublier()
        ouvrir()
        attendreFocus("reglage:volume", "ouverture")
        donnerFocus("reglage:ambiance")
        if (!texteAffiche("Mode Ambiance")) fail("[${design.name}] tuile « Mode Ambiance » sans son titre")
        touche(Key.DirectionCenter)
        if (ambiances != 1) fail("[${design.name}] OK sur « Mode Ambiance » : réglages attendus (appels : $ambiances)")
        if (fermetures != 0) fail("[${design.name}] le panneau ne devait pas se fermer")
    }

    /** Réglages → Mode Ambiance : liste à cases, aux couleurs du design. */
    @Test
    fun captureDialogueAmbiance() {
        fr.projo.launcher.ambiance.ModeAmbiance.oublier()
        capturerDialogue("r8_dialogue_ambiance", "Mode Ambiance") {
            AmbianceDialog(
                etat = EtatAmbiance(micro = true, notifications = false, service = true),
                onMicro = {}, onNotifications = {}, onService = {}, onAide = {}, onDismiss = {},
            )
        }
    }

    @Test
    fun gaucheFerme() {
        ouvrir()
        attendreFocus("reglage:volume", "ouverture")
        touche(Key.DirectionLeft)
        if (fermetures != 1) fail("[${design.name}] ◀ sur le volume : le panneau devait se fermer (fermetures = $fermetures)")
    }

    @Test
    fun retourFerme() {
        ouvrir()
        attendreFocus("reglage:volume", "ouverture")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        avancer(800)
        if (fermetures != 1) fail("[${design.name}] Retour : le panneau devait se fermer (fermetures = $fermetures)")
    }

    @Test
    fun reglageDuVolume() {
        ouvrir()
        attendreFocus("reglage:volume", "ouverture")
        touche(Key.DirectionCenter)
        if (!texteAffiche("Volume · ◀ ▶")) fail("[${design.name}] OK sur le volume : le réglage devait commencer")
        touche(Key.DirectionLeft)
        touche(Key.DirectionRight)
        touche(Key.DirectionRight)
        if (volumes != listOf(-1, 1, 1)) fail("[${design.name}] ◀ ▶ ▶ pendant le réglage : volume $volumes")
        if (fermetures != 0) fail("[${design.name}] ◀ pendant le réglage ne doit pas fermer le panneau")
        attendreFocus("reglage:volume", "pendant le réglage")
        capturer("r2_reglage_volume")
        // Retour quitte le réglage sans fermer le panneau…
        touche(Key.Back)
        if (fermetures != 0) fail("[${design.name}] Retour pendant le réglage ne doit pas fermer le panneau")
        // … et ◀ ferme de nouveau, sans toucher au volume
        touche(Key.DirectionLeft)
        if (volumes.size != 3) fail("[${design.name}] ◀ après Retour : le volume ne devait plus changer ($volumes)")
        if (fermetures != 1) fail("[${design.name}] ◀ après Retour : le panneau devait se fermer")
    }

    /** Dialogue ouvert depuis le panneau (Design) : fond, forme et sélection du style du design. */
    @Test
    fun captureDialogue() {
        capturerDialogue("r4_dialogue_design", "Design de l'accueil") {
            DesignDialog(current = design, onChoose = {}, onDismiss = {})
        }
    }

    /** Réglages → Modules : liste à sous-titres, aux couleurs du design. */
    @Test
    fun captureDialogueModules() {
        capturerDialogue("r5_dialogue_modules", "Modules de l'accueil") {
            ModulesDialog(ModulesFactices.hote("meteo", "sport"), onChoisir = {}, onDismiss = {})
        }
    }

    /** Studio → Suggestions : saisie de la clé TMDB (champ, boutons), aux couleurs du design. */
    @Test
    fun captureDialogueCleTmdb() {
        capturerDialogue("r6_dialogue_cle_tmdb", "Suggestions de films et séries") {
            StudioCleDialog(onDismiss = {})
        }
    }

    /** Télécommande → Appairer : QR code, code de secours et bouton, aux couleurs du design. */
    @Test
    fun captureDialogueAppairage() {
        val t0 = 1_760_000_000_000L
        capturerDialogue("r7_dialogue_appairage", "Appairer un téléphone") {
            AppairageDialog(
                EtatAppairage.EnCours(RemoteConfig.URL_PWA + "#pair=essai", "K7QM-3XPA", t0 + 272_000),
                onNouveauCode = {}, onFermer = {}, horloge = { t0 },
            )
        }
    }

    // ------------------------------------------------------------------ outils

    /** Affiche seul un dialogue aux couleurs du design, vérifie son titre et le capture. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun capturerDialogue(nom: String, titre: String, contenu: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            CompositionLocalProvider(LocalScale provides echelle, LocalDesign provides design) {
                MaterialTheme(colorScheme = darkColorScheme()) { contenu() }
            }
        }
        avancer(1500)
        if (!texteAffiche(titre)) fail("[${design.name}] dialogue « $titre » non affiché")
        val fichier = File("build/captures/${design.name.lowercase()}_$nom.png")
        fichier.parentFile?.mkdirs()
        captureScreenRoboImage(fichier.path)
    }

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
            // Studio : suggestions factices (repli, sans réseau), comme dans StudioTest
            CompositionLocalProvider(LocalScale provides echelle, LocalDesign provides design, LocalStudioSource provides SourceStudioFactice()) {
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
                                    suggestions = { suggestions++ },
                                    ambiance = { ambiances++ },
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

    /**
     * Appui sur une touche, puis quelques images de plus : un état changé à l'appui ou au
     * relâchement (fermeture, fin du réglage) est recomposé même sans animation en cours
     * (horloge de test avancée à la main).
     */
    private fun touche(k: Key) {
        appuyer(k)
        avancer(200)
        avancer(200)
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
