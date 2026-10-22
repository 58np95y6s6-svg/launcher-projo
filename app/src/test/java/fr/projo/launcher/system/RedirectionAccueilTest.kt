package fr.projo.launcher.system

import android.view.accessibility.AccessibilityEvent
import fr.projo.launcher.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Ouverture automatique (Fire TV) : la pause « accueil Amazon » après un appui long sur Maison.
 * Bug corrigé : le clavier iBiProjo (même paquet), ouvert pour une recherche dans l'accueil
 * Amazon, annulait la pause et l'on était renvoyé vers iBiProjo en plein téléchargement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RedirectionAccueilTest {

    private val amazon = "com.amazon.tv.launcher"
    private val appstore = "com.amazon.venezia"

    private fun service(): RedirectionAccueil = Robolectric.setupService(RedirectionAccueil::class.java)

    /** Comme un appui long sur Maison : redirection suspendue [ms] millisecondes. */
    private fun RedirectionAccueil.pause(ms: Long) {
        val champ = RedirectionAccueil::class.java.getDeclaredField("pauseJusqua")
        champ.isAccessible = true
        champ.setLong(this, android.os.SystemClock.uptimeMillis() + ms)
    }

    private fun RedirectionAccueil.ecran(paquet: String, classe: String) {
        val e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        e.packageName = paquet
        e.className = classe
        onAccessibilityEvent(e)
    }

    /** Activité ouverte par le service depuis le dernier appel (null : aucune). */
    private fun ouverte(): String? =
        shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity?.component?.className

    @Test
    fun sansPauseLAccueilAmazonRenvoieVersIBiProjo() {
        val s = service()
        s.ecran(amazon, "com.amazon.tv.launcher.ui.HomeActivity")
        assertEquals(MainActivity::class.java.name, ouverte())
    }

    @Test
    fun leClavierIBiProjoNAnnulePasLaPause() {
        val s = service()
        s.pause(10 * 60_000L)
        // Recherche dans l'accueil Amazon : notre clavier s'affiche (même paquet qu'iBiProjo)
        s.ecran(s.packageName, "android.inputmethodservice.SoftInputWindow")
        s.ecran(appstore, "com.amazon.venezia.AppDetailActivity")
        s.ecran(amazon, "com.amazon.tv.launcher.ui.DetailActivity")
        assertNull("renvoyé vers iBiProjo pendant la pause", ouverte())
    }

    @Test
    fun laPauseDureTantQuOnNavigueChezAmazon() {
        val s = service()
        s.pause(60_000L)
        // Un changement d'écran toutes les 50 s pendant 5 min : la pause est prolongée à chaque fois
        repeat(6) {
            ShadowLooper.idleMainLooper(50, TimeUnit.SECONDS)
            s.ecran(amazon, "com.amazon.tv.launcher.ui.HomeActivity")
        }
        assertNull("renvoyé vers iBiProjo alors qu'on navigue chez Amazon", ouverte())
    }

    @Test
    fun lEcranIBiProjoTermineLaPause() {
        val s = service()
        s.pause(10 * 60_000L)
        s.ecran(s.packageName, MainActivity::class.java.name)
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        s.ecran(amazon, "com.amazon.tv.launcher.ui.HomeActivity")
        assertEquals(MainActivity::class.java.name, ouverte())
    }

    @Test
    fun auDemarrageLAccueilAmazonDejaAfficheEstRemplace() {
        val s = service()
        s.paquetAffiche = { amazon }
        s.verifierEcran()
        assertEquals(MainActivity::class.java.name, ouverte())
    }

    @Test
    fun auDemarrageUneAppAfficheeResteLa() {
        val s = service()
        s.paquetAffiche = { "com.netflix.ninja" }
        s.verifierEcran()
        assertNull(ouverte())
    }

    @Test
    fun laVerificationRespecteLaPauseAmazon() {
        val s = service()
        s.pause(10 * 60_000L)
        s.paquetAffiche = { amazon }
        s.verifierEcran()
        assertNull(ouverte())
    }
}
