package fr.projo.launcher.remote

import fr.projo.launcher.boxscore.Affichage
import fr.projo.launcher.boxscore.EquipeEtat
import fr.projo.launcher.boxscore.EtatNba
import fr.projo.launcher.boxscore.EtatPari
import fr.projo.launcher.boxscore.GroupeEtat
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

    private var combines = 0

    override fun ajouterParis(texte: String, rappel: (EtatNba) -> Unit) {
        textes += texte
        demandes += "paris ajouter"
        val g = "g${++combines}"
        val lignes = ParisNba.lire(texte).mapIndexed { i, _ -> PariEtat("p${etat.paris.size + i + 1}", "ligne", EtatPari.ATTENTE, groupe = g) }
        val groupe = GroupeEtat(g, "Combiné $combines", ParisNba.cote(texte), EtatPari.ATTENTE, 0, lignes.size, 0, true)
        etat = etat.copy(paris = etat.paris + lignes, groupes = etat.groupes.map { it.copy(ouvert = false) } + groupe)
        rappel(etat)
    }

    override fun effacerParis(groupe: String?, rappel: (EtatNba) -> Unit) {
        demandes += "paris effacer $groupe"
        etat = if (groupe == null) {
            etat.copy(paris = emptyList(), groupes = emptyList())
        } else {
            etat.copy(paris = etat.paris.filter { it.groupe != groupe }, groupes = etat.groupes.filter { it.id != groupe })
        }
        rappel(etat)
    }

    override fun ouvrirParis(groupe: String, rappel: (EtatNba) -> Unit) {
        demandes += "paris ouvrir $groupe"
        if (etat.groupes.any { it.id == groupe }) etat = etat.copy(groupes = etat.groupes.map { it.copy(ouvert = it.id == groupe) })
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
        assertEquals(listOf("nba", "nba_paris", "nba_paris_groupes"), fonctions(envoyes.single()))
        val (s2, _) = session(pilotage = PilotageFactice())
        s2.recevoir(hello())
        assertEquals(listOf("apps", "reglages", "nba", "nba_paris", "nba_paris_groupes"), fonctions(envoyes.single()))
        // Sans box score : ni annonce ni réponse
        val (s3, _) = session(avecNba = false)
        s3.recevoir(hello())
        assertFalse(envoyes.single().has("fonctions"))
        s3.recevoir(msg("nba_etat"))
        s3.recevoir(msg("nba_afficher", "affichage" to "resume"))
        s3.recevoir(msg("nba_paris_ajouter", "texte" to "Tyrese Maxey Plus de 19,5 points"))
        s3.recevoir(msg("nba_paris_effacer"))
        s3.recevoir(msg("nba_paris_ouvrir", "groupe" to "g1"))
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
        s.recevoir(msg("nba_paris_effacer", "groupe" to "g1"))
        s.recevoir(msg("nba_paris_ouvrir", "groupe" to "g1"))
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
        assertEquals(setOf("v", "t", "matchs", "choisi", "affichage", "decalage", "paris", "groupes"), m.keys().asSequence().toSet())
        assertEquals(0, m.getJSONArray("paris").length())
        assertEquals(0, m.getJSONArray("groupes").length())
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
        assertEquals("paris effacer null", nba.demandes.last())
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

    // ------------------------------------------------------------------ combinés

    @Test
    fun combinesDansLEtatNba() {
        val e = nba.etat.copy(
            paris = listOf(
                PariEtat("p1", "76ers vainqueurs", EtatPari.EN_COURS, "PHI-MIL", 7, null, groupe = "g1"),
                PariEtat("p2", "76ers · handicap \u22124,5", EtatPari.EN_COURS, "PHI-MIL", 7, 4.5, handicap = true, groupe = "g2"),
                PariEtat("p3", "Joel Embiid + de 1,5 · Nombre de fautes du joueur", EtatPari.NON_SUIVI, groupe = "g2"),
            ),
            groupes = listOf(
                GroupeEtat("g1", "Combiné 1", null, EtatPari.EN_COURS, 0, 1, 0, false),
                GroupeEtat("g2", "Combiné 2", "5,10", EtatPari.GAGNE, 1, 1, 1, true),
            ),
        )
        val m = JSONObject(Protocole.nba(e))
        val p = m.getJSONArray("paris")
        assertEquals(setOf("id", "libelle", "etat", "match", "valeur", "groupe"), p.getJSONObject(0).keys().asSequence().toSet())
        assertEquals("g1", p.getJSONObject(0).getString("groupe"))
        // Le handicap reste une donnée de la TV
        assertEquals(setOf("id", "libelle", "etat", "match", "valeur", "seuil", "groupe"), p.getJSONObject(1).keys().asSequence().toSet())
        assertEquals(setOf("id", "libelle", "etat", "groupe"), p.getJSONObject(2).keys().asSequence().toSet())
        assertEquals("g2", p.getJSONObject(2).getString("groupe"))
        val g = m.getJSONArray("groupes")
        assertEquals(2, g.length())
        val g1 = g.getJSONObject(0)
        // Sans cote : champ absent
        assertEquals(setOf("id", "nom", "etat", "gagnees", "suivies", "nonSuivies", "ouvert"), g1.keys().asSequence().toSet())
        assertEquals("g1", g1.getString("id"))
        assertEquals("Combiné 1", g1.getString("nom"))
        assertEquals("en_cours", g1.getString("etat"))
        assertEquals(0, g1.get("gagnees"))
        assertEquals(1, g1.get("suivies"))
        assertEquals(0, g1.get("nonSuivies"))
        assertEquals(false, g1.get("ouvert"))
        val g2 = g.getJSONObject(1)
        assertEquals(setOf("id", "nom", "cote", "etat", "gagnees", "suivies", "nonSuivies", "ouvert"), g2.keys().asSequence().toSet())
        assertEquals("5,10", g2.getString("cote"))
        assertEquals("gagne", g2.getString("etat"))
        assertEquals(1, g2.get("gagnees"))
        assertEquals(1, g2.get("nonSuivies"))
        assertEquals(true, g2.get("ouvert"))
    }

    @Test
    fun combinesOuvrirEtEffacer() {
        val (s, hote) = connectee()
        s.recevoir(msg("nba_paris_ajouter", "texte" to TICKET))
        s.recevoir(msg("nba_paris_ajouter", "texte" to "Joel Embiid Plus de 9,5 rebonds"))
        var m = envoyes.last()
        assertEquals(listOf("g1", "g2"), (0 until m.getJSONArray("groupes").length()).map { m.getJSONArray("groupes").getJSONObject(it).getString("id") })
        assertEquals("8,75", m.getJSONArray("groupes").getJSONObject(0).getString("cote"))
        assertTrue(m.getJSONArray("groupes").getJSONObject(1).getBoolean("ouvert"))
        assertEquals(listOf("g1", "g1", "g2"), (0 until m.getJSONArray("paris").length()).map { m.getJSONArray("paris").getJSONObject(it).getString("groupe") })
        // `nba_paris_ouvrir` : la TV répond par `nba`
        envoyes.clear()
        s.recevoir(msg("nba_paris_ouvrir", "groupe" to "g1"))
        assertEquals("paris ouvrir g1", nba.demandes.last())
        m = envoyes.single()
        assertTrue(m.getJSONArray("groupes").getJSONObject(0).getBoolean("ouvert"))
        assertTrue(hote.journaux.contains("nba : combiné demandé"))
        // Inconnu : transmis (la TV l'ignore) ; illisible : rien de demandé, l'état actuel est renvoyé
        s.recevoir(msg("nba_paris_ouvrir", "groupe" to "g9"))
        assertEquals("paris ouvrir g9", nba.demandes.last())
        val n = nba.demandes.size
        for (g in listOf(3, "", "x".repeat(21), null, JSONObject())) {
            envoyes.clear()
            s.recevoir(msg("nba_paris_ouvrir", "groupe" to g))
            assertEquals("nba", envoyes.single().getString("t"))
        }
        // `nba_paris_effacer {groupe}` : ce combiné ; `groupe` illisible : rien n'est retiré
        for (g in listOf(3, "", "x".repeat(21), JSONObject())) {
            envoyes.clear()
            s.recevoir(msg("nba_paris_effacer", "groupe" to g))
            assertEquals(2, envoyes.single().getJSONArray("groupes").length())
        }
        assertEquals(n, nba.demandes.size)
        s.recevoir(msg("nba_paris_effacer", "groupe" to "g1"))
        assertEquals("paris effacer g1", nba.demandes.last())
        m = envoyes.last()
        assertEquals(1, m.getJSONArray("groupes").length())
        assertEquals(1, m.getJSONArray("paris").length())
        assertTrue(hote.journaux.contains("nba : effacement d'un combiné demandé"))
        // `groupe` null : tous
        s.recevoir(msg("nba_paris_effacer", "groupe" to JSONObject.NULL))
        assertEquals("paris effacer null", nba.demandes.last())
        assertEquals(0, envoyes.last().getJSONArray("groupes").length())
        // Journal : jamais le texte ni une cote
        assertTrue(hote.journaux.none { it.contains("Maxey") || it.contains("Embiid") || it.contains("8,75") })
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
