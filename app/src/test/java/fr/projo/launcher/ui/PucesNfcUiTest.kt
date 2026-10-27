package fr.projo.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.projo.launcher.data.AppInfo
import fr.projo.launcher.puces.PucesNfc
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Réglages → Puces NFC : activation, une ligne par app, lien en QR code, aide (captures). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class PucesNfcUiTest : BancAccueil("VERRE") {

    private val apps = listOf(
        AppInfo("com.netflix.ninja", "Netflix", isTvApp = true),
        AppInfo("com.liskovsoft.smarttubetv", "SmartTube", isTvApp = true),
        AppInfo("com.canal.android.canal", "CANAL+", isTvApp = true),
    )

    @Before
    fun avant() = PucesNfc.oublier()

    @After
    fun apres() = PucesNfc.oublier()

    private fun present(t: String) = compose.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun activerPuisChoisirUneApp() {
        var choisie: Pair<String, String>? = null
        afficherSeul {
            PucesNfcDialog(apps = apps, ip = "192.168.1.19", onApp = { a, adr -> choisie = a.label to adr }, onAide = {}, onDismiss = {})
        }
        assertTrue(present("Désactivé · OK pour activer"))
        assertFalse("pas de liens avant l'activation", present("Netflix"))
        compose.onAllNodes(hasText("Lancement par puce NFC")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        avancer(500)
        assertTrue(PucesNfc.actif)
        assertEquals(20, PucesNfc.code.length)
        for (t in listOf("Netflix", "SmartTube", "CANAL+", "192.168.1.19")) if (!present(t)) fail("Puces NFC : « $t » absent")
        capturerEcran("puces_reglages")
        compose.onAllNodes(hasText("Netflix")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        avancer(300)
        // Netflix n'est pas installée dans les tests : son adresse est alors son nom de paquet
        assertEquals("Netflix", choisie?.first)
    }

    @Test
    fun lienDUneAppEnQrCode() {
        val lien = PucesNfc.lien("192.168.1.19", "netflix", "AbCdEfGhJkMnPqRsTuVw")
        afficherSeul { LienPuceDialog("Netflix", lien, onDismiss = {}) }
        assertTrue(present("Puce « Netflix »"))
        assertTrue(present(lien))
        if (compose.onAllNodes(hasTestTag("qr-puce")).fetchSemanticsNodes().isEmpty()) fail("QR code absent")
        capturerEcran("puces_lien")
    }

    @Test
    fun aide() {
        afficherSeul { AidePucesDialog("192.168.1.19", onDismiss = {}) }
        for (t in listOf("NFC Tools", "Raccourcis", "Android", "iPhone", "192.168.1.19")) if (!present(t)) fail("aide : « $t » absent")
        capturerEcran("puces_aide")
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
