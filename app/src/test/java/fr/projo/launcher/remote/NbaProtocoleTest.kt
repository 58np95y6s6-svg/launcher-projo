package fr.projo.launcher.remote

import fr.projo.launcher.boxscore.Affichage
import fr.projo.launcher.boxscore.EquipeEtat
import fr.projo.launcher.boxscore.EtatNba
import fr.projo.launcher.boxscore.MatchEtat
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

/** Box score de test : état en mémoire, demandes notées, mêmes règles que la TV pour le choix. */
class NbaFactice : NbaTv {
    private val lal = EquipeEtat("LAL", "Lakers", 98, "#6c3fb5", "#f2b631")
    private val bos = EquipeEtat("BOS", "Celtics", 102, "#0d8a4f", "#ffffff")
    var etat = EtatNba(listOf(MatchEtat("LAL-BOS", lal, bos, "4e QT · 2:31", true)), "LAL-BOS", Affichage.CACHE, 0, null)
    val demandes = mutableListOf<String>()
    var abonnements = 0

    override fun abonner(rappel: (EtatNba) -> Unit) {
        abonnements++
        rappel(etat)
    }

    override fun etat(rappel: (EtatNba) -> Unit) = rappel(etat)

    override fun afficher(affichage: Affichage, id: String?, rappel: (EtatNba) -> Unit) {
        demandes += "afficher ${affichage.id} $id"
        if (id != null && etat.matchs.any { it.id == id }) etat = etat.copy(choisi = id)
        if (etat.choisi != null) etat = etat.copy(affichage = affichage)
        rappel(etat)
    }

    override fun choisir(id: String, rappel: (EtatNba) -> Unit) {
        demandes += "choisir $id"
        if (etat.matchs.any { it.id == id }) etat = etat.copy(choisi = id)
        rappel(etat)
    }
}

/** Hôte de test avec box score NBA. */
class HoteNba(val base: HoteFactice, override val nba: NbaTv?, override val pilotage: Pilotage? = null) : HoteSession by base {
    val journaux = mutableListOf<String>()
    override fun journal(evenement: String) {
        journaux += evenement
    }
}

/**
 * `nba_etat`, `nba_afficher`, `nba_choisir` (SessionTelecommande) et le message `nba` : annonce
 * dans `fonctions`, rien avant l'authentification, abonnement propre à la connexion, format et
 * identifiant vérifiés, champs exacts de la spécification, journal sans score.
 */
@RunWith(RobolectricTestRunner::class)
class NbaProtocoleTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private lateinit var base: HoteFactice
    private val nba = NbaFactice()
    private val envoyes = mutableListOf<JSONObject>()

    @Before
    fun preparer() {
        base = HoteFactice(dossier.root)
        base.registre.ajouter(TelephoneAppaire("tel-1", "Téléphone de Marie", Vecteurs.TEL_SPKI, 1L))
    }

    private fun session(avecNba: Boolean = true, pilotage: Pilotage? = null): Pair<SessionTelecommande, HoteNba> {
        val hote = HoteNba(base, if (avecNba) nba else null, pilotage)
        envoyes.clear()
        return SessionTelecommande(hote, Vecteurs.DTLS_TEL, Vecteurs.DTLS_TV, envoyer = { envoyes += JSONObject(it) }, fermer = {}) to hote
    }

    private fun hello() =
        JSONObject().put("v", 1).put("t", "hello").put("appareil", "tel-1").put("nonce", Vecteurs.NONCE_TEL).put("sig", Vecteurs.SIG_TEL).toString()

    private fun msg(t: String, vararg champs: Pair<String, Any?>) =
        JSONObject().put("v", 1).put("t", t).apply { champs.forEach { put(it.first, it.second) } }.toString()

    private fun connectee(): Pair<SessionTelecommande, HoteNba> {
        val (s, hote) = session()
        s.recevoir(hello())
        assertTrue(s.authentifiee)
        envoyes.clear()
        return s to hote
    }

    private fun fonctions(m: JSONObject): List<String>? =
        m.optJSONArray("fonctions")?.let { f -> (0 until f.length()).map { f.getString(it) } }

    @Test
    fun nbaAnnonceDansFonctions() {
        val (s, _) = session()
        s.recevoir(hello())
        assertEquals(listOf("nba"), fonctions(envoyes.single()))
        val (s2, _) = session(pilotage = PilotageFactice())
        s2.recevoir(hello())
        assertEquals(listOf("apps", "reglages", "nba"), fonctions(envoyes.single()))
        // Sans box score : ni annonce ni réponse
        val (s3, _) = session(avecNba = false)
        s3.recevoir(hello())
        assertFalse(envoyes.single().has("fonctions"))
        s3.recevoir(msg("nba_etat"))
        s3.recevoir(msg("nba_afficher", "affichage" to "resume"))
        assertEquals(1, envoyes.size)
        assertFalse(s3.abonneNba)
    }

    @Test
    fun rienAvantLAuthentification() {
        val (s, _) = session()
        s.recevoir(msg("nba_etat"))
        s.recevoir(msg("nba_afficher", "affichage" to "details", "id" to "LAL-BOS"))
        s.recevoir(msg("nba_choisir", "id" to "LAL-BOS"))
        assertTrue(envoyes.isEmpty())
        assertTrue(nba.demandes.isEmpty())
        assertFalse(s.abonneNba)
    }

    @Test
    fun etatEtAbonnement() {
        val (s, hote) = connectee()
        s.envoyerNbaSiAbonne(Protocole.nba(nba.etat))
        assertTrue("pas abonné : rien", envoyes.isEmpty())
        s.recevoir(msg("nba_etat"))
        assertTrue(s.abonneNba)
        assertEquals(1, nba.abonnements)
        val m = envoyes.single()
        assertEquals("nba", m.getString("t"))
        assertEquals(1, m.getInt("v"))
        assertEquals(setOf("v", "t", "matchs", "choisi", "affichage", "decalage"), m.keys().asSequence().toSet())
        val match = m.getJSONArray("matchs").getJSONObject(0)
        assertEquals(setOf("id", "ext", "dom", "statut", "direct"), match.keys().asSequence().toSet())
        assertEquals("LAL-BOS", match.getString("id"))
        assertEquals("4e QT · 2:31", match.getString("statut"))
        assertTrue(match.getBoolean("direct"))
        val ext = match.getJSONObject("ext")
        assertEquals(setOf("code", "nom", "score", "couleur", "texte"), ext.keys().asSequence().toSet())
        assertEquals("LAL", ext.getString("code"))
        assertEquals("Lakers", ext.getString("nom"))
        assertEquals(98, ext.get("score"))
        assertEquals("#6c3fb5", ext.getString("couleur"))
        assertEquals("#f2b631", ext.getString("texte"))
        assertEquals(102, match.getJSONObject("dom").getInt("score"))
        assertEquals("LAL-BOS", m.getString("choisi"))
        assertEquals("cache", m.getString("affichage"))
        assertEquals(0, m.get("decalage"))
        assertTrue(hote.journaux.contains("nba : état demandé"))
        // Journal : jamais de score
        assertTrue(hote.journaux.none { it.contains("98") || it.contains("102") })

        // Changement vu par la TV : envoyé aux abonnés
        envoyes.clear()
        nba.etat = nba.etat.copy(decalage = 30, erreur = EtatNba.ERREUR_SOURCE)
        s.envoyerNbaSiAbonne(Protocole.nba(nba.etat))
        assertEquals(30, envoyes.single().getInt("decalage"))
        assertEquals("source", envoyes.single().getString("erreur"))

        // Nouvelle connexion : pas abonnée tant qu'elle n'a pas redemandé
        s.terminer()
        val (s2, _) = connectee()
        assertFalse(s2.abonneNba)
    }

    @Test
    fun aucunMatchChoisiNull() {
        val m = JSONObject(Protocole.nba(EtatNba(emptyList(), null, Affichage.CACHE, 15, EtatNba.ERREUR_ACCESSIBILITE)))
        assertEquals(0, m.getJSONArray("matchs").length())
        assertTrue(m.has("choisi"))
        assertTrue(m.isNull("choisi"))
        assertEquals("accessibilite", m.getString("erreur"))
        assertEquals(15, m.getInt("decalage"))
    }

    @Test
    fun afficherEtChoisir() {
        val (s, hote) = connectee()
        s.recevoir(msg("nba_afficher", "affichage" to "details", "id" to "LAL-BOS"))
        assertEquals(listOf("afficher details LAL-BOS"), nba.demandes)
        assertEquals("details", envoyes.last().getString("affichage"))
        assertTrue(hote.journaux.contains("nba : affichage demandé"))
        // Identifiant inconnu : transmis (la TV l'ignore), le format s'applique quand même
        s.recevoir(msg("nba_afficher", "affichage" to "resume", "id" to "XXX-YYY"))
        assertEquals("afficher resume XXX-YYY", nba.demandes.last())
        assertEquals("resume", envoyes.last().getString("affichage"))
        assertEquals("LAL-BOS", envoyes.last().getString("choisi"))
        // Sans identifiant (« cache »)
        s.recevoir(msg("nba_afficher", "affichage" to "cache"))
        assertEquals("afficher cache null", nba.demandes.last())
        assertEquals("cache", envoyes.last().getString("affichage"))
        s.recevoir(msg("nba_choisir", "id" to "LAL-BOS"))
        assertEquals("choisir LAL-BOS", nba.demandes.last())
        assertEquals("LAL-BOS", envoyes.last().getString("choisi"))
    }

    @Test
    fun formatOuIdentifiantIllisiblesIgnores() {
        val (s, _) = connectee()
        // Format inconnu : rien n'est demandé, l'état actuel est renvoyé (le téléphone se recale)
        for (a in listOf("plein_ecran", 2, null, "")) {
            envoyes.clear()
            s.recevoir(msg("nba_afficher", "affichage" to a, "id" to "LAL-BOS"))
            assertEquals("nba", envoyes.single().getString("t"))
        }
        // `id` qui n'est pas une chaîne courte : pas de choix
        for (id in listOf(12, "", "x".repeat(21), null)) {
            envoyes.clear()
            s.recevoir(msg("nba_choisir", "id" to id))
            assertEquals("nba", envoyes.single().getString("t"))
        }
        s.recevoir(msg("nba_afficher", "affichage" to "resume", "id" to 42))
        assertEquals(listOf("afficher resume null"), nba.demandes)
    }
}
