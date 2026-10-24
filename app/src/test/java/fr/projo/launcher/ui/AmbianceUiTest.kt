package fr.projo.launcher.ui

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.projo.launcher.ambiance.ModeAmbiance
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.data.AppRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Mode Ambiance côté iBiProjo : entrée « Ouvrir en mode Ambiance » dans le menu de toutes
 * les apps (elle ouvre l'app et allume le néon pour elle), explication avant la demande du
 * micro, réglages et aide (captures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class AmbianceUiTest : BancAccueil("VERRE") {

    private val spotify = "com.spotify.tv.android"
    private val netflix = "com.netflix.ninja"

    @Before
    fun avant() = ModeAmbiance.oublier()

    @After
    fun apres() = ModeAmbiance.oublier()

    private fun menu(app: AppInfo, ambiance: () -> Unit = {}) = actionsMenuApp(
        app, favori = true, accueil = false, index = 1, taille = 6,
        ActionsMenuApp(
            basculerFavori = {}, basculerAccueil = {}, deplacer = {}, changerLogo = {}, masquer = {},
            infos = {}, choisirEntree = {}, ambiance = ambiance,
        ),
    )

    @Test
    fun menuDeToutesLesAppsProposeLeModeAmbiance() {
        for (app in listOf(AppInfo(spotify, "Spotify", true), AppInfo(netflix, "Netflix", true), AppInfo("com.deezer.android.tv", "Deezer", true))) {
            val lignes = menu(app).map { it.label }
            if (ModeAmbiance.LIBELLE_MENU !in lignes) fail("menu de ${app.label} sans « ${ModeAmbiance.LIBELLE_MENU} » : $lignes")
            // Les entrées habituelles sont toujours là
            assertTrue(lignes.containsAll(listOf("Retirer des favoris", "Changer le logo…", "Masquer partout", "Infos de l'app", "Annuler")))
        }
        // Tuile « Source HDMI » (pas une vraie app) : pas de néon
        assertFalse(menu(AppInfo.HDMI).any { it.label == ModeAmbiance.LIBELLE_MENU })
    }

    @Test
    fun ouvrirEnModeAmbianceDepuisLeMenu() {
        val contexte = RuntimeEnvironment.getApplication()
        DonneesFactices.installer(contexte)
        // Spotify : écran d'ouverture déclaré (comme sur l'appareil)
        val ecranSpotify = ComponentName(spotify, "$spotify.SpotifyTVActivity")
        val pm = shadowOf(contexte.packageManager)
        pm.addActivityIfNotPresent(ecranSpotify)
        pm.addIntentFilterForActivity(
            ecranSpotify,
            IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            },
        )
        val repo = AppRepository(contexte)
        val app = AppInfo(spotify, "Spotify", true)
        afficherSeul {
            MenuDialog(
                title = app.label, subtitle = spotify,
                actions = menu(app) { ModeAmbiance.ouvrir(contexte, spotify) { repo.launch(it) } },
                onDismiss = {},
            )
        }
        capturerEcran("ambiance_menu")
        compose.onAllNodes(hasText(ModeAmbiance.LIBELLE_MENU)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        avancer(500)
        val ouverte = shadowOf(contexte).nextStartedActivity
        assertEquals("Spotify ouvert", spotify, ouverte?.component?.packageName)
        assertEquals("néon demandé pour Spotify", spotify, ModeAmbiance.logique.demande)
        assertTrue(ModeAmbiance.logique.demandeActive)
    }

    @Test
    fun explicationAvantLaDemandeDuMicro() {
        var continuer = 0
        afficherSeul { ExplicationMicroDialog(onContinuer = { continuer++ }, onPlusTard = {}) }
        if (compose.onAllNodes(hasText("rien n'est enregistré ni envoyé", substring = true)).fetchSemanticsNodes().isEmpty()) {
            fail("explication du micro : « rien n'est enregistré ni envoyé » absent")
        }
        capturerEcran("ambiance_explication_micro")
        compose.onAllNodes(hasText("Continuer")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        avancer(300)
        assertEquals(1, continuer)
    }

    @Test
    fun reglagesDuModeAmbiance() {
        afficherSeul {
            AmbianceDialog(
                etat = EtatAmbiance(micro = false, notifications = false, service = true),
                onMicro = {}, onNotifications = {}, onService = {}, onAide = {}, onDismiss = {},
            )
        }
        for (t in listOf("Mode Ambiance", "Douce", "Normale", "Forte", "Pochette", "Arc-en-ciel")) {
            if (compose.onAllNodes(hasText(t)).fetchSemanticsNodes().isEmpty()) fail("Mode Ambiance : « $t » absent")
        }
        capturerEcran("ambiance_reglages")
        // Intensité et couleurs : enregistrées, le dialogue reste ouvert
        compose.onAllNodes(hasText("Forte")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        avancer(300)
        compose.onAllNodes(hasText("Arc-en-ciel")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        avancer(300)
        assertEquals("forte", ModeAmbiance.intensite.id)
        assertEquals("arc", ModeAmbiance.couleurs.id)
        // Synchro avec le son : 0,1 s conseillé par défaut, réglable
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Retard de 0,2 s"))
        avancer(300)
        if (compose.onAllNodes(hasText("Retard de 0,1 s (conseillé)")).fetchSemanticsNodes().isEmpty()) fail("réglage 0,1 s conseillé absent")
        compose.onAllNodes(hasText("Retard de 0,2 s")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        avancer(300)
        assertEquals(200, ModeAmbiance.decalageMs)
        if (compose.onAllNodes(hasText("Mode Ambiance")).fetchSemanticsNodes().isEmpty()) fail("le dialogue devait rester ouvert")
    }

    @Test
    fun aideAvecLesCommandesAdb() {
        val contexte = RuntimeEnvironment.getApplication()
        afficherSeul { AideAmbianceDialog(ModeAmbiance.commandesAdb(contexte), onDismiss = {}) }
        for (c in listOf(
            "./adb shell pm grant fr.projo.launcher android.permission.RECORD_AUDIO",
            "./adb shell cmd notification allow_listener fr.projo.launcher/fr.projo.launcher.ambiance.EcouteNotifications",
        )) {
            if (compose.onAllNodes(hasText(c)).fetchSemanticsNodes().isEmpty()) fail("aide : commande absente « $c »")
        }
        capturerEcran("ambiance_aide")
    }

    // ------------------------------------------------------------------ outils

    private fun afficherSeul(contenu: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            CompositionLocalProvider(LocalScale provides echelle, LocalDesign provides design) {
                MaterialTheme(colorScheme = darkColorScheme()) { contenu() }
            }
        }
        avancer(1500)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capturerEcran(nom: String) {
        val fichier = File("build/captures/$nom.png")
        fichier.parentFile?.mkdirs()
        captureScreenRoboImage(fichier.path)
    }
}
