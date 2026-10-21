package fr.projo.launcher.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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
 * « Studio » : photo sur le grand fond de l'écran 1 (StudioPhotos.kt).
 *  - sans réseau (source factice : images en dégradé) : héros d'une suggestion, retour au fond
 *    clair (barre, écran 2), réglage coupé, repli sans clé ;
 *  - avec la clé TMDB (variable TMDB_KEY_TESTS, job tests de la CI) : Netflix et Prime Video
 *    dans la barre, une vraie suggestion en héros, une app sans catalogue, zoom sur l'en-tête.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class StudioPhotosTest : BancStudio() {

    @Before
    fun avant() {
        Nouveautes.vider()
    }

    @After
    fun apres() {
        Nouveautes.vider()
        ReglageNouveautes.ecrire(RuntimeEnvironment.getApplication(), true)
    }

    // ------------------------------------------------------------------ lecture de l'écran

    /** Légende de la nouveauté affichée (« Nouveau sur Netflix · … »), ou null. */
    private fun legende(): String? =
        compose.onAllNodes(hasTestTag("studio:photo"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.let { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: "?" }

    /** Clé de la suggestion affichée en héros, ou null. */
    private fun heros(): String? {
        val m = SemanticsMatcher("héros") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("studio:heros:") == true }
        return compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.TestTag)?.removePrefix("studio:heros:")
    }

    /** Une photo (nouveauté ou héros) est-elle dessinée sur le fond ? */
    private fun photoSurLeFond(): Boolean =
        compose.onAllNodes(hasTestTag("studio:fond-photo"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Avance l'horloge en laissant travailler le réseau et le décodage. */
    private fun patienter(ms: Long) {
        var reste = ms
        while (reste > 0) {
            Thread.sleep(100)
            avancer(200)
            reste -= 200
        }
    }

    private fun verifierFondClair(contexte: String) {
        if (photoSurLeFond()) fail("[STUDIO] $contexte : le fond doit rester clair (photo : ${legende() ?: heros()})")
    }

    /** Agrandissement ×2 d'une zone de la capture (en unités .u). */
    private fun zoom(source: File, nom: String, x0: Int, y0: Int, x1: Int, y1: Int) {
        val image = BitmapFactory.decodeFile(source.path) ?: return fail("[STUDIO] capture illisible")
        val k = pxParU
        val g = (x0 * k).toInt().coerceIn(0, image.width - 1)
        val h = (y0 * k).toInt().coerceIn(0, image.height - 1)
        val d = (x1 * k).toInt().coerceIn(g + 1, image.width)
        val b = (y1 * k).toInt().coerceIn(h + 1, image.height)
        val m = Bitmap.createBitmap(image, g, h, d - g, b - h)
        val grand = Bitmap.createScaledBitmap(m, m.width * 2, m.height * 2, true)
        File(source.parentFile, "studio_$nom.png").outputStream().use { grand.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ------------------------------------------------------------------ sans réseau

    /**
     * Suggestion sélectionnée : son image (celle de la carte) passe sur le fond ; ▶ : la suivante ;
     * ▼ vers Netflix sans clé : retour au fond clair ; ▼ vers l'écran 2 : rien.
     */
    @Test
    fun herosSuggestion() {
        sourceStudio = TmdbFactice.source()
        afficher()
        avancer(500)
        verifierFondClair("Netflix sans clé")
        appuyer(Key.DirectionUp)
        attendre("suggestion:tv:101", "▲ depuis la barre")
        patienter(800)
        if (heros() != "prog:tv:101") fail("[STUDIO] héros attendu : prog:tv:101, obtenu ${heros()}")
        if (legende() != null) fail("[STUDIO] pas de légende pour un héros (le titre est sous la carte) : ${legende()}")
        capturer("sp3b_heros_factice")
        appuyer(Key.DirectionRight)
        attendre("suggestion:movie:202", "▶ vers la 2e suggestion")
        patienter(1000)
        if (heros() != "prog:movie:202") fail("[STUDIO] ▶ : héros attendu prog:movie:202, obtenu ${heros()}")
        // ▼ vers la barre : sans clé, aucune nouveauté : le fond redevient clair
        appuyer(Key.DirectionDown)
        patienter(800)
        verifierFondClair("▼ vers la barre (sans clé)")
        // ▲ puis ▼ ▼ : écran 2, aucune photo
        appuyer(Key.DirectionUp)
        patienter(800)
        if (heros() == null) fail("[STUDIO] ▲ : héros attendu de nouveau")
        appuyer(Key.DirectionDown)
        appuyer(Key.DirectionDown)
        verifierEcran(2, "▼ ▼ depuis les suggestions")
        patienter(800)
        verifierFondClair("écran 2")
    }

    /** Réglage « Affiches des nouveautés » coupé : aucune photo, même sur une suggestion. */
    @Test
    fun reglageCoupe() {
        ReglageNouveautes.ecrire(RuntimeEnvironment.getApplication(), false)
        sourceStudio = TmdbFactice.source()
        afficher()
        appuyer(Key.DirectionUp)
        patienter(800)
        verifierFondClair("réglage coupé")
    }

    /** Pas de clé TMDB : ni suggestion, ni photo ; le fond clair actuel. */
    @Test
    fun repliSansCle() {
        afficher()
        patienter(3000)
        verifierFondClair("sans clé")
        if (legende() != null) fail("[STUDIO] légende affichée sans clé : ${legende()}")
        capturer("sp5_repli_sans_cle")
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

    /** Attend la nouveauté dont la légende contient [service] (40 s au plus), puis la fin du fondu. */
    private fun attendrePhoto(service: String) {
        var essais = 0
        while (essais++ < 200 && legende()?.contains(service) != true) patienter(200)
        val l = legende()
        if (l?.contains(service) != true) fail("[STUDIO] nouveauté de $service attendue, légende : $l")
        println("[STUDIO] légende : $l")
        patienter(1200)
    }

    /** Attend les vraies suggestions TMDB et leurs images (40 s au plus). */
    private fun attendreSuggestions() {
        var essais = 0
        fun pretes() = elements(Bande.SUGGESTIONS).any { it.first.startsWith("suggestion:tv:") || it.first.startsWith("suggestion:movie:") }
        while (essais++ < 200 && !pretes()) patienter(200)
        if (!pretes()) fail("[STUDIO] suggestions TMDB attendues : ${elements(Bande.SUGGESTIONS).map { it.first }}")
    }

    @Test
    fun netflixDansLaBarre() {
        preparerTmdb()
        afficher()
        attendreSuggestions()
        attendrePhoto("Netflix")
        val f = capturer("sp1_netflix")
        zoom(f, "sp1z_zoom_entete", 440, 0, 960, 130)
        zoom(f, "sp1z_zoom_entete_gauche", 0, 0, 520, 130)
    }

    @Test
    fun primeVideo() {
        preparerTmdb()
        afficher()
        attendreSuggestions()
        appuyer(Key.DirectionRight)
        attendre(favori(1), "▶ vers Prime Video")
        attendrePhoto("Prime Video")
        capturer("sp2_prime_video")
    }

    /** Vraie suggestion sélectionnée : son image (déjà chargée par la carte) en héros. */
    @Test
    fun suggestionEnHeros() {
        preparerTmdb()
        afficher()
        attendreSuggestions()
        appuyer(Key.DirectionUp)
        val s = focus()?.etiquette.orEmpty()
        if (!s.startsWith("suggestion:")) fail("[STUDIO] ▲ depuis la barre : suggestion attendue, obtenu $s")
        var essais = 0
        while (essais++ < 200 && heros() == null) patienter(200)
        if (heros() != "prog:" + s.removePrefix("suggestion:")) fail("[STUDIO] héros attendu pour $s, obtenu ${heros()}")
        patienter(1200)
        val f = capturer("sp3_suggestion_heros")
        zoom(f, "sp3z_zoom_entete", 440, 0, 960, 130)
    }

    /** SmartTube (pas de catalogue) après Netflix : la photo s'efface, le fond clair revient. */
    @Test
    fun appSansCatalogue() {
        preparerTmdb()
        afficher()
        attendreSuggestions()
        attendrePhoto("Netflix")
        allerAuFavori(3)
        patienter(2500)
        verifierFondClair("SmartTube (sans catalogue)")
        capturer("sp4_sans_catalogue")
    }
}
