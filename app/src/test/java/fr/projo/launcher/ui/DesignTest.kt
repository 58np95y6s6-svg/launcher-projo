package fr.projo.launcher.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.hasText
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import fr.projo.launcher.data.LauncherPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Identifiants des designs supprimés : une préférence enregistrée peut encore les contenir. */
private val IDS_SUPPRIMES = listOf("aurore", "graphite", "playlist", "neon")

/** Liste des designs, design par défaut et lecture de la préférence (Design.from). */
class DesignTest {

    @Test
    fun designsDansLOrdre() {
        assertEquals(
            listOf(Design.VERRE, Design.PASTEL, Design.CONSOLE, Design.UNE, Design.CHAINES, Design.STUDIO),
            Design.entries.toList(),
        )
        assertEquals("identifiants uniques", Design.entries.size, Design.entries.map { it.id }.toSet().size)
    }

    @Test
    fun parDefautALaUne() {
        assertEquals(Design.UNE, Design.PAR_DEFAUT)
        assertEquals("aucune préférence", Design.UNE, Design.from(null))
        assertEquals("préférence vide", Design.UNE, Design.from(""))
        assertEquals("identifiant inconnu", Design.UNE, Design.from("inconnu"))
    }

    /** Préférence d'un design supprimé : retour sur « À la une », sans planter. */
    @Test
    fun migrationDesignsSupprimes() {
        for (id in IDS_SUPPRIMES) assertEquals("préférence « $id »", Design.UNE, Design.from(id))
    }

    /** Les designs restants se relisent tels quels. */
    @Test
    fun designsRestantsRelus() {
        for (d in Design.entries) assertEquals(d, Design.from(d.id))
    }
}

/**
 * Même chemin que l'app : préférence « design » écrite par LauncherPrefs (DataStore), relue
 * par LauncherPrefs.state puis Design.from (comme MainActivity).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PreferenceDesignTest {

    @Test
    fun preferenceEnregistreeRelue() = runBlocking {
        val prefs = LauncherPrefs(RuntimeEnvironment.getApplication())
        assertEquals("sans préférence", Design.UNE, Design.from(prefs.state.first().design))
        for (id in IDS_SUPPRIMES) {
            prefs.setDesign(id)
            val lu = prefs.state.first().design
            assertEquals("préférence enregistrée", id, lu)
            assertEquals("préférence « $id » (design supprimé)", Design.UNE, Design.from(lu))
        }
        prefs.setDesign(Design.VERRE.id)
        assertEquals("design restant", Design.VERRE, Design.from(prefs.state.first().design))
    }
}

/** Réglages → Design : les 6 designs proposés, « À la une » coché ; capture « dialogue_design ». */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CapturesDesignTest : BancAccueil("UNE") {

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun choixDuDesign() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            CompositionLocalProvider(LocalScale provides echelle, LocalDesign provides design) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    DesignDialog(current = Design.from(null), onChoose = {}, onDismiss = {})
                }
            }
        }
        avancer(1500)
        fun present(texte: String) =
            compose.onAllNodes(hasText(texte, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        for (d in Design.entries) {
            if (!present(d.nom + " — " + d.resume)) fail("Réglages → Design : « ${d.nom} » absent")
        }
        if (!present("✓ " + Design.UNE.nom)) fail("Réglages → Design : « À la une » devrait être coché")
        for (nom in listOf("Aurore", "Graphite", "Playlist", "Néon")) {
            if (present(nom)) fail("Réglages → Design : « $nom » ne devrait plus être proposé")
        }
        val fichier = File("build/captures/dialogue_design.png")
        fichier.parentFile?.mkdirs()
        captureScreenRoboImage(fichier.path)
    }
}
