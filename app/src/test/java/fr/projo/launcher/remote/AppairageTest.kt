package fr.projo.launcher.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // L'ancien n'est plus valable : « expiré » (QR code scanné juste avant), pas une tentative
        assertEquals(RaisonRefus.EXPIRE, appairage.verifier(ancien.secret, null, empreinteTv))
        assertNull(appairage.verifier(nouveau.secret, null, empreinteTv))
        appairage.arreter()
        assertNotNull(nouveau)
        assertEquals(RaisonRefus.EXPIRE, appairage.verifier(nouveau.secret, null, empreinteTv))
    }

    // ------------------------------------------------------------------ renouvellement (QR code permanent)

    /** Horloge simulée : rien avant l'échéance, un nouveau secret à l'échéance, l'ancien est refusé. */
    @Test
    fun renouvellementALEcheance() {
        val s1 = appairage.renouvelerSiEchu()
        assertNotNull("aucun secret : on en crée un", s1)
        assertNull("secret encore valable", appairage.renouvelerSiEchu())
        temps += RemoteConfig.DUREE_SECRET_MS - 1
        assertNull("1 ms avant l'échéance", appairage.renouvelerSiEchu())
        temps += 1
        val s2 = appairage.renouvelerSiEchu()
        assertNotNull("à l'échéance", s2)
        assertNotEquals(s1!!.secret, s2!!.secret)
        assertNotEquals(s1.code, s2.code)
        assertEquals(temps + RemoteConfig.DUREE_SECRET_MS, s2.expire)
        assertEquals(RaisonRefus.EXPIRE, appairage.verifier(s1.secret, empreinteTv, empreinteTv))
        assertNull(appairage.verifier(s2.secret, empreinteTv, empreinteTv))
        // Consommé (téléphone appairé) : renouvelé tout de suite, sans attendre l'échéance
        appairage.consommer()
        assertNotNull(appairage.renouvelerSiEchu())
    }

    /** Une demande attend « Autoriser / Refuser » : pas de renouvellement, même après l'échéance. */
    @Test
    fun pasDeRenouvellementPendantUneConfirmation() {
        val s1 = appairage.renouvelerSiEchu()!!
        assertNull(appairage.verifier(s1.secret, empreinteTv, empreinteTv))
        val reserve = appairage.reserver()
        assertTrue(reserve === s1)
        assertTrue(appairage.confirmationEnCours)
        temps += RemoteConfig.DUREE_SECRET_MS + 60_000
        assertNull("confirmation en cours : on attend sa fin", appairage.renouvelerSiEchu())
        assertNull(appairage.actif())
        // Refus : la confirmation est finie, le renouvellement reprend
        appairage.liberer(reserve, accepte = false)
        assertFalse(appairage.confirmationEnCours)
        val s2 = appairage.renouvelerSiEchu()
        assertNotNull(s2)
        assertNull(appairage.verifier(s2!!.secret, empreinteTv, empreinteTv))
    }

    /** Accepter une demande ne consomme que SON secret : un secret créé entre-temps reste valable. */
    @Test
    fun accepterNeConsommeQueSonSecret() {
        val s1 = appairage.demarrer()
        assertNull(appairage.verifier(s1.secret, empreinteTv, empreinteTv))
        val reserve = appairage.reserver()
        val s2 = appairage.demarrer() // « Nouveau code » pendant la confirmation
        appairage.liberer(reserve, accepte = true)
        assertTrue(appairage.actif() === s2)
        assertNull(appairage.verifier(s2.secret, empreinteTv, empreinteTv))
        // Sans secret créé entre-temps : le secret de la demande est consommé (usage unique)
        val r2 = appairage.reserver()
        appairage.liberer(r2, accepte = true)
        assertNull(appairage.actif())
        assertEquals(RaisonRefus.EXPIRE, appairage.verifier(s2.secret, empreinteTv, empreinteTv))
    }

    /** QR code scanné juste avant le renouvellement : « expiré », et ça ne compte pas comme un essai. */
    @Test
    fun ancienSecretNeComptePasCommeUnEssai() {
        val s1 = appairage.demarrer()
        temps += RemoteConfig.DUREE_SECRET_MS
        val s2 = appairage.renouvelerSiEchu()!!
        repeat(RemoteConfig.ESSAIS_MAX + 2) {
            assertEquals(RaisonRefus.EXPIRE, appairage.verifier(s1.secret, empreinteTv, empreinteTv))
            assertEquals(RaisonRefus.EXPIRE, appairage.verifier(s1.codeAffiche, null, empreinteTv))
        }
        assertFalse(appairage.estBloque())
        assertNull(appairage.verifier(s2.secretCourt, null, empreinteTv))
    }

    /** Renouveler ne remet pas à zéro la limite de tentatives. */
    @Test
    fun renouvellementGardeLeBlocage() {
        appairage.demarrer()
        temps += RemoteConfig.DUREE_SECRET_MS - 10_000
        repeat(RemoteConfig.ESSAIS_MAX) { appairage.verifier("AAAAAAAAAAAAAAAAAAAAAA", null, empreinteTv) }
        assertTrue(appairage.estBloque())
        temps += 10_000 // échéance du secret, 10 s après le début du blocage
        val s2 = appairage.renouvelerSiEchu()!!
        assertEquals(RaisonRefus.TROP_D_ESSAIS, appairage.verifier(s2.secret, empreinteTv, empreinteTv))
        temps += RemoteConfig.BLOCAGE_MS
        assertNull(appairage.verifier(s2.secret, empreinteTv, empreinteTv))
    }
}
