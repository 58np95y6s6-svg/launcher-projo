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
 * « Verre » : photo d'une nouveauté du service sélectionné incrustée dans le papier peint.
 *  - sans clé (ou hors ligne) : le papier peint seul, comme avant ;
 *  - avec la clé TMDB (variable TMDB_KEY_TESTS ou TMDB_KEY, job tests de la CI) : Netflix,
 *    Prime Video, Disney+ dans « Mes apps », une app sans catalogue, l'ornement, et des zooms
 *    sur les zones chargées en texte (fenêtre NBA, ligne du nom de l'app et de la légende).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class VerrePhotosTest : BancAccueil("VERRE") {

    @Before
    fun avant() = Nouveautes.vider()

    @After
    fun apres() = Nouveautes.vider()

    /** Légende de la photo affichée (« Nouveau sur Netflix · … »), null : pas de photo. */
    private fun photoAffichee(): String? =
        compose.onAllNodes(hasTestTag("verre-photo"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { n ->
            n.children.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() } ?: "?"
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

    /** Agrandissement ×2 d'une zone de la capture (en unités .u). */
    private fun zoom(source: File, nom: String, x0: Int, y0: Int, x1: Int, y1: Int) {
        val image = BitmapFactory.decodeFile(source.path) ?: return fail("[VERRE] capture illisible")
        val k = pxParU
        val g = (x0 * k).toInt().coerceIn(0, image.width - 1)
        val h = (y0 * k).toInt().coerceIn(0, image.height - 1)
        val d = (x1 * k).toInt().coerceIn(g + 1, image.width)
        val b = (y1 * k).toInt().coerceIn(h + 1, image.height)
        val m = Bitmap.createBitmap(image, g, h, d - g, b - h)
        val grand = Bitmap.createScaledBitmap(m, m.width * 2, m.height * 2, true)
        File(source.parentFile, "verre_$nom.png").outputStream().use { grand.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ------------------------------------------------------------------ sans clé

    /** Pas de clé TMDB : aucune photo, le papier peint seul (et aucune légende). */
    @Test
    fun repliSansCle() {
        afficher()
        attendre(3000)
        if (photoAffichee() != null) fail("[VERRE] photo affichée sans clé TMDB : ${photoAffichee()}")
        capturer("vp5_repli_sans_cle")
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

    /** Attend la photo dont la légende contient [service] (60 s au plus), puis la fin du fondu. */
    private fun attendrePhoto(service: String) {
        var essais = 0
        while (essais++ < 300 && photoAffichee()?.contains(service) != true) {
            Thread.sleep(200)
            avancer(200)
        }
        val p = photoAffichee()
        if (p?.contains(service) != true) fail("[VERRE] photo de $service attendue, légende : $p")
        println("[VERRE] légende : $p")
        attendre(1200)
    }

    /** Netflix (1er favori) : la photo, puis des zooms sur la NBA et sur la ligne du nom et de la légende. */
    @Test
    fun netflix() {
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        val f = capturer("vp1_netflix")
        zoom(f, "vp1z_zoom_nba", 515, 22, 940, 208)
        zoom(f, "vp1z_zoom_horloge_titre", 40, 18, 520, 250)
        zoom(f, "vp1z_zoom_legende", 520, 200, 960, 250)
    }

    @Test
    fun primeVideo() {
        preparerTmdb()
        afficher()
        appuyer(Key.DirectionRight)
        attendrePhoto("Prime Video")
        capturer("vp2_prime_video")
    }

    /** SmartTube (pas de catalogue) après Netflix : la photo s'efface, le papier peint seul revient. */
    @Test
    fun appSansCatalogue() {
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        allerAuFavori(3)
        attendre(2500)
        if (photoAffichee() != null) fail("[VERRE] photo encore affichée sur ${favori(3)} : ${photoAffichee()}")
        capturer("vp3_sans_catalogue")
    }

    /** Disney+ dans « Mes apps » : même photo de fond que pour un favori. */
    @Test
    fun mesAppsDisney() {
        preparerTmdb()
        afficher()
        val disney = "app:com.disney.disneyplus"
        donnerFocus(disney)
        attendrePhoto("Disney+")
        val f = capturer("vp4_disney_mes_apps")
        zoom(f, "vp4z_zoom_mes_apps", 90, 380, 940, 520)
    }

    /** ◀ vers l'ornement (boutons rapides) : la photo s'efface aussi. */
    @Test
    fun ornement() {
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        appuyer(Key.DirectionLeft)
        if (focus()?.etiquette?.startsWith("rapide:") != true) fail("[VERRE] ◀ depuis Netflix : ornement attendu, obtenu ${decrire(focus())}")
        attendre(2000)
        if (photoAffichee() != null) fail("[VERRE] photo encore affichée sur l'ornement : ${photoAffichee()}")
        capturer("vp6_ornement")
    }
}
