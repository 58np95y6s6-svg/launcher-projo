package fr.projo.launcher.liens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plateforme de test : VPN, applis et horloge à la main, ouvertures et journal notés. */
class PlateformeFactice : PlateformeLiens {
    var vpn = false
    var garde = true
    var appliVpnInstallee: String? = "VYPN"
    var appliVpnSOuvre = true
    /** Appli qui ouvre les liens (null : aucune). */
    var appliPages: String? = "Silk"
    val ouverts = mutableListOf<Pair<String, String>>()
    var ouverturesVpn = 0
    val journaux = mutableListOf<String>()
    var question: QuestionVpn? = null
    var temps = 0L
    private val taches = mutableListOf<Pair<Long, () -> Unit>>()

    override fun vpnActif() = vpn
    override fun seulementVpn() = garde
    override fun appliVpn() = appliVpnInstallee
    override fun ouvrirAppliVpn(): Boolean {
        ouverturesVpn++
        return appliVpnSOuvre
    }

    override fun ouvrir(lien: LienTv, expediteur: String, rappel: (String?) -> Unit) {
        val a = appliPages
        if (a != null) ouverts += lien.url to expediteur
        rappel(a)
    }

    override fun montrerQuestion(q: QuestionVpn?) {
        question = q
    }

    override fun planifier(delaiMs: Long, tache: () -> Unit): () -> Unit {
        val t = (temps + delaiMs) to tache
        taches += t
        return { taches.remove(t) }
    }

    override fun journal(evenement: String) {
        journaux += evenement
    }

    fun avancer(ms: Long) {
        val fin = temps + ms
        while (true) {
            val prochaine = taches.filter { it.first <= fin }.minByOrNull { it.first } ?: break
            taches.remove(prochaine)
            temps = prochaine.first
            prochaine.second()
        }
        temps = fin
    }

    val tachesEnAttente: Int get() = taches.size
}

/**
 * Déroulé d'un lien envoyé sur la TV (EnvoiLiens) : garde-fou du VPN, `sansVpn`, `vpnDabord` et
 * son attente de 60 s (une seule à la fois, `remplace`), garde-fou du raccourci iOS.
 */
class EnvoiLiensTest {

    private val p = PlateformeFactice()
    private val envoi = EnvoiLiens(p)
    private val arte = Liens.lien("https://www.arte.tv/fr/", "Les grands documentaires")!!
    private val youtube = Liens.lien("https://youtu.be/abc")!!

    private fun envoyer(
        lien: LienTv = arte,
        source: SourceLien = SourceLien.TELEPHONE,
        sansVpn: Boolean = false,
        vpnDabord: Boolean = false,
    ): MutableList<ResultatLien> {
        val r = mutableListOf<ResultatLien>()
        envoi.envoyer(lien, source, "le téléphone de Marie", sansVpn, vpnDabord) { r += it }
        return r
    }

    private fun refus(raison: String) = ResultatLien.Refuse(raison)

    @Test
    fun vpnActifLeLienSOuvre() {
        p.vpn = true
        assertEquals(listOf(ResultatLien.Ouvert("Silk")), envoyer())
        assertEquals(listOf(arte.url to "le téléphone de Marie"), p.ouverts)
        assertTrue(p.journaux.contains("lien ouvert"))
    }

    @Test
    fun sansLeGardeFouLeLienSOuvreSansVpn() {
        p.garde = false
        assertEquals(listOf(ResultatLien.Ouvert("Silk")), envoyer())
    }

    @Test
    fun vpnCoupeRienNeSOuvre() {
        assertEquals(listOf(refus(ResultatLien.VPN_COUPE)), envoyer())
        assertTrue(p.ouverts.isEmpty())
        assertEquals(0, p.ouverturesVpn)
        assertTrue(p.journaux.contains("lien refusé vpn_coupe"))
        // « Ouvrir sans VPN »
        assertEquals(listOf(ResultatLien.Ouvert("Silk")), envoyer(sansVpn = true))
        assertEquals(1, p.ouverts.size)
    }

    @Test
    fun aucuneAppli() {
        p.vpn = true
        p.appliPages = null
        assertEquals(listOf(refus(ResultatLien.AUCUNE_APPLI)), envoyer())
    }

    @Test
    fun vpnDabordAttendQueLeVpnSAllume() {
        val r = envoyer(vpnDabord = true)
        assertEquals(listOf(refus(ResultatLien.ATTENTE_VPN)), r)
        assertEquals(1, p.ouverturesVpn)
        assertTrue(envoi.enAttente)
        assertTrue(p.ouverts.isEmpty())
        p.avancer(30_000)
        // Le VPN s'allume : le lien s'ouvre, second `lien_ouvert`
        p.vpn = true
        envoi.surVpn(true)
        assertEquals(listOf(refus(ResultatLien.ATTENTE_VPN), ResultatLien.Ouvert("Silk")), r)
        assertEquals(1, p.ouverts.size)
        assertFalse(envoi.enAttente)
        assertEquals("le délai est annulé", 0, p.tachesEnAttente)
        // Plus rien ensuite
        p.avancer(60_000)
        envoi.surVpn(true)
        assertEquals(2, r.size)
    }

    @Test
    fun vpnCoupeDeNouveauNOuvreRien() {
        val r = envoyer(vpnDabord = true)
        envoi.surVpn(false)
        assertEquals(1, r.size)
        assertTrue(envoi.enAttente)
    }

    @Test
    fun delaiDe60s() {
        val r = envoyer(vpnDabord = true)
        p.avancer(59_999)
        assertEquals(1, r.size)
        p.avancer(1)
        assertEquals(listOf(refus(ResultatLien.ATTENTE_VPN), refus(ResultatLien.DELAI_VPN)), r)
        assertFalse(envoi.enAttente)
        // Le VPN s'allume trop tard : rien ne s'ouvre
        envoi.surVpn(true)
        assertTrue(p.ouverts.isEmpty())
    }

    @Test
    fun uneSeuleAttenteALaFois() {
        val premier = envoyer(vpnDabord = true)
        p.avancer(10_000)
        val second = envoyer(lien = youtube, vpnDabord = true)
        assertEquals(listOf(refus(ResultatLien.ATTENTE_VPN), refus(ResultatLien.REMPLACE)), premier)
        assertEquals(listOf(refus(ResultatLien.ATTENTE_VPN)), second)
        // Le délai repart de la seconde demande
        p.avancer(55_000)
        assertEquals(1, second.size)
        envoi.surVpn(true)
        assertEquals(ResultatLien.Ouvert("Silk"), second.last())
        assertEquals(listOf(youtube.url), p.ouverts.map { it.first })
        assertEquals(2, premier.size)
    }

    @Test
    fun pasDAppliVpn() {
        p.appliVpnInstallee = null
        assertEquals(listOf(refus(ResultatLien.PAS_D_APPLI_VPN)), envoyer(vpnDabord = true))
        assertFalse(envoi.enAttente)
        // L'appli VPN refuse de s'ouvrir : même réponse
        p.appliVpnInstallee = "VYPN"
        p.appliVpnSOuvre = false
        assertEquals(listOf(refus(ResultatLien.PAS_D_APPLI_VPN)), envoyer(vpnDabord = true))
        assertFalse(envoi.enAttente)
    }

    @Test
    fun vpnDejaActifAvecVpnDabord() {
        p.vpn = true
        assertEquals(listOf(ResultatLien.Ouvert("Silk")), envoyer(vpnDabord = true))
        assertEquals(0, p.ouverturesVpn)
    }

    @Test
    fun vpnDabordLEmporteSurSansVpn() {
        assertEquals(listOf(refus(ResultatLien.ATTENTE_VPN)), envoyer(sansVpn = true, vpnDabord = true))
        assertTrue(p.ouverts.isEmpty())
    }

    // ------------------------------------------------------------------ raccourci iOS : garde-fou

    @Test
    fun raccourciVpnCoupeQuestionALEcran() {
        val r = envoyer(source = SourceLien.RACCOURCI)
        assertEquals(listOf<ResultatLien>(ResultatLien.Question), r)
        val q = p.question!!
        assertEquals(arte, q.lien)
        assertEquals("VYPN", q.appliVpn)
        assertEquals(q, envoi.question)
        assertTrue(p.ouverts.isEmpty())
        // « Ouvrir sans VPN »
        envoi.repondre(ChoixQuestion.SANS_VPN)
        assertNull(p.question)
        assertNull(envoi.question)
        assertEquals(1, p.ouverts.size)
    }

    @Test
    fun raccourciAllumerLeVpnEtOuvrir() {
        envoyer(source = SourceLien.RACCOURCI)
        envoi.repondre(ChoixQuestion.ALLUMER_VPN)
        assertEquals(1, p.ouverturesVpn)
        assertTrue(envoi.enAttente)
        p.vpn = true
        envoi.surVpn(true)
        assertEquals(1, p.ouverts.size)
        // Délai : rien ne se passe (personne à prévenir)
        p.vpn = false
        envoyer(source = SourceLien.RACCOURCI)
        envoi.repondre(ChoixQuestion.ALLUMER_VPN)
        p.avancer(60_000)
        assertFalse(envoi.enAttente)
        assertTrue(p.journaux.contains("lien refusé delai_vpn"))
    }

    @Test
    fun raccourciAnnuler() {
        envoyer(source = SourceLien.RACCOURCI)
        envoi.repondre(ChoixQuestion.ANNULER)
        assertNull(p.question)
        assertTrue(p.ouverts.isEmpty())
        assertEquals(0, p.ouverturesVpn)
        // Plus de question : une autre réponse ne fait rien
        envoi.repondre(ChoixQuestion.SANS_VPN)
        assertTrue(p.ouverts.isEmpty())
    }

    @Test
    fun raccourciVpnAllumeEntreTempsOuvreTout() {
        envoyer(source = SourceLien.RACCOURCI)
        p.vpn = true
        envoi.repondre(ChoixQuestion.ALLUMER_VPN)
        assertEquals(0, p.ouverturesVpn)
        assertEquals(1, p.ouverts.size)
    }

    @Test
    fun raccourciAvecVpnOuSansGardeFou() {
        p.vpn = true
        assertEquals(listOf(ResultatLien.Ouvert("Silk")), envoyer(source = SourceLien.RACCOURCI))
        p.vpn = false
        p.garde = false
        assertEquals(listOf(ResultatLien.Ouvert("Silk")), envoyer(source = SourceLien.RACCOURCI))
        assertNull(p.question)
    }

    @Test
    fun journalSansUrlNiTitre() {
        envoyer()
        envoyer(vpnDabord = true)
        envoyer(source = SourceLien.RACCOURCI)
        envoi.repondre(ChoixQuestion.SANS_VPN)
        for (j in p.journaux) {
            assertFalse(j, j.contains("arte") || j.contains("documentaires") || j.contains("http"))
        }
    }
}
