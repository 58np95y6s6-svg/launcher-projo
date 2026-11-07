package fr.projo.launcher.remote

import fr.projo.launcher.liens.LienTv
import fr.projo.launcher.liens.ResultatLien
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** « Envoyer sur la TV » de test : état en mémoire, demandes notées, réponses choisies par le test. */
class LiensFactices : LiensTv {
    var etat = EtatLiens(vpn = true, seulementVpn = true, appliPages = "VYPN", appliVpn = "VYPN", raccourci = null)

    class Demande(val lien: LienTv, val sansVpn: Boolean, val vpnDabord: Boolean, val expediteur: String, val rappel: (ResultatLien) -> Unit)

    val demandes = mutableListOf<Demande>()

    /** Réponse immédiate (null : le test répond plus tard par [demandes]). */
    var reponse: ResultatLien? = ResultatLien.Ouvert("VYPN")

    override fun etat(rappel: (EtatLiens) -> Unit) = rappel(etat)

    override fun ouvrir(lien: LienTv, sansVpn: Boolean, vpnDabord: Boolean, expediteur: String, rappel: (ResultatLien) -> Unit) {
        demandes += Demande(lien, sansVpn, vpnDabord, expediteur, rappel)
        reponse?.let(rappel)
    }
}

/** Hôte de test avec pilotage et liens (l'un ou l'autre peut manquer). */
class HoteLiens(val base: HoteFactice, override val pilotage: Pilotage?, override val liens: LiensTv?) : HoteSession by base {
    val journaux = mutableListOf<String>()
    override fun journal(evenement: String) {
        journaux += evenement
    }
}

/**
 * `liens_etat` et `lien_ouvrir` (SessionTelecommande) : annonce `liens` dans `fonctions`, rien
 * avant l'authentification, abonnement propre à la connexion, URL vérifiée, réponses
 * `lien_ouvert` (dont la seconde après `attente_vpn`), journal sans URL.
 */
@RunWith(RobolectricTestRunner::class)
class LiensProtocoleTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private lateinit var base: HoteFactice
    private val liens = LiensFactices()
    private val envoyes = mutableListOf<JSONObject>()

    @Before
    fun preparer() {
        base = HoteFactice(dossier.root)
        base.registre.ajouter(TelephoneAppaire("tel-1", "Téléphone de Marie", Vecteurs.TEL_SPKI, 1L))
    }

    private fun session(pilotage: Pilotage? = PilotageFactice(), avecLiens: Boolean = true): Pair<SessionTelecommande, HoteLiens> {
        val hote = HoteLiens(base, pilotage, if (avecLiens) liens else null)
        envoyes.clear()
        return SessionTelecommande(hote, Vecteurs.DTLS_TEL, Vecteurs.DTLS_TV, envoyer = { envoyes += JSONObject(it) }, fermer = {}) to hote
    }

    private fun hello() =
        JSONObject().put("v", 1).put("t", "hello").put("appareil", "tel-1").put("nonce", Vecteurs.NONCE_TEL).put("sig", Vecteurs.SIG_TEL).toString()

    private fun msg(t: String, vararg champs: Pair<String, Any?>) =
        JSONObject().put("v", 1).put("t", t).apply { champs.forEach { put(it.first, it.second) } }.toString()

    private fun connectee(pilotage: Pilotage? = PilotageFactice()): Pair<SessionTelecommande, HoteLiens> {
        val (s, hote) = session(pilotage)
        s.recevoir(hello())
        assertTrue(s.authentifiee)
        envoyes.clear()
        return s to hote
    }

    private fun fonctions(m: JSONObject): List<String>? =
        m.optJSONArray("fonctions")?.let { f -> (0 until f.length()).map { f.getString(it) } }

    // ------------------------------------------------------------------ annonce

    @Test
    fun liensAnnonceDansFonctions() {
        val (s, _) = session()
        s.recevoir(hello())
        assertEquals(listOf("apps", "reglages", "liens"), fonctions(envoyes.single()))
        // Liens sans pilotage : seulement « liens »
        val (s2, _) = session(pilotage = null)
        s2.recevoir(hello())
        assertEquals(listOf("liens"), fonctions(envoyes.single()))
        // Ni l'un ni l'autre : pas de `fonctions`
        val (s3, _) = session(pilotage = null, avecLiens = false)
        s3.recevoir(hello())
        assertFalse(envoyes.single().has("fonctions"))
        s3.recevoir(msg("liens_etat"))
        s3.recevoir(msg("lien_ouvrir", "url" to "https://arte.tv"))
        assertEquals(1, envoyes.size)
        assertTrue(liens.demandes.isEmpty())
    }

    @Test
    fun rienAvantLAuthentification() {
        val (s, _) = session()
        s.recevoir(msg("liens_etat"))
        s.recevoir(msg("lien_ouvrir", "url" to "https://arte.tv"))
        assertTrue(envoyes.isEmpty())
        assertTrue(liens.demandes.isEmpty())
        assertFalse(s.abonneLiens)
    }

    // ------------------------------------------------------------------ liens_etat

    @Test
    fun etatEtAbonnement() {
        val (s, hote) = connectee()
        s.envoyerLiensSiAbonne(Protocole.liens(liens.etat))
        assertTrue("pas abonné : rien", envoyes.isEmpty())
        s.recevoir(msg("liens_etat"))
        assertTrue(s.abonneLiens)
        val m = envoyes.single()
        assertEquals("liens", m.getString("t"))
        assertEquals(1, m.getInt("v"))
        assertTrue(m.getBoolean("vpn"))
        assertTrue(m.getBoolean("seulementVpn"))
        assertEquals("VYPN", m.getString("appliPages"))
        assertEquals("VYPN", m.getString("appliVpn"))
        assertFalse(m.has("raccourci"))
        assertTrue(hote.journaux.contains("état des liens demandé"))

        // Changement vu par la TV : envoyé aux abonnés
        envoyes.clear()
        liens.etat = EtatLiens(vpn = false, seulementVpn = false, appliPages = "", raccourci = "http://192.168.1.42:8642/envoyer?c=K7Q2M9XD")
        s.envoyerLiensSiAbonne(Protocole.liens(liens.etat))
        val m2 = envoyes.single()
        assertFalse(m2.getBoolean("vpn"))
        assertEquals("", m2.getString("appliPages"))
        assertFalse(m2.has("appliVpn"))
        assertEquals("http://192.168.1.42:8642/envoyer?c=K7Q2M9XD", m2.getJSONObject("raccourci").getString("adresse"))

        // Nouvelle connexion : pas abonnée tant qu'elle n'a pas redemandé
        s.terminer()
        val (s2, _) = connectee()
        assertFalse(s2.abonneLiens)
        s2.envoyerLiensSiAbonne(Protocole.liens(liens.etat))
        assertTrue(envoyes.isEmpty())
    }

    // ------------------------------------------------------------------ lien_ouvrir

    @Test
    fun lienOuvert() {
        val (s, hote) = connectee()
        s.recevoir(msg("lien_ouvrir", "url" to "https://www.arte.tv/fr/", "titre" to "Les grands\ndocumentaires"))
        val d = liens.demandes.single()
        assertEquals("https://www.arte.tv/fr/", d.lien.url)
        assertEquals("Les grands documentaires", d.lien.titre)
        assertFalse(d.sansVpn)
        assertFalse(d.vpnDabord)
        // Nom du téléphone donné à l'appairage, avec son article
        assertEquals("le téléphone de Marie", d.expediteur)
        val r = envoyes.single()
        assertEquals("lien_ouvert", r.getString("t"))
        assertEquals("https://www.arte.tv/fr/", r.getString("url"))
        assertTrue(r.getBoolean("ok"))
        assertEquals("VYPN", r.getString("ouvertDans"))
        assertFalse(r.has("raison"))
        assertTrue(hote.journaux.contains("lien envoyé (téléphone)"))
    }

    @Test
    fun sansVpnEtVpnDabordStrictementBooleens() {
        val (s, _) = connectee()
        s.recevoir(msg("lien_ouvrir", "url" to "https://arte.tv", "sansVpn" to true))
        s.recevoir(msg("lien_ouvrir", "url" to "https://arte.tv", "vpnDabord" to true))
        s.recevoir(msg("lien_ouvrir", "url" to "https://arte.tv", "sansVpn" to "true", "vpnDabord" to 1))
        assertEquals(listOf(true to false, false to true, false to false), liens.demandes.map { it.sansVpn to it.vpnDabord })
    }

    @Test
    fun urlInvalide() {
        val (s, hote) = connectee()
        val invalides = listOf<Any>("javascript:alert(1)", "file:///sdcard/x", "intent://x#Intent;end", "arte.tv", "https://", "https://a b.fr", 42)
        for (u in invalides) s.recevoir(msg("lien_ouvrir", "url" to u))
        s.recevoir(msg("lien_ouvrir"))
        s.recevoir(msg("lien_ouvrir", "url" to "https://exemple.fr/" + "a".repeat(2_100)))
        assertTrue(liens.demandes.isEmpty())
        assertEquals(invalides.size + 2, envoyes.size)
        for (r in envoyes) {
            assertEquals("lien_ouvert", r.getString("t"))
            assertFalse(r.getBoolean("ok"))
            assertEquals("invalide", r.getString("raison"))
        }
        // `url` : celle de la demande (vide si ce n'était pas une chaîne)
        assertEquals("javascript:alert(1)", envoyes[0].getString("url"))
        assertEquals("", envoyes[invalides.size - 1].getString("url"))
        assertTrue(hote.journaux.contains("lien refusé invalide"))
        assertTrue(hote.journaux.none { it.contains("javascript") || it.contains("sdcard") })
    }

    @Test
    fun refusEtSecondeReponseApresLAttenteDuVpn() {
        liens.reponse = null
        val (s, _) = connectee()
        s.recevoir(msg("lien_ouvrir", "url" to "https://youtu.be/abc", "vpnDabord" to true))
        val d = liens.demandes.single()
        d.rappel(ResultatLien.Refuse(ResultatLien.ATTENTE_VPN))
        d.rappel(ResultatLien.Ouvert("SmartTube"))
        assertEquals(2, envoyes.size)
        assertEquals("attente_vpn", envoyes[0].getString("raison"))
        assertFalse(envoyes[0].getBoolean("ok"))
        assertEquals("https://youtu.be/abc", envoyes[1].getString("url"))
        assertTrue(envoyes[1].getBoolean("ok"))
        assertEquals("SmartTube", envoyes[1].getString("ouvertDans"))

        // Toutes les raisons passent telles quelles
        envoyes.clear()
        for (raison in listOf("vpn_coupe", "delai_vpn", "pas_d_appli_vpn", "remplace", "aucune_appli")) d.rappel(ResultatLien.Refuse(raison))
        assertEquals(listOf("vpn_coupe", "delai_vpn", "pas_d_appli_vpn", "remplace", "aucune_appli"), envoyes.map { it.getString("raison") })

        // Téléphone parti : la réponse tardive n'est envoyée à personne
        envoyes.clear()
        s.terminer()
        d.rappel(ResultatLien.Ouvert("SmartTube"))
        assertTrue(envoyes.isEmpty())
    }

    @Test
    fun telephoneInconnuDuRegistre() {
        val (s, _) = connectee()
        base.registre.renommer("tel-1", "iPhone de Paul")
        s.recevoir(msg("lien_ouvrir", "url" to "https://arte.tv"))
        assertEquals("l'iPhone de Paul", liens.demandes.single().expediteur)
    }
}
