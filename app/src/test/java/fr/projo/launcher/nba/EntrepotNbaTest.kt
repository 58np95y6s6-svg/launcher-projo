package fr.projo.launcher.nba

import fr.projo.launcher.boxscore.LectureNba
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
import java.util.Collections

/**
 * Faux flux ESPN : les extraits de app/src/test/resources/nba-carte/ (le Thunder, son dernier
 * match, les mêmes moyennes pour chaque joueur), réponses remplaçables ; compte les requêtes.
 */
internal class FauxEspn : ChargeurEspn {
    val requetes: MutableList<RequeteEspn> = Collections.synchronizedList(ArrayList())
    val remplacees = HashMap<RequeteEspn, ReponseEspn>()

    /** Réponse par défaut d'un type de requête (après [remplacees]). */
    var parType: (RequeteEspn) -> ReponseEspn? = { null }

    override fun charger(requete: RequeteEspn): ReponseEspn {
        requetes += requete
        synchronized(remplacees) { remplacees[requete] }?.let { return it }
        parType(requete)?.let { return it }
        return when (requete) {
            RequeteEspn.Classement -> ok("classement.json")
            RequeteEspn.Equipes -> ok("equipes.json")
            is RequeteEspn.Calendrier -> if (requete.idEquipe == "25") ok("calendrier.json") else ReponseEspn.Echec(404)
            is RequeteEspn.Effectif -> if (requete.idEquipe == "25") ok("effectif.json") else ReponseEspn.Echec(404)
            is RequeteEspn.Resume -> if (requete.idMatch == "401909975") ok("resume.json") else ReponseEspn.Echec(404)
            is RequeteEspn.Joueur -> ok("joueur.json")
        }
    }

    fun nombre(filtre: (RequeteEspn) -> Boolean): Int = synchronized(requetes) { requetes.count(filtre) }

    companion object {
        fun ok(nom: String) = ReponseEspn.Ok(exempleCarte(nom))
    }
}

/** Cache disque en mémoire. */
internal class DisqueMemoire : StockageNba {
    val fichiers = HashMap<String, String>()
    override fun lire(nom: String): String? = fichiers[nom]
    override fun ecrire(nom: String, texte: String) {
        fichiers[nom] = texte
    }
}

/**
 * Données du design NBA sans réseau ni Android : fiche assemblée (morceaux en parallèle, chacun
 * facultatif), caches et leurs durées, classement (cache disque, hors ligne), équipes (une fois
 * par jour), logos, rythme des rafraîchissements. Robolectric : org.json est celui d'Android.
 */
@RunWith(RobolectricTestRunner::class)
class EntrepotNbaTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val minute = 60_000L
    private val heure = 60 * minute

    /** 12 novembre 2026, 22 h (UTC). */
    private var t = LectureNba.dateUtc("2026-11-12T22:00Z")
    private val faux = FauxEspn()
    private val journal: MutableList<String> = Collections.synchronizedList(ArrayList())
    private val etat = MutableStateFlow<ClassementNba?>(null)
    private val entrepot = EntrepotNba(faux, { t }, etat, { journal += it })

    private fun fiche(code: String = "OKC") = runBlocking { entrepot.fiche(code) }

    private fun nombre(filtre: (RequeteEspn) -> Boolean) = faux.nombre(filtre)

    // ------------------------------------------------------------------ fiche

    @Test
    fun ficheComplete() {
        val f = fiche()!!
        assertEquals("OKC", f.code)
        assertEquals("Oklahoma City Thunder", f.nomComplet)
        assertEquals(MatchEquipe("POR", false, LectureNba.dateUtc("2026-11-12T03:00Z"), 108, 118), f.dernier)
        assertEquals(false, f.dernier?.victoire)
        assertEquals(MatchEquipe("LAC", false, LectureNba.dateUtc("2026-11-14T03:30Z"), null, null), f.prochain)
        // Cinq de départ du dernier match, avec les moyennes de la saison
        assertEquals(listOf("I. Hartenstein", "A. Mara", "A. Caruso", "S. Gilgeous-Alexander", "J. McCain"), f.titulaires.map { it.nom })
        assertEquals(JoueurNba("I. Hartenstein", "55", "C", false, 24.9, 8.0, 10.1, 3.7), f.titulaires[0])
        assertEquals(
            setOf("4222252", "5174983", "2991350", "4278073", "4683778"),
            faux.requetes.filterIsInstance<RequeteEspn.Joueur>().map { it.idJoueur }.toSet(),
        )
        // Effectif complet, sans moyennes
        assertEquals(18, f.effectif.size)
        assertEquals(JoueurNba("C. Holmgren", "7", "C", true, null, null, null, null), f.effectif.first { it.nom == "C. Holmgren" })
        assertEquals(6, f.effectif.count { it.blesse })
        // Blessures de l'effectif, zones du résumé (par identifiant : deux « J. Williams »)
        assertEquals(
            listOf(
                BlessureNba("C. Holmgren", "Absent", "genou"),
                BlessureNba("A. Mitchell", "Au jour le jour", "cheville"),
                BlessureNba("T. Sorber", "Absent", "genou"),
                BlessureNba("C. Wallace", "Absent", "épaule"),
                BlessureNba("J. Williams", "Absent", null),
                BlessureNba("J. Williams", "Absent", "dos"),
            ),
            f.blessures,
        )
        // Une requête par morceau (5 moyennes)
        assertEquals(1, nombre { it is RequeteEspn.Calendrier })
        assertEquals(1, nombre { it is RequeteEspn.Effectif })
        assertEquals(1, nombre { it is RequeteEspn.Resume })
        assertEquals(5, nombre { it is RequeteEspn.Joueur })
        assertEquals(0, nombre { it == RequeteEspn.Equipes })
    }

    @Test
    fun ficheParCodeEspnEtEquipeInconnue() {
        assertEquals("OKC", fiche("okc")?.code)
        assertNull(fiche("XYZ"))
        // Code ESPN accepté (GS → GSW) : Golden State n'a pas d'extrait, donc rien
        assertNull(fiche("GS"))
        assertTrue(faux.requetes.filterIsInstance<RequeteEspn.Calendrier>().any { it.idEquipe == "9" })
    }

    @Test
    fun ficheSansEffectif() {
        faux.remplacees[RequeteEspn.Effectif("25")] = ReponseEspn.Echec(500)
        val f = fiche()!!
        assertTrue(f.effectif.isEmpty())
        assertEquals(5, f.titulaires.size)
        // Blessés du résumé du dernier match, traduits
        assertEquals(
            listOf(
                BlessureNba("A. Mitchell", "Au jour le jour", "cheville"),
                BlessureNba("J. Williams", "Absent", "dos"),
                BlessureNba("T. Sorber", "Absent", "genou"),
                BlessureNba("C. Wallace", "Absent", "épaule"),
                BlessureNba("C. Holmgren", "Absent", "genou"),
            ),
            f.blessures,
        )
        assertTrue("nba carte : effectif indisponible (500)" in journal)
    }

    @Test
    fun ficheSansCalendrier() {
        faux.remplacees[RequeteEspn.Calendrier("25")] = ReponseEspn.Echec(404)
        val f = fiche()!!
        assertEquals("Oklahoma City Thunder", f.nomComplet)
        assertNull(f.dernier)
        assertNull(f.prochain)
        assertTrue(f.titulaires.isEmpty())
        assertEquals(18, f.effectif.size)
        // Sans résumé : statuts de l'effectif, sans zone
        assertEquals(6, f.blessures.size)
        assertTrue(f.blessures.all { it.zone == null && it.statut in setOf("Absent", "Au jour le jour") })
        assertEquals(0, nombre { it is RequeteEspn.Resume })
    }

    @Test
    fun ficheSansResumeNiMoyennes() {
        faux.remplacees[RequeteEspn.Resume("401909975")] = ReponseEspn.Ok("<html>")
        val f = fiche()!!
        assertNotNull(f.dernier)
        assertTrue(f.titulaires.isEmpty())
        assertEquals(6, f.blessures.size)
        assertTrue("nba carte : résumé illisible" in journal)
    }

    @Test
    fun ficheAvecUneMoyenneManquante() {
        faux.remplacees[RequeteEspn.Joueur("4278073")] = ReponseEspn.Echec(500)
        faux.remplacees[RequeteEspn.Joueur("5174983")] = FauxEspn.ok("joueur_sans_saison.json")
        val f = fiche()!!
        assertEquals(5, f.titulaires.size)
        val sga = f.titulaires.first { it.nom == "S. Gilgeous-Alexander" }
        assertEquals(JoueurNba("S. Gilgeous-Alexander", "2", "G", false, null, null, null, null), sga)
        assertNull(f.titulaires.first { it.nom == "A. Mara" }.points)
        assertEquals(8.0, f.titulaires.first { it.nom == "I. Hartenstein" }.points!!, 0.0)
        assertTrue("nba carte : moyennes indisponibles (500)" in journal)
    }

    @Test
    fun ficheIndisponible() {
        faux.parType = { if (it is RequeteEspn.Calendrier || it is RequeteEspn.Effectif) ReponseEspn.Echec() else null }
        assertNull(fiche())
        assertTrue("nba carte : fiche indisponible" in journal)
        // Journal : types d'événements seulement, ni adresse ni nom de joueur
        assertTrue(journal.isNotEmpty())
        for (ligne in journal) {
            assertTrue(ligne, ligne.startsWith("nba carte : "))
            assertFalse(ligne, ligne.contains("http") || ligne.contains("espn") || ligne.contains("Williams"))
        }
    }

    @Test
    fun blesseParmiLesTitulaires() {
        // Caruso blessé depuis le dernier match
        faux.remplacees[RequeteEspn.Effectif("25")] = ReponseEspn.Ok(
            modifie("effectif.json") { r ->
                val a = r.getJSONArray("athletes")
                for (i in 0 until a.length()) {
                    val j = a.getJSONObject(i)
                    if (j.getString("id") == "2991350") {
                        j.put("injuries", org.json.JSONArray().put(org.json.JSONObject().put("status", "Questionable").put("details", org.json.JSONObject().put("type", "Wrist"))))
                    }
                }
            },
        )
        val f = fiche()!!
        assertTrue(f.titulaires.first { it.nom == "A. Caruso" }.blesse)
        assertEquals(1, f.titulaires.count { it.blesse })
        assertTrue(BlessureNba("A. Caruso", "Incertain", "poignet") in f.blessures)
    }

    // ------------------------------------------------------------------ caches

    @Test
    fun cachesEtLeursDurees() {
        fiche()
        fiche()
        assertEquals(1, nombre { it is RequeteEspn.Calendrier })
        assertEquals(5, nombre { it is RequeteEspn.Joueur })
        // 29 min : tout est encore en cache
        t += 29 * minute
        fiche()
        assertEquals(1, nombre { it is RequeteEspn.Calendrier })
        // 31 min : calendrier rechargé, pas l'effectif (1 h)
        t += 2 * minute
        fiche()
        assertEquals(2, nombre { it is RequeteEspn.Calendrier })
        assertEquals(1, nombre { it is RequeteEspn.Effectif })
        // 1 h 01 : effectif rechargé ; résumé et moyennes gardés 6 h
        t += 30 * minute
        fiche()
        assertEquals(3, nombre { it is RequeteEspn.Calendrier })
        assertEquals(2, nombre { it is RequeteEspn.Effectif })
        assertEquals(1, nombre { it is RequeteEspn.Resume })
        assertEquals(5, nombre { it is RequeteEspn.Joueur })
        t += 5 * heure
        fiche()
        assertEquals(2, nombre { it is RequeteEspn.Resume })
        assertEquals(10, nombre { it is RequeteEspn.Joueur })
    }

    @Test
    fun echecsPasGardesEnCache() {
        faux.remplacees[RequeteEspn.Effectif("25")] = ReponseEspn.Echec()
        assertTrue(fiche()!!.effectif.isEmpty())
        faux.remplacees.clear()
        assertEquals(18, fiche()!!.effectif.size)
        assertEquals(2, nombre { it is RequeteEspn.Effectif })
        assertEquals(1, nombre { it is RequeteEspn.Calendrier })
    }

    @Test
    fun heureQuiRecule() {
        fiche()
        t -= 10 * minute
        fiche()
        assertEquals(2, nombre { it is RequeteEspn.Calendrier })
    }

    // ------------------------------------------------------------------ classement

    @Test
    fun classementChargeEtGardeSurDisque() {
        val disque = DisqueMemoire()
        entrepot.brancher(disque)
        assertNull(etat.value)
        entrepot.charger()
        val c = etat.value!!
        assertEquals(30, c.equipes.size)
        assertEquals(t, c.misAJour)
        assertFalse(c.horsLigne)
        assertTrue(EntrepotNba.FICHIER_CLASSEMENT in disque.fichiers)

        // Redémarrage : le classement revient du disque, à l'identique
        val etat2 = MutableStateFlow<ClassementNba?>(null)
        EntrepotNba(FauxEspn(), { t }, etat2, {}).brancher(disque)
        assertEquals(c, etat2.value)
    }

    @Test
    fun classementHorsLigne() {
        entrepot.charger()
        val c = etat.value!!
        t += 11 * minute
        faux.remplacees[RequeteEspn.Classement] = ReponseEspn.Echec(403)
        entrepot.charger()
        // Le dernier classement reste affiché, marqué hors ligne
        assertEquals(c.copy(horsLigne = true), etat.value)
        assertTrue("nba carte : classement indisponible (403)" in journal)
        // Classement illisible : pareil
        faux.remplacees[RequeteEspn.Classement] = ReponseEspn.Ok("{}")
        entrepot.charger()
        assertEquals(c.copy(horsLigne = true), etat.value)
        assertTrue("nba carte : classement illisible" in journal)
        // De retour
        faux.remplacees.clear()
        t += minute
        entrepot.charger()
        assertFalse(etat.value!!.horsLigne)
        assertEquals(t, etat.value!!.misAJour)
    }

    @Test
    fun premierDemarrageHorsLigne() {
        faux.remplacees[RequeteEspn.Classement] = ReponseEspn.Echec()
        entrepot.charger()
        assertNull(etat.value)
        assertTrue("nba carte : classement indisponible" in journal)
    }

    @Test
    fun cacheDisqueIllisible() {
        val disque = DisqueMemoire()
        disque.fichiers[EntrepotNba.FICHIER_CLASSEMENT] = "{\"equipes\":"
        disque.fichiers[EntrepotNba.FICHIER_EQUIPES] = "[]"
        entrepot.brancher(disque)
        assertNull(etat.value)
        assertTrue("nba carte : cache disque illisible" in journal)
    }

    @Test
    fun fichiersSurDisque() {
        val f = FichiersNba(dossier.root.resolve("nba-carte"))
        assertNull(f.lire("classement.json"))
        f.ecrire("classement.json", "un")
        f.ecrire("classement.json", "deux")
        assertEquals("deux", f.lire("classement.json"))
        assertEquals(listOf("classement.json"), dossier.root.resolve("nba-carte").list()!!.toList())

        // Classement relu d'un vrai fichier
        entrepot.brancher(f)
        assertNull(etat.value)
        entrepot.charger()
        val etat2 = MutableStateFlow<ClassementNba?>(null)
        EntrepotNba(FauxEspn(), { t }, etat2, {}).brancher(FichiersNba(dossier.root.resolve("nba-carte")))
        assertEquals(etat.value, etat2.value)
    }

    // ------------------------------------------------------------------ équipes et logos

    @Test
    fun logos() {
        // Avant la liste des équipes : adresse ESPN habituelle
        assertEquals("https://a.espncdn.com/i/teamlogos/nba/500/gs.png", entrepot.logo("GSW"))
        assertEquals("https://a.espncdn.com/i/teamlogos/nba/500/utah.png", entrepot.logo("UTA"))
        assertEquals("https://a.espncdn.com/i/teamlogos/nba/500/okc.png", entrepot.logo("OKC"))
        // Avec la liste : son logo
        val liste = modifie("equipes.json") { r ->
            r.getJSONArray("sports").getJSONObject(0).getJSONArray("leagues").getJSONObject(0).getJSONArray("teams")
                .getJSONObject(0).getJSONObject("team").getJSONArray("logos").getJSONObject(0)
                .put("href", "https://a.espncdn.com/i/teamlogos/nba/500/nouveau-atl.png")
        }
        faux.remplacees[RequeteEspn.Equipes] = ReponseEspn.Ok(liste)
        entrepot.charger()
        assertEquals("https://a.espncdn.com/i/teamlogos/nba/500/nouveau-atl.png", entrepot.logo("ATL"))
        assertEquals("https://a.espncdn.com/i/teamlogos/nba/500/gs.png", entrepot.logo("GSW"))
        // Nom complet de la liste dans la fiche
        assertEquals("Oklahoma City Thunder", fiche()?.nomComplet)
    }

    @Test
    fun equipesUneFoisParJour() {
        val disque = DisqueMemoire()
        entrepot.brancher(disque)
        entrepot.charger()
        assertEquals(1, nombre { it == RequeteEspn.Equipes })
        assertTrue(EntrepotNba.FICHIER_EQUIPES in disque.fichiers)
        t += 10 * minute
        entrepot.charger()
        t += 23 * heure
        entrepot.charger()
        assertEquals(1, nombre { it == RequeteEspn.Equipes })
        t += heure
        entrepot.charger()
        assertEquals(2, nombre { it == RequeteEspn.Equipes })

        // Redémarrage : la liste revient du disque, pas de requête avant un jour
        val autre = FauxEspn()
        val e2 = EntrepotNba(autre, { t }, MutableStateFlow(null), {})
        e2.brancher(disque)
        e2.charger()
        assertEquals(0, autre.nombre { it == RequeteEspn.Equipes })
        assertEquals("https://a.espncdn.com/i/teamlogos/nba/500/okc.png", e2.logo("OKC"))
    }

    @Test
    fun equipesEnEchecReessayeesApresUneHeure() {
        faux.remplacees[RequeteEspn.Equipes] = ReponseEspn.Echec(500)
        entrepot.charger()
        t += 30 * minute
        entrepot.charger()
        assertEquals(1, nombre { it == RequeteEspn.Equipes })
        assertTrue("nba carte : équipes indisponibles (500)" in journal)
        t += 31 * minute
        entrepot.charger()
        assertEquals(2, nombre { it == RequeteEspn.Equipes })
        // Le classement n'en souffre pas
        assertFalse(etat.value!!.horsLigne)
    }

    // ------------------------------------------------------------------ rythme

    @Test
    fun rythmeDuClassement() {
        assertEquals(PlanClassement.Etape.Arret, entrepot.etape())
        entrepot.plan.actif = true
        // Aucun classement : tout de suite
        assertEquals(PlanClassement.Etape.Charger, entrepot.etape())
        entrepot.charger()
        assertEquals(PlanClassement.Etape.Attendre(10 * minute), entrepot.etape())
        t += 4 * minute
        assertEquals(PlanClassement.Etape.Attendre(6 * minute), entrepot.etape())
        t += 6 * minute
        assertEquals(PlanClassement.Etape.Charger, entrepot.etape())
        // Échecs : 1 min, 2 min, 4 min… jamais plus de 10 min
        faux.remplacees[RequeteEspn.Classement] = ReponseEspn.Echec()
        val attentes = (1..6).map {
            entrepot.charger()
            val e = entrepot.etape() as PlanClassement.Etape.Attendre
            t += e.ms
            assertEquals(PlanClassement.Etape.Charger, entrepot.etape())
            e.ms / minute
        }
        assertEquals(listOf(1L, 2L, 4L, 8L, 10L, 10L), attentes)
        faux.remplacees.clear()
        entrepot.charger()
        assertEquals(PlanClassement.Etape.Attendre(10 * minute), entrepot.etape())
        entrepot.plan.actif = false
        assertEquals(PlanClassement.Etape.Arret, entrepot.etape())
    }

    @Test
    fun classementDuDisqueRecent() {
        val disque = DisqueMemoire()
        entrepot.brancher(disque)
        entrepot.charger()
        // Redémarrage 3 min plus tard : pas de requête avant 7 min
        t += 3 * minute
        val autre = FauxEspn()
        val e2 = EntrepotNba(autre, { t }, MutableStateFlow(null), {})
        e2.brancher(disque)
        e2.plan.actif = true
        assertEquals(PlanClassement.Etape.Attendre(7 * minute), e2.etape())
        // Vieux de 12 min : tout de suite
        t += 9 * minute
        assertEquals(PlanClassement.Etape.Charger, e2.etape())
    }
}
