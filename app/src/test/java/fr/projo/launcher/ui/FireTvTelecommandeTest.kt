package fr.projo.launcher.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.projo.launcher.R
import fr.projo.launcher.remote.NavigationTelecommande
import fr.projo.launcher.system.RedirectionAccueil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * Fire TV : un seul service d'accessibilité (ouverture automatique) porte aussi la
 * navigation de la télécommande smartphone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class FireTvTelecommandeTest : BancAccueil("VERRE") {

    private val contexte get() = RuntimeEnvironment.getApplication()

    @Test
    fun redirectionPorteLaNavigation() {
        assertTrue(NavigationTelecommande::class.java.isAssignableFrom(RedirectionAccueil::class.java))
        // Le service de navigation séparé est désactivé sur Fire TV
        assertFalse(contexte.resources.getBoolean(R.bool.telecommande_service_navigation))
    }

    @Test
    fun configurationDuService() {
        val xml = contexte.resources.getXml(R.xml.redirection_accueil)
        val attributs = mutableMapOf<String, String>()
        while (xml.next() != XmlPullParser.END_DOCUMENT) {
            if (xml.eventType == XmlPullParser.START_TAG && xml.name == "accessibility-service") {
                for (i in 0 until xml.attributeCount) attributs[xml.getAttributeName(i)] = xml.getAttributeValue(i)
            }
        }
        assertEquals("true", attributs["canRetrieveWindowContent"])
        assertEquals("true", attributs["canRequestFilterKeyEvents"])
        // flagRequestFilterKeyEvents (0x20) | flagIncludeNotImportantViews (0x2)
        val drapeaux = Integer.decode(attributs["accessibilityFlags"]!!)
        assertEquals(0x22, drapeaux and 0x22)
    }

    @Test
    fun commandesAdbFireTv() {
        val c = commandesAdb("fr.projo.launcher", null, navigation = "fr.projo.launcher/.system.RedirectionAccueil")
        assertTrue(c.contains("adb shell settings put secure enabled_accessibility_services fr.projo.launcher/.system.RedirectionAccueil"))
        assertFalse(c.any { it.contains("NavigationTelecommande") })
    }

    @Test
    fun aideFireTv() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            CompositionLocalProvider(LocalScale provides echelle, LocalDesign provides design) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    AideTelecommandeDialog(
                        ResumeTelecommande(telephones = 1, clavierActive = true, clavierChoisi = true),
                        commandesAdb("fr.projo.launcher", null, navigation = "fr.projo.launcher/.system.RedirectionAccueil"),
                        onReglagesClavier = {}, onChoisirClavier = {}, onAccessibilite = {}, onDismiss = {},
                        note = "Fire TV : la navigation passe par le service « Ouverture automatique », qui lit maintenant " +
                            "aussi le contenu des fenêtres. Après la mise à jour, redémarre le Fire TV (ou refais " +
                            "« Activer en un clic »).",
                    )
                }
            }
        }
        avancer(1500)
        val fichier = File("build/captures/telecommande_8_aide_firetv.png")
        fichier.parentFile?.mkdirs()
        captureScreenRoboImage(fichier.path)
        compose.onNodeWithText("Clavier et navigation").assertIsDisplayed()
        compose.onNodeWithText("Fermer").assertIsDisplayed()
    }
}
