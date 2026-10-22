package fr.projo.launcher.ambiance

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import fr.projo.launcher.MainActivity
import fr.projo.launcher.system.RedirectionAccueil
import fr.projo.launcher.ui.ECRAN_TV
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.util.concurrent.TimeUnit

/**
 * Le néon porté par le service d'ouverture automatique (RedirectionAccueil) : affiché avec
 * une app de musique, jamais avec une app vidéo (sauf à la demande), caché sur l'accueil ;
 * appui long sur ☰ qui bascule, appui court qui passe ; Maison toujours redirigée.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class NeonServiceTest {

    private val spotify = "com.spotify.tv.android"
    private val netflix = "com.netflix.ninja"
    private val amazon = "com.amazon.tv.launcher"
    private val contexte: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun avant() = ModeAmbiance.oublier()

    @After
    fun apres() = ModeAmbiance.oublier()

    private fun service(): RedirectionAccueil = Robolectric.setupService(RedirectionAccueil::class.java)

    private fun RedirectionAccueil.ecran(paquet: String, classe: String) {
        val e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        e.packageName = paquet
        e.className = classe
        onAccessibilityEvent(e)
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
    }

    /** Fenêtres du néon ajoutées par le service. */
    private fun RedirectionAccueil.bandes(): List<View> =
        Shadow.extract<ShadowWindowManagerImpl>(getSystemService(Context.WINDOW_SERVICE)).views.filterIsInstance<BandeNeon>()

    /** onKeyEvent (protégé) : true = touche gardée par le service, false = passée à l'app. */
    private fun RedirectionAccueil.touche(code: Int, action: Int, repetition: Int = 0): Boolean {
        val t = SystemClock.uptimeMillis()
        val m = RedirectionAccueil::class.java.getDeclaredMethod("onKeyEvent", KeyEvent::class.java)
        m.isAccessible = true
        return m.invoke(this, KeyEvent(t, t, action, code, repetition)) as Boolean
    }

    private fun ouverte(): String? = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity?.component?.className

    @Test
    fun neonAutourDUneAppDeMusiquePuisCacheSurLAccueil() {
        val s = service()
        s.ecran(spotify, "com.spotify.tv.android.SpotifyTVActivity")
        assertEquals("4 bandes de néon autour de Spotify", 4, s.bandes().size)
        assertEquals(spotify, ModeAmbiance.affichePour)
        // Quelques images : le néon s'anime (sans micro ni pochette ici : animation)
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        assertNotNull(ModeAmbiance.diagnostic)
        assertTrue(ModeAmbiance.diagnostic!!, ModeAmbiance.diagnostic!!.startsWith("animation"))
        // Accueil Amazon : néon retiré aussitôt, et la redirection vers iBiProjo marche toujours
        s.ecran(amazon, "com.amazon.tv.launcher.ui.HomeActivity")
        assertEquals(0, s.bandes().size)
        assertNull(ModeAmbiance.affichePour)
        assertEquals(MainActivity::class.java.name, ouverte())
    }

    @Test
    fun jamaisAutomatiqueAvecUneAppVideo() {
        val s = service()
        s.ecran(netflix, "com.netflix.ninja.MainActivity")
        assertEquals(0, s.bandes().size)
    }

    @Test
    fun automatiqueDesactiveDansLesReglages() {
        ModeAmbiance.ecrireAuto(contexte, false)
        val s = service()
        s.ecran(spotify, "com.spotify.tv.android.SpotifyTVActivity")
        assertEquals(0, s.bandes().size)
    }

    @Test
    fun ouvrirEnModeAmbianceUneAppVideo() {
        val s = service()
        s.ecran(contexte.packageName, MainActivity::class.java.name)
        assertTrue(ModeAmbiance.ouvrir(contexte, netflix) { true })
        // Le menu d'iBiProjo se ferme pendant l'ouverture : pas de néon sur l'accueil
        s.ecran(contexte.packageName, MainActivity::class.java.name)
        assertEquals(0, s.bandes().size)
        s.ecran(netflix, "com.netflix.ninja.MainActivity")
        assertEquals("Netflix ouvert en mode Ambiance", 4, s.bandes().size)
        // Maison : fin du mode pour Netflix
        s.ecran(contexte.packageName, MainActivity::class.java.name)
        assertEquals(0, s.bandes().size)
        s.ecran(netflix, "com.netflix.ninja.MainActivity")
        assertEquals("Netflix rouvert normalement", 0, s.bandes().size)
    }

    @Test
    fun leClavierNeCachePasLeNeon() {
        val s = service()
        s.ecran(spotify, "com.spotify.tv.android.SpotifyTVActivity")
        // Notre clavier (télécommande smartphone) vient du même paquet qu'iBiProjo
        s.ecran(contexte.packageName, "android.inputmethodservice.SoftInputWindow")
        assertEquals(4, s.bandes().size)
    }

    @Test
    fun fenetresNonFocalisablesNonTouchablesEtSurLesBordsReels() {
        val s = service()
        s.ecran(spotify, "com.spotify.tv.android.SpotifyTVActivity")
        val bandes = s.bandes()
        assertEquals(4, bandes.size)
        for (b in bandes) {
            val p = b.layoutParams as WindowManager.LayoutParams
            assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, p.type)
            assertTrue("non focalisable", p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            assertTrue("non touchable", p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
            @Suppress("DEPRECATION")
            assertTrue("overscan", p.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN != 0)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, b.importantForAccessibility)
            assertFalse(b.isFocusable)
        }
        // Bandes haut et bas sur toute la largeur réelle (1920 px), centre de l'écran libre
        val zones = bandes.map { (it as BandeNeon).zone }
        assertEquals(2, zones.count { it.width() == 1920 })
        assertTrue(zones.all { it.width() < 300 || it.height() < 300 })
    }

    @Test
    fun appuiLongSurMenuBasculeLeNeon() {
        val s = service()
        s.ecran(netflix, "com.netflix.ninja.MainActivity")
        assertEquals(0, s.bandes().size)
        // Appui long : l'appui passe à l'app, puis le néon s'allume ; la suite est gardée
        assertFalse(s.touche(KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_DOWN))
        ShadowLooper.idleMainLooper(CadreAmbiance.DUREE_APPUI_LONG + 100, TimeUnit.MILLISECONDS)
        assertTrue("répétition gardée", s.touche(KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_DOWN, repetition = 1))
        assertTrue("relâchement gardé", s.touche(KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_UP))
        assertEquals(4, s.bandes().size)
        assertTrue(ShadowToast.getTextOfLatestToast(), ShadowToast.getTextOfLatestToast().startsWith("Néon allumé"))
        // Encore un appui long : néon éteint
        assertFalse(s.touche(KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_DOWN))
        ShadowLooper.idleMainLooper(CadreAmbiance.DUREE_APPUI_LONG + 100, TimeUnit.MILLISECONDS)
        assertTrue(s.touche(KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_UP))
        assertEquals(0, s.bandes().size)
        assertTrue(ShadowToast.getTextOfLatestToast().startsWith("Néon éteint"))
    }

    @Test
    fun appuiCourtSurMenuPasseALApp() {
        val s = service()
        s.ecran(netflix, "com.netflix.ninja.MainActivity")
        assertFalse(s.touche(KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_DOWN))
        ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS)
        assertFalse("appui court : relâchement passé à l'app", s.touche(KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_UP))
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        assertEquals(0, s.bandes().size)
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun menuSurIBiProjoPasseToujours() {
        val s = service()
        s.ecran(contexte.packageName, MainActivity::class.java.name)
        assertFalse(s.touche(KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_DOWN))
        ShadowLooper.idleMainLooper(CadreAmbiance.DUREE_APPUI_LONG + 100, TimeUnit.MILLISECONDS)
        assertFalse(s.touche(KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_DOWN, repetition = 1))
        assertFalse(s.touche(KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_UP))
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    fun maisonToujoursRedirigeeQuandLeNeonEstAffiche() {
        val s = service()
        s.ecran(spotify, "com.spotify.tv.android.SpotifyTVActivity")
        assertEquals(4, s.bandes().size)
        // Appui court sur Maison : gardé par le service, iBiProjo s'ouvre
        assertTrue(s.touche(KeyEvent.KEYCODE_HOME, KeyEvent.ACTION_DOWN))
        assertTrue(s.touche(KeyEvent.KEYCODE_HOME, KeyEvent.ACTION_UP))
        assertEquals(MainActivity::class.java.name, ouverte())
        // Les autres touches passent comme avant
        assertFalse(s.touche(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN))
        assertFalse(s.touche(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP))
    }
}
