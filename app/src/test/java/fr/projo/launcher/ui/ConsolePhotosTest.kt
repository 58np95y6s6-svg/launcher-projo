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
import kotlinx.coroutines.runBlocking
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
 * « Console » : la photo d'une nouveauté du service sélectionné remplace le fond cinéma, en
 * plein écran (ConsolePhotos.kt).
 *  - sans clé (ou hors ligne) : le fond cinéma seul, comme avant ;
 *  - avec la clé TMDB (variable TMDB_KEY_TESTS ou TMDB_KEY, job tests de la CI) : Netflix le
 *    jour et le soir, Prime Video, Disney+ dans « Mes apps », une app sans catalogue, un bouton
 *    du haut, le réglage coupé, l'alternance des nouveautés, et des zooms sur les zones chargées
 *    en texte (grand titre, barre du haut, carte NBA et légende).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class ConsolePhotosTest : BancAccueil("CONSOLE") {

    @Before
    fun avant() {
        Nouveautes.vider()
        SoirConsole.impose = false
    }

    @After
    fun apres() {
        Nouveautes.vider()
        SoirConsole.impose = null
        ReglageNouveautes.ecrire(RuntimeEnvironment.getApplication(), true)
    }

    /** Légende de la photo affichée (« Nouveau sur Netflix · … »), null : pas de photo. */
    private fun photoAffichee(): String? =
        compose.onAllNodes(hasTestTag("console-photo"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { n ->
            n.children.firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString() } ?: "?"
        }

    /** Une photo (affichée ou qui s'efface) est-elle encore là ? */
    private fun photoPresente(): Boolean = photoAffichee() != null ||
        compose.onAllNodes(hasTestTag("console-photo-sortante"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

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
        val image = BitmapFactory.decodeFile(source.path) ?: return fail("[CONSOLE] capture illisible")
        val k = pxParU
        val g = (x0 * k).toInt().coerceIn(0, image.width - 1)
        val h = (y0 * k).toInt().coerceIn(0, image.height - 1)
        val d = (x1 * k).toInt().coerceIn(g + 1, image.width)
        val b = (y1 * k).toInt().coerceIn(h + 1, image.height)
        val m = Bitmap.createBitmap(image, g, h, d - g, b - h)
        val grand = Bitmap.createScaledBitmap(m, m.width * 2, m.height * 2, true)
        File(source.parentFile, "console_$nom.png").outputStream().use { grand.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ------------------------------------------------------------------ sans clé

    /** Pas de clé TMDB : aucune photo, le fond cinéma seul (et aucune légende). */
    @Test
    fun repliSansCle() {
        afficher()
        attendre(3000)
        if (photoPresente()) fail("[CONSOLE] photo affichée sans clé TMDB : ${photoAffichee()}")
        capturer("cp5_repli_sans_cle")
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
        if (p?.contains(service) != true) fail("[CONSOLE] photo de $service attendue, légende : $p")
        println("[CONSOLE] légende : $p")
        attendre(1200)
    }

    /** Netflix (1er favori) : la photo en grand fond, puis des zooms sur les zones chargées en texte. */
    @Test
    fun netflix() {
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        val f = capturer("cp1_netflix")
        zoom(f, "cp1z_zoom_heros", 0, 0, 540, 400)
        zoom(f, "cp1z_zoom_barre", 520, 0, 960, 130)
        zoom(f, "cp1z_zoom_nba_legende", 500, 210, 960, 460)
    }

    /** Le soir (19 h – 7 h) : photo plus sombre. */
    @Test
    fun netflixSoir() {
        SoirConsole.impose = true
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        val f = capturer("cp6_netflix_soir")
        zoom(f, "cp6z_zoom_heros_soir", 0, 0, 540, 400)
    }

    @Test
    fun primeVideo() {
        preparerTmdb()
        afficher()
        appuyer(Key.DirectionRight)
        if (focus()?.etiquette != favori(1)) fail("[CONSOLE] ▶ depuis Netflix : attendu ${favori(1)}, obtenu ${decrire(focus())}")
        attendrePhoto("Prime Video")
        capturer("cp2_prime_video")
    }

    /** Disney+ dans « Mes apps » : même photo de fond que pour un favori. */
    @Test
    fun mesAppsDisney() {
        preparerTmdb()
        afficher()
        donnerFocus("app:com.disney.disneyplus")
        attendrePhoto("Disney+")
        val f = capturer("cp4_disney_mes_apps")
        zoom(f, "cp4z_zoom_mes_apps", 0, 360, 560, 540)
        zoom(f, "cp4z_zoom_legende", 480, 360, 960, 470)
    }

    /** SmartTube (pas de catalogue) après Netflix : la photo s'efface, le fond cinéma revient. */
    @Test
    fun appSansCatalogue() {
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        allerAuFavori(3)
        attendre(2500)
        if (photoPresente()) fail("[CONSOLE] photo encore affichée sur ${favori(3)} : ${photoAffichee()}")
        capturer("cp3_sans_catalogue")
    }

    /** Un bouton du haut (▲ depuis Netflix) : la photo s'efface, le fond cinéma revient. */
    @Test
    fun boutonDuHaut() {
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        appuyer(Key.DirectionUp)
        if (focus()?.etiquette?.startsWith("rapide:") != true) donnerFocus("rapide:Réglages")
        attendre(2000)
        if (photoPresente()) fail("[CONSOLE] photo encore affichée sur ${decrire(focus())} : ${photoAffichee()}")
        capturer("cp7_bouton")
    }

    /** Réglage « Affiches des nouveautés » coupé : aucune photo, même sur Netflix. */
    @Test
    fun reglageCoupe() {
        ReglageNouveautes.ecrire(RuntimeEnvironment.getApplication(), false)
        preparerTmdb()
        afficher()
        attendre(4000)
        if (photoPresente()) fail("[CONSOLE] photo affichée malgré le réglage coupé : ${photoAffichee()}")
    }

    /** Plusieurs nouveautés : la suivante remplace la photo au bout de 12 s. */
    @Test
    fun alternance() {
        preparerTmdb()
        afficher()
        attendrePhoto("Netflix")
        val premiere = photoAffichee()
        val contexte = RuntimeEnvironment.getApplication()
        val netflix = ui.favorites[0]
        // Déjà en cache (aucun accès réseau)
        val nombre = runBlocking { Nouveautes.titresRecents(contexte, netflix, 3)?.size ?: 0 }
        assumeTrue("Une seule nouveauté pour Netflix", nombre >= 2)
        var essais = 0
        while (essais++ < 100 && (photoAffichee() == null || photoAffichee() == premiere)) {
            Thread.sleep(200)
            avancer(200)
        }
        val suivante = photoAffichee()
        println("[CONSOLE] alternance : $premiere → $suivante")
        if (suivante == null || suivante == premiere) fail("[CONSOLE] nouveauté suivante attendue après 12 s, légende : $suivante")
    }
}
