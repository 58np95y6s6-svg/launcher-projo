package fr.projo.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import fr.projo.launcher.data.CleTmdb
import fr.projo.launcher.data.Nouveautes
import fr.projo.launcher.data.ReglageNouveautes
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
 * « Pastel » : photo d'une nouveauté du service sélectionné dans le fond lilas (PastelPhotos.kt).
 *  - sans clé (ou hors ligne) : le fond lilas seul, comme avant ;
 *  - avec la clé TMDB (variable TMDB_KEY_TESTS ou TMDB_KEY, job tests de la CI) : Netflix,
 *    Prime Video, Disney+ dans « Mes apps », une app sans catalogue, un réglage rapide,
 *    le réglage « Affiches des nouveautés » coupé, et des zooms sur les zones chargées en texte.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class PastelPhotosTest : BancAccueil("PASTEL") {

    @Before
    fun avant() {
        Nouveautes.vider()
    }

    @After
    fun apres() {
        Nouveautes.vider()
        ReglageNouveautes.ecrire(RuntimeEnvironment.getApplication(), true)
    }

    /** Légende de la photo affichée (« Nouveau sur Netflix · … »), null : pas de photo. */
    private fun photoAffichee(): String? =
        compose.onAllNodes(hasTestTag("pastel-photo"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { n ->
            n.children.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() } ?: "?"
        }

    /** Une photo (entrante ou sortante) est-elle dessinée sur le fond ? */
    private fun photoSurLeFond(): Boolean =
        compose.onAllNodes(hasTestTag("pastel:fond-photo"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun verifierFondSeul(contexte: String) {
        if (photoSurLeFond() || photoAffichee() != null) fail("[PASTEL] $contexte : le fond lilas doit rester seul (photo : ${photoAffichee()})")
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
        val image = BitmapFactory.decodeFile(source.path) ?: return fail("[PASTEL] capture illisible")
        val k = pxParU
        val g = (x0 * k).toInt().coerceIn(0, image.width - 1)
        val h = (y0 * k).toInt().coerceIn(0, image.height - 1)
        val d = (x1 * k).toInt().coerceIn(g + 1, image.width)
        val b = (y1 * k).toInt().coerceIn(h + 1, image.height)
        val m = Bitmap.createBitmap(image, g, h, d - g, b - h)
        val grand = Bitmap.createScaledBitmap(m, m.width * 2, m.height * 2, true)
        File(source.parentFile, "pastel_$nom.png").outputStream().use { grand.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ------------------------------------------------------------------ sans clé

    /** Pas de clé TMDB : aucune photo, le fond lilas seul (et aucune légende). */
    @Test
    fun repliSansCle() {
        afficher()
        attendre(3000)
        verifierFondSeul("sans clé")
        capturer("pp5_repli_sans_cle")
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
        if (p?.contains(service) != true) fail("[PASTEL] photo de $service attendue, légende : $p")
        println("[PASTEL] légende : $p")
        attendre(1200)
    }

    /** Netflix (1er favori) : la photo, puis des zooms sur la ligne du nom (légende) et la bande haute. */
    @Test
    fun netflix() {
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        val f = capturer("pp1_netflix")
        zoom(f, "pp1z_zoom_nom_legende", 0, 200, 960, 310)
        zoom(f, "pp1z_zoom_bande_haute", 220, 16, 960, 232)
        zoom(f, "pp1z_zoom_favoris", 360, 210, 960, 450)
    }

    @Test
    fun primeVideo() {
        preparerTmdb()
        afficher()
        appuyer(Key.DirectionRight)
        attendrePhoto("Prime Video")
        capturer("pp2_prime_video")
    }

    /** Disney+ dans « Mes apps » : même photo de fond que pour un favori. */
    @Test
    fun mesAppsDisney() {
        preparerTmdb()
        afficher()
        donnerFocus("app:com.disney.disneyplus")
        attendrePhoto("Disney+")
        val f = capturer("pp4_disney_mes_apps")
        zoom(f, "pp4z_zoom_mes_apps", 0, 400, 960, 540)
    }

    /** SmartTube (pas de catalogue) après Netflix : la photo s'efface, le fond lilas seul revient. */
    @Test
    fun appSansCatalogue() {
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        allerAuFavori(3)
        attendre(2500)
        verifierFondSeul("${favori(3)} (sans catalogue)")
        capturer("pp3_sans_catalogue")
    }

    /** ▲ depuis Netflix vers la bande haute (réglages rapides ou NBA) : la photo s'efface aussi. */
    @Test
    fun bandeHaute() {
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        appuyer(Key.DirectionUp)
        val f = focus()
        if (estFavori(f) || estDansMesApps(f)) fail("[PASTEL] ▲ depuis Netflix : bande haute attendue, obtenu ${decrire(f)}")
        attendre(2000)
        verifierFondSeul("▲ vers ${f?.etiquette}")
        capturer("pp6_bande_haute")
    }

    /** Réglage « Affiches des nouveautés » coupé : aucune photo, même avec la clé. */
    @Test
    fun reglageCoupe() {
        preparerTmdb()
        ReglageNouveautes.ecrire(RuntimeEnvironment.getApplication(), false)
        afficher()
        attendre(4000)
        verifierFondSeul("réglage coupé")
    }
}
