package fr.projo.launcher.puces

import fr.projo.launcher.liens.EnvoiTv
import fr.projo.launcher.liens.LienTv
import fr.projo.launcher.liens.ResultatLien
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Raccourci iOS « Envoyer sur la TV » : POST / GET /envoyer?c=<code> sur le serveur des puces NFC,
 * avec son propre code ; statuts et textes de la spécification ; puces NFC inchangées.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RaccourciTest {

    private val code = "K7Q2M9XD"
    private var actif = true
    private var pucesActives = true
    private var t = 0L
    private val envoyes = mutableListOf<LienTv>()
    private val journaux = mutableListOf<String>()
    private val ouvertes = mutableListOf<String>()

    /** Réponse de la TV au prochain lien (null : pas de réponse à temps). */
    private var resultat: ResultatLien? = ResultatLien.Ouvert("VYPN")

    private fun serveur() = ServeurPuces(
        port = 0,
        code = { "CodeNfc123" },
        resoudre = { nom -> if (nom == "netflix") "com.netflix.ninja" else null },
        libelle = { "Netflix" },
        ouvrir = { ouvertes += it },
        horloge = { t },
        pucesActives = { pucesActives },
        raccourci = RouteRaccourci(
            actif = { actif },
            code = { code },
            nomTv = { "Salon" },
            envoyer = { lien -> envoyes += lien; resultat },
            journal = { journaux += it },
        ),
    )

    @Before
    fun avant() {
        PucesNfc.oublier()
        EnvoiTv.oublier()
    }

    @After
    fun apres() {
        PucesNfc.oublier()
        EnvoiTv.oublier()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Test
    fun getEtPostAvecLeBonCode() {
        val s = serveur()
        val r = s.repondre("GET", "/envoyer?c=$code&url=" + enc("https://www.arte.tv/fr/?a=1&b=2"))
        assertEquals(200, r.statut)
        assertTrue(r.texte)
        assertEquals("Envoyé sur Salon · ouverture dans VYPN", r.message)
        assertEquals("https://www.arte.tv/fr/?a=1&b=2", envoyes.single().url)

        // POST, corps JSON {url, titre}
        val json = s.repondre("POST", "/envoyer?c=$code", """{"url":"https://youtu.be/abc","titre":"Une vidéo"}""")
        assertEquals(200, json.statut)
        assertEquals("https://youtu.be/abc", envoyes.last().url)
        assertEquals("Une vidéo", envoyes.last().titre)
        // POST, corps texte avec l'URL seule (et espaces autour)
        assertEquals(200, s.repondre("POST", "/envoyer?c=$code", "  https://exemple.fr/page \n").statut)
        assertEquals("https://exemple.fr/page", envoyes.last().url)
        // Formulaire url=…&titre=…
        assertEquals(200, s.repondre("POST", "/envoyer/?c=$code", "url=" + enc("https://exemple.fr/f") + "&titre=Film").statut)
        assertEquals("Film", envoyes.last().titre)
        assertTrue(journaux.contains("lien envoyé (raccourci)"))
    }

    @Test
    fun statutsDeLaSpecification() {
        val s = serveur()
        resultat = ResultatLien.Question
        val q = s.repondre("GET", "/envoyer?c=$code&url=" + enc("https://arte.tv"))
        assertEquals(202, q.statut)
        assertEquals("Le VPN de la TV est coupé : réponds sur la TV", q.message)
        resultat = ResultatLien.Refuse(ResultatLien.AUCUNE_APPLI)
        val a = s.repondre("GET", "/envoyer?c=$code&url=" + enc("https://arte.tv"))
        assertEquals(422, a.statut)
        assertEquals("Aucune appli de la TV ne sait ouvrir ce lien", a.message)
        resultat = null
        assertEquals(500, s.repondre("GET", "/envoyer?c=$code&url=" + enc("https://arte.tv")).statut)

        // Lien invalide : 400, rien n'est envoyé
        val avant = envoyes.size
        for (corps in listOf("javascript:alert(1)", "file:///sdcard/x", "", "{\"url\": 3}", "{pas du json", "arte.tv")) {
            val r = s.repondre("POST", "/envoyer?c=$code", corps)
            assertEquals(corps, 400, r.statut)
            assertEquals("Lien invalide", r.message)
        }
        assertEquals(400, s.repondre("POST", "/envoyer?c=$code", null).statut)
        assertEquals(400, s.repondre("GET", "/envoyer?c=$code").statut)
        assertEquals(avant, envoyes.size)
        assertTrue(journaux.contains("lien refusé invalide"))
    }

    @Test
    fun codeIncorrectEtBlocage() {
        val s = serveur()
        for (c in listOf("", "faux", "k7q2m9xd", "CodeNfc123")) {
            val r = s.repondre("GET", "/envoyer?c=$c&url=" + enc("https://arte.tv"))
            assertEquals(c, 403, r.statut)
            assertEquals("Code incorrect", r.message)
        }
        assertEquals(403, s.repondre("GET", "/envoyer?url=" + enc("https://arte.tv")).statut)
        assertTrue(envoyes.isEmpty())
        // 10 codes faux dans la minute : tout est refusé, même le bon code
        repeat(5) { s.repondre("POST", "/envoyer?c=essai$it", "https://arte.tv") }
        val bloque = s.repondre("GET", "/envoyer?c=$code&url=" + enc("https://arte.tv"))
        assertEquals(429, bloque.statut)
        assertTrue(envoyes.isEmpty())
        // Les puces NFC ont leur propre compte
        assertEquals(200, s.repondre("GET", "/o/netflix?c=CodeNfc123").statut)
        t += 61_000
        assertEquals(200, s.repondre("GET", "/envoyer?c=$code&url=" + enc("https://arte.tv")).statut)
        assertTrue(journaux.none { it.contains(code) || it.contains("arte") })
    }

    @Test
    fun raccourciDesactiveOuAutreAdresse() {
        val s = serveur()
        actif = false
        for (m in listOf("GET", "POST")) {
            val r = s.repondre(m, "/envoyer?c=$code&url=" + enc("https://arte.tv"), "https://arte.tv")
            assertEquals(404, r.statut)
            assertEquals("Envoi depuis l'iPhone désactivé sur la TV", r.message)
        }
        assertTrue(envoyes.isEmpty())
        // Toute autre adresse : même réponse
        for (cible in listOf("/", "/favicon.ico", "/envoyer2?c=$code")) {
            val r = s.repondre("GET", cible)
            assertEquals(cible, 404, r.statut)
            assertEquals("Envoi depuis l'iPhone désactivé sur la TV", r.message)
        }
        // Autres méthodes sur /envoyer
        actif = true
        assertEquals(405, s.repondre("PUT", "/envoyer?c=$code").statut)
        assertEquals(405, s.repondre("HEAD", "/envoyer?c=$code&url=" + enc("https://arte.tv")).statut)
        assertTrue(envoyes.isEmpty())
    }

    @Test
    fun pucesNfcInchangeesEtDesactivables() {
        val s = serveur()
        assertEquals(200, s.repondre("GET", "/o/netflix?c=CodeNfc123").statut)
        assertEquals(listOf("com.netflix.ninja"), ouvertes)
        // Le code du raccourci n'ouvre pas une puce, et inversement
        assertEquals(403, s.repondre("GET", "/o/netflix?c=$code").statut)
        // Serveur démarré pour le raccourci seulement : les puces ne marchent pas
        pucesActives = false
        val r = s.repondre("GET", "/o/netflix?c=CodeNfc123")
        assertEquals(404, r.statut)
        assertFalse(r.texte)
        assertEquals(1, ouvertes.size)
    }

    @Test
    fun parLeReseau() {
        val s = serveur()
        assertTrue(s.demarrer())
        val port = s.portEcoute
        try {
            // POST JSON, comme le raccourci iOS
            val c = URL("http://127.0.0.1:$port/envoyer?c=$code").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write("""{"url":"https://www.arte.tv/fr/","titre":"Les grands documentaires"}""".toByteArray()) }
            assertEquals(200, c.responseCode)
            assertTrue(c.contentType, c.contentType.startsWith("text/plain"))
            assertEquals("Envoyé sur Salon · ouverture dans VYPN", c.inputStream.bufferedReader(Charsets.UTF_8).readText())
            assertEquals("Les grands documentaires", envoyes.single().titre)

            // Envoi par morceaux (sans Content-Length)
            val m = URL("http://127.0.0.1:$port/envoyer?c=$code").openConnection() as HttpURLConnection
            m.requestMethod = "POST"
            m.doOutput = true
            m.setChunkedStreamingMode(8)
            m.outputStream.use { it.write("https://youtu.be/abcdefghijkl".toByteArray()) }
            assertEquals(200, m.responseCode)
            assertEquals("https://youtu.be/abcdefghijkl", envoyes.last().url)

            // GET et code faux
            val g = URL("http://127.0.0.1:$port/envoyer?c=FAUX&url=" + enc("https://arte.tv")).openConnection() as HttpURLConnection
            assertEquals(403, g.responseCode)
            assertEquals("Code incorrect", g.errorStream.bufferedReader(Charsets.UTF_8).readText())
            assertEquals(2, envoyes.size)
        } finally {
            s.arreter()
        }
    }

    @Test
    fun codeDuRaccourci() {
        val c = RuntimeEnvironment.getApplication()
        EnvoiTv.charger(c)
        assertFalse("désactivé par défaut", EnvoiTv.raccourci)
        assertTrue("seulement avec le VPN par défaut", EnvoiTv.seulementVpn)
        assertEquals("auto", EnvoiTv.pages)
        assertEquals("", EnvoiTv.code)
        try {
            EnvoiTv.ecrireRaccourci(c, true)
            val code = EnvoiTv.code
            assertEquals(8, code.length)
            assertTrue(code, code.all { it in EnvoiTv.ALPHABET_CODE })
            assertFalse(code.any { it in "IO01" })
            // Désactiver puis réactiver : même code ; nouveau code : différent
            EnvoiTv.ecrireRaccourci(c, false)
            EnvoiTv.ecrireRaccourci(c, true)
            assertEquals(code, EnvoiTv.code)
            EnvoiTv.nouveauCode(c)
            assertNotEquals(code, EnvoiTv.code)
            val nouveau = EnvoiTv.code
            EnvoiTv.ecrireSeulementVpn(c, false)
            EnvoiTv.ecrirePages(c, "com.amazon.cloud9")
            // Relu après redémarrage
            EnvoiTv.oublier()
            EnvoiTv.charger(c)
            assertTrue(EnvoiTv.raccourci)
            assertEquals(nouveau, EnvoiTv.code)
            assertFalse(EnvoiTv.seulementVpn)
            assertEquals("com.amazon.cloud9", EnvoiTv.pages)
            assertEquals("http://192.168.1.42:8642/envoyer?c=$nouveau", EnvoiTv.adresseRaccourci("192.168.1.42", nouveau))
        } finally {
            EnvoiTv.ecrireRaccourci(c, false)
            EnvoiTv.ecrireSeulementVpn(c, true)
            EnvoiTv.ecrirePages(c, "auto")
        }
    }
}
