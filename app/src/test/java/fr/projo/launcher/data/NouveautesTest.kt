package fr.projo.launcher.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Données des nouveautés (data/Nouveautes.kt), sans interface : services reconnus, repli sans
 * clé, réglage « Affiches des nouveautés », et, avec la clé TMDB (variable TMDB_KEY_TESTS ou
 * TMDB_KEY, job tests de la CI), titres et photos réels de Netflix, Prime Video et Canal+.
 * Le rendu (photos en fond d'« À la une ») est vérifié par UnePhotosTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class NouveautesTest {

    private val contexte: Context get() = RuntimeEnvironment.getApplication()

    private val netflix = AppInfo("com.netflix.ninja", "Netflix", isTvApp = true)
    private val prime = AppInfo("com.amazon.amazonvideo.livingroom", "Prime Video", isTvApp = true)
    private val canal = AppInfo("com.canal.android.canal", "Canal+", isTvApp = true)
    private val smartTube = AppInfo("com.teamsmart.videomanager.tv", "SmartTube", isTvApp = true)

    @Before
    fun avant() {
        Nouveautes.vider()
        CleTmdb.ecrire(contexte, null)
    }

    @After
    fun apres() {
        Nouveautes.vider()
        CleTmdb.ecrire(contexte, null)
    }

    /** Services de streaming reconnus sans réseau ; une app sans catalogue et la tuile HDMI, non. */
    @Test
    fun catalogues() {
        for (app in listOf(netflix, prime, canal)) assertTrue("${app.label} : service attendu", Nouveautes.aUnCatalogue(app))
        // Reconnu aussi par son nom (paquet inconnu)
        assertTrue("Netflix (par le nom)", Nouveautes.aUnCatalogue(AppInfo("fr.test.netflix", "Netflix", isTvApp = true)))
        assertFalse("SmartTube : pas de catalogue", Nouveautes.aUnCatalogue(smartTube))
        assertFalse("tuile HDMI", Nouveautes.aUnCatalogue(AppInfo.HDMI))
    }

    /** Sans clé TMDB : aucun titre, rien à afficher (« À la une » garde son aplat). */
    @Test
    fun sansCle() = runBlocking {
        val titres = Nouveautes.titresRecents(contexte, netflix)
        assertTrue("sans clé : aucun titre attendu, obtenu ${titres?.size}", titres.isNullOrEmpty())
        assertNull("app sans catalogue", Nouveautes.titresRecents(contexte, smartTube))
    }

    /** « Affiches des nouveautés » : simple interrupteur, enregistré sur l'appareil. */
    @Test
    fun reglageAffiches() {
        val prefs = contexte.getSharedPreferences("nouveautes", Context.MODE_PRIVATE)
        ReglageNouveautes.ecrire(contexte, false)
        assertFalse(ReglageNouveautes.actif)
        assertFalse(prefs.getBoolean("actif", true))
        ReglageNouveautes.ecrire(contexte, true)
        assertTrue(ReglageNouveautes.actif)
        assertTrue(prefs.getBoolean("actif", false))
    }

    // ------------------------------------------------------------------ TMDB réel (CI avec la clé)

    /** Clé fournie et TMDB joignable : sinon le test est ignoré. */
    private fun preparerTmdb() {
        val cle = (System.getenv("TMDB_KEY_TESTS") ?: System.getenv("TMDB_KEY"))?.trim()
        assumeTrue("Pas de clé TMDB (TMDB_KEY_TESTS)", !cle.isNullOrEmpty())
        CleTmdb.ecrire(contexte, cle)
        var titres: Int? = null
        val t = Thread { titres = runCatching { Tmdb(contexte).dernieresSortiesFournisseurs(listOf(8), 3)?.size }.getOrNull() }
        t.start()
        t.join(30_000)
        println("[TMDB] ${titres ?: "injoignable"} titre(s) Netflix")
        assumeTrue("TMDB injoignable", (titres ?: 0) > 0)
    }

    /** Netflix et Prime Video : des nouveautés avec photo, décodée à 1280 px au plus. Canal+ : journalisé. */
    @Test
    fun reelNetflixPrimeCanal() = runBlocking {
        preparerTmdb()
        for (app in listOf(netflix, prime, canal)) {
            val titres = Nouveautes.titresRecents(contexte, app, 3)
            println("[TMDB] ${app.label} : ${titres?.joinToString { it.titre } ?: "injoignable"}")
            if (app == canal) continue
            if (titres.isNullOrEmpty()) {
                fail("${app.label} : aucune nouveauté TMDB")
                continue
            }
            if (titres.any { it.image.isEmpty() }) fail("${app.label} : titre sans photo")
            // Première photo lisible parmi les titres (une image peut manquer côté TMDB)
            val photo = withContext(Dispatchers.IO) {
                titres.firstNotNullOfOrNull { Nouveautes.photo(contexte, it.image, 1280) }
            }
            if (photo == null) {
                fail("${app.label} : aucune photo lisible")
                continue
            }
            println("[TMDB] ${app.label} : photo ${photo.width} × ${photo.height}")
            assertTrue("${app.label} : photo de ${photo.width} px (1280 au plus)", photo.width in 1..1280)
        }
    }
}
