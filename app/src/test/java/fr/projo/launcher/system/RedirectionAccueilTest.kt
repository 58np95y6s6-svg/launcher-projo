package fr.projo.launcher.system

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.view.accessibility.AccessibilityEvent
import fr.projo.launcher.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
 * Ouverture automatique : la pause « accueil Amazon » après un appui long sur Maison, et les
 * accueils surveillés (tous ceux de l'appareil, sauf iBiTV et le système).
 * Bug corrigé : le clavier iBiTV (même paquet), ouvert pour une recherche dans l'accueil
 * Amazon, annulait la pause et l'on était renvoyé vers iBiTV en plein téléchargement.
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
    fun sansPauseLAccueilAmazonRenvoieVersIBiTV() {
        val s = service()
        s.ecran(amazon, "com.amazon.tv.launcher.ui.HomeActivity")
        assertEquals(MainActivity::class.java.name, ouverte())
    }

    /** Bug corrigé : Prime Video (com.amazon.firebat sur Fire TV) était pris pour l'accueil Amazon et refermé aussitôt. */
    @Test
    fun primeVideoResteOuvert() {
        val s = service()
        for (prime in listOf("com.amazon.firebat", "com.amazon.avod", "com.amazon.avod.thirdpartyclient")) {
            s.ecran(prime, "com.amazon.firebatcore.deeplink.DeepLinkRoutingActivity")
            assertNull("$prime renvoyé vers iBiTV", ouverte())
        }
        // Aucune app de streaming connue n'est un accueil
        for (p in listOf("com.netflix.ninja", "com.disney.disneyplus", "com.amazon.firetv.youtube")) {
            s.ecran(p, "Main")
            assertNull("$p renvoyé vers iBiTV", ouverte())
        }
    }

    @Test
    fun leClavierIBiTVNAnnulePasLaPause() {
        val s = service()
        s.pause(10 * 60_000L)
        // Recherche dans l'accueil Amazon : notre clavier s'affiche (même paquet qu'iBiTV)
        s.ecran(s.packageName, "android.inputmethodservice.SoftInputWindow")
        s.ecran(appstore, "com.amazon.venezia.AppDetailActivity")
        s.ecran(amazon, "com.amazon.tv.launcher.ui.DetailActivity")
        assertNull("renvoyé vers iBiTV pendant la pause", ouverte())
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
        assertNull("renvoyé vers iBiTV alors qu'on navigue chez Amazon", ouverte())
    }

    @Test
    fun lEcranIBiTVTermineLaPause() {
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

    /** Écrans d'accueil déclarés sur l'appareil (paquet → activité). */
    private fun accueilsDeclares(vararg paquets: Pair<String, String>) {
        val infos = paquets.map { (paquet, classe) ->
            ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = paquet; name = classe } }
        }
        @Suppress("DEPRECATION")
        shadowOf(RuntimeEnvironment.getApplication().packageManager)
            .addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), infos)
    }

    /**
     * Bug corrigé (boîtier X96 mini) : iBiTV choisi comme accueil par défaut, mais le boîtier relance
     * MediaLauncher à chaque démarrage, et seul l'accueil par défaut était surveillé.
     */
    @Test
    fun lAccueilDuBoitierEstRemplaceMemeSiIBiTVEstParDefaut() {
        val soi = RuntimeEnvironment.getApplication().packageName
        accueilsDeclares(
            "com.droidlogic.mboxlauncher" to "com.droidlogic.mboxlauncher.Launcher",
            soi to MainActivity::class.java.name,
            "com.android.settings" to "com.android.settings.FallbackHome",
        )
        val s = service()
        s.paquetAffiche = { "com.droidlogic.mboxlauncher" }
        s.verifierEcran()
        assertEquals(MainActivity::class.java.name, ouverte())
    }

    /** Les Réglages (écran d'attente « FallbackHome » au démarrage) ne sont jamais remplacés. */
    @Test
    fun lesReglagesNeSontJamaisRemplaces() {
        accueilsDeclares("com.android.settings" to "com.android.settings.FallbackHome")
        val s = service()
        s.ecran("com.android.settings", "com.android.settings.Settings")
        assertNull(ouverte())
    }

    @Test
    fun accueilsARemplacerSansIBiTVNiSysteme() {
        val soi = "fr.projo.launcher"
        val r = FireTv.accueilsARemplacer(
            listOf(
                "com.droidlogic.mboxlauncher", "com.google.android.tvlauncher", soi, "android",
                "com.android.settings", "com.android.tv.settings", "com.android.provision",
                "com.google.android.tungsten.setupwraith", "com.android.setupwizard",
                "com.amazon.firebat", "com.amazon.avod",
            ),
            soi,
        )
        assertEquals(setOf("com.droidlogic.mboxlauncher", "com.google.android.tvlauncher"), r)
        assertTrue(FireTv.accueilsARemplacer(emptyList(), soi).isEmpty())
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
