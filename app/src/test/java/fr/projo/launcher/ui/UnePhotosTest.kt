package fr.projo.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import fr.projo.launcher.data.CleTmdb
import fr.projo.launcher.data.Nouveautes
import fr.projo.launcher.data.Tmdb
import org.junit.After
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * « À la une » : photos des nouveautés du service sélectionné derrière l'affiche.
 *  - sans clé (ou hors ligne) : l'aplat seul, comme avant ;
 *  - avec la clé TMDB (variable TMDB_KEY_TESTS ou TMDB_KEY, job tests de la CI) : Netflix le
 *    jour et le soir, Prime Video, une app sans catalogue, et des zooms sur l'heure et le score.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class UnePhotosTest : BancAccueil("UNE") {

    @Before
    fun avant() {
        Nouveautes.vider()
        SoirUne.impose = false
    }

    @After
    fun apres() {
        Nouveautes.vider()
        SoirUne.impose = null
    }

    private fun photoAffichee(): String? =
        compose.onAllNodes(hasTestTag("une-photo"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { n ->
            // Légende : premier texte sous le nœud
            n.children.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() } ?: "?"
        }

    // ------------------------------------------------------------------ sans clé

    /** Pas de clé TMDB : aucune photo, l'aplat seul (et aucune légende). */
    @Test
    fun repliSansCle() {
        afficher()
        attendre(3000)
        if (photoAffichee() != null) fail("[UNE] photo affichée sans clé TMDB")
        capturer("ph5_repli_sans_cle")
    }

    // ------------------------------------------------------------------ TMDB réel (CI avec la clé)

    private fun preparerTmdb() {
        val cle = (System.getenv("TMDB_KEY_TESTS") ?: System.getenv("TMDB_KEY"))?.trim()
        assumeTrue("Pas de clé TMDB (TMDB_KEY_TESTS)", !cle.isNullOrEmpty())
        val contexte = RuntimeEnvironment.getApplication()
        CleTmdb.ecrire(contexte, cle)
        var titres: Int? = null
        val t = Thread { titres = runCatching { Tmdb(contexte).dernieresSortiesFournisseurs(listOf(8), 3)?.size }.getOrNull() }
        t.start()
        t.join(30_000)
        assumeTrue("TMDB injoignable", (titres ?: 0) > 0)
    }

    /** Avance l'horloge en laissant travailler le réseau et le décodage. */
    private fun attendre(ms: Long) {
        var reste = ms
        while (reste > 0) {
            Thread.sleep(200)
            avancer(200)
            reste -= 200
        }
    }

    /** Attend la photo dont la légende contient [service] (40 s au plus), puis la fin du fondu. */
    private fun attendrePhoto(service: String) {
        var essais = 0
        while (essais++ < 200 && photoAffichee()?.contains(service) != true) {
            Thread.sleep(200)
            avancer(200)
        }
        val p = photoAffichee()
        if (p?.contains(service) != true) fail("[UNE] photo de $service attendue, légende : $p")
        println("[UNE] légende : $p")
        attendre(1200)
    }

    /** Agrandissement ×2 d'une zone de la capture (en unités .u). */
    private fun zoom(source: File, nom: String, x0: Int, y0: Int, x1: Int, y1: Int) {
        val image = BitmapFactory.decodeFile(source.path) ?: return fail("[UNE] capture illisible")
        val k = pxParU
        val g = (x0 * k).toInt().coerceIn(0, image.width - 1)
        val h = (y0 * k).toInt().coerceIn(0, image.height - 1)
        val d = (x1 * k).toInt().coerceIn(g + 1, image.width)
        val b = (y1 * k).toInt().coerceIn(h + 1, image.height)
        val m = Bitmap.createBitmap(image, g, h, d - g, b - h)
        val grand = Bitmap.createScaledBitmap(m, m.width * 2, m.height * 2, true)
        File(source.parentFile, "une_$nom.png").outputStream().use { grand.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun netflixJour() {
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        val f = capturer("ph1_netflix_jour")
        zoom(f, "ph1z_zoom_heure", 30, 50, 470, 195)
        zoom(f, "ph1z_zoom_score", 520, 60, 930, 195)
    }

    @Test
    fun primeVideo() {
        preparerTmdb()
        afficher()
        appuyer(Key.DirectionRight)
        attendrePhoto("Prime Video")
        capturer("ph2_prime_video")
    }

    @Test
    fun netflixSoir() {
        SoirUne.impose = true
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        val f = capturer("ph3_netflix_soir")
        zoom(f, "ph3z_zoom_heure_soir", 30, 50, 470, 195)
    }

    /** SmartTube (pas de catalogue) après Netflix : la photo s'efface, l'aplat seul reste. */
    @Test
    fun appSansCatalogue() {
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        allerAuFavori(3)
        attendre(2500)
        if (photoAffichee() != null) fail("[UNE] photo encore affichée sur ${favori(3)} : ${photoAffichee()}")
        capturer("ph4_sans_catalogue")
    }
}
