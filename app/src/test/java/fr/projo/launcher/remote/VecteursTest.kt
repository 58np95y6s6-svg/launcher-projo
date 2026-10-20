package fr.projo.launcher.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Vecteurs de test partagés avec la PWA (voir docs/telecommande.md, « Vecteurs de test »).
 *
 * - Clé du téléphone et signature `sigTel` : produites par WebCrypto (Node 22,
 *   `crypto.subtle`, comme la PWA) ; la TV doit les vérifier.
 * - Clé de la TV et signature `sigTv` : `sigTv` produite par CryptoTelecommande.signer
 *   (code de la TV) et vérifiée par WebCrypto ; la PWA doit la vérifier.
 *
 * Les clés privées sont des clés de TEST publiées ici : ne jamais les utiliser ailleurs.
 */
object Vecteurs {
    const val DTLS_TEL = "sha-256 00:01:02:03:04:05:06:07:08:09:0A:0B:0C:0D:0E:0F:10:11:12:13:14:15:16:17:18:19:1A:1B:1C:1D:1E:1F"
    const val DTLS_TV = "sha-256 A0:A1:A2:A3:A4:A5:A6:A7:A8:A9:AA:AB:AC:AD:AE:AF:B0:B1:B2:B3:B4:B5:B6:B7:B8:B9:BA:BB:BC:BD:BE:BF"
    const val NONCE_TEL = "AAECAwQFBgcICQoLDA0ODw"
    const val NONCE_TV = "EBESExQVFhcYGRobHB0eHw"

    const val TEL_SPKI = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfXhOCAc8ijZlkV1xxYqDAvSUM3TX6o-m-9oZiXMShPiGgUY0Y_JAZ2scmZtD3MMs8QEUcMJe9A2lNf6lYoQWRQ"
    const val TEL_PKCS8 = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgZVEnuUk3XJpN13xv7gNZqYEwJ_VDS28nLiULD82hbvuhRANCAAR9eE4IBzyKNmWRXXHFioMC9JQzdNfqj6b72hmJcxKE-IaBRjRj8kBnaxyZm0PcwyzxARRwwl70DaU1_qVihBZF"
    const val TEL_EMPREINTE = "yGw8iBaC6qZywzkIGurx7w"

    const val TV_SPKI = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECGDuYL_37I0zG84bk87LU49uh8u9m8XC06NjyB16vlf19zvpIot7-oQ5LgZ2ZD5QJtQTzx2UiTYXsK1Oano10Q"
    const val TV_PKCS8 = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgoDtJK7SVh1q8DDIZ7xnZztzr86DEFPHU7et2O-MygXihRANCAAQIYO5gv_fsjTMbzhuTzstTj26Hy72bxcLTo2PIHXq-V_X3O-kii3v6hDkuBnZkPlAm1BPPHZSJNhewrU5qejXR"
    const val TV_EMPREINTE = "YPXzIOhZAT92PqkIdIKfBw"

    const val MSG_TEL = "ibiprojo-v1|hello|$DTLS_TEL|$DTLS_TV|$NONCE_TEL"
    const val SIG_TEL = "f3TG45JmH2GRnufVp1fo39Bv6DNHW_kg_qoTBC_3LBBcIx_FCtsR2AUgz5GPLcaa0yz1F6DSdssycYPjwQP7GQ"

    const val MSG_TV = "ibiprojo-v1|hello-tv|$DTLS_TV|$DTLS_TEL|$NONCE_TEL|$NONCE_TV"
    const val SIG_TV = "3Y3_K7efigm7PyFWSNjImjCq1UYNyjGeqr2tObQ0-eK74k9_EYJB0x9BBIv3M0jPNJDEH1WZdZcHREhizKC2Fw"

    fun cleTel() = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64Url.decoder(TEL_PKCS8)!!))
    fun cleTv() = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64Url.decoder(TV_PKCS8)!!))
    fun identiteTv(): IdentiteTv = IdentiteJava(cleTv(), Base64Url.decoder(TV_SPKI)!!)

    /** SDP minimal avec une empreinte « sha-256 AB:… » (format de Chrome). */
    fun sdp(empreinte: String) = "v=0\r\no=- 1 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\na=group:BUNDLE 0\r\n" +
        "m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\nc=IN IP4 0.0.0.0\r\n" +
        "a=fingerprint:$empreinte\r\na=setup:actpass\r\na=mid:0\r\na=sctp-port:5000\r\n"
}

@RunWith(RobolectricTestRunner::class)
class VecteursTest {

    @Test
    fun chainesSignees() {
        assertEquals(Vecteurs.MSG_TEL, CryptoTelecommande.chaineHelloTelephone(Vecteurs.DTLS_TEL, Vecteurs.DTLS_TV, Vecteurs.NONCE_TEL))
        assertEquals(Vecteurs.MSG_TV, CryptoTelecommande.chaineHelloTv(Vecteurs.DTLS_TV, Vecteurs.DTLS_TEL, Vecteurs.NONCE_TEL, Vecteurs.NONCE_TV))
    }

    @Test
    fun empreintesDesCles() {
        assertEquals(Vecteurs.TEL_EMPREINTE, CryptoTelecommande.empreinteCle(Base64Url.decoder(Vecteurs.TEL_SPKI)!!))
        assertEquals(Vecteurs.TV_EMPREINTE, CryptoTelecommande.empreinteCle(Base64Url.decoder(Vecteurs.TV_SPKI)!!))
        assertEquals(22, Vecteurs.TV_EMPREINTE.length)
    }

    /** Signature WebCrypto du téléphone : vérifiée par la TV. */
    @Test
    fun signatureWebCryptoVerifieeParLaTv() {
        val cle = CryptoTelecommande.clePublique(Base64Url.decoder(Vecteurs.TEL_SPKI)!!)!!
        val sig = Base64Url.decoder(Vecteurs.SIG_TEL)!!
        assertEquals(64, sig.size)
        assertTrue(CryptoTelecommande.verifier(cle, Vecteurs.MSG_TEL, sig))
        // Message, signature ou clé modifiés : refus
        assertFalse(CryptoTelecommande.verifier(cle, Vecteurs.MSG_TEL.replace("|hello|", "|hello-tv|"), sig))
        assertFalse(CryptoTelecommande.verifier(cle, Vecteurs.MSG_TEL, sig.copyOf().also { it[10] = (it[10] + 1).toByte() }))
        val autre = CryptoTelecommande.clePublique(Base64Url.decoder(Vecteurs.TV_SPKI)!!)!!
        assertFalse(CryptoTelecommande.verifier(autre, Vecteurs.MSG_TEL, sig))
        // Format DER refusé : seul le format brut r‖s est valable
        assertFalse(CryptoTelecommande.verifier(cle, Vecteurs.MSG_TEL, CryptoTelecommande.brutVersDer(sig)!!))
    }

    /** Signature produite par la TV (vérifiée par WebCrypto) : vérifiable par la TV aussi. */
    @Test
    fun signatureDeLaTv() {
        val cle = CryptoTelecommande.clePublique(Base64Url.decoder(Vecteurs.TV_SPKI)!!)!!
        assertTrue(CryptoTelecommande.verifier(cle, Vecteurs.MSG_TV, Base64Url.decoder(Vecteurs.SIG_TV)!!))
        // Nouvelle signature de la TV : 64 octets bruts, vérifiable
        val sig = Vecteurs.identiteTv().signer(Vecteurs.MSG_TV)
        assertEquals(64, sig.size)
        assertTrue(CryptoTelecommande.verifier(cle, Vecteurs.MSG_TV, sig))
    }

    @Test
    fun conversionsDer() {
        repeat(50) {
            val sig = Vecteurs.identiteTv().signer("message $it")
            assertArrayEquals(sig, CryptoTelecommande.derVersBrut(CryptoTelecommande.brutVersDer(sig)!!))
        }
        assertNull(CryptoTelecommande.brutVersDer(ByteArray(63)))
        assertNull(CryptoTelecommande.brutVersDer(ByteArray(64)))
    }

    @Test
    fun base64url() {
        for (n in 0..40) {
            val b = ByteArray(n) { (it * 37 + 5).toByte() }
            val s = Base64Url.encoder(b)
            assertFalse(s.contains('=') || s.contains('+') || s.contains('/'))
            assertArrayEquals(b, Base64Url.decoder(s))
        }
        assertEquals("AAECAwQFBgcICQoLDA0ODw", Base64Url.encoder(ByteArray(16) { it.toByte() }))
        assertArrayEquals(byteArrayOf(-5, -1), Base64Url.decoder("-_8"))
        assertArrayEquals(byteArrayOf(-5, -1), Base64Url.decoder("+/8="))
        assertNull(Base64Url.decoder("a"))
        assertNull(Base64Url.decoder("ab.c"))
    }

    @Test
    fun empreinteDtlsDuSdp() {
        assertEquals(Vecteurs.DTLS_TV, CryptoTelecommande.empreinteDtls(Vecteurs.sdp(Vecteurs.DTLS_TV)))
        // Algorithme en majuscules, hexadécimal en minuscules : normalisés
        assertEquals(Vecteurs.DTLS_TV, CryptoTelecommande.empreinteDtls(Vecteurs.sdp(Vecteurs.DTLS_TV.lowercase().replace("sha-256", "SHA-256"))))
        // La ligne sha-256 passe avant une autre ; sans elle, la première ligne
        val sha1 = "sha-1 01:02:03:04:05:06:07:08:09:0A:0B:0C:0D:0E:0F:10:11:12:13:14"
        assertEquals(Vecteurs.DTLS_TV, CryptoTelecommande.empreinteDtls(Vecteurs.sdp(sha1) + "a=fingerprint:" + Vecteurs.DTLS_TV + "\r\n"))
        assertEquals(sha1, CryptoTelecommande.empreinteDtls(Vecteurs.sdp(sha1)))
        assertNull(CryptoTelecommande.empreinteDtls("v=0\r\n"))
    }

    @Test
    fun clesInvalides() {
        assertNull(CryptoTelecommande.clePublique(ByteArray(10)))
        assertNotNull(CryptoTelecommande.clePublique(Base64Url.decoder(Vecteurs.TEL_SPKI)!!))
    }
}
