package fr.projo.launcher.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Injecteur qui note les commandes reçues. */
class InjecteurFactice : Injecteur {
    val commandes = mutableListOf<String>()
    override fun touche(k: String) { commandes += "key:$k" }
    override fun texte(s: String, curseur: Int?) { commandes += "text:$s:$curseur" }
    override fun valider() { commandes += "submit" }
}

/** Hôte de test : identité TV des vecteurs, registre dans un dossier temporaire, horloge à la main. */
class HoteFactice(dossier: File) : HoteSession {
    var temps = 1_700_000_000_000L
    override val identite = Vecteurs.identiteTv()
    override val registre = RegistreTelecommande(File(dossier, "telecommande.json"))
    override val appairage = GestionAppairage(horloge = { temps })
    override val injecteur = InjecteurFactice()
    override val nomTv = "Projecteur du salon"
    override val versionApp = "test-1"
    override fun maintenant() = temps

    /** Demandes de confirmation affichées (nom → réponse à donner). */
    val demandes = mutableListOf<Pair<String, (Boolean) -> Unit>>()
    var occupe = false
    val authentifiees = mutableListOf<SessionTelecommande>()

    override fun demanderConfirmation(session: SessionTelecommande, nom: String, reponse: (Boolean) -> Unit): Boolean {
        if (occupe) return false
        demandes += nom to reponse
        return true
    }

    override fun surAuthentifiee(session: SessionTelecommande) { authentifiees += session }
}

@RunWith(RobolectricTestRunner::class)
class SessionTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private lateinit var hote: HoteFactice
    private val envoyes = mutableListOf<JSONObject>()
    private var fermee = false

    private fun session(dtlsTel: String? = Vecteurs.DTLS_TEL, dtlsTv: String? = Vecteurs.DTLS_TV): SessionTelecommande {
        if (!::hote.isInitialized) hote = HoteFactice(dossier.root)
        envoyes.clear()
        fermee = false
        return SessionTelecommande(hote, dtlsTel, dtlsTv, envoyer = { envoyes += JSONObject(it) }, fermer = { fermee = true })
    }

    private fun hello(appareil: String = "tel-1", cle: String? = null, v: Int = 1, sig: String = Vecteurs.SIG_TEL, nom: String? = null) =
        JSONObject().put("v", v).put("t", "hello").put("appareil", appareil).put("nonce", Vecteurs.NONCE_TEL).put("sig", sig)
            .apply { if (cle != null) put("cle", cle); if (nom != null) put("nom", nom) }.toString()

    private fun msg(t: String, vararg champs: Pair<String, Any>) =
        JSONObject().put("v", 1).put("t", t).apply { champs.forEach { put(it.first, it.second) } }.toString()

    private fun appairer(id: String = "tel-1") {
        if (!::hote.isInitialized) hote = HoteFactice(dossier.root)
        hote.registre.ajouter(TelephoneAppaire(id, "Téléphone de Marie", Vecteurs.TEL_SPKI, 1L))
    }

    private fun toutesLesCommandes(s: SessionTelecommande) {
        s.recevoir(msg("key", "k" to "up"))
        s.recevoir(msg("text", "s" to "bonjour"))
        s.recevoir(msg("submit"))
        s.recevoir(msg("ping", "n" to 3))
    }

    // ------------------------------------------------------------------ authentification

    @Test
    fun helloDUnTelephoneAppaire() {
        appairer()
        val s = session()
        s.recevoir(hello())
        assertTrue(s.authentifiee)
        assertEquals(1, envoyes.size)
        val r = envoyes[0]
        assertEquals("hello", r.getString("t"))
        assertEquals(1, r.getInt("v"))
        assertEquals("Projecteur du salon", r.getString("nomTV"))
        assertEquals("test-1", r.getString("versionApp"))
        assertEquals(hote.registre.idTv(), r.getString("idTV"))
        assertEquals(Vecteurs.TV_SPKI, r.getString("cle"))
        // Signature de la TV : liée aux deux empreintes DTLS et aux deux nonces
        val cleTv = CryptoTelecommande.clePublique(Base64Url.decoder(Vecteurs.TV_SPKI)!!)!!
        val chaine = CryptoTelecommande.chaineHelloTv(Vecteurs.DTLS_TV, Vecteurs.DTLS_TEL, Vecteurs.NONCE_TEL, r.getString("nonce"))
        assertTrue(CryptoTelecommande.verifier(cleTv, chaine, Base64Url.decoder(r.getString("sig"))!!))
        assertEquals(16, Base64Url.decoder(r.getString("nonce"))!!.size)

        envoyes.clear()
        toutesLesCommandes(s)
        assertEquals(listOf("key:up", "text:bonjour:null", "submit"), hote.injecteur.commandes)
        assertEquals("pong", envoyes.single().getString("t"))
        assertEquals(3, envoyes.single().getInt("n"))
        assertFalse(fermee)
    }

    @Test
    fun commandesRefuseesAvantAuthentification() {
        appairer()
        val s = session()
        toutesLesCommandes(s)
        s.recevoir(msg("pair_request", "secret" to "x", "nom" to "x", "cle" to Vecteurs.TEL_SPKI))
        assertTrue(hote.injecteur.commandes.isEmpty())
        assertTrue(envoyes.isEmpty())
        assertFalse(s.authentifiee)
    }

    @Test
    fun signatureFausseFermeLeCanal() {
        appairer()
        val s = session()
        val sig = Base64Url.decoder(Vecteurs.SIG_TEL)!!.also { it[0] = (it[0] + 1).toByte() }
        s.recevoir(hello(sig = Base64Url.encoder(sig)))
        assertTrue(fermee)
        assertTrue(envoyes.isEmpty()) // sans détail
        toutesLesCommandes(s)
        assertTrue(hote.injecteur.commandes.isEmpty())
    }

    /** Signature valable mais pour d'autres empreintes DTLS (homme du milieu) : refus. */
    @Test
    fun signatureLieeAuxEmpreintesDtls() {
        appairer()
        val s = session(dtlsTv = Vecteurs.DTLS_TEL)
        s.recevoir(hello())
        assertTrue(fermee)
        assertFalse(s.authentifiee)
    }

    @Test
    fun sansEmpreinteDtlsRienNePasse() {
        appairer()
        val s = session(dtlsTel = null)
        s.recevoir(hello())
        assertTrue(fermee)
    }

    /** Clé différente de celle enregistrée pour cet appareil : refus. */
    @Test
    fun cleDifferenteDeLaCleEnregistree() {
        appairer()
        val s = session()
        s.recevoir(hello(cle = Vecteurs.TV_SPKI))
        assertTrue(fermee)
    }

    @Test
    fun messagesIllisiblesIgnores() {
        appairer()
        val s = session()
        s.recevoir("pas du json")
        s.recevoir("{\"t\":\"key\",\"k\":\"up\"}")
        s.recevoir("x".repeat(Protocole.TAILLE_MAX + 1))
        assertFalse(fermee)
        s.recevoir(hello())
        assertTrue(s.authentifiee)
        // Touche inconnue et message sans version : ignorés
        s.recevoir(msg("key", "k" to "formater"))
        s.recevoir("{\"t\":\"key\",\"k\":\"up\"}")
        assertTrue(hote.injecteur.commandes.isEmpty())
    }

    // ------------------------------------------------------------------ version

    @Test
    fun versionIncompatible() {
        appairer()
        val s = session()
        s.recevoir(hello(v = 2))
        val r = envoyes.single()
        assertEquals("erreur", r.getString("t"))
        assertEquals("version", r.getString("code"))
        assertEquals(1, r.getInt("vMax"))
        assertTrue(fermee)
        assertFalse(s.authentifiee)
    }

    @Test
    fun versionAbsente() {
        appairer()
        val s = session()
        s.recevoir(JSONObject(hello()).apply { remove("v") }.toString())
        assertEquals("version", envoyes.single().getString("code"))
        assertTrue(fermee)
    }

    // ------------------------------------------------------------------ révocation

    @Test
    fun revocation() {
        appairer()
        val s = session()
        s.recevoir(hello())
        assertTrue(s.authentifiee)
        // Révoqué depuis la TV pendant qu'il est connecté
        assertTrue(hote.registre.revoquer("tel-1"))
        envoyes.clear()
        s.revoquer()
        assertEquals("revoked", envoyes.single().getString("t"))
        assertTrue(fermee)
        s.recevoir(msg("key", "k" to "up"))
        assertTrue(hote.injecteur.commandes.isEmpty())

        // Il revient comme s'il était encore appairé : la TV lui dit d'oublier
        val s2 = session()
        s2.recevoir(hello())
        assertEquals("revoked", envoyes.single().getString("t"))
        assertTrue(fermee)

        // Il revient avec sa clé : seulement un nouvel appairage possible, aucune commande
        val s3 = session()
        s3.recevoir(hello(cle = Vecteurs.TEL_SPKI))
        assertEquals("hello", envoyes.single().getString("t"))
        assertEquals(SessionTelecommande.Etat.CLE_VERIFIEE, s3.etat)
        toutesLesCommandes(s3)
        assertTrue(hote.injecteur.commandes.isEmpty())
        assertEquals(1, envoyes.size)
    }

    // ------------------------------------------------------------------ appairage

    private fun demandeAppairage(secret: String, cle: String = Vecteurs.TEL_SPKI, empreinte: String? = Vecteurs.TV_EMPREINTE) =
        JSONObject().put("v", 1).put("t", "pair_request").put("secret", secret).put("nom", "Téléphone de Marie").put("cle", cle)
            .apply { if (empreinte != null) put("empreinteTV", empreinte) }.toString()

    private fun nouveauTelephone(): SessionTelecommande {
        hote = HoteFactice(dossier.root)
        val s = session()
        s.recevoir(hello(appareil = "tel-2", cle = Vecteurs.TEL_SPKI, nom = "Marie"))
        assertEquals(SessionTelecommande.Etat.CLE_VERIFIEE, s.etat)
        envoyes.clear()
        return s
    }

    @Test
    fun appairageAccepte() {
        val s = nouveauTelephone()
        val secret = hote.appairage.demarrer()
        s.recevoir(demandeAppairage(secret.secret))
        assertTrue(envoyes.isEmpty())
        val (nom, reponse) = hote.demandes.single()
        assertEquals("Téléphone de Marie", nom)
        reponse(true)
        val r = envoyes.single()
        assertEquals("paired", r.getString("t"))
        assertEquals("Projecteur du salon", r.getString("nomTV"))
        assertEquals(hote.registre.idTv(), r.getString("idTV"))
        assertEquals(Vecteurs.TV_SPKI, r.getString("cle"))
        assertTrue(s.authentifiee)
        val t = hote.registre.trouver("tel-2")
        assertNotNull(t)
        assertEquals("Téléphone de Marie", t!!.nom)
        assertEquals(Vecteurs.TEL_SPKI, t.cle)
        // Secret consommé : usage unique
        assertNull(hote.appairage.actif())
        s.recevoir(msg("key", "k" to "ok"))
        assertEquals(listOf("key:ok"), hote.injecteur.commandes)
    }

    @Test
    fun appairageParCodeDeSecours() {
        val s = nouveauTelephone()
        val secret = hote.appairage.demarrer()
        s.recevoir(demandeAppairage(secret.secretCourt, empreinte = null))
        hote.demandes.single().second(true)
        assertEquals("paired", envoyes.single().getString("t"))
    }

    @Test
    fun appairageRefuseParLUtilisateur() {
        val s = nouveauTelephone()
        val secret = hote.appairage.demarrer()
        s.recevoir(demandeAppairage(secret.secret))
        hote.demandes.single().second(false)
        val r = envoyes.single()
        assertEquals("pair_refused", r.getString("t"))
        assertEquals("refuse", r.getString("raison"))
        assertNull(hote.registre.trouver("tel-2"))
        assertFalse(s.authentifiee)
        s.recevoir(msg("key", "k" to "ok"))
        assertTrue(hote.injecteur.commandes.isEmpty())
    }

    @Test
    fun appairageSecretFauxPuisTropDEssais() {
        val s = nouveauTelephone()
        hote.appairage.demarrer()
        repeat(4) {
            s.recevoir(demandeAppairage("AAAAAAAAAAAAAAAAAAAAAA"))
            assertEquals("invalide", envoyes.last().getString("raison"))
        }
        s.recevoir(demandeAppairage("AAAAAAAAAAAAAAAAAAAAAA"))
        assertEquals("trop_d_essais", envoyes.last().getString("raison"))
        assertTrue(hote.demandes.isEmpty())
    }

    @Test
    fun appairageExpire() {
        val s = nouveauTelephone()
        val secret = hote.appairage.demarrer()
        hote.temps += RemoteConfig.DUREE_SECRET_MS
        s.recevoir(demandeAppairage(secret.secret))
        assertEquals("expire", envoyes.single().getString("raison"))
    }

    @Test
    fun appairageMauvaiseEmpreinteTv() {
        val s = nouveauTelephone()
        val secret = hote.appairage.demarrer()
        s.recevoir(demandeAppairage(secret.secret, empreinte = Vecteurs.TEL_EMPREINTE))
        assertEquals("invalide", envoyes.single().getString("raison"))
    }

    /** La clé de la demande doit être celle qui a signé le hello. */
    @Test
    fun appairageAvecUneAutreCle() {
        val s = nouveauTelephone()
        val secret = hote.appairage.demarrer()
        s.recevoir(demandeAppairage(secret.secret, cle = Vecteurs.TV_SPKI))
        assertEquals("invalide", envoyes.single().getString("raison"))
        assertTrue(hote.demandes.isEmpty())
    }

    @Test
    fun appairagePendantUneAutreDemande() {
        val s = nouveauTelephone()
        val secret = hote.appairage.demarrer()
        hote.occupe = true
        s.recevoir(demandeAppairage(secret.secret))
        assertEquals("refuse", envoyes.single().getString("raison"))
    }

    // ------------------------------------------------------------------ registre

    @Test
    fun registrePersistant() {
        appairer()
        val id = hote.registre.idTv()
        assertTrue(id.matches(Regex("ibiprojo-[0-9a-f]{32}")))
        val relu = RegistreTelecommande(File(dossier.root, "telecommande.json"))
        assertEquals(id, relu.idTv())
        assertEquals(hote.registre.jetonPeerJs(), relu.jetonPeerJs())
        assertEquals("Téléphone de Marie", relu.trouver("tel-1")!!.nom)
        assertTrue(relu.renommer("tel-1", "  Salon\n"))
        assertEquals("Salon", RegistreTelecommande(File(dossier.root, "telecommande.json")).trouver("tel-1")!!.nom)
        assertTrue(relu.revoquer("tel-1"))
        assertTrue(RegistreTelecommande(File(dossier.root, "telecommande.json")).telephones().isEmpty())
    }
}
