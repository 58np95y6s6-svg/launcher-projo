package fr.projo.launcher.remote

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Vecteurs de la PWA (copie de remote-pwa-dev/tests/vecteurs.json, branche claude/wf-remote-pwa) :
 * la TV doit lire les mêmes empreintes, construire les mêmes textes signés et vérifier les
 * mêmes signatures (format brut r‖s ; DER refusé, mais converti à l'identique).
 */
@RunWith(RobolectricTestRunner::class)
class VecteursPwaTest {

    private val v = JSONObject(
        javaClass.classLoader!!.getResourceAsStream("telecommande/vecteurs-pwa.json")!!.readBytes().toString(Charsets.UTF_8),
    )
    private fun octets(cle: String) = Base64Url.decoder(v.getString(cle))!!

    @Test
    fun empreintesDtlsDesSdp() {
        assertEquals(v.getString("empreinteDtlsTel"), CryptoTelecommande.empreinteDtls(v.getString("sdpTel")))
        assertEquals(v.getString("empreinteDtlsTV"), CryptoTelecommande.empreinteDtls(v.getString("sdpTV")))
    }

    @Test
    fun empreintesDesCles() {
        assertEquals(v.getString("empreinteCleTel"), CryptoTelecommande.empreinteCle(octets("cleTel")))
        assertEquals(v.getString("empreinteCleTV"), CryptoTelecommande.empreinteCle(octets("cleTV")))
    }

    @Test
    fun textesSignes() {
        val tel = v.getString("empreinteDtlsTel")
        val tv = v.getString("empreinteDtlsTV")
        assertEquals(v.getString("texteHelloTel"), CryptoTelecommande.chaineHelloTelephone(tel, tv, v.getString("nonceTel")))
        assertEquals(v.getString("texteHelloTV"), CryptoTelecommande.chaineHelloTv(tv, tel, v.getString("nonceTel"), v.getString("nonceTV")))
        assertEquals(16, octets("nonceTel").size)
        assertEquals(16, octets("nonceTV").size)
    }

    @Test
    fun signaturesBrutesVerifiees() {
        val cleTel = CryptoTelecommande.clePublique(octets("cleTel"))!!
        val cleTv = CryptoTelecommande.clePublique(octets("cleTV"))!!
        assertTrue(CryptoTelecommande.verifier(cleTel, v.getString("texteHelloTel"), octets("sigHelloTelP1363")))
        assertTrue(CryptoTelecommande.verifier(cleTv, v.getString("texteHelloTV"), octets("sigHelloTVP1363")))
        assertFalse(CryptoTelecommande.verifier(cleTv, v.getString("texteHelloTel"), octets("sigHelloTelP1363")))
        // Le format DER est refusé sur le canal…
        assertFalse(CryptoTelecommande.verifier(cleTel, v.getString("texteHelloTel"), octets("sigHelloTelDer")))
        // … mais les conversions de la TV donnent exactement les mêmes octets que la PWA
        assertArrayEquals(octets("sigHelloTelDer"), CryptoTelecommande.brutVersDer(octets("sigHelloTelP1363")))
        assertArrayEquals(octets("sigHelloTVP1363"), CryptoTelecommande.derVersBrut(octets("sigHelloTVDer")))
        assertEquals(86, v.getString("sigHelloTelP1363").length)
    }

    /** Session complète avec les vecteurs : la TV accepte le hello signé par WebCrypto. */
    @Test
    fun helloDeLaPwaAccepte() {
        val dossier = kotlin.io.path.createTempDirectory().toFile()
        val hote = HoteFactice(dossier)
        hote.registre.ajouter(TelephoneAppaire("tel-0123456789abcdef0123456789abcdef", "PWA", v.getString("cleTel"), 1L))
        val envoyes = mutableListOf<JSONObject>()
        val s = SessionTelecommande(
            hote,
            CryptoTelecommande.empreinteDtls(v.getString("sdpTel")),
            CryptoTelecommande.empreinteDtls(v.getString("sdpTV")),
            envoyer = { envoyes += JSONObject(it) }, fermer = {},
        )
        s.recevoir(
            JSONObject().put("v", 1).put("t", "hello").put("appareil", "tel-0123456789abcdef0123456789abcdef")
                .put("nonce", v.getString("nonceTel")).put("sig", v.getString("sigHelloTelP1363")).toString(),
        )
        assertTrue(s.authentifiee)
        assertEquals("hello", envoyes.single().getString("t"))
        assertTrue(envoyes.single().has("cle"))
    }

    @Test
    fun fragmentEtCode() {
        val f = v.getString("fragment").removePrefix("#pair=").split('.')
        assertEquals(3, f.size)
        assertTrue(f[0].matches(Regex("ibiprojo-[0-9a-f]{32}")))
        assertEquals(16, Base64Url.decoder(f[1])!!.size)
        assertEquals(v.getString("empreinteCleTV"), f[2])
        val code = v.getString("code").replace("-", "")
        assertTrue(code.all { it in RemoteConfig.ALPHABET_CODE })
        assertEquals(v.getString("codeIdTV"), RemoteConfig.PREFIXE_CODE + code.take(4))
        assertEquals(v.getString("codeSecret"), code.substring(4))
    }
}
