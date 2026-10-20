package fr.projo.launcher.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Secret d'appairage : expiration, usage unique, limite de tentatives, code de secours. */
@RunWith(RobolectricTestRunner::class)
class AppairageTest {

    private var temps = 1_000_000L
    private val appairage = GestionAppairage(horloge = { temps })
    private val empreinteTv = Vecteurs.TV_EMPREINTE

    @Test
    fun formatDuSecretEtDuCode() {
        val s = appairage.demarrer()
        assertEquals(22, s.secret.length)
        assertEquals(16, Base64Url.decoder(s.secret)!!.size)
        assertEquals(8, s.code.length)
        assertTrue(s.code.all { it in RemoteConfig.ALPHABET_CODE })
        assertTrue(s.codeAffiche.matches(Regex("[2-9A-Z]{4}-[2-9A-Z]{4}")))
        assertEquals("ibiprojo-c-" + s.code.take(4), s.idTemporaire)
        assertEquals(31, RemoteConfig.ALPHABET_CODE.length)
        // Le secret n'apparaît jamais dans toString (journaux)
        assertTrue(!s.toString().contains(s.secret) && !s.toString().contains(s.code))
        // Deux secrets successifs diffèrent
        assertNotEquals(s.secret, appairage.demarrer().secret)
    }

    @Test
    fun secretValable() {
        val s = appairage.demarrer()
        assertNull(appairage.verifier(s.secret, empreinteTv, empreinteTv))
        // Sans empreinte attendue (code de secours) : accepté aussi
        assertNull(appairage.verifier(s.secret, null, empreinteTv))
    }

    @Test
    fun expirationApres5Minutes() {
        val s = appairage.demarrer()
        temps += RemoteConfig.DUREE_SECRET_MS - 1
        assertNull(appairage.verifier(s.secret, empreinteTv, empreinteTv))
        temps += 1
        assertEquals(RaisonRefus.EXPIRE, appairage.verifier(s.secret, empreinteTv, empreinteTv))
        assertNull(appairage.actif())
    }

    @Test
    fun usageUnique() {
        val s = appairage.demarrer()
        assertNull(appairage.verifier(s.secret, empreinteTv, empreinteTv))
        appairage.consommer()
        assertEquals(RaisonRefus.EXPIRE, appairage.verifier(s.secret, empreinteTv, empreinteTv))
        assertEquals(RaisonRefus.EXPIRE, appairage.verifier(s.secretCourt, null, empreinteTv))
    }

    @Test
    fun sansAppairageEnCours() {
        assertEquals(RaisonRefus.EXPIRE, appairage.verifier("n'importe quoi", null, empreinteTv))
    }

    @Test
    fun mauvaiseEmpreinteDeLaTv() {
        val s = appairage.demarrer()
        assertEquals(RaisonRefus.INVALIDE, appairage.verifier(s.secret, Vecteurs.TEL_EMPREINTE, empreinteTv))
    }

    @Test
    fun codeDeSecours() {
        val s = appairage.demarrer()
        assertNull(appairage.verifier(s.secretCourt, null, empreinteTv))
        assertNull(appairage.verifier(s.secretCourt.lowercase(), null, empreinteTv))
        assertNull(appairage.verifier(s.codeAffiche, null, empreinteTv))
        assertNull(appairage.verifier(s.code.lowercase(), null, empreinteTv))
        assertEquals(RaisonRefus.INVALIDE, appairage.verifier(s.code.take(4), null, empreinteTv))
    }

    /** 5 échecs en 1 minute : le 5e répond trop_d_essais, puis blocage d'1 minute, même avec le bon secret. */
    @Test
    fun limiteDeTentatives() {
        val s = appairage.demarrer()
        repeat(4) {
            assertEquals(RaisonRefus.INVALIDE, appairage.verifier("AAAAAAAAAAAAAAAAAAAAAA", null, empreinteTv))
            temps += 1_000
        }
        assertEquals(RaisonRefus.TROP_D_ESSAIS, appairage.verifier("AAAAAAAAAAAAAAAAAAAAAA", null, empreinteTv))
        assertTrue(appairage.estBloque())
        temps += 30_000
        assertEquals(RaisonRefus.TROP_D_ESSAIS, appairage.verifier(s.secret, empreinteTv, empreinteTv))
        temps += 30_000
        // Blocage terminé : le bon secret passe (toujours dans les 5 minutes)
        assertNull(appairage.verifier(s.secret, empreinteTv, empreinteTv))
    }

    /** Des échecs espacés de plus d'une minute ne bloquent pas. */
    @Test
    fun fenetreGlissante() {
        val s = appairage.demarrer()
        repeat(8) {
            assertEquals(RaisonRefus.INVALIDE, appairage.verifier("ZZZZ", null, empreinteTv))
            temps += 20_000
        }
        assertNull(appairage.verifier(s.secret, null, empreinteTv))
    }

    @Test
    fun nouveauSecretRemplaceLAncien() {
        val ancien = appairage.demarrer()
        val nouveau = appairage.demarrer()
        assertEquals(RaisonRefus.INVALIDE, appairage.verifier(ancien.secret, null, empreinteTv))
        assertNull(appairage.verifier(nouveau.secret, null, empreinteTv))
        appairage.arreter()
        assertNotNull(nouveau)
        assertEquals(RaisonRefus.EXPIRE, appairage.verifier(nouveau.secret, null, empreinteTv))
    }
}
