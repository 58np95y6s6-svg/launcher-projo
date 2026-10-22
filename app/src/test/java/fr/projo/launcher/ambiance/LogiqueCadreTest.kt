package fr.projo.launcher.ambiance

import fr.projo.launcher.ambiance.LogiqueCadre.Ecran
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quand afficher le néon : app de musique au premier plan (automatique), app ouverte en
 * mode Ambiance, appui long sur ☰ ; jamais sur l'accueil, écran éteint ou mode coupé.
 */
class LogiqueCadreTest {

    private val moi = "fr.projo.launcher"
    private val accueil = "fr.projo.launcher.MainActivity"
    private val spotify = "com.spotify.tv.android"
    private val deezer = "com.deezer.android.tv"
    private val netflix = "com.netflix.ninja"
    private val amazon = "com.amazon.tv.launcher"
    private var maintenant = 1_000L
    private val musiques = setOf(spotify, deezer)
    private val musique: (String) -> Boolean = { it in musiques }

    private fun logique() = LogiqueCadre(moi) { maintenant }

    private fun LogiqueCadre.app(p: String) = ecran(Ecran.APP, p)
    private fun LogiqueCadre.maison() = ecran(Ecran.ACCUEIL, null)
    private fun LogiqueCadre.visible(auto: Boolean = true) = visible(auto, musique)

    @Test
    fun appDeMusiqueAuPremierPlanAllumeLeNeon() {
        val l = logique()
        l.app(spotify)
        assertTrue("Spotify au premier plan", l.visible())
        l.app(deezer)
        assertTrue("Deezer au premier plan", l.visible())
    }

    @Test
    fun autreAppOuMaisonCacheLeNeon() {
        val l = logique()
        l.app(spotify)
        l.app(netflix)
        assertFalse("autre app (vidéo)", l.visible())
        l.app(spotify)
        l.maison()
        assertFalse("Maison", l.visible())
        assertNull(l.premierPlan)
    }

    @Test
    fun jamaisDAllumageAutomatiqueDansUneAppVideo() {
        val l = logique()
        l.app(netflix)
        assertFalse(l.visible(auto = true))
    }

    @Test
    fun modeCoupeCacheLeNeon() {
        val l = logique()
        l.app(spotify)
        assertFalse("automatique désactivé, rien demandé", l.visible(auto = false))
    }

    @Test
    fun ouvrirEnModeAmbianceAllumeLeNeonPourCetteAppSeulement() {
        val l = logique()
        l.maison()
        l.demander(netflix)
        // Le menu d'iBiProjo se ferme pendant que l'app s'ouvre : la demande reste
        l.ecran(Ecran.ACCUEIL, null)
        l.app(netflix)
        assertTrue("Netflix ouvert en mode Ambiance", l.visible(auto = false))
        // Maison : fin de la demande ; Netflix rouvert normalement → pas de néon
        l.maison()
        assertFalse(l.visible(auto = false))
        l.app(netflix)
        assertFalse("rouvert sans le mode", l.visible(auto = false))
        assertNull(l.demande)
    }

    @Test
    fun demandeOublieeSiLAppNeSOuvrePas() {
        val l = logique()
        l.demander(netflix)
        maintenant += LogiqueCadre.ATTENTE_OUVERTURE + 1
        l.app(netflix)
        assertFalse(l.visible(auto = false))
    }

    @Test
    fun appuiLongSurMenuBascule() {
        val l = logique()
        l.app(netflix)
        assertEquals(true, l.basculer(true, musique))
        assertTrue(l.visible())
        assertEquals(false, l.basculer(true, musique))
        assertFalse(l.visible())
        // App de musique : éteint à la main, reste éteint jusqu'à Maison
        l.app(deezer)
        assertTrue(l.visible())
        assertEquals(false, l.basculer(true, musique))
        assertFalse(l.visible())
        l.maison()
        l.app(deezer)
        assertTrue("rallumé après Maison", l.visible())
    }

    @Test
    fun rienABasculerSurLAccueil() {
        val l = logique()
        l.maison()
        assertFalse(l.basculable)
        assertNull(l.basculer(true, musique))
    }

    @Test
    fun ecranEteintOuEconomiseurCacheLeNeon() {
        val l = logique()
        l.app(spotify)
        l.ecranAllume = false
        assertFalse(l.visible())
        l.ecranAllume = true
        l.enReve = true
        assertFalse(l.visible())
        l.enReve = false
        assertTrue(l.visible())
    }

    @Test
    fun classementDesFenetres() {
        val accueils = setOf(amazon)
        fun classe(p: String, c: String?, clavier: String? = null) = LogiqueCadre.classer(p, c, moi, accueil, accueils, clavier)
        assertEquals(Ecran.ACCUEIL, classe(moi, accueil))
        assertEquals(Ecran.ACCUEIL, classe(amazon, "com.amazon.tv.launcher.ui.HomeActivity"))
        // Notre clavier (même paquet qu'iBiProjo) : Spotify reste au premier plan
        assertEquals(Ecran.IGNORE, classe(moi, "android.inputmethodservice.SoftInputWindow"))
        assertEquals(Ecran.IGNORE, classe("com.amazon.tv.ime", "android.inputmethodservice.SoftInputWindow"))
        assertEquals(Ecran.IGNORE, classe("com.amazon.tv.ime", "android.widget.FrameLayout", clavier = "com.amazon.tv.ime"))
        // Nos propres fenêtres (néon, dialogues) : ignorées
        assertEquals(Ecran.IGNORE, classe(moi, "fr.projo.launcher.ambiance.BandeNeon"))
        assertEquals(Ecran.IGNORE, classe("com.android.systemui", "android.app.Dialog"))
        assertEquals(Ecran.APP, classe(spotify, "com.spotify.tv.android.SpotifyTVActivity"))
    }

    @Test
    fun leClavierNeCachePasLeNeon() {
        val l = logique()
        l.app(spotify)
        val sorte = LogiqueCadre.classer(moi, "android.inputmethodservice.SoftInputWindow", moi, accueil, emptySet(), null)
        l.ecran(sorte, moi)
        assertTrue(l.visible())
    }
}
