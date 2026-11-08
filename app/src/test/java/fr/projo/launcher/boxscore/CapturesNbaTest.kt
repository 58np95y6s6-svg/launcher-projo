package fr.projo.launcher.boxscore

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.projo.launcher.ui.ECRAN_TV
import fr.projo.launcher.ui.LocalScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Captures du box score NBA (1920 × 1080) sur un faux fond de vidéo sombre, avec les exemples de
 * flux de app/src/test/resources/nba/ : résumé, détails (deux équipes, et source ESPN sans point
 * « sur le terrain »), attente « Synchronisation avec la vidéo… ». Résultats dans
 * app/build/captures/nba_*.png.
 *
 * SDK 35 : un bac à sable Robolectric à part, pour ne pas partager l'état de Compose (répartiteur
 * de l'interface, notifications des instantanés) avec les tests de l'accueil en SDK 33.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = ECRAN_TV)
class CapturesNbaTest {

    /** Robolectric : déclare l'activité vide utilisée par Compose. */
    @get:Rule(order = 0)
    val activiteVide = object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val nba by lazy { LectureNba.boxScoreNba(exempleNba("boxscore_nba.json")) }

    private fun capturer(nom: String, vue: VueTv): File {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val echelle = LocalConfiguration.current.screenWidthDp / 960f
            CompositionLocalProvider(LocalScale provides echelle) {
                // Faux fond de vidéo (pénombre d'une salle), pour voir le verre et son ombre
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF151A26), Color(0xFF3A2C22), Color(0xFF8B5A2E))))) {
                    EcranBoxScore(vue, Modifier.align(Alignment.TopEnd))
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val fichier = File("build/captures/nba_$nom.png")
        fichier.parentFile?.mkdirs()
        compose.onRoot().captureRoboImage(fichier.path)
        return fichier
    }

    @Test
    fun resume() {
        capturer("resume", VueTv(Affichage.RESUME, nba, null, 0))
        compose.onNodeWithTag("nba_resume").assertIsDisplayed()
        compose.onNodeWithText("EN DIRECT").assertIsDisplayed()
        compose.onNodeWithText("4e QT · 2:31").assertIsDisplayed()
        compose.onNodeWithText("MEILLEURS JOUEURS").assertIsDisplayed()
        compose.onNodeWithText("ÉQUIPES").assertIsDisplayed()
        compose.onNodeWithText("J. Brown").assertIsDisplayed()
        compose.onNodeWithText("48 %").assertIsDisplayed()
        compose.onNodeWithText("PERTES").assertIsDisplayed()
    }

    @Test
    fun details() {
        capturer("details", VueTv(Affichage.DETAILS, nba, null, 0))
        compose.onNodeWithTag("nba_details").assertIsDisplayed()
        compose.onNodeWithText("TOTAL").assertIsDisplayed()
        compose.onNodeWithText("BANC").assertIsDisplayed()
        // Ligne « Équipe » du tableau et aide « ◀ ▶ Équipe »
        compose.onAllNodesWithText("Équipe").assertCountEquals(2)
        compose.onNodeWithText("38-79").assertIsDisplayed()
        compose.onNodeWithText("R. Hachimura").assertIsDisplayed()
        compose.onNodeWithText("Masquer").assertIsDisplayed()
        // N'a pas joué : absent du tableau
        compose.onAllNodesWithText("M. Kleber").assertCountEquals(0)
        val panneau = compose.onNodeWithTag("nba_details").fetchSemanticsNode().boundsInRoot
        // 680 px de large, en haut à droite (marges de 56 px), dans l'écran
        assertEquals(680f, panneau.width, 2f)
        assertEquals(1920f - 56f, panneau.right, 2f)
        assertEquals(56f, panneau.top, 2f)
        assertTrue("panneau trop haut : ${panneau.bottom}", panneau.bottom < 1080f - 24f)
    }

    @Test
    fun detailsDomicile() {
        capturer("details_domicile", VueTv(Affichage.DETAILS, nba, null, 1))
        compose.onNodeWithText("J. Brown").assertIsDisplayed()
        compose.onNodeWithText("39-76").assertIsDisplayed()
        compose.onAllNodesWithText("L. Dončić").assertCountEquals(0)
    }

    @Test
    fun detailsSourceEspn() {
        val espn = LectureNba.boxScoreEspn(exempleNba("resume_espn.json"))
        capturer("details_espn", VueTv(Affichage.DETAILS, espn, null, 0))
        compose.onNodeWithText("L. Doncic").assertIsDisplayed()
        // Nom dans l'en-tête et onglet
        compose.onAllNodesWithText("Celtics").assertCountEquals(2)
    }

    @Test
    fun synchronisation() {
        capturer("synchronisation", VueTv(Affichage.RESUME, null, SuiviNba.SYNCHRONISATION, 0))
        compose.onNodeWithText("Synchronisation avec la vidéo…").assertIsDisplayed()
        val carte = compose.onNodeWithTag("nba_attente").fetchSemanticsNode().boundsInRoot
        assertEquals(600f, carte.width, 2f)
    }

    @Test
    fun resumeTailleDeLaCarte() {
        capturer("resume_taille", VueTv(Affichage.RESUME, nba, null, 0)).delete()
        val carte = compose.onNodeWithTag("nba_resume").fetchSemanticsNode().boundsInRoot
        assertEquals(600f, carte.width, 2f)
        assertEquals(1920f - 56f, carte.right, 2f)
        assertEquals(56f, carte.top, 2f)
    }
}
