package fr.projo.launcher.remote

import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import fr.projo.launcher.modules.telecommande.NIVEAU_QR_MODULE
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * QR code permanent du module Télécommande (RenouvellementQr), horloge simulée :
 * renouvellement à l'échéance, jamais accueil caché / écran éteint, jamais pendant une
 * confirmation, pause « ✓ » après un appairage ; et contenu du QR code.
 */
@RunWith(RobolectricTestRunner::class)
class QrPermanentTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private var temps = 1_700_000_000_000L
    private val appairage = GestionAppairage(horloge = { temps })
    private val qr = RenouvellementQr(appairage, horloge = { temps })
    private val module = Any()
    private val empreinteTv = Vecteurs.TV_EMPREINTE

    @Test
    fun renouvellementALEcheance() {
        assertTrue(qr.afficher(module))
        val s1 = qr.maintenir()
        assertNotNull("module affiché : un secret", s1)
        assertEquals(s1!!.expire, qr.prochainRappel())
        assertNull("encore valable", qr.maintenir())
        temps += RemoteConfig.DUREE_SECRET_MS - 1
        assertNull(qr.maintenir())
        temps += 1
        val s2 = qr.maintenir()
        assertNotNull("à l'échéance : un nouveau secret", s2)
        assertNotEquals(s1.secret, s2!!.secret)
        assertEquals(s2.expire, qr.prochainRappel())
        assertTrue(appairage.actif() === s2)
        // Et ainsi de suite tant que le module est affiché
        temps += RemoteConfig.DUREE_SECRET_MS
        assertNotNull(qr.maintenir())
    }

    @Test
    fun rienQuandLAccueilNEstPasVisible() {
        // Aucun module affiché (accueil caché, autre module choisi) : aucun secret créé
        assertNull(qr.maintenir())
        assertNull(appairage.actif())
        assertNull(qr.prochainRappel())
        // Affiché puis caché (une appli passe au premier plan) : plus de renouvellement
        qr.afficher(module)
        val s1 = qr.maintenir()!!
        assertTrue(qr.masquer(module))
        assertFalse(qr.actif)
        temps += RemoteConfig.DUREE_SECRET_MS
        assertNull(qr.maintenir())
        assertNull(appairage.actif())
        assertNull(qr.prochainRappel())
        // Écran éteint, module encore composé : rien non plus
        qr.afficher(module)
        qr.ecranAllume = false
        assertNull(qr.maintenir())
        assertNull(appairage.actif())
        // Écran rallumé : nouveau secret
        qr.ecranAllume = true
        val s2 = qr.maintenir()
        assertNotNull(s2)
        assertNotEquals(s1.secret, s2!!.secret)
    }

    /** Deux modules (changement de design) : le secret reste tant qu'au moins un est affiché. */
    @Test
    fun plusieursAffichages() {
        val autre = Any()
        qr.afficher(module)
        qr.afficher(autre)
        val s1 = qr.maintenir()!!
        qr.masquer(module)
        assertTrue(qr.actif)
        assertNull(qr.maintenir())
        assertTrue(appairage.actif() === s1)
    }

    @Test
    fun pasDeRenouvellementPendantUneConfirmation() {
        qr.afficher(module)
        val s1 = qr.maintenir()!!
        assertNull(appairage.verifier(s1.secret, empreinteTv, empreinteTv))
        val reserve = appairage.reserver()
        // Échéance pendant la confirmation : rien, et pas de rappel (la fin de la confirmation rappelle)
        temps += RemoteConfig.DUREE_SECRET_MS + 30_000
        assertNull(qr.maintenir())
        assertNull(qr.prochainRappel())
        // Acceptée : son secret est consommé, « ✓ » quelques secondes, puis nouveau secret
        appairage.liberer(reserve, accepte = true)
        qr.appaire()
        assertNull(qr.maintenir())
        assertEquals(temps + RemoteConfig.PAUSE_APRES_APPAIRAGE_MS, qr.prochainRappel())
        temps += RemoteConfig.PAUSE_APRES_APPAIRAGE_MS
        val s2 = qr.maintenir()
        assertNotNull(s2)
        assertNotEquals(s1.secret, s2!!.secret)
    }

    /** « ✓ appairé » affiché dans le dialogue en grand : nouveau secret seulement à sa fermeture. */
    @Test
    fun dialogueOuvertApresAppairage() {
        qr.afficher(module)
        qr.maintenir()
        appairage.consommer()
        qr.appaire()
        qr.dialogueOuvert = true
        temps += RemoteConfig.PAUSE_APRES_APPAIRAGE_MS * 3
        assertNull(qr.maintenir())
        assertNull(qr.prochainRappel())
        qr.dialogueOuvert = false
        assertNotNull(qr.maintenir())
    }

    // ------------------------------------------------------------------ avec une vraie session

    private val envoyes = mutableListOf<JSONObject>()

    private fun session(hote: HoteFactice): SessionTelecommande {
        val s = SessionTelecommande(hote, Vecteurs.DTLS_TEL, Vecteurs.DTLS_TV, envoyer = { envoyes += JSONObject(it) }, fermer = {})
        s.recevoir(
            JSONObject().put("v", 1).put("t", "hello").put("appareil", "tel-2").put("nonce", Vecteurs.NONCE_TEL)
                .put("sig", Vecteurs.SIG_TEL).put("cle", Vecteurs.TEL_SPKI).put("nom", "Marie").toString(),
        )
        assertEquals(SessionTelecommande.Etat.CLE_VERIFIEE, s.etat)
        envoyes.clear()
        return s
    }

    private fun demande(secret: String) =
        JSONObject().put("v", 1).put("t", "pair_request").put("secret", secret).put("nom", "Téléphone de Marie")
            .put("cle", Vecteurs.TEL_SPKI).put("empreinteTV", Vecteurs.TV_EMPREINTE).toString()

    /**
     * Téléphone en cours de confirmation quand le secret arrive à échéance : le module n'en
     * crée pas de nouveau avant la réponse ; la réponse arrive, l'appairage aboutit, puis le
     * renouvellement reprend.
     */
    @Test
    fun confirmationEnCoursAvecUneVraieSession() {
        val hote = HoteFactice(dossier.root)
        val qrHote = RenouvellementQr(hote.appairage, horloge = { hote.temps })
        qrHote.afficher(module)
        val s1 = qrHote.maintenir()!!
        val s = session(hote)
        s.recevoir(demande(s1.secret))
        val (_, reponse) = hote.demandes.single()
        assertTrue(hote.appairage.confirmationEnCours)
        hote.temps += RemoteConfig.DUREE_SECRET_MS + 1
        assertNull("pas de renouvellement pendant la confirmation", qrHote.maintenir())
        reponse(true)
        assertEquals("paired", envoyes.single().getString("t"))
        assertTrue(s.authentifiee)
        assertNotNull(hote.registre.trouver("tel-2"))
        assertFalse(hote.appairage.confirmationEnCours)
        qrHote.appaire()
        hote.temps += RemoteConfig.PAUSE_APRES_APPAIRAGE_MS
        val s2 = qrHote.maintenir()
        assertNotNull(s2)
        assertTrue(hote.appairage.actif() === s2)
    }

    /** Même si un nouveau secret est créé pendant la confirmation, l'accepter ne le consomme pas. */
    @Test
    fun secretRenouveleEntreTempsResteValable() {
        val hote = HoteFactice(dossier.root)
        val s1 = hote.appairage.demarrer()
        val s = session(hote)
        s.recevoir(demande(s1.secret))
        val s2 = hote.appairage.demarrer() // « Nouveau code » forcé
        hote.demandes.single().second(true)
        assertTrue(s.authentifiee)
        assertTrue("le nouveau QR code reste valable", hote.appairage.actif() === s2)
    }

    /** Session fermée pendant la confirmation : la réservation est libérée, le renouvellement reprend. */
    @Test
    fun sessionFermeePendantLaConfirmation() {
        val hote = HoteFactice(dossier.root)
        val s1 = hote.appairage.demarrer()
        val s = session(hote)
        s.recevoir(demande(s1.secret))
        assertTrue(hote.appairage.confirmationEnCours)
        s.terminer()
        assertFalse(hote.appairage.confirmationEnCours)
        hote.temps += RemoteConfig.DUREE_SECRET_MS
        assertNotNull(hote.appairage.renouvelerSiEchu())
    }

    // ------------------------------------------------------------------ contenu du QR code

    private val idTv = "ibiprojo-0123456789abcdef0123456789abcdef"
    private val secret = "q2Xk9_LmT0aZr1bYwE3uVg"
    private val empreinte = "YPXzIOhZAT92PqkIdIKfBw"

    @Test
    fun urlDAppairage() {
        val url = RemoteConfig.urlAppairage(idTv, secret, empreinte)
        assertEquals("https://58np95y6s6-svg.github.io/launcher-projo/#pair=$idTv.$secret.$empreinte", url)
        assertEquals(141, url.length)
    }

    private fun decoder(modules: Array<BooleanArray>): String {
        val n = modules.size
        val bits = BitMatrix(n, n)
        for (y in 0 until n) for (x in 0 until n) if (modules[y][x]) bits.set(x, y)
        return Decoder().decode(bits).text
    }

    /** Le QR code du module encode exactement l'URL d'appairage (celle du dialogue). */
    @Test
    fun qrCodeEncodeLUrl() {
        val url = RemoteConfig.urlAppairage(idTv, secret, empreinte)
        val module = QrCode.matrice(url, NIVEAU_QR_MODULE)
        assertEquals(url, decoder(module))
        val dialogue = QrCode.matrice(url)
        assertEquals(url, decoder(dialogue))
        // Niveau L : version 7 (45 × 45 modules) au lieu de 8 (49 × 49) pour le dialogue (M)
        assertEquals(ErrorCorrectionLevel.L, NIVEAU_QR_MODULE)
        assertEquals(45, module.size)
        assertEquals(49, dialogue.size)
        // Un secret réel (GestionAppairage) passe aussi
        val s = appairage.demarrer()
        val vraie = RemoteConfig.urlAppairage(idTv, s.secret, CryptoTelecommande.empreinteCle(Vecteurs.identiteTv().spki))
        assertEquals(vraie, decoder(QrCode.matrice(vraie, NIVEAU_QR_MODULE)))
        assertEquals(45, QrCode.matrice(vraie, NIVEAU_QR_MODULE).size)
    }
}
