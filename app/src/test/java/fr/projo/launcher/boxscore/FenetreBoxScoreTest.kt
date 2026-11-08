package fr.projo.launcher.boxscore

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import fr.projo.launcher.MainActivity
import fr.projo.launcher.system.RedirectionAccueil
import fr.projo.launcher.ui.ECRAN_TV
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.util.concurrent.TimeUnit

/**
 * Le box score porté par le service d'accessibilité (RedirectionAccueil, comme le néon) : une
 * seule fenêtre ni focalisable ni touchable, retirée quand il est caché ; ◀ ▶ et Retour gardés
 * seulement en format détails ; caché au retour sur l'accueil d'iBiTV ; sans service, erreur
 * « accessibilite ». Réseau remplacé par un faux (exemples de flux).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class FenetreBoxScoreTest {

    private val box by lazy { LectureNba.boxScoreNba(exempleNba("boxscore_nba.json")) }

    /** Requêtes servies tout de suite, comptées. */
    private inner class Faux : ChargeurNba {
        var tableaux = 0
        var boxs = 0
        override fun tableau(source: SourceNba): SuiviNba.Reponse<List<MatchNba>> {
            tableaux++
            return SuiviNba.Reponse.Ok(listOf(box.match))
        }

        override fun box(source: SourceNba, match: MatchNba): SuiviNba.Reponse<BoxScore> {
            boxs++
            return SuiviNba.Reponse.Ok(box)
        }
    }

    private val faux = Faux()

    @Before
    fun avant() {
        BoxScoreNba.oublier()
        BoxScoreNba.chargeur = faux
        BoxScoreNba.filReseau = Dispatchers.Unconfined
    }

    @After
    fun apres() {
        BoxScoreNba.oublier()
        ShadowLooper.idleMainLooper()
    }

    private fun attendre() = ShadowLooper.idleMainLooper()

    private fun RedirectionAccueil.fenetres(): List<ComposeView> =
        Shadow.extract<ShadowWindowManagerImpl>(getSystemService(Context.WINDOW_SERVICE)).views.filterIsInstance<ComposeView>()

    /** onKeyEvent (protégé) : true = touche gardée, false = passée à l'appli. */
    private fun RedirectionAccueil.touche(code: Int, action: Int): Boolean {
        val t = SystemClock.uptimeMillis()
        val m = RedirectionAccueil::class.java.getDeclaredMethod("onKeyEvent", KeyEvent::class.java)
        m.isAccessible = true
        return m.invoke(this, KeyEvent(t, t, action, code, 0)) as Boolean
    }

    private fun RedirectionAccueil.ecran(paquet: String, classe: String) {
        val e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        e.packageName = paquet
        e.className = classe
        onAccessibilityEvent(e)
        attendre()
    }

    @Test
    fun fenetreEtTouchesDuFormatDetails() {
        val s = Robolectric.setupService(RedirectionAccueil::class.java)
        BoxScoreNba.attacher(s)
        // Personne d'abonné, rien d'affiché : aucune requête
        attendre()
        assertEquals(0, faux.tableaux)
        BoxScoreNba.abonnement(true)
        attendre()
        assertEquals(1, faux.tableaux)
        assertEquals(0, faux.boxs)
        assertEquals(listOf("LAL-BOS"), BoxScoreNba.etat().matchs.map { it.id })
        assertNull(BoxScoreNba.etat().erreur)
        assertTrue(s.fenetres().isEmpty())

        BoxScoreNba.afficher(Affichage.DETAILS, "LAL-BOS")
        attendre()
        assertEquals(1, faux.boxs)
        val fenetres = s.fenetres()
        assertEquals(1, fenetres.size)
        val lp = fenetres.single().layoutParams as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, lp.type)
        assertTrue(lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertNotNull(BoxScoreNba.vue.value.box)
        assertEquals(Affichage.DETAILS, BoxScoreNba.etat().affichage)

        // ▶ : équipe à domicile, appui et relâchement gardés ; ◀ : retour à l'extérieur
        assertTrue(s.touche(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN))
        assertTrue(s.touche(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_UP))
        assertEquals(1, BoxScoreNba.vue.value.equipe)
        assertTrue(s.touche(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN))
        assertTrue(s.touche(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_UP))
        assertEquals(0, BoxScoreNba.vue.value.equipe)
        // Les autres touches passent à l'appli
        assertFalse(s.touche(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN))
        assertFalse(s.touche(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_UP))
        assertFalse(s.touche(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN))

        // Retour : caché, fenêtre retirée ; le relâchement est gardé aussi
        assertTrue(s.touche(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN))
        attendre()
        assertTrue(s.fenetres().isEmpty())
        assertEquals(Affichage.CACHE, BoxScoreNba.etat().affichage)
        assertTrue(s.touche(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP))

        // Résumé : aucune touche gardée
        BoxScoreNba.afficher(Affichage.RESUME, null)
        attendre()
        assertEquals(1, s.fenetres().size)
        assertFalse(s.touche(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN))
        assertFalse(s.touche(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP))
        assertFalse(s.touche(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN))
        assertEquals(Affichage.RESUME, BoxScoreNba.etat().affichage)

        // Une autre appli au premier plan : rien ne change ; l'accueil d'iBiTV : caché
        s.ecran("com.amazon.avod", "com.amazon.avod.PlaybackActivity")
        assertEquals(1, s.fenetres().size)
        s.ecran(s.packageName, MainActivity::class.java.name)
        assertTrue(s.fenetres().isEmpty())
        assertEquals(Affichage.CACHE, BoxScoreNba.etat().affichage)

        // Plus d'abonné, rien d'affiché : la boucle des requêtes s'arrête
        BoxScoreNba.abonnement(false)
        attendre()
        val avant = faux.tableaux
        ShadowLooper.idleMainLooper(5, TimeUnit.MINUTES)
        assertEquals(avant, faux.tableaux)
    }

    @Test
    fun sansServiceDAccessibilite() {
        val s = Robolectric.setupService(RedirectionAccueil::class.java)
        BoxScoreNba.abonnement(true)
        attendre()
        assertEquals(EtatNba.ERREUR_ACCESSIBILITE, BoxScoreNba.etat().erreur)
        // Le box score ne peut pas s'afficher : il reste caché
        BoxScoreNba.afficher(Affichage.RESUME, "LAL-BOS")
        attendre()
        assertEquals(Affichage.CACHE, BoxScoreNba.etat().affichage)
        assertEquals("LAL-BOS", BoxScoreNba.etat().choisi)
        // Service relié puis arrêté pendant l'affichage : fenêtre retirée, erreur
        BoxScoreNba.attacher(s)
        assertNull(BoxScoreNba.etat().erreur)
        BoxScoreNba.afficher(Affichage.RESUME, null)
        attendre()
        assertEquals(1, s.fenetres().size)
        BoxScoreNba.detacher(s)
        attendre()
        assertTrue(s.fenetres().isEmpty())
        assertEquals(Affichage.CACHE, BoxScoreNba.etat().affichage)
        assertEquals(EtatNba.ERREUR_ACCESSIBILITE, BoxScoreNba.etat().erreur)
    }

    @Test
    fun decalageAttendAvantDeMontrer() {
        val s = Robolectric.setupService(RedirectionAccueil::class.java)
        BoxScoreNba.attacher(s)
        BoxScoreNba.reglerDecalage(30)
        BoxScoreNba.abonnement(true)
        attendre()
        // Réponse reçue, pas encore assez ancienne : rien n'est montré au téléphone
        assertEquals(1, faux.tableaux)
        assertTrue(BoxScoreNba.etat().matchs.isEmpty())
        assertEquals(30, BoxScoreNba.etat().decalage)
        // 30 s plus tard : montrée (et la requête suivante est partie)
        ShadowLooper.idleMainLooper(31, TimeUnit.SECONDS)
        assertEquals(listOf("LAL-BOS"), BoxScoreNba.etat().matchs.map { it.id })
        assertTrue(faux.tableaux >= 2)
    }
}
