package fr.projo.launcher.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures d'écran 1920 × 1080 de chaque design (rendu graphique réel de Robolectric),
 * enregistrées dans app/build/captures/.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CapturesTest(nomDesign: String) : BancAccueil(nomDesign) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun designs(): List<Array<Any>> = Design.entries.map { arrayOf<Any>(it.name) }
    }

    /** Accueil, 1er favori sélectionné (animations terminées : 1 s après l'affichage). */
    @Test
    fun accueilFavori1() {
        afficher()
        avancer(1000)
        capturer("1_accueil_favori1")
    }

    /** 1er élément de « Mes apps » sélectionné. */
    @Test
    fun mesAppsPremier() {
        afficher()
        donnerFocus(app(0))
        capturer("2_mesapps_app1")
    }

    /** 2e favori puis ▼ : le focus est descendu dans « Mes apps ». */
    @Test
    fun basVersMesApps() {
        afficher()
        appuyer(Key.DirectionRight)
        appuyer(Key.DirectionDown)
        avancer(1000)
        capturer("5_favori2_puis_bas")
    }
}

/**
 * Captures du design « Galerie » (titres TMDB factices : images en dégradé) : accueil, après ▶,
 * glissement en cours, focus sur le dock, Ma liste vide et remplie, grille « agrandir »,
 * panneau Réglages ouvert.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CapturesGalerieTest : BancGalerie() {

    @Test
    fun accueil() {
        installerLecteurYoutube()
        afficher()
        avancer(1000)
        capturer("g1_accueil")
    }

    @Test
    fun apresDroite() {
        installerLecteurYoutube()
        afficher()
        donnerFocus("galerie:carte")
        capturer("g2a_carte")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        avancer(450)
        capturer("g2b_mi_glissement")
        avancer(1200)
        capturer("g2c_apres_droite")
    }

    @Test
    fun focusSurLeDock() {
        installerLecteurYoutube()
        afficher()
        donnerFocus(app(1))
        avancer(500)
        capturer("g3_focus_dock")
    }

    @Test
    fun maListe() {
        installerLecteurYoutube()
        afficher()
        donnerFocus("onglet:maliste")
        appuyer(Key.DirectionCenter)
        avancer(800)
        capturer("g4_ma_liste_vide")
        donnerFocus("onglet:pourvous")
        appuyer(Key.DirectionCenter)
        donnerFocus("galerie:ajout")
        appuyer(Key.DirectionCenter)
        donnerFocus("galerie:carte")
        appuyer(Key.DirectionLeft)
        avancer(400)
        donnerFocus("galerie:ajout")
        appuyer(Key.DirectionCenter)
        donnerFocus("onglet:maliste")
        appuyer(Key.DirectionCenter)
        avancer(1200)
        capturer("g4b_ma_liste")
    }

    @Test
    fun grille() {
        afficher()
        donnerFocus("galerie:agrandir")
        appuyer(Key.DirectionCenter)
        appuyer(Key.DirectionRight)
        avancer(500)
        capturer("g5_grille")
    }

    @Test
    fun reglagesOuverts() {
        afficher()
        ouvrirReglages()
        capturer("g6_reglages")
    }

    /** Envoyer sur la TV : pastille VPN verte, puis bandeau « … envoyé par … — Ouverture dans VYPN… ». */
    @Test
    fun lienRecu() {
        installerLecteurYoutube()
        vpnTv = EtatVpnTv(actif = true, garde = true, appli = "VYPN") {}
        afficher()
        avancer(1000)
        capturer("g7a_pastille_vpn")
        val b = fr.projo.launcher.liens.BandeauLien("Les grands documentaires", "arte.tv", "l'iPhone de Paul", "VYPN")
        compose.runOnIdle {
            pardessus.value = { BandeauLienTv(b) }
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        avancer(600)
        capturer("g7b_lien_recu")
    }

    /** Raccourci iOS, VPN coupé : pastille rouge « VPN coupé », puis le garde-fou à l'écran. */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    @Test
    fun gardeFou() {
        installerLecteurYoutube()
        vpnTv = EtatVpnTv(actif = false, garde = true, appli = "VYPN") {}
        afficher()
        avancer(1000)
        capturer("g8a_vpn_coupe")
        val q = fr.projo.launcher.liens.QuestionVpn(
            fr.projo.launcher.liens.Liens.lien("https://www.arte.tv/fr/videos/1/", "Les grands documentaires")!!,
            expediteur = "l'iPhone",
            appliVpn = "VYPN",
        )
        compose.runOnIdle {
            pardessus.value = { GardeFouVpnDialog(q, onAllumer = {}, onSansVpn = {}, onAnnuler = {}) }
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        avancer(1500)
        val fichier = java.io.File("build/captures/galerie_g8b_garde_fou.png")
        fichier.parentFile?.mkdirs()
        com.github.takahirom.roborazzi.captureScreenRoboImage(fichier.path)
    }
}

/**
 * Galerie avec les vrais titres et images de TMDB (clé TMDB_KEY_TESTS du job tests de la CI ;
 * test ignoré sans clé ou sans réseau) : à comparer avec la maquette.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = ECRAN_TV)
class CapturesGalerieTmdbTest : BancGalerie() {

    init {
        sourceStudio = null
    }

    @org.junit.After
    fun oublierCle() {
        fr.projo.launcher.data.CleTmdb.ecrire(org.robolectric.RuntimeEnvironment.getApplication(), null)
    }

    /** Avance l'horloge en laissant travailler le réseau et le décodage. */
    private fun patienter(ms: Long) {
        var reste = ms
        while (reste > 0) {
            Thread.sleep(100)
            avancer(200)
            reste -= 200
        }
    }

    private fun preparer() {
        val cle = (System.getenv("TMDB_KEY_TESTS") ?: System.getenv("TMDB_KEY"))?.trim()
        org.junit.Assume.assumeTrue("Pas de clé TMDB (TMDB_KEY_TESTS)", !cle.isNullOrEmpty())
        val contexte = org.robolectric.RuntimeEnvironment.getApplication()
        fr.projo.launcher.data.CleTmdb.ecrire(contexte, cle)
        var titres: Int? = null
        val t = Thread { titres = runCatching { fr.projo.launcher.data.Tmdb(contexte).dernieresSortiesFournisseurs(listOf(8), 3)?.size }.getOrNull() }
        t.start()
        t.join(30_000)
        org.junit.Assume.assumeTrue("TMDB injoignable", (titres ?: 0) > 0)
        installerLecteurYoutube()
        afficher()
        var essais = 0
        while (essais++ < 200 && heros()?.startsWith("prog:") != true) patienter(200)
        if (heros()?.startsWith("prog:") != true) org.junit.Assert.fail("[GALERIE] titres TMDB attendus, titre au centre : ${heros()}")
        println("[GALERIE] titre au centre : ${heros()}")
        // Images des titres voisins (réseau, décodage, flous)
        patienter(6000)
    }

    @Test
    fun vraisTitres() {
        preparer()
        capturer("gt1_accueil_tmdb")
        donnerFocus("galerie:carte")
        patienter(1000)
        capturer("gt2_carte_tmdb")
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        avancer(300)
        capturer("gt3a_glissement_300ms")
        avancer(300)
        capturer("gt3b_glissement_600ms")
        patienter(3000)
        capturer("gt4_apres_droite_tmdb")
        donnerFocus("galerie:agrandir")
        appuyer(Key.DirectionCenter)
        patienter(4000)
        capturer("gt5_grille_tmdb")
    }

    @Test
    fun dockEtSeries() {
        preparer()
        donnerFocus(app(1))
        patienter(600)
        capturer("gt6_focus_dock_tmdb")
        donnerFocus("onglet:series")
        appuyer(Key.DirectionCenter)
        patienter(5000)
        capturer("gt7_series_tmdb")
    }
}
