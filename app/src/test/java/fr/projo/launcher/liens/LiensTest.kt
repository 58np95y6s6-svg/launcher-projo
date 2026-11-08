package fr.projo.launcher.liens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * « Envoyer sur la TV », partie sans Android : vérification des liens (http/https, hôte,
 * 2 048 caractères), titre, genre du lien et applis essayées dans l'ordre de la spécification.
 */
class LiensTest {

    private fun lien(url: String, titre: Any? = null) = Liens.lien(url, titre) ?: throw AssertionError("refusé : $url")

    // ------------------------------------------------------------------ vérification

    @Test
    fun liensAcceptesEtNormalises() {
        val l = lien("https://www.arte.tv/fr/videos/123/les-grands-documentaires/?x=1#debut")
        assertEquals("https://www.arte.tv/fr/videos/123/les-grands-documentaires/?x=1#debut", l.url)
        assertEquals("www.arte.tv", l.hote)
        assertEquals("arte.tv", l.hoteAffiche)
        assertEquals("/fr/videos/123/les-grands-documentaires/", l.chemin)
        // Espaces autour retirés ; schéma et hôte en minuscules, le reste tel quel
        assertEquals("http://exemple.fr/Page?Q=A", lien("  HTTP://Exemple.FR/Page?Q=A \n").url)
        assertEquals("https://exemple.fr:8443/a", lien("https://exemple.fr:8443/a").url)
        assertEquals("exemple.fr", lien("https://exemple.fr").hote)
        assertEquals("", lien("https://exemple.fr").chemin)
        assertEquals("[::1]", lien("http://[::1]:8080/x").hote)
        assertEquals("192.168.1.10", lien("http://192.168.1.10/film.mp4").hote)
        assertEquals("exemple.fr", lien("https://moi:secret@exemple.fr/").hote)
        assertEquals("bücher.de", lien("https://Bücher.de/").hote)
        // 2 048 caractères : accepté ; un de plus : refusé
        val base = "https://exemple.fr/"
        assertNotNull(Liens.lien(base + "a".repeat(Liens.URL_MAX - base.length)))
        assertNull(Liens.lien(base + "a".repeat(Liens.URL_MAX - base.length + 1)))
    }

    @Test
    fun liensRefuses() {
        val refuses = listOf(
            "", "   ", "exemple.fr", "//exemple.fr", "ftp://exemple.fr/", "file:///sdcard/a.mp4",
            "intent://scan/#Intent;scheme=zxing;end", "javascript:alert(1)", "content://media/external/1",
            "https://", "https:///chemin", "https://:443/", "https://exemple.fr:0/", "https://exemple.fr:99999/",
            "https://exemple.fr:8a/", "https://exe mple.fr/", "https://exemple.fr/a b", "https://exemple.fr/\u0000",
            "https://exemple.fr/\u0085", "https://ex..emple.fr/", "https://[::1/", "https://%65xemple.fr/", "data:text/html,x",
        )
        for (u in refuses) assertNull("devait être refusé : $u", Liens.lien(u))
        assertNull(Liens.lien(null))
        assertNull(Liens.lien(42))
        assertNull(Liens.lien(listOf("https://exemple.fr")))
    }

    @Test
    fun titreNettoye() {
        assertEquals("Les grands documentaires", lien("https://arte.tv", "  Les grands\n\tdocumentaires ").titre)
        assertNull(lien("https://arte.tv", "\u0000\u0007 ").titre)
        assertNull(lien("https://arte.tv", 12).titre)
        assertNull(lien("https://arte.tv").titre)
        // 200 caractères au plus, sans moitié d'émoji
        assertEquals(Liens.TITRE_MAX, lien("https://arte.tv", "a".repeat(500)).titre!!.length)
        val emoji = "a".repeat(199) + "😀"
        assertEquals("a".repeat(199), Liens.nettoyerTitre(emoji))
        // Affiché : le titre, sinon l'hôte sans « www. »
        assertEquals("Les grands documentaires", lien("https://www.arte.tv", "Les grands documentaires").affichage)
        assertEquals("arte.tv", lien("https://www.arte.tv").affichage)
    }

    // ------------------------------------------------------------------ genre et applis

    @Test
    fun genres() {
        val attendus = mapOf(
            "https://www.youtube.com/watch?v=abc" to GenreLien.YOUTUBE,
            "https://youtube.com/watch?v=abc" to GenreLien.YOUTUBE,
            "https://m.youtube.com/watch?v=abc" to GenreLien.YOUTUBE,
            "https://youtu.be/abc" to GenreLien.YOUTUBE,
            "https://music.youtube.com/watch?v=abc" to GenreLien.PAGE,
            "https://www.netflix.com/title/80100172" to GenreLien.NETFLIX,
            "https://netflix.com/watch/1" to GenreLien.NETFLIX,
            "https://www.primevideo.com/detail/0ABC" to GenreLien.PRIME_VIDEO,
            "https://www.amazon.fr/gp/video/detail/B0ABC" to GenreLien.PRIME_VIDEO,
            "https://amazon.co.uk/gp/video/storefront" to GenreLien.PRIME_VIDEO,
            "https://www.amazon.fr/dp/B0ABC" to GenreLien.PAGE,
            "https://www.disneyplus.com/fr-fr/video/x" to GenreLien.DISNEY,
            "http://192.168.1.10/films/Film.MP4" to GenreLien.VIDEO,
            "https://cdn.exemple.fr/live/index.m3u8?jeton=1" to GenreLien.VIDEO,
            "https://cdn.exemple.fr/a.mpd#t=10" to GenreLien.VIDEO,
            "https://exemple.fr/v.mkv" to GenreLien.VIDEO,
            "https://exemple.fr/parts" to GenreLien.PAGE,
            "https://www.arte.tv/fr/" to GenreLien.PAGE,
            "https://pasnetflix.com/" to GenreLien.PAGE,
            "https://exemple.fr/?f=a.mp4" to GenreLien.PAGE,
        )
        for ((u, g) in attendus) assertEquals(u, g, Liens.genre(lien(u)))
    }

    @Test
    fun applisEssayeesDansLOrdre() {
        assertEquals(
            listOf("com.ibi.ibitube", "com.teamsmart.videomanager.tv", "com.google.android.youtube.tv", "com.amazon.firetv.youtube"),
            Liens.candidats(GenreLien.YOUTUBE),
        )
        assertEquals(listOf("com.netflix.ninja"), Liens.candidats(GenreLien.NETFLIX))
        assertEquals(listOf("com.amazon.firebat", "com.amazon.avod.thirdpartyclient", "com.amazon.avod"), Liens.candidats(GenreLien.PRIME_VIDEO))
        assertEquals(listOf("com.disney.disneyplus"), Liens.candidats(GenreLien.DISNEY))
        assertEquals(listOf("org.videolan.vlc", "org.xbmc.kodi", "com.mxtech.videoplayer.ad"), Liens.candidats(GenreLien.VIDEO))
        assertTrue(Liens.candidats(GenreLien.PAGE).isEmpty())
    }

    @Test
    fun pagesWebOrdreAuto() {
        val soi = "fr.projo.launcher"
        val installees = mapOf(
            "com.android.chrome" to "Chrome",
            "org.zeta.browser" to "Zeta",
            "com.amazon.cloud9" to "Silk",
            "com.autre.vypn" to "VYPN",
            "org.alpha.web" to "Alpha",
            soi to "iBiTV",
            "net.vypn.app" to "VYPN",
        )
        // VYPN (paquet, puis une appli nommée « VYPN »), Silk, Chrome, puis les autres par ordre alphabétique ; jamais iBiTV
        assertEquals(
            listOf("net.vypn.app", "com.autre.vypn", "com.amazon.cloud9", "com.android.chrome", "org.alpha.web", "org.zeta.browser"),
            Liens.ordreAuto(installees, soi),
        )
        // Choix enregistré installé : en premier, puis l'ordre « auto »
        assertEquals("com.android.chrome", Liens.ordrePages(installees, "com.android.chrome", soi).first())
        assertEquals(6, Liens.ordrePages(installees, "com.android.chrome", soi).size)
        // Choix qui n'est plus installé, ou iBiTV : ordre « auto »
        assertEquals(Liens.ordreAuto(installees, soi), Liens.ordrePages(installees, "org.desinstalle", soi))
        assertEquals(Liens.ordreAuto(installees, soi), Liens.ordrePages(installees, soi, soi))
        assertTrue(Liens.ordreAuto(emptyMap(), soi).isEmpty())
    }

    @Test
    fun pagesWebSansComposantsDuSysteme() {
        val soi = "fr.projo.launcher"
        // Fire TV : le composant d'Amazon qui reçoit les liens n'a pas de nom (Android donne son paquet)
        val installees = mapOf(
            "com.amazon.tv.intentsupport" to "com.amazon.tv.intentsupport",
            "com.google.android.tv.frameworkpackagestubs" to "Stubs",
            "com.amazon.cloud9" to "Navigateur Silk",
            "org.sans.nom" to "org.sans.nom",
            "org.vide" to " ",
            "net.vypn.app" to "VYPN",
            "org.alpha.web" to "Alpha",
        )
        // Proposés : les vrais navigateurs seulement, dans l'ordre « auto »
        assertEquals(listOf("net.vypn.app", "com.amazon.cloud9", "org.alpha.web"), Liens.choixPages(installees, soi))
        // Essayés : les mêmes, puis les autres en tout dernier recours
        assertEquals(
            listOf(
                "net.vypn.app", "com.amazon.cloud9", "org.alpha.web",
                "com.amazon.tv.intentsupport", "com.google.android.tv.frameworkpackagestubs", "org.sans.nom", "org.vide",
            ),
            Liens.ordreAuto(installees, soi),
        )
        assertTrue(Liens.navigateurVisible("com.amazon.cloud9", "Navigateur Silk"))
        assertTrue(!Liens.navigateurVisible("com.amazon.tv.intentsupport", "Intent Support"))
        assertTrue(!Liens.navigateurVisible("org.x", " org.x "))
        assertTrue(Liens.choixPages(mapOf("com.amazon.tv.intentsupport" to "com.amazon.tv.intentsupport"), soi).isEmpty())
    }

    @Test
    fun appliVpnLaPremiereInstallee() {
        assertEquals("ch.protonvpn.android" to "Proton VPN", Liens.appliVpn { it in setOf("com.wireguard.android", "ch.protonvpn.android") })
        assertEquals("net.vypn.app" to "VYPN", Liens.appliVpn { it in setOf("net.mullvad.mullvadvpn", "net.vypn.app") })
        assertNull(Liens.appliVpn { false })
        assertEquals(
            listOf(
                "net.vypn.app", "ch.protonvpn.android", "com.wireguard.android", "com.cloudflare.onedotonedotonedotone",
                "com.nordvpn.android", "com.surfshark.vpnclient.android", "com.expressvpn.vpn", "com.windscribe.vpn", "net.mullvad.mullvadvpn",
            ),
            Liens.APPLIS_VPN.map { it.first },
        )
    }

    @Test
    fun expediteurAvecSonArticle() {
        assertEquals("le téléphone de Marie", Liens.expediteur("Téléphone de Marie"))
        assertEquals("l'iPhone de Paul", Liens.expediteur("iPhone de Paul"))
        assertEquals("la tablette", Liens.expediteur("Tablette"))
        assertEquals("Mon téléphone", Liens.expediteur("Mon téléphone"))
        assertEquals("un téléphone", Liens.expediteur("  "))
    }
}
