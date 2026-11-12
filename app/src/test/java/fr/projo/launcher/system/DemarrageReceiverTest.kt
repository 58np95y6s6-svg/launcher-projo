package fr.projo.launcher.system

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import fr.projo.launcher.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Démarrage d'un boîtier (hors Fire TV) : iBiTV s'ouvre s'il est l'écran d'accueil par défaut.
 * Bug corrigé (X96 mini) : le boîtier relançait MediaLauncher à chaque démarrage malgré ce choix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DemarrageReceiverTest {

    private val app get() = RuntimeEnvironment.getApplication()

    private fun demarrage() = DemarrageReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

    private fun ouverte(): String? = shadowOf(app).nextStartedActivity?.component?.className

    @Test
    fun iBiTVParDefautSOuvreAuDemarrage() {
        // Seul accueil déclaré : celui d'iBiTV (manifeste)
        assertEquals(true, FireTv.iBiTVParDefaut(app))
        demarrage()
        assertEquals(MainActivity::class.java.name, ouverte())
    }

    @Test
    fun unAutreAccueilParDefautResteLa() {
        val mediaLauncher = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.droidlogic.mboxlauncher"
                name = "com.droidlogic.mboxlauncher.Launcher"
            }
            isDefault = true
        }
        @Suppress("DEPRECATION")
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), listOf(mediaLauncher),
        )
        assertEquals(false, FireTv.iBiTVParDefaut(app))
        demarrage()
        assertNull(ouverte())
    }

    @Test
    fun autreEvenementIgnore() {
        DemarrageReceiver().onReceive(app, Intent(Intent.ACTION_SCREEN_ON))
        assertNull(ouverte())
    }
}
