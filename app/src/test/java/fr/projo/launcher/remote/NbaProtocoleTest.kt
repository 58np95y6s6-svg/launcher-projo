package fr.projo.launcher.remote

import fr.projo.launcher.boxscore.Affichage
import fr.projo.launcher.boxscore.EquipeEtat
import fr.projo.launcher.boxscore.EtatNba
import fr.projo.launcher.boxscore.EtatPari
import fr.projo.launcher.boxscore.MatchEtat
import fr.projo.launcher.boxscore.PariEtat
import fr.projo.launcher.boxscore.ParisNba
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

    /** Textes de pari reçus. */
    val textes = mutableListOf<String>()

    override fun ajouterParis(texte: String, rappel: (EtatNba) -> Unit) {
        textes += texte
        demandes += "paris ajouter"
        val lignes = ParisNba.lire(texte).mapIndexed { i, _ -> PariEtat("p${etat.paris.size + i + 1}", "ligne", EtatPari.ATTENTE) }
        etat = etat.copy(paris = etat.paris + lignes)
        rappel(etat)
    }

    override fun effacerParis(rappel: (EtatNba) -> Unit) {
        demandes += "paris effacer"
        etat = etat.copy(paris = emptyList())
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
        assertEquals(listOf("nba", "nba_paris"), fonctions(envoyes.single()))
        val (s2, _) = session(pilotage = PilotageFactice())
        s2.recevoir(hello())
        assertEquals(listOf("apps", "reglages", "nba", "nba_paris"), fonctions(envoyes.single()))
        // Sans box score : ni annonce ni réponse
        val (s3, _) = session(avecNba = false)
        s3.recevoir(hello())
        assertFalse(envoyes.single().has("fonctions"))
        s3.recevoir(msg("nba_etat"))
        s3.recevoir(msg("nba_afficher", "affichage" to "resume"))
        s3.recevoir(msg("nba_paris_ajouter", "texte" to "Tyrese Maxey Plus de 19,5 points"))
        s3.recevoir(msg("nba_paris_effacer"))
        assertEquals(1, envoyes.size)
        assertFalse(s3.abonneNba)
    }

    @Test
    fun rienAvantLAuthentification() {
        val (s, _) = session()
        s.recevoir(msg("nba_etat"))
        s.recevoir(msg("nba_afficher", "affichage" to "details", "id" to "LAL-BOS"))
        s.recevoir(msg("nba_choisir", "id" to "LAL-BOS"))
        s.recevoir(msg("nba_paris_ajouter", "texte" to "Tyrese Maxey Plus de 19,5 points"))
        s.recevoir(msg("nba_paris_effacer"))
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
        assertEquals(setOf("v", "t", "matchs", "choisi", "affichage", "decalage", "paris"), m.keys().asSequence().toSet())
        assertEquals(0, m.getJSONArray("paris").length())
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

    // ------------------------------------------------------------------ paris suivis

    @Test
    fun parisAjouterEtEffacer() {
        val (s, hote) = connectee()
        s.recevoir(msg("nba_paris_ajouter", "texte" to TICKET))
        assertEquals(listOf("paris ajouter"), nba.demandes)
        assertEquals(TICKET, nba.textes.single())
        // Réponse : `nba` avec les lignes, à ce téléphone (même sans abonnement)
        val m = envoyes.single()
        assertEquals("nba", m.getString("t"))
        assertEquals(2, m.getJSONArray("paris").length())
        s.recevoir(msg("nba_paris_effacer"))
        assertEquals("paris effacer", nba.demandes.last())
        assertEquals(0, envoyes.last().getJSONArray("paris").length())
        // Journal : jamais le texte, une cote ni une mise
        assertTrue(hote.journaux.contains("nba : pari reçu"))
        assertTrue(hote.journaux.contains("nba : effacement des paris demandé"))
        assertTrue(hote.journaux.none { it.contains("Maxey") || it.contains("19,5") || it.contains("1,50") || it.contains("8,75") })
    }

    @Test
    fun parisTexteIllisibleIgnore() {
        val (s, _) = connectee()
        // Pas une chaîne, vide : rien d'ajouté, l'état actuel est renvoyé
        for (t in listOf(42, null, "", "   ", JSONObject())) {
            envoyes.clear()
            s.recevoir(msg("nba_paris_ajouter", "texte" to t))
            assertEquals("nba", envoyes.single().getString("t"))
        }
        assertTrue(nba.demandes.isEmpty())
        // Texte trop long : 4000 caractères au plus transmis, sans la ligne entamée
        val long = "Tyrese Maxey Plus de 19,5 points\n" + "x".repeat(5000)
        s.recevoir(msg("nba_paris_ajouter", "texte" to long))
        assertEquals("Tyrese Maxey Plus de 19,5 points", nba.textes.single())
    }

    @Test
    fun parisDansLEtatNba() {
        val e = nba.etat.copy(
            affichage = Affichage.PARI,
            paris = listOf(
                PariEtat("p1", "76ers vainqueurs", EtatPari.EN_COURS, "PHI-IND", -3, null),
                PariEtat("p2", "T. Maxey · + de 19,5 pts", EtatPari.GAGNE, "PHI-IND", 23, 19.5),
                PariEtat("p3", "Total du match · - de 221,5 pts", EtatPari.ATTENTE, null, null, 221.5),
                PariEtat("p4", "Philadelphie 76ers (-4,5) · Handicap", EtatPari.NON_SUIVI),
                PariEtat("p5", "J. Embiid · - de 10 rebonds", EtatPari.PERDU, "PHI-IND", 11, 10.0),
            ),
        )
        val m = JSONObject(Protocole.nba(e))
        assertEquals("pari", m.getString("affichage"))
        val p = m.getJSONArray("paris")
        assertEquals(5, p.length())
        val vainqueur = p.getJSONObject(0)
        assertEquals(setOf("id", "libelle", "etat", "match", "valeur"), vainqueur.keys().asSequence().toSet())
        assertEquals("p1", vainqueur.getString("id"))
        assertEquals("76ers vainqueurs", vainqueur.getString("libelle"))
        assertEquals("en_cours", vainqueur.getString("etat"))
        assertEquals("PHI-IND", vainqueur.getString("match"))
        assertEquals(-3, vainqueur.getInt("valeur"))
        val maxey = p.getJSONObject(1)
        assertEquals(setOf("id", "libelle", "etat", "match", "valeur", "seuil"), maxey.keys().asSequence().toSet())
        assertEquals("gagne", maxey.getString("etat"))
        assertEquals(23, maxey.getInt("valeur"))
        assertEquals(19.5, maxey.getDouble("seuil"), 0.0)
        val total = p.getJSONObject(2)
        assertEquals(setOf("id", "libelle", "etat", "seuil"), total.keys().asSequence().toSet())
        assertEquals("attente", total.getString("etat"))
        assertEquals(setOf("id", "libelle", "etat"), p.getJSONObject(3).keys().asSequence().toSet())
        assertEquals("non_suivi", p.getJSONObject(3).getString("etat"))
        assertEquals("perdu", p.getJSONObject(4).getString("etat"))
        assertEquals(10.0, p.getJSONObject(4).getDouble("seuil"), 0.0)
    }

    @Test
    fun afficherFormatPari() {
        val (s, _) = connectee()
        s.recevoir(msg("nba_afficher", "affichage" to "pari", "id" to "LAL-BOS"))
        assertEquals(listOf("afficher pari LAL-BOS"), nba.demandes)
        assertEquals("pari", envoyes.last().getString("affichage"))
    }

    private companion object {
        const val TICKET = "Tyrese Maxey + de 19,5\nNombre de points du joueur (paliers)\nPhiladelphie 76ers\nVainqueur du match\nCote 8,75\nMise 1,50 €"
    }
}
