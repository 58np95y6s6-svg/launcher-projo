package fr.projo.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasTestTag
import fr.projo.launcher.data.CleTmdb
import fr.projo.launcher.data.GenreTmdb
import fr.projo.launcher.data.Nouveaute
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

/**
 * Cartes « nouveauté » (photo d'un titre récent du service, stylisée) dans Playlist :
 *  - captures des trois traitements avec une photo dessinée ici (sans réseau) ;
 *  - captures « réelles » : TMDB interrogé pour Netflix et Prime Video en France, seulement si
 *    la clé est fournie (variable TMDB_KEY_TESTS ou TMDB_KEY) et que le réseau répond.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class NouveautesTest : BancAccueil("PLAYLIST") {

    private val netflix = "com.netflix.ninja"
    private val prime = "com.amazon.amazonvideo.livingroom"
    private val canal = "com.canal.android.canal"

    @Before
    fun avant() = Nouveautes.vider()

    @After
    fun apres() {
        Nouveautes.vider()
        StylesNouveautes.courant = StylesNouveautes.PAR_DEFAUT
    }

    // ------------------------------------------------------------------ photo dessinée (sans réseau)

    /** Photo 16:9 factice : ciel en dégradé, soleil, montagnes, silhouette. */
    private fun photo(ciel1: Int, ciel2: Int, sol: Int): Nouveaute {
        val b = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, 360f, ciel1, ciel2, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 640f, 360f, p)
        p.shader = null
        p.color = 0xFFFFE9B0.toInt()
        c.drawCircle(450f, 120f, 55f, p)
        p.color = sol
        c.drawPath(Path().apply { moveTo(0f, 260f); lineTo(170f, 150f); lineTo(330f, 270f); lineTo(470f, 190f); lineTo(640f, 280f); lineTo(640f, 360f); lineTo(0f, 360f); close() }, p)
        p.color = 0xFF101010.toInt()
        c.drawCircle(250f, 215f, 22f, p)
        c.drawRect(232f, 235f, 268f, 330f, p)
        return Nouveaute("Le Dernier Horizon", GenreTmdb.FILM, b.asImageBitmap(), "test:" + ciel1)
    }

    private fun afficherAvecPhotos(style: StyleNouveaute) {
        StylesNouveautes.courant = style
        Nouveautes.imposer(netflix, photo(0xFF2B1B4F.toInt(), 0xFFE0705A.toInt(), 0xFF2A1A2E.toInt()))
        Nouveautes.imposer(prime, photo(0xFF0E3A5C.toInt(), 0xFF9AD0E8.toInt(), 0xFF1F3B2C.toInt()))
        afficher()
        avancer(1000)
        verifierCartes()
    }

    private fun verifierCartes() {
        for (p in listOf(netflix, prime)) {
            if (compose.onAllNodes(hasTestTag("nouveaute:$p"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                fail("[PLAYLIST] carte nouveauté absente pour $p")
            }
        }
    }

    /** Fondu en vague (retenu) : carte Netflix sélectionnée (photo), zoom, puis au repos (logos). */
    @Test
    fun fondu() {
        afficherAvecPhotos(StyleNouveaute.FONDU)
        val f = capturer("n_A_fondu")
        zoomer(f, "n_A_zoom_netflix")
        auRepos()
        capturer("n_A_repos")
    }

    /** Focus hors des cartes (barre latérale) : toutes les cartes montrent leur logo. */
    private fun auRepos() {
        appuyer(Key.DirectionLeft)
        if (focus()?.etiquette?.startsWith("ligne:") != true) fail("[PLAYLIST] ◀ depuis la 1re carte : obtenu ${decrire(focus())}")
        avancer(500)
    }

    @Test
    fun decoupe() {
        afficherAvecPhotos(StyleNouveaute.DECOUPE)
        capturer("n_B_decoupe")
    }

    @Test
    fun duotone() {
        afficherAvecPhotos(StyleNouveaute.DUOTONE)
        capturer("n_C_duotone")
    }

    /** Sans nouveauté (pas de clé) : les cartes gardent leur illustration. */
    @Test
    fun sansCleRepli() {
        afficher()
        avancer(1000)
        if (compose.onAllNodes(hasTestTag("nouveaute:$netflix"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            fail("[PLAYLIST] carte nouveauté affichée sans clé TMDB")
        }
    }

    // ------------------------------------------------------------------ TMDB réel (CI avec la clé)

    /** Clé fournie et TMDB joignable : sinon le test est ignoré. */
    private fun preparerTmdb() {
        val cle = (System.getenv("TMDB_KEY_TESTS") ?: System.getenv("TMDB_KEY"))?.trim()
        assumeTrue("Pas de clé TMDB (TMDB_KEY_TESTS)", !cle.isNullOrEmpty())
        val contexte = RuntimeEnvironment.getApplication()
        CleTmdb.ecrire(contexte, cle)
        // Réseau hors du fil principal
        var titres: Int? = null
        val t = Thread { titres = runCatching { Tmdb(contexte).dernieresSortiesFournisseurs(listOf(8), 3)?.size }.getOrNull() }
        t.start()
        t.join(30_000)
        println("[PLAYLIST] TMDB : ${titres ?: "injoignable"} titre(s) Netflix")
        assumeTrue("TMDB injoignable", (titres ?: 0) > 0)
    }

    private fun afficherReel(style: StyleNouveaute) {
        preparerTmdb()
        StylesNouveautes.courant = style
        afficher()
        // Chargements réels (réseau, décodage) : 240 essais de 250 ms, au moins 60 s (plusieurs requêtes
        // TMDB à la suite, chacune jusqu'à 13 s si le réseau est lent ; on sort dès que les cartes sont là)
        // (Canal+ aussi s'il a une nouveauté : il est à l'écran)
        var essais = 0
        while (essais++ < 240) {
            val ok = listOf(netflix, prime, canal).all {
                compose.onAllNodes(hasTestTag("nouveaute:$it"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            if (ok || (essais > 40 && listOf(netflix, prime).all { p ->
                    compose.onAllNodes(hasTestTag("nouveaute:$p"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                })
            ) break
            Thread.sleep(250)
            avancer(250)
        }
        // Traitements calculés hors du fil principal (duotone) : laissés finir
        repeat(8) {
            Thread.sleep(250)
            avancer(250)
        }
        verifierCartes()
    }

    /** Netflix puis Prime Video sélectionnés (photo réelle), puis au repos (logos). */
    @Test
    fun reelNetflixPrime() {
        afficherReel(StylesNouveautes.PAR_DEFAUT)
        capturer("reel_netflix_prime")
        appuyer(Key.DirectionRight)
        capturer("reel_prime_focus")
    }

    /** Fondu en vague (retenu) : Netflix sélectionné, zoom sur la carte, accueil au repos. */
    @Test
    fun reelFondu() {
        afficherReel(StyleNouveaute.FONDU)
        val f = capturer("reel_A")
        zoomer(f, "reel_A_zoom_netflix")
        auRepos()
        capturer("reel_repos")
    }

    @Test
    fun reelDecoupe() {
        afficherReel(StyleNouveaute.DECOUPE)
        capturer("reel_B")
    }

    @Test
    fun reelDuotone() {
        afficherReel(StyleNouveaute.DUOTONE)
        capturer("reel_C")
    }
}
