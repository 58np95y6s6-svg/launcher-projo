package fr.projo.launcher.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Ouvrir une app vidéo ou TV met en pause la musique en arrière-plan (Spotify…). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PauseMusiqueTest {

    private val contexte get() = RuntimeEnvironment.getApplication()

    private fun installer(paquet: String, nom: String) {
        val pi = PackageInfo().apply {
            packageName = paquet
            applicationInfo = ApplicationInfo().apply {
                packageName = paquet
                nonLocalizedLabel = nom
            }
        }
        shadowOf(contexte.packageManager).installPackage(pi)
    }

    private fun audio() = shadowOf(contexte.getSystemService(AudioManager::class.java))

    @Test
    fun ouvrirYouTubeMetLaMusiqueEnPause() {
        installer("com.amazon.firetv.youtube", "YouTube")
        audio().setIsMusicActive(true)
        PauseMusique.avantOuverture(contexte, "com.amazon.firetv.youtube")
        val demande = audio().lastAudioFocusRequest
        assertNotNull("aucune demande du son : la musique continue", demande)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN, demande!!.durationHint)
    }

    @Test
    fun ouvrirNetflixMetLaMusiqueEnPause() {
        installer("com.netflix.ninja", "Netflix")
        audio().setIsMusicActive(true)
        PauseMusique.avantOuverture(contexte, "com.netflix.ninja")
        assertNotNull(audio().lastAudioFocusRequest)
    }

    @Test
    fun ouvrirUneAppDeMusiqueNeCoupeRien() {
        installer("com.deezer.android.tv", "Deezer")
        audio().setIsMusicActive(true)
        PauseMusique.avantOuverture(contexte, "com.deezer.android.tv")
        assertNull(audio().lastAudioFocusRequest)
    }

    @Test
    fun sansMusiqueRienNeSePasse() {
        installer("com.amazon.firetv.youtube", "YouTube")
        audio().setIsMusicActive(false)
        PauseMusique.avantOuverture(contexte, "com.amazon.firetv.youtube")
        assertNull(audio().lastAudioFocusRequest)
    }
}
