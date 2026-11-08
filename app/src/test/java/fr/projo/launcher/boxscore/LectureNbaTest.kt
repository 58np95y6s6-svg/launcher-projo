package fr.projo.launcher.boxscore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Lit un exemple de flux (app/src/test/resources/nba/). */
fun exempleNba(nom: String): String =
    LectureNbaTest::class.java.getResource("/nba/$nom")?.readText() ?: throw AssertionError("exemple introuvable : $nom")

/**
 * Lecture des flux NBA et ESPN (exemples faits à la main d'après les champs de la
 * spécification) : même modèle des deux côtés, tolérance aux champs manquants, aux nombres
 * écrits en texte, aux pourcentages sur 1 ou sur 100, aux codes ESPN et aux équipes inconnues.
 * Robolectric : org.json est celui d'Android.
 */
@RunWith(RobolectricTestRunner::class)
class LectureNbaTest {

    private fun List<MatchNba>.de(id: String) = firstOrNull { it.id == id } ?: throw AssertionError("match $id absent de ${map { it.id }}")

    // ------------------------------------------------------------------ NBA

    @Test
    fun tableauNba() {
        val matchs = LectureNba.tableauNba(exempleNba("tableau_nba.json"))
        // Le match sans équipe à domicile est sauté, les autres sont lus
        assertEquals(listOf("LAL-BOS", "GSW-NYK", "DEN-MIA", "OKC-PHX", "UTA-SAS", "WAS-POR", "CHI-MEL"), matchs.map { it.id })

        val m = matchs.de("LAL-BOS")
        assertEquals("0022500201", m.idSource)
        assertEquals(EtatMatch.EN_COURS, m.etat)
        assertTrue(m.direct)
        assertEquals("Lakers", m.ext.nom)
        assertEquals("Celtics", m.dom.nom)
        assertEquals(98, m.ext.score)
        assertEquals(102, m.dom.score)
        assertEquals(listOf(27, 24, 29, 18), m.ext.periodes)
        assertEquals("4e QT · 2:31", m.statut)
        assertEquals(LectureNba.dateUtc("2025-11-09T00:30:00Z"), m.debut)

        assertEquals("Mi-temps", matchs.de("GSW-NYK").statut)
        // Score en texte, moins d'une minute : dixièmes
        val mia = matchs.de("DEN-MIA")
        assertEquals(24, mia.dom.score)
        assertEquals("1er QT · 0:45.3", mia.statut)
        val okc = matchs.de("OKC-PHX")
        assertEquals("Prol. · 1:12", okc.statut)
        assertEquals(5, okc.ext.periodes.size)
        assertEquals("Terminé", matchs.de("UTA-SAS").statut)
        assertFalse(matchs.de("UTA-SAS").direct)
        assertEquals(EtatMatch.A_VENIR, matchs.de("WAS-POR").etat)
        // Équipe inconnue : lue quand même
        assertFalse(EquipesNba.connue("MEL"))
        assertEquals("United", matchs.de("CHI-MEL").dom.nom)
    }

    @Test
    fun boxScoreNba() {
        val b = LectureNba.boxScoreNba(exempleNba("boxscore_nba.json"))
        assertEquals("LAL-BOS", b.match.id)
        assertEquals("4e QT · 2:31", b.match.statut)
        assertTrue(b.surTerrainConnu)

        val lal = b.ext.joueurs
        assertEquals(11, lal.size)
        assertEquals(listOf("L. Dončić", "L. James", "A. Reaves", "D. Ayton", "R. Hachimura"), lal.filter { it.titulaire }.map { it.nom })
        val luka = lal.first()
        assertEquals(38, luka.minutes)
        assertEquals(34, luka.points)
        assertEquals(8, luka.rebonds)
        assertEquals(9, luka.passes)
        assertEquals(12 to 24, luka.tirsReussis to luka.tirsTentes)
        assertEquals(4 to 11, luka.troisReussis to luka.troisTentes)
        assertEquals(-3, luka.plusMoins)
        assertEquals(true, luka.surTerrain)
        assertEquals(false, lal.first { it.nom == "D. Ayton" }.surTerrain)
        assertEquals(2, lal.first { it.nom == "A. Reaves" }.plusMoins)
        // N'a pas joué
        val kleber = lal.first { it.nom == "M. Kleber" }
        assertFalse(kleber.aJoue)
        assertEquals(0, kleber.minutes)

        val se = b.ext.stats!!
        assertEquals(0.481, se.pctTirs!!, 0.001)
        assertEquals(0.344, se.pctTrois!!, 0.001)
        assertEquals(42, se.rebonds)
        assertEquals(25, se.passes)
        // Lakers : « turnoversTotal » seulement ; Celtics : « turnovers »
        assertEquals(12, se.pertes)
        assertEquals(9, b.dom.stats!!.pertes)
        assertEquals(38 to 79, se.tirsReussis to se.tirsTentes)
        assertEquals(11 to 32, se.troisReussis to se.troisTentes)
        assertEquals(8, b.dom.joueurs.size)
    }

    @Test
    fun boxScoreSansStatistiquesDEquipe() {
        // Statistiques d'équipe absentes : déduites des joueurs (sauf les pertes)
        val json = """{"game":{"gameId":"1","gameStatus":2,"period":3,"gameClock":"PT05M00.00S",
            "awayTeam":{"teamTricode":"ATL","score":"50","players":[
              {"nameI":"T. Young","starter":"1","oncourt":"1","played":"1","statistics":{"minutes":"PT20M00.00S","points":"30","reboundsTotal":2,"assists":9,
               "fieldGoalsMade":10,"fieldGoalsAttempted":20,"threePointersMade":4,"threePointersAttempted":10,"plusMinusPoints":"5"}},
              {"nameI":"X. Inconnu","statistics":null},
              {"starter":"1"}]},
            "homeTeam":{"teamTricode":"BKN","score":48,"players":[],"statistics":{"fieldGoalsPercentage":"45.5","turnovers":null,"turnoversTotal":"7"}}}}"""
        val b = LectureNba.boxScoreNba(json)
        assertEquals("ATL-BKN", b.match.id)
        assertEquals("Hawks", b.match.ext.nom)
        assertEquals("Nets", b.match.dom.nom)
        assertEquals(50, b.match.ext.score)
        // Joueur sans nom : sauté ; joueur sans statistiques : 0 partout, n'a pas joué
        assertEquals(listOf("T. Young", "X. Inconnu"), b.ext.joueurs.map { it.nom })
        assertFalse(b.ext.joueurs[1].aJoue)
        val s = b.ext.stats!!
        assertEquals(0.5, s.pctTirs!!, 1e-9)
        assertEquals(0.4, s.pctTrois!!, 1e-9)
        assertEquals(2, s.rebonds)
        assertNull(s.pertes)
        // Pourcentage donné sur 100 ; pertes en texte
        assertEquals(0.455, b.dom.stats!!.pctTirs!!, 1e-9)
        assertEquals(7, b.dom.stats!!.pertes)
    }

    @Test
    fun documentsIllisiblesOuIncomplets() {
        for (json in listOf("<HTML>Access Denied</HTML>", "{}", """{"scoreboard":{}}""")) {
            try {
                LectureNba.tableauNba(json)
                fail("illisible accepté : $json")
            } catch (e: Exception) {
                // attendu : compté comme un échec de la source
            }
        }
        assertTrue(LectureNba.tableauNba("""{"scoreboard":{"games":[]}}""").isEmpty())
        try {
            LectureNba.boxScoreNba("""{"meta":{}}""")
            fail("box score sans « game » accepté")
        } catch (e: LectureNba.FormatInconnu) {
            // attendu
        }
    }

    // ------------------------------------------------------------------ ESPN

    @Test
    fun tableauEspnAvecLesCodesNba() {
        val matchs = LectureNba.tableauEspn(exempleNba("tableau_espn.json"))
        // GS → GSW, NY → NYK, UTAH → UTA, SA → SAS, WSH → WAS, NO → NOP ; l'événement sans compétition est sauté
        assertEquals(listOf("LAL-BOS", "GSW-NYK", "DEN-MIA", "UTA-SAS", "WAS-NOP"), matchs.map { it.id })
        val m = matchs.de("LAL-BOS")
        assertEquals("401810201", m.idSource)
        assertEquals(98, m.ext.score)
        assertEquals(102, m.dom.score)
        assertEquals(listOf(25, 30, 24, 23), m.dom.periodes)
        assertEquals("4e QT · 2:31", m.statut)
        assertEquals(LectureNba.dateUtc("2025-11-09T00:30:00Z"), m.debut)
        assertEquals("Mi-temps", matchs.de("GSW-NYK").statut)
        assertEquals("1er QT · 0:45.3", matchs.de("DEN-MIA").statut)
        assertEquals("Terminé", matchs.de("UTA-SAS").statut)
        assertEquals(EtatMatch.A_VENIR, matchs.de("WAS-NOP").etat)
        assertEquals("Warriors", matchs.de("GSW-NYK").ext.nom)
    }

    @Test
    fun resumeEspnMemeModeleQueNba() {
        val nba = LectureNba.boxScoreNba(exempleNba("boxscore_nba.json"))
        val espn = LectureNba.boxScoreEspn(exempleNba("resume_espn.json"))
        assertEquals(nba.match.id, espn.match.id)
        assertEquals(nba.match.statut, espn.match.statut)
        assertEquals(nba.match.ext.score, espn.match.ext.score)
        assertEquals(nba.match.dom.periodes, espn.match.dom.periodes)
        // Pas d'info « sur le terrain » chez ESPN
        assertFalse(espn.surTerrainConnu)
        assertTrue(espn.ext.joueurs.all { it.surTerrain == null })
        // Mêmes lignes de joueurs (noms ESPN sans diacritiques ici)
        val luka = espn.ext.joueurs.first()
        assertEquals("L. Doncic", luka.nom)
        assertTrue(luka.titulaire)
        assertEquals(38, luka.minutes)
        assertEquals(34, luka.points)
        assertEquals(8, luka.rebonds)
        assertEquals(9, luka.passes)
        assertEquals(12 to 24, luka.tirsReussis to luka.tirsTentes)
        assertEquals(4 to 11, luka.troisReussis to luka.troisTentes)
        assertEquals(-3, luka.plusMoins)
        assertEquals(2, espn.ext.joueurs.first { it.nom == "A. Reaves" }.plusMoins)
        assertFalse(espn.ext.joueurs.first { it.nom == "M. Kleber" }.aJoue)
        assertEquals(nba.ext.joueurs.map { it.points }, espn.ext.joueurs.map { it.points })
        assertEquals(nba.dom.joueurs.map { it.nom }, espn.dom.joueurs.map { it.nom })
        // Statistiques d'équipe : « 48.1 » → 0,481
        val s = espn.ext.stats!!
        assertEquals(0.481, s.pctTirs!!, 1e-9)
        assertEquals(0.344, s.pctTrois!!, 1e-9)
        assertEquals(42, s.rebonds)
        assertEquals(25, s.passes)
        assertEquals(12, s.pertes)
        assertEquals(38 to 79, s.tirsReussis to s.tirsTentes)
        assertEquals(9, espn.dom.stats!!.pertes)
    }

    @Test
    fun resumeEspnSansEnTete() {
        // En-tête absent : le match du tableau sert d'en-tête ; équipes reconnues par leur code
        val tableau = LectureNba.tableauEspn(exempleNba("tableau_espn.json")).de("LAL-BOS")
        val json = exempleNba("resume_espn.json").replace("\"header\"", "\"autre\"")
        val b = LectureNba.boxScoreEspn(json, tableau)
        assertEquals(tableau, b.match)
        assertEquals(34, b.ext.joueurs.first().points)
        assertEquals(29, b.dom.joueurs.first().points)
        assertNotNull(b.dom.stats)
    }

    // ------------------------------------------------------------------ outils

    @Test
    fun pourcentagesEtDates() {
        assertEquals(0.481, LectureNba.pourcentage(0.481)!!, 1e-9)
        assertEquals(0.481, LectureNba.pourcentage(48.1)!!, 1e-9)
        assertEquals(1.0, LectureNba.pourcentage(1.0)!!, 1e-9)
        assertEquals(0.0, LectureNba.pourcentage(0.0)!!, 1e-9)
        assertNull(LectureNba.pourcentage(null))
        assertNull(LectureNba.pourcentage(-3.0))
        assertEquals(1_762_648_200_000L, LectureNba.dateUtc("2025-11-09T00:30:00Z"))
        assertEquals(1_762_648_200_000L, LectureNba.dateUtc("2025-11-09T00:30Z"))
        assertEquals(1_762_648_200_000L, LectureNba.dateUtc("2025-11-08T19:30:00-05:00"))
        assertEquals(1_762_648_200_000L, LectureNba.dateUtc("2025-11-09T00:30:00.000Z"))
        assertEquals(0L, LectureNba.dateUtc("demain"))
        assertEquals(0L, LectureNba.dateUtc(null))
    }
}
