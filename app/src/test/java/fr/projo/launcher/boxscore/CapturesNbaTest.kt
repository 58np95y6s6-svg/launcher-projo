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
import androidx.compose.ui.test.onAllNodesWithTag
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
 * « sur le terrain »), attente « Synchronisation avec la vidéo… », pari suivi (ticket de
 * l'utilisateur en plein match, au coup d'envoi, match sans ligne du pari, handicaps et
 * double-double / triple-double), combinés en accordéon (trois combinés de PHI-MIL, lignes d'un
 * autre match résumées). Résultats dans app/build/captures/nba_*.png.
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

    /** Vue de la TV au format pari, par le vrai suivi : ticket collé, tableau du jour et box score servis. */
    private fun vuePari(texte: String, box: BoxScore): VueTv {
        val s = SuiviNba().apply { accessibilite = true }
        s.ajouterParis(texte, 0)
        s.abonnement(true, 0)
        fun servir() {
            repeat(5) {
                when (val e = s.prochaine(0)) {
                    is SuiviNba.Etape.Tableau -> s.tableauRecu(0, e.source, SuiviNba.Reponse.Ok(listOf(box.match)))
                    is SuiviNba.Etape.Box -> s.boxRecu(0, e, SuiviNba.Reponse.Ok(box))
                    else -> return
                }
            }
        }
        servir()
        assertEquals(Affichage.PARI, s.afficher(Affichage.PARI, box.match.id, 0))
        servir()
        return s.vueTv(0)
    }

    @Test
    fun pari() {
        // Ticket Betclic de l'utilisateur (le football est ignoré), plus une ligne perdue
        val vue = vuePari(TICKET_BETCLIC + "\nJoel Embiid Moins de 8,5 rebonds", boxPhiInd())
        assertEquals(5, vue.paris.size)
        capturer("pari", vue)
        compose.onNodeWithTag("nba_pari").assertIsDisplayed()
        compose.onNodeWithText("MON PARI").assertIsDisplayed()
        compose.onNodeWithText("3e QT · 5:48").assertIsDisplayed()
        compose.onAllNodesWithTag("nba_pari_ligne").assertCountEquals(5)
        compose.onNodeWithText("76ers vainqueurs").assertIsDisplayed()
        compose.onNodeWithText("\u22123").assertIsDisplayed()
        compose.onNodeWithText("T. Maxey · + de 19,5 pts").assertIsDisplayed()
        compose.onNodeWithText("23").assertIsDisplayed()
        compose.onNodeWithText("K. Caldwell-Pope · + de 9,5 pts").assertIsDisplayed()
        compose.onNodeWithText(" / 9,5").assertIsDisplayed()
        compose.onNodeWithText("I. Zubac · + de 2,5 passes").assertIsDisplayed()
        compose.onNodeWithText("J. Embiid · - de 8,5 rebonds").assertIsDisplayed()
        compose.onNodeWithText(" / 8,5").assertIsDisplayed()
        compose.onAllNodesWithText("Turquie", substring = true).assertCountEquals(0)
        val carte = compose.onNodeWithTag("nba_pari").fetchSemanticsNode().boundsInRoot
        // 600 px de large, en haut à droite (marges de 56 px), comme le résumé
        assertEquals(600f, carte.width, 2f)
        assertEquals(1920f - 56f, carte.right, 2f)
        assertEquals(56f, carte.top, 2f)
        assertTrue("carte trop haute : ${carte.bottom}", carte.bottom < 1080f - 24f)
    }

    @Test
    fun pariHandicap() {
        // Lignes Winamax : handicaps (écart −3 pour PHI, +3 pour IND) ; Embiid 16 pts, 10 rebonds : double-double, pas encore triple
        val texte = listOf(
            "Écart de points (handicap) Philadelphia 76ers -15.5",
            "Écart de points (handicap) Indiana Pacers +6.5",
            "Triple-double Joel Embiid",
            "Double-double Joel Embiid",
        ).joinToString("\n")
        val vue = vuePari(texte, boxPhiInd())
        assertEquals(listOf(EtatPari.EN_COURS, EtatPari.EN_COURS, EtatPari.EN_COURS, EtatPari.GAGNE), vue.paris.map { it.etat })
        capturer("pari_handicap", vue)
        compose.onAllNodesWithTag("nba_pari_ligne").assertCountEquals(4)
        compose.onNodeWithText("76ers · handicap \u221215,5").assertIsDisplayed()
        compose.onNodeWithText("\u22123").assertIsDisplayed()
        compose.onNodeWithText(" / 15,5").assertIsDisplayed()
        compose.onNodeWithText("Pacers · handicap +6,5").assertIsDisplayed()
        compose.onNodeWithText("+3").assertIsDisplayed()
        compose.onNodeWithText(" / \u22126,5").assertIsDisplayed()
        compose.onNodeWithText("J. Embiid · triple-double").assertIsDisplayed()
        compose.onNodeWithText(" / 3").assertIsDisplayed()
        compose.onNodeWithText("J. Embiid · double-double").assertIsDisplayed()
        compose.onNodeWithText(" / 2").assertIsDisplayed()
        val carte = compose.onNodeWithTag("nba_pari").fetchSemanticsNode().boundsInRoot
        assertEquals(600f, carte.width, 2f)
        assertTrue("carte trop haute : ${carte.bottom}", carte.bottom < 1080f - 24f)
    }

    @Test
    fun pariAuCoupDEnvoi() {
        // Pari collé avant le match ; box score pas encore publié : joueurs en attente, vainqueur en cours
        val debut = matchPhiInd(2, 0, EtatMatch.EN_COURS, 1, 700.0)
        val vue = vuePari(TICKET_BETCLIC, BoxScore.depuisMatch(debut))
        assertEquals(listOf(EtatPari.EN_COURS, EtatPari.ATTENTE, EtatPari.ATTENTE, EtatPari.ATTENTE), vue.paris.map { it.etat })
        capturer("pari_avant", vue)
        compose.onAllNodesWithTag("nba_pari_ligne").assertCountEquals(4)
        compose.onNodeWithText("I. Zubac · + de 2,5 passes").assertIsDisplayed()
        compose.onNodeWithText("+2").assertIsDisplayed()
        compose.onAllNodesWithText("–").assertCountEquals(3)
    }

    @Test
    fun pariAucuneLigne() {
        // Le ticket ne parle pas de LAL-BOS
        val vue = vuePari(TICKET_BETCLIC, nba)
        assertTrue(vue.paris.isEmpty())
        capturer("pari_aucune", vue)
        compose.onNodeWithTag("nba_pari").assertIsDisplayed()
        compose.onNodeWithText("MON PARI").assertIsDisplayed()
        compose.onNodeWithText("Aucune ligne de ton pari dans ce match").assertIsDisplayed()
        compose.onAllNodesWithTag("nba_pari_ligne").assertCountEquals(0)
    }

    /** Vue de la TV au format pari sur le match de [box] : un combiné par texte, [ouvrir] ouvert (sinon le dernier). */
    private fun vueCombines(textes: List<String>, box: BoxScore, autres: List<MatchNba> = emptyList(), ouvrir: String? = null): VueTv {
        val s = SuiviNba().apply { accessibilite = true }
        for (t in textes) s.ajouterParis(t, 0)
        s.abonnement(true, 0)
        fun servir() {
            repeat(5) {
                when (val e = s.prochaine(0)) {
                    is SuiviNba.Etape.Tableau -> s.tableauRecu(0, e.source, SuiviNba.Reponse.Ok(listOf(box.match) + autres))
                    is SuiviNba.Etape.Box -> s.boxRecu(0, e, SuiviNba.Reponse.Ok(box))
                    else -> return
                }
            }
        }
        servir()
        assertEquals(Affichage.PARI, s.afficher(Affichage.PARI, box.match.id, 0))
        servir()
        if (ouvrir != null) assertTrue(s.ouvrirParis(ouvrir))
        return s.vueTv(0)
    }

    @Test
    fun pariCombines() {
        // Combiné 1 perdu (sans cote), Combiné 2 ouvert (Winamax, cote 5,10 : handicap, ligne gagnée…), Combiné 3 gagné (Betclic, cote 10,06)
        val vue = vueCombines(listOf(COMBINE_SANS_COTE_PHI_MIL, COMBINE_WINAMAX_PHI_MIL, COMBINE_BETCLIC_PHI_MIL), boxPhiMil(), ouvrir = "g2")
        assertEquals(listOf(EtatPari.PERDU, EtatPari.EN_COURS, EtatPari.GAGNE), vue.combines.map { it.groupe.etat })
        assertEquals(listOf(false, true, false), vue.combines.map { it.ouvert })
        assertEquals(5, vue.paris.size)
        capturer("pari_combines", vue)
        compose.onNodeWithTag("nba_pari").assertIsDisplayed()
        compose.onNodeWithText("MON PARI").assertIsDisplayed()
        compose.onNodeWithText("4e QT · 7:12").assertIsDisplayed()
        compose.onAllNodesWithTag("nba_pari_combine").assertCountEquals(2)
        compose.onAllNodesWithTag("nba_pari_combine_ouvert").assertCountEquals(1)
        compose.onAllNodesWithTag("nba_pari_ligne").assertCountEquals(5)
        compose.onAllNodesWithTag("nba_pari_autres").assertCountEquals(0)
        compose.onNodeWithText("Combiné 1").assertIsDisplayed()
        compose.onNodeWithText("0/2").assertIsDisplayed()
        compose.onNodeWithText("Combiné 2").assertIsDisplayed()
        compose.onNodeWithText("cote 5,10").assertIsDisplayed()
        compose.onNodeWithText("1/5").assertIsDisplayed()
        compose.onNodeWithText("Combiné 3").assertIsDisplayed()
        compose.onNodeWithText("cote 10,06").assertIsDisplayed()
        compose.onNodeWithText("2/2").assertIsDisplayed()
        compose.onNodeWithText("76ers · handicap \u22124,5").assertIsDisplayed()
        compose.onNodeWithText("+7").assertIsDisplayed()
        compose.onNodeWithText(" / 4,5").assertIsDisplayed()
        compose.onNodeWithText("T. Maxey · + de 19,5 pts").assertIsDisplayed()
        compose.onNodeWithText("27").assertIsDisplayed()
        compose.onNodeWithText("J. Embiid · + de 24,5 pts").assertIsDisplayed()
        compose.onNodeWithText("G. Antetokounmpo · + de 9,5 rebonds").assertIsDisplayed()
        compose.onNodeWithText("Total du match · + de 210,5 pts").assertIsDisplayed()
        compose.onNodeWithText("169").assertIsDisplayed()
        // Lignes des combinés repliés : absentes ; aide « ◀ ▶ Combiné »
        compose.onAllNodesWithText("76ers vainqueurs").assertCountEquals(0)
        compose.onNodeWithText("Combiné").assertIsDisplayed()
        compose.onNodeWithText("Masquer").assertIsDisplayed()
        val carte = compose.onNodeWithTag("nba_pari").fetchSemanticsNode().boundsInRoot
        assertEquals(600f, carte.width, 2f)
        assertEquals(1920f - 56f, carte.right, 2f)
        assertEquals(56f, carte.top, 2f)
        assertTrue("carte trop haute : ${carte.bottom}", carte.bottom < 1080f - 24f)
    }

    @Test
    fun pariCombinesAutreMatch() {
        // Combiné 2 ouvert : deux lignes de PHI-MIL, deux de LAL-BOS résumées
        val texte = "Tyrese Maxey Plus de 19,5 points\nÉcart de points (handicap) Philadelphia 76ers -4.5\n" +
            "Los Angeles Lakers\nVainqueur du match\nNombre de points de Boston Celtics Plus de 110,5\nCote totale 7,45"
        val vue = vueCombines(listOf(COMBINE_SANS_COTE_PHI_MIL, texte), boxPhiMil(), autres = listOf(matchNba("LAL", "BOS", 2_000)))
        assertEquals(2, vue.combines.single { it.ouvert }.autres)
        capturer("pari_combines_autre_match", vue)
        compose.onAllNodesWithTag("nba_pari_combine").assertCountEquals(1)
        compose.onAllNodesWithTag("nba_pari_combine_ouvert").assertCountEquals(1)
        compose.onAllNodesWithTag("nba_pari_ligne").assertCountEquals(2)
        compose.onNodeWithText("+ 2 lignes d'un autre match").assertIsDisplayed()
        compose.onNodeWithText("cote 7,45").assertIsDisplayed()
        compose.onNodeWithText("1/4").assertIsDisplayed()
        compose.onAllNodesWithText("Lakers", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Celtics", substring = true).assertCountEquals(0)
    }

    @Test
    fun pariCombinesHauteurMaximale() {
        // 5 combinés repliés et un ouvert de 10 lignes de ce match (8 montrées) et 2 d'un autre : la carte tient dans l'écran
        val textes = (1..5).map { "Joel Embiid Plus de $it,5 points" } +
            ((1..10).joinToString("\n") { "Tyrese Maxey Plus de $it,5 points" } + "\nLos Angeles Lakers\nVainqueur du match\nCote totale 12,40")
        val vue = vueCombines(textes, boxPhiMil(), autres = listOf(matchNba("LAL", "BOS", 2_000)))
        assertEquals(6, vue.combines.size)
        capturer("pari_combines_hauteur", vue).delete()
        compose.onAllNodesWithTag("nba_pari_combine").assertCountEquals(5)
        compose.onAllNodesWithTag("nba_pari_ligne").assertCountEquals(SuiviNba.PARIS_TV_MAX)
        val carte = compose.onNodeWithTag("nba_pari").fetchSemanticsNode().boundsInRoot
        assertTrue("carte trop haute : ${carte.bottom}", carte.bottom < 1080f - 24f)
        // Rien d'écrasé : le dernier élément (aide) a toute sa hauteur, dans la carte
        val autres = compose.onNodeWithTag("nba_pari_autres").fetchSemanticsNode().boundsInRoot
        val aide = compose.onNodeWithText("Combiné").fetchSemanticsNode().boundsInRoot
        assertTrue("autres lignes écrasées : $autres (carte $carte)", autres.height > 15f)
        assertTrue("aide écrasée : $aide (carte $carte)", aide.height > 15f && aide.bottom <= carte.bottom)
        compose.onNodeWithText("+ 1 ligne d'un autre match").assertIsDisplayed()
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
