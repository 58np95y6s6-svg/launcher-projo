package fr.projo.launcher.puces

import fr.projo.launcher.data.AppInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Puces NFC : le téléphone ouvre http://<Fire TV>:8642/o/<app>?c=<code>, le Fire TV ouvre
 * l'app. Code obligatoire, apps installées seulement, rien d'autre n'est possible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PucesNfcTest {

    private val soi = "fr.projo.launcher"
    private var installees = setOf("com.netflix.ninja", "com.liskovsoft.smarttubetv", "com.canal.android.canal")
    private val ouvertes = mutableListOf<String>()
    private var t = 0L

    private fun serveur(code: String = "Code123abc") = ServeurPuces(
        port = 0,
        code = { code },
        resoudre = { nom -> PucesNfc.resoudre(nom, soi) { it in installees } },
        libelle = { p -> if (p == "com.netflix.ninja") "Netflix" else p },
        ouvrir = { ouvertes.add(it) },
        horloge = { t },
    )

    @Before
    fun avant() = PucesNfc.oublier()

    @After
    fun apres() = PucesNfc.oublier()

    @Test
    fun leBonCodeOuvreLApp() {
        val r = serveur().repondre("GET", "/o/netflix?c=Code123abc")
        assertEquals(200, r.statut)
        assertEquals("Netflix s'ouvre sur la TV", r.titre)
        assertEquals(listOf("com.netflix.ninja"), ouvertes)
    }

    @Test
    fun sansLeBonCodeRienNeSOuvre() {
        val s = serveur()
        for (cible in listOf("/o/netflix?c=faux", "/o/netflix", "/o/netflix?c=", "/o/netflix?x=Code123abc", "/o/netflix?c=code123abc")) {
            assertEquals(cible, 403, s.repondre("GET", cible).statut)
        }
        assertTrue("ouvert sans code : $ouvertes", ouvertes.isEmpty())
        // Fonction jamais activée (pas de code) : tout est refusé
        assertEquals(403, serveur(code = "").repondre("GET", "/o/netflix?c=").statut)
    }

    @Test
    fun seulementDesAppsInstallees() {
        val s = serveur()
        // SmartTube : la version installée (pas la bêta)
        assertEquals(200, s.repondre("GET", "/o/smarttube?c=Code123abc").statut)
        assertEquals("com.liskovsoft.smarttubetv", ouvertes.last())
        // Nom de paquet d'une app installée
        assertEquals(200, s.repondre("GET", "/o/com.canal.android.canal?c=Code123abc").statut)
        assertEquals("com.canal.android.canal", ouvertes.last())
        // Accueil d'iBiProjo
        assertEquals(200, s.repondre("GET", "/o/accueil?c=Code123abc").statut)
        assertEquals(soi, ouvertes.last())
        // Pas installée, inconnue, ou adresse bizarre : rien
        val avant = ouvertes.size
        for (cible in listOf("/o/prime?c=Code123abc", "/o/com.inconnue.app?c=Code123abc", "/o/..%2F..%2Fetc?c=Code123abc", "/o/?c=Code123abc")) {
            assertEquals(cible, 404, s.repondre("GET", cible).statut)
        }
        assertEquals(avant, ouvertes.size)
    }

    @Test
    fun autresDemandesSansEffet() {
        val s = serveur()
        assertEquals(404, s.repondre("GET", "/").statut)
        assertEquals(404, s.repondre("GET", "/favicon.ico").statut)
        assertEquals(405, s.repondre("POST", "/o/netflix?c=Code123abc").statut)
        // HEAD (aperçu du lien) : réponse, mais l'app ne s'ouvre pas
        assertEquals(200, s.repondre("HEAD", "/o/netflix?c=Code123abc").statut)
        assertTrue(ouvertes.isEmpty())
    }

    @Test
    fun uneSeuleOuvertureParScan() {
        val s = serveur()
        s.repondre("GET", "/o/netflix?c=Code123abc")
        t += 1_000
        // Deuxième lecture de la même puce juste après : pas de deuxième ouverture
        assertEquals(200, s.repondre("GET", "/o/netflix?c=Code123abc").statut)
        assertEquals(1, ouvertes.size)
        t += 4_000
        s.repondre("GET", "/o/netflix?c=Code123abc")
        assertEquals(2, ouvertes.size)
    }

    @Test
    fun tropDeCodesFauxBloquentUneMinute() {
        val s = serveur()
        repeat(10) { s.repondre("GET", "/o/netflix?c=essai$it") }
        assertEquals(429, s.repondre("GET", "/o/netflix?c=Code123abc").statut)
        assertTrue(ouvertes.isEmpty())
        t += 61_000
        assertEquals(200, s.repondre("GET", "/o/netflix?c=Code123abc").statut)
    }

    @Test
    fun parLeReseau() {
        val s = serveur()
        assertTrue(s.demarrer())
        val port = s.portEcoute
        try {
            val c = URL("http://127.0.0.1:$port/o/netflix?c=Code123abc").openConnection() as HttpURLConnection
            assertEquals(200, c.responseCode)
            assertTrue(c.contentType.startsWith("text/html"))
            val page = c.inputStream.bufferedReader().readText()
            assertTrue(page, page.contains("Netflix s&#39;ouvre sur la TV"))
            assertTrue("bouton de copie du lien", page.contains("Copier le lien"))
            assertEquals(listOf("com.netflix.ninja"), ouvertes)
            val refus = URL("http://127.0.0.1:$port/o/netflix?c=faux").openConnection() as HttpURLConnection
            assertEquals(403, refus.responseCode)
            val pageRefus = refus.errorStream.bufferedReader().readText()
            assertFalse("le code ne doit jamais apparaître", pageRefus.contains("Code123abc"))
        } finally {
            s.arreter()
        }
        // Arrêté : plus rien n'écoute
        try {
            (URL("http://127.0.0.1:$port/o/netflix?c=Code123abc").openConnection() as HttpURLConnection).apply { connectTimeout = 1_000 }.responseCode
            fail("le serveur écoute encore")
        } catch (e: ConnectException) {
            // attendu
        }
    }

    @Test
    fun adressesEtLiens() {
        val inst: (String) -> Boolean = { it in installees }
        assertEquals("netflix", PucesNfc.adressePour("com.netflix.ninja", soi, inst))
        assertEquals("smarttube", PucesNfc.adressePour("com.liskovsoft.smarttubetv", soi, inst))
        assertEquals("accueil", PucesNfc.adressePour(soi, soi, inst))
        assertEquals("com.exemple.jeu", PucesNfc.adressePour("com.exemple.jeu", soi, inst))
        // Bêta et version normale de SmartTube installées : « smarttube » ouvre la bêta,
        // la version normale garde son nom de paquet
        installees = installees + "com.liskovsoft.smarttubetv.beta"
        val inst2: (String) -> Boolean = { it in installees }
        assertEquals("smarttube", PucesNfc.adressePour("com.liskovsoft.smarttubetv.beta", soi, inst2))
        assertEquals("com.liskovsoft.smarttubetv", PucesNfc.adressePour("com.liskovsoft.smarttubetv", soi, inst2))
        assertEquals("http://192.168.1.19:8642/o/netflix?c=AbC", PucesNfc.lien("192.168.1.19", "netflix", "AbC"))
        assertNull(PucesNfc.resoudre("prime", soi, inst))
    }

    @Test
    fun appsProposeesNetflixEnTete() {
        val apps = listOf(
            AppInfo("com.zapp.tv", "Zapp", isTvApp = true),
            AppInfo("com.canal.android.canal", "CANAL+", isTvApp = true),
            AppInfo.HDMI,
            AppInfo("com.netflix.ninja", "Netflix", isTvApp = true),
            AppInfo("com.arte.tv", "Arte", isTvApp = true),
        )
        val noms = PucesNfc.appsProposees(apps, soi).map { it.label }
        assertEquals(listOf("Netflix", "CANAL+", "Arte", "Zapp", "iBiProjo (accueil)"), noms)
    }

    @Test
    fun activerCreeUnCodeQuiResteEtNouveauCodeLeChange() {
        val c = RuntimeEnvironment.getApplication()
        PucesNfc.charger(c)
        assertFalse("désactivé par défaut", PucesNfc.actif)
        assertEquals("", PucesNfc.code)
        PucesNfc.activer(c, true)
        val code = PucesNfc.code
        assertEquals(20, code.length)
        assertTrue(code.all { it.isLetterOrDigit() })
        // Désactiver puis réactiver : même code (les puces écrites restent bonnes)
        PucesNfc.activer(c, false)
        PucesNfc.activer(c, true)
        assertEquals(code, PucesNfc.code)
        PucesNfc.nouveauCode(c)
        assertNotEquals(code, PucesNfc.code)
        val nouveau = PucesNfc.code
        // Relu après redémarrage
        PucesNfc.oublier()
        PucesNfc.charger(c)
        assertTrue(PucesNfc.actif)
        assertEquals(nouveau, PucesNfc.code)
    }

    @Test
    fun pageSansHtmlInjecte() {
        val p = ServeurPuces.page(ServeurPuces.Reponse(404, "<script>x</script>", "a & b"))
        assertFalse(p.contains("<script>x"))
        assertTrue(p.contains("&lt;script&gt;x&lt;/script&gt;"))
        assertTrue(p.contains("a &amp; b"))
    }
}
