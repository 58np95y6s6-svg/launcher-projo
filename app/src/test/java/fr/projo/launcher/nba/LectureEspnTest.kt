package fr.projo.launcher.nba

import fr.projo.launcher.boxscore.LectureNba
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Lit un extrait raccourci d'un vrai flux ESPN (app/src/test/resources/nba-carte/). */
fun exempleCarte(nom: String): String =
    LectureEspnTest::class.java.getResource("/nba-carte/$nom")?.readText() ?: throw AssertionError("exemple introuvable : $nom")

/** Modifie un extrait (JSON) pour un cas particulier. */
fun modifie(nom: String, modification: (JSONObject) -> Unit): String = JSONObject(exempleCarte(nom)).also(modification).toString()

/**
 * Lecture des flux ESPN du design NBA sur de vrais extraits (saison 2026-27, raccourcis) :
 * classement et rangs, équipes et logos, calendrier (dernier et prochain match), effectif et
 * blessures, titulaires du dernier match, moyennes d'un joueur, traductions.
 * Robolectric : org.json est celui d'Android.
 */
@RunWith(RobolectricTestRunner::class)
class LectureEspnTest {

    // ------------------------------------------------------------------ classement

    @Test
    fun classementLesDeuxConferences() {
        val c = LectureEspn.classement(exempleCarte("classement.json"), 1_234L)
        assertEquals(1_234L, c.misAJour)
        assertFalse(c.horsLigne)
        assertEquals(30, c.equipes.size)
        // Codes ESPN convertis (GS, NY, SA, NO, UTAH, WSH)
        assertTrue(c.equipes.keys.containsAll(listOf("GSW", "NYK", "SAS", "NOP", "UTA", "WAS")))
        DivisionNba.entries.flatMap { it.equipes }.forEach { assertTrue("$it absent", it in c.equipes) }
        // Rangs de conférence : 1 à 15 une fois chacun, par conférence
        for (conf in ConferenceNba.entries) {
            val rangs = c.equipes.values.filter { DivisionNba.de(it.code)!!.conference == conf }.map { it.rangConference }.sorted()
            assertEquals((1..15).toList(), rangs)
        }
        assertEquals(BilanNba("BOS", 10, 2, 1, 1, 5, "9-1"), c.equipes["BOS"])
        assertEquals(BilanNba("MIL", 1, 11, 15, 5, -8, "1-9"), c.equipes["MIL"])
        assertEquals(BilanNba("LAL", 11, 2, 1, 1, 7, "8-2"), c.equipes["LAL"])
        assertEquals(BilanNba("OKC", 8, 4, 7, 3, -2, "6-4"), c.equipes["OKC"])
        assertEquals(BilanNba("SAS", 10, 2, 2, 1, 10, "10-0"), c.equipes["SAS"])
        assertEquals(BilanNba("UTA", 1, 11, 15, 5, -10, "0-10"), c.equipes["UTA"])
    }

    @Test
    fun rangsDeDivisionAvecEgalites() {
        val c = LectureEspn.classement(exempleCarte("classement.json"), 0L)
        fun ordre(d: DivisionNba) = d.equipes.sortedBy { c.equipes.getValue(it).rangDivision }
        // POR et MIN à 8-3 : départagés par le rang de conférence (4e et 5e)
        assertEquals(listOf("POR", "MIN", "OKC", "DEN", "UTA"), ordre(DivisionNba.NORD_OUEST))
        // CHA et ORL à 4-7 : 9e et 10e de la conférence
        assertEquals(listOf("ATL", "MIA", "CHA", "ORL", "WAS"), ordre(DivisionNba.SUD_EST))
        assertEquals(listOf("BOS", "PHI", "NYK", "TOR", "BKN"), ordre(DivisionNba.ATLANTIQUE))
        assertEquals(listOf("DET", "CLE", "IND", "CHI", "MIL"), ordre(DivisionNba.CENTRALE))
        assertEquals(listOf("LAL", "GSW", "PHX", "LAC", "SAC"), ordre(DivisionNba.PACIFIQUE))
        // DAL et MEM à 7-4 : 8e et 9e
        assertEquals(listOf("SAS", "HOU", "DAL", "MEM", "NOP"), ordre(DivisionNba.SUD_OUEST))
        for (d in DivisionNba.entries) assertEquals((1..5).toList(), d.equipes.map { c.equipes.getValue(it).rangDivision }.sorted())
    }

    /** Entrée de classement au format ESPN. */
    private fun entree(code: String, v: Int, d: Int, tete: Int?, serie: String = "W1", dix: String = "5-5"): String {
        val stats = buildList {
            add("""{"name":"wins","type":"wins","value":$v.0,"displayValue":"$v"}""")
            add("""{"name":"losses","type":"losses","value":$d.0,"displayValue":"$d"}""")
            if (tete != null) add("""{"name":"playoffSeed","type":"playoffseed","value":$tete.0,"displayValue":"$tete"}""")
            add("""{"name":"streak","type":"streak","value":0,"displayValue":"$serie"}""")
            add("""{"name":"Last Ten Games","type":"lasttengames","displayValue":"$dix"}""")
        }
        return """{"team":{"abbreviation":"$code"},"stats":[${stats.joinToString(",")}]}"""
    }

    @Test
    fun divisionPourcentagePuisVictoires() {
        // A : 6-4 (60 %), B : 3-2 (60 %) mais moins de victoires, C : 2-0 (100 %)
        val json = """{"children":[{"standings":{"entries":[
            ${entree("NYK", 3, 2, 1)}, ${entree("BOS", 6, 4, 2)}, ${entree("PHI", 2, 0, 3)}, ${entree("ATL", 0, 0, 4, serie = "-", dix = "")}
        ]}}]}"""
        val c = LectureEspn.classement(json, 0L)
        assertEquals(1, c.equipes.getValue("PHI").rangDivision)
        assertEquals(2, c.equipes.getValue("BOS").rangDivision)
        assertEquals(3, c.equipes.getValue("NYK").rangDivision)
        // Série « - » : 0 ; dix derniers vides : null
        assertEquals(0, c.equipes.getValue("ATL").serie)
        assertNull(c.equipes.getValue("ATL").dixDerniers)
        assertEquals(1, c.equipes.getValue("ATL").rangDivision)
    }

    @Test
    fun rangDeConferenceCalculeSansPlayoffSeed() {
        val json = """{"children":[{"children":[{"standings":{"entries":[
            ${entree("LAL", 2, 8, null)}, ${entree("OKC", 8, 2, null)}, ${entree("XYZ", 9, 1, 1)}
        ]}}]}]}"""
        val c = LectureEspn.classement(json, 0L)
        // Équipe inconnue sautée, entrées trouvées à n'importe quel niveau
        assertEquals(setOf("LAL", "OKC"), c.equipes.keys)
        assertEquals(1, c.equipes.getValue("OKC").rangConference)
        assertEquals(2, c.equipes.getValue("LAL").rangConference)
    }

    @Test
    fun series() {
        assertEquals(3, LectureEspn.lireSerie("W3"))
        assertEquals(-8, LectureEspn.lireSerie("L8"))
        assertEquals(-10, LectureEspn.lireSerie(" l10 "))
        assertNull(LectureEspn.lireSerie("-"))
        assertNull(LectureEspn.lireSerie(null))
    }

    @Test
    fun classementIllisible() {
        for (json in listOf("{}", """{"children":[]}""", """{"children":[{"standings":{"entries":[{"team":{}}]}}]}""")) {
            try {
                LectureEspn.classement(json, 0L)
                fail("format inconnu attendu : $json")
            } catch (e: LectureEspn.FormatInconnu) {
                // attendu
            }
        }
    }

    // ------------------------------------------------------------------ équipes

    @Test
    fun equipesEtLogos() {
        val e = LectureEspn.equipes(exempleCarte("equipes.json"))
        assertEquals(30, e.size)
        assertEquals(EquipeEspn("OKC", "25", "Oklahoma City Thunder", "https://a.espncdn.com/i/teamlogos/nba/500/okc.png"), e["OKC"])
        assertEquals(EquipeEspn("GSW", "9", "Golden State Warriors", "https://a.espncdn.com/i/teamlogos/nba/500/gs.png"), e["GSW"])
        assertEquals("LA Clippers", e.getValue("LAC").nom)
        assertEquals("https://a.espncdn.com/i/teamlogos/nba/500/utah.png", e.getValue("UTA").logo)
        // Les identifiants de secours sont ceux d'ESPN
        assertEquals(EntrepotNba.ID_ESPN, e.mapValues { it.value.id })
    }

    // ------------------------------------------------------------------ calendrier

    @Test
    fun calendrierDernierEtProchain() {
        for (nom in listOf("calendrier.json", "calendrier_scores_texte.json")) {
            val c = LectureEspn.calendrier(exempleCarte(nom), "OKC")
            assertEquals("Oklahoma City Thunder", c.nomEquipe)
            assertEquals("401909975", c.dernier?.idMatch)
            assertEquals(MatchEquipe("POR", false, LectureNba.dateUtc("2026-11-12T03:00Z"), 108, 118), c.dernier?.match)
            assertEquals(false, c.dernier?.match?.victoire)
            assertEquals("401909304", c.prochain?.idMatch)
            assertEquals(MatchEquipe("LAC", false, LectureNba.dateUtc("2026-11-14T03:30Z"), null, null), c.prochain?.match)
            assertNull(c.prochain?.match?.victoire)
        }
    }

    @Test
    fun calendrierMatchADomicile() {
        // Seul le premier match (victoire 114-75 contre Washington, à domicile) reste
        val json = modifie("calendrier.json") { r -> r.getJSONArray("events").let { a -> while (a.length() > 1) a.remove(a.length() - 1) } }
        val c = LectureEspn.calendrier(json, "OKC")
        assertEquals(MatchEquipe("WAS", true, LectureNba.dateUtc("2026-11-08T01:00Z"), 114, 75), c.dernier?.match)
        assertEquals(true, c.dernier?.match?.victoire)
        assertNull(c.prochain)
        // Équipe reconnue par son identifiant ESPN si son code manque
        val parId = LectureEspn.calendrier(json.replace("\"abbreviation\":\"OKC\"", "\"abbreviation\":\"?\""), "OKC", "25")
        assertEquals("WAS", parId.dernier?.match?.adversaire)
    }

    @Test
    fun calendrierMatchReporteSaute() {
        val json = modifie("calendrier.json") { r ->
            val type = r.getJSONArray("events").getJSONObject(3).getJSONArray("competitions").getJSONObject(0)
                .getJSONObject("status").getJSONObject("type")
            type.put("name", "STATUS_POSTPONED")
        }
        val c = LectureEspn.calendrier(json, "OKC")
        assertEquals("SAC", c.prochain?.match?.adversaire)
        assertEquals("POR", c.dernier?.match?.adversaire)
    }

    @Test
    fun calendrierSansMatch() {
        val c = LectureEspn.calendrier("""{"events":[]}""", "OKC")
        assertNull(c.dernier)
        assertNull(c.prochain)
        try {
            LectureEspn.calendrier("{}", "OKC")
            fail("format inconnu attendu")
        } catch (e: LectureEspn.FormatInconnu) {
            // attendu
        }
    }

    // ------------------------------------------------------------------ effectif

    @Test
    fun effectifEtBlessures() {
        val e = LectureEspn.effectif(exempleCarte("effectif.json"))
        assertEquals("Oklahoma City Thunder", e.nomEquipe)
        assertEquals(18, e.joueurs.size)
        assertEquals(JoueurLu("4684208", "B. Barnhizer", "23", "F", null, null), e.joueurs[0])
        val sga = e.joueurs.first { it.id == "4278073" }
        assertEquals("S. Gilgeous-Alexander", sga.nom)
        assertEquals("2", sga.numero)
        assertEquals("G", sga.poste)
        assertNull(sga.blessure)
        // Sans numéro
        assertNull(e.joueurs.first { it.id == "4702384" }.numero)
        // Blessés : statut d'origine, sans zone dans l'effectif
        val blesses = e.joueurs.filter { it.blessure != null }
        assertEquals(listOf("C. Holmgren", "A. Mitchell", "T. Sorber", "C. Wallace", "J. Williams", "J. Williams"), blesses.map { it.nom })
        assertEquals("Day-To-Day", blesses[1].blessure)
        assertEquals("Out", blesses[0].blessure)
        assertTrue(blesses.all { it.zone == null })
    }

    @Test
    fun effectifGroupeParPosteEtZoneDuCommentaire() {
        val json = """{"athletes":[{"position":"guards","items":[
            {"id":"1","displayName":"Asa Newell","jersey":"14","position":{"abbreviation":"F"},
             "injuries":[{"status":"Out","shortComment":"Newell (toe) has been ruled out."}]},
            {"id":"2","shortName":"T. Young","injuries":[{"status":"Questionable","details":{"type":"Hamstring"}}]},
            {"id":"3"}
        ]}]}"""
        val e = LectureEspn.effectif(json)
        assertNull(e.nomEquipe)
        assertEquals(listOf("Asa Newell", "T. Young"), e.joueurs.map { it.nom })
        assertEquals("toe", e.joueurs[0].zone)
        assertEquals("Hamstring", e.joueurs[1].zone)
        assertEquals("Questionable", e.joueurs[1].blessure)
    }

    // ------------------------------------------------------------------ résumé et moyennes

    @Test
    fun titulairesEtBlessuresDuResume() {
        val r = LectureEspn.resume(exempleCarte("resume.json"), "OKC")
        assertEquals(
            listOf("I. Hartenstein", "A. Mara", "A. Caruso", "S. Gilgeous-Alexander", "J. McCain"),
            r.titulaires.map { it.nom },
        )
        assertEquals(listOf("4222252", "5174983", "2991350", "4278073", "4683778"), r.titulaires.map { it.id })
        assertEquals(JoueurLu("4222252", "I. Hartenstein", "55", "C", null, null), r.titulaires[0])
        assertEquals(
            listOf(
                BlessureLue("4900671", "A. Mitchell", "Day-To-Day", "Ankle"),
                BlessureLue("4432823", "J. Williams", "Out", "Back"),
                BlessureLue("5061603", "T. Sorber", "Out", "Knee"),
                BlessureLue("4683692", "C. Wallace", "Out", "Shoulder"),
                BlessureLue("4433255", "C. Holmgren", "Out", "Knee"),
            ),
            r.blessures,
        )
        // L'autre équipe du match
        val por = LectureEspn.resume(exempleCarte("resume.json"), "POR")
        assertEquals(listOf("T. Camara", "D. Avdija", "D. Clingan", "D. Lillard", "S. Cissoko"), por.titulaires.map { it.nom })
        assertEquals(4, por.blessures.size)
        // Équipe absente du match
        val bos = LectureEspn.resume(exempleCarte("resume.json"), "BOS")
        assertTrue(bos.titulaires.isEmpty() && bos.blessures.isEmpty())
    }

    @Test
    fun moyennesDeLaSaison() {
        assertEquals(MoyennesLues(24.9, 8.0, 10.1, 3.7), LectureEspn.moyennes(exempleCarte("joueur.json")))
        assertEquals(MoyennesLues.AUCUNE, LectureEspn.moyennes(exempleCarte("joueur_sans_saison.json")))
        // Colonnes retrouvées par leur libellé si les noms manquent
        val parLibelle = """{"statistics":{"labels":["PTS","MIN","AST","REB"],"splits":[{"displayName":"Regular Season","stats":["31.2","35.0","6.1","4.5"]}]}}"""
        assertEquals(MoyennesLues(35.0, 31.2, 4.5, 6.1), LectureEspn.moyennes(parLibelle))
    }

    // ------------------------------------------------------------------ traductions

    @Test
    fun statutsTraduits() {
        assertEquals("Absent", LectureEspn.statut("Out"))
        assertEquals("Absent", LectureEspn.statut("Out For Season"))
        assertEquals("Au jour le jour", LectureEspn.statut("Day-To-Day"))
        assertEquals("Incertain", LectureEspn.statut("Questionable"))
        assertEquals("Peu probable", LectureEspn.statut("Doubtful"))
        assertEquals("Probable", LectureEspn.statut("Probable"))
        assertEquals("Suspendu", LectureEspn.statut("Suspension"))
        assertEquals("Inactive", LectureEspn.statut("Inactive"))
        assertEquals("Blessé", LectureEspn.statut(""))
        assertEquals("Blessé", LectureEspn.statut(null))
    }

    @Test
    fun zonesTraduites() {
        assertEquals("genou", LectureEspn.zone("Knee"))
        assertEquals("orteil", LectureEspn.zone("toe"))
        assertEquals("ischios", LectureEspn.zone("Hamstring"))
        assertEquals("cheville", LectureEspn.zone("Ankle"))
        assertEquals("tendon d'Achille", LectureEspn.zone("Achilles"))
        assertEquals("jambe", LectureEspn.zone("Lower  Leg"))
        assertEquals("maladie", LectureEspn.zone("Illness"))
        assertEquals("repos", LectureEspn.zone("Rest"))
        assertEquals("commotion", LectureEspn.zone("Concussion"))
        // Inconnue : le terme d'origine
        assertEquals("patella", LectureEspn.zone("Patella"))
        assertNull(LectureEspn.zone(" "))
        assertNull(LectureEspn.zone(null))
    }
}
