package fr.projo.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.projo.launcher.remote.EtatAppairage
import fr.projo.launcher.remote.QrCode
import fr.projo.launcher.remote.RemoteConfig
import fr.projo.launcher.remote.TelephoneAppaire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Captures des dialogues de la télécommande smartphone (QR code, confirmation, liste, aide). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CapturesTelecommandeTest : BancAccueil("UNE") {

    private val t0 = 1_760_000_000_000L
    private val url = RemoteConfig.URL_PWA + "#pair=ibiprojo-0123456789abcdef0123456789abcdef.q2Xk9_LmT0aZr1bYwE3uVg.YPXzIOhZAT92PqkIdIKfBw"

    @OptIn(ExperimentalRoborazziApi::class)
    private fun dialogue(nom: String, design: Design = this.design, contenu: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            CompositionLocalProvider(LocalScale provides echelle, LocalDesign provides design) {
                MaterialTheme(colorScheme = darkColorScheme()) { contenu() }
            }
        }
        avancer(1500)
        val fichier = File("build/captures/telecommande_$nom.png")
        fichier.parentFile?.mkdirs()
        captureScreenRoboImage(fichier.path)
    }

    @Test
    fun appairageQrCode() {
        dialogue("1_appairage_qr") {
            AppairageDialog(EtatAppairage.EnCours(url, "K7QM-3XPA", t0 + 272_000), onNouveauCode = {}, onFermer = {}, horloge = { t0 })
        }
        compose.onNodeWithTag("qr").assertIsDisplayed()
        compose.onNodeWithText("K7QM-3XPA").assertIsDisplayed()
        compose.onNodeWithText("Valable encore 4:32").assertIsDisplayed()
        compose.onNodeWithText(RemoteConfig.URL_PWA_COURTE).assertIsDisplayed()
    }

    @Test
    fun appairageExpire() {
        dialogue("2_appairage_expire") { AppairageDialog(EtatAppairage.Expire, onNouveauCode = {}, onFermer = {}) }
        compose.onNodeWithText("Nouveau code").assertIsDisplayed()
    }

    @Test
    fun appairageReussi() {
        dialogue("3_appairage_reussi") { AppairageDialog(EtatAppairage.Reussi("Téléphone de Marie"), onNouveauCode = {}, onFermer = {}) }
        compose.onNodeWithText("Téléphone de Marie est appairé.").assertIsDisplayed()
    }

    @Test
    fun confirmation() {
        dialogue("4_confirmation") { ConfirmationAppairageDialog("Téléphone de Marie", onAutoriser = {}, onRefuser = {}) }
        compose.onNodeWithText("Autoriser le téléphone de Marie ?").assertIsDisplayed()
        compose.onNodeWithText("Autoriser").assertIsDisplayed()
        compose.onNodeWithText("Refuser").assertIsDisplayed()
    }

    /** Design clair : textes foncés sur panneau clair. */
    @Test
    fun confirmationPastel() {
        dialogue("4b_confirmation_pastel", Design.PASTEL) { ConfirmationAppairageDialog("Pixel 8", onAutoriser = {}, onRefuser = {}) }
        compose.onNodeWithText("Autoriser « Pixel 8 » ?").assertIsDisplayed()
    }

    @Test
    fun listeDesTelephones() {
        val jour = 86_400_000L
        val telephones = listOf(
            TelephoneAppaire("tel-a", "Téléphone de Marie", "cle", t0 - 3 * jour),
            TelephoneAppaire("tel-b", "Téléphone du salon", "cle", t0 - 40 * jour),
            TelephoneAppaire("tel-c", "Tablette", "cle", t0 - 100 * jour),
        )
        dialogue("5_telephones") {
            TelephonesDialog(telephones, connectes = setOf("tel-a"), onRenommer = { _, _ -> }, onRevoquer = {}, onAppairer = {}, onDismiss = {})
        }
        compose.onNodeWithText("Téléphone de Marie").assertIsDisplayed()
        compose.onNodeWithText("Appairer un téléphone").assertIsDisplayed()
    }

    @Test
    fun aucunTelephone() {
        dialogue("6_telephones_vide") {
            TelephonesDialog(emptyList(), connectes = emptySet(), onRenommer = { _, _ -> }, onRevoquer = {}, onAppairer = {}, onDismiss = {})
        }
        compose.onNodeWithText("Aucun téléphone pour l'instant").assertIsDisplayed()
    }

    @Test
    fun aide() {
        val commandes = commandesAdb("fr.projo.launcher", null)
        dialogue("7_aide") {
            AideTelecommandeDialog(
                ResumeTelecommande(telephones = 1, clavierActive = true),
                commandes, onReglagesClavier = {}, onChoisirClavier = {}, onAccessibilite = {}, onDismiss = {},
            )
        }
        compose.onNodeWithText("Clavier et navigation").assertIsDisplayed()
    }

    @Test
    fun commandesAdbGardentLesAutresServices() {
        val c = commandesAdb("fr.projo.launcher", "com.autre/.Service:fr.projo.launcher/.remote.NavigationTelecommande")
        assertEquals("adb shell ime enable fr.projo.launcher/.remote.ClavierTelecommande", c[0])
        assertTrue(c.contains("adb shell settings put secure enabled_accessibility_services com.autre/.Service:fr.projo.launcher/.remote.NavigationTelecommande"))
    }

    @Test
    fun qrCodeLisible() {
        val m = QrCode.matrice(url)
        // Taille d'un QR code (21 + 4 × version) et carrés de repérage aux coins
        assertTrue((m.size - 21) % 4 == 0)
        assertTrue(m[0][0] && m[0][6] && m[6][0] && !m[1][1] && m[2][2])
    }
}
