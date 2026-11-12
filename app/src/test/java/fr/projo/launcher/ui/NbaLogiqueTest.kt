package fr.projo.launcher.ui

import androidx.compose.ui.geometry.Offset
import fr.projo.launcher.data.AmbianceNba
import fr.projo.launcher.nba.BilanNba
import fr.projo.launcher.nba.DivisionNba
import fr.projo.launcher.nba.MatchEquipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Design NBA, logique pure : couleurs de l'ambiance Leaders (règle de la maquette, au pixel près),
 * navigation entre pastilles, rangs, saison, textes de la fiche, classement factice.
 */
class NbaLogiqueTest {

    private val paris = TimeZone.getTimeZone("Europe/Paris")

    // ------------------------------------------------------------------ couleurs des leaders

    private fun leaders(vararg codes: String): Map<DivisionNba, String> = DivisionNba.entries.zip(codes).toMap()

    private fun fondsTexte(vararg codes: String): List<String> {
        val f = RegleLeadersNba.fonds(leaders(*codes))
        return DivisionNba.entries.map { RegleLeadersNba.texte(f.getValue(it)) }
    }

    /** Leaders de la maquette (BOS, CLE, ORL, OKC, GSW, HOU) : les teintes exactes de maquette2-leaders.png. */
    @Test
    fun couleursDesLeadersDeLaMaquette() {
        // Atlantique, Centrale, Sud-Est, Nord-Ouest, Pacifique, Sud-Ouest (valeurs calculées par le script de la maquette)
        assertEquals(
            listOf("#0d7046", "#6b0434", "#02619e", "#02639f", "#70591b", "#a3113b"),
            fondsTexte("BOS", "CLE", "ORL", "OKC", "GSW", "HOU"),
        )
    }

    /** Autres jeux de leaders, comparés à la maquette (couleurs trop claires, voisins trop proches, gris). */
    @Test
    fun couleursDesLeadersAutresCas() {
        assertEquals(
            listOf("#616572", "#195736", "#79042c", "#5e4422", "#573596", "#4c5463"),
            fondsTexte("BKN", "MIL", "MIA", "UTA", "LAL", "SAS"),
        )
        assertEquals(
            listOf("#025796", "#6f541d", "#026276", "#193774", "#563585", "#025292"),
            fondsTexte("NYK", "IND", "CHA", "DEN", "SAC", "DAL"),
        )
        assertEquals(
            listOf("#a3113b", "#a3113b", "#586028", "#b13139", "#b54f21", "#4b608c"),
            fondsTexte("TOR", "CHI", "ATL", "POR", "PHX", "MEM"),
        )
    }

    /** Pacifique voisin du Nord-Ouest bleu (OKC) : Golden State prend son accent assombri (or). */
    @Test
    fun voisinTropProcheAccentAssombri() {
        val avec = RegleLeadersNba.fonds(mapOf(DivisionNba.NORD_OUEST to "OKC", DivisionNba.PACIFIQUE to "GSW"))
        val seul = RegleLeadersNba.fonds(mapOf(DivisionNba.PACIFIQUE to "GSW"))
        // Seul : le bleu des Warriors (mêlé à 22 % de bleu nuit)
        assertEquals(RegleLeadersNba.melange(0x1D428A, 0x0B1226, 0.22), seul[DivisionNba.PACIFIQUE])
        // À côté du Thunder : l'accent or (#ffc72c) assombri de 45 %, puis mêlé
        val or = RegleLeadersNba.melange(0xFFC72C, 0x000000, 0.45)
        assertEquals(RegleLeadersNba.melange(or, 0x0B1226, 0.22), avec[DivisionNba.PACIFIQUE])
    }

    @Test
    fun outilsDeCouleur() {
        assertEquals(0x808080, RegleLeadersNba.melange(0x000000, 0xFFFFFF, 0.5))
        assertEquals(1.0, RegleLeadersNba.luminance(0xFFFFFF), 1e-9)
        assertEquals(0.0, RegleLeadersNba.luminance(0x000000), 1e-9)
        assertEquals(-1.0, RegleLeadersNba.teinte(0x777777), 0.0)
        assertEquals(0.0, RegleLeadersNba.teinte(0xFF0000), 1e-9)
        assertEquals(120.0, RegleLeadersNba.teinte(0x00FF00), 1e-9)
        assertEquals(240.0, RegleLeadersNba.teinte(0x0000FF), 1e-9)
        // Écart de teinte au plus court (350° et 10° : 20°), gris face à une couleur : 180
        assertEquals(20.0, RegleLeadersNba.ecartTeinte(0xFF002B, 0xFF2B00), 0.5)
        assertEquals(180.0, RegleLeadersNba.ecartTeinte(0x777777, 0xFF0000), 0.0)
        assertEquals(0.0, RegleLeadersNba.ecartTeinte(0x777777, 0x333333), 0.0)
        assertEquals("#0b1226", RegleLeadersNba.texte(RegleLeadersNba.hex("#0B1226")))
    }

    /** Sans classement : îles gris ardoise (Leaders) ; Parquet et Nuit d'arène gardent leurs couleurs. */
    @Test
    fun palettesSansClassement() {
        val p = PaletteNba.de(AmbianceNba.LEADERS, emptyMap())
        assertTrue(DivisionNba.entries.all { p.fonds[it] == PaletteNba.ARDOISE })
        assertTrue(p.filigrane)
        assertNull(p.conferences)
        assertEquals("chaque division aux couleurs de son leader", p.sousTitre)
        for (a in listOf(AmbianceNba.PARQUET, AmbianceNba.NUIT)) {
            val q = PaletteNba.de(a, emptyMap())
            assertEquals(6, q.fonds.size)
            assertTrue(q.fonds.values.none { it == PaletteNba.ARDOISE })
            assertEquals("conférences et divisions", q.sousTitre)
            assertTrue(q.conferences != null && !q.filigrane)
        }
        assertEquals(AmbianceNba.LEADERS, AmbianceNba.de(null))
        assertEquals(AmbianceNba.NUIT, AmbianceNba.de("nuit"))
        assertEquals(AmbianceNba.LEADERS, AmbianceNba.de("inconnue"))
    }

    // ------------------------------------------------------------------ navigation

    /** Places des pastilles (carte_nba.json) : logo (x, y) et sens de la capsule. */
    private val places = mapOf(
        "POR" to Triple(415f, 226f, -1), "SAC" to Triple(300f, 392f, -1), "GSW" to Triple(300f, 460f, -1), "LAL" to Triple(300f, 562f, -1),
        "LAC" to Triple(300f, 624f, -1), "PHX" to Triple(535f, 633f, -1), "UTA" to Triple(567f, 400f, 1), "DEN" to Triple(684f, 467f, -1),
        "OKC" to Triple(815f, 606f, -1), "DAL" to Triple(828f, 686f, -1), "SAS" to Triple(794f, 786f, -1), "HOU" to Triple(870f, 850f, 1),
        "NOP" to Triple(983f, 767f, 1), "MEM" to Triple(980f, 560f, 1), "MIN" to Triple(891f, 319f, -1), "MIL" to Triple(1000f, 250f, -1),
        "CHI" to Triple(999f, 408f, -1), "IND" to Triple(1040f, 480f, -1), "DET" to Triple(1060f, 310f, 1), "CLE" to Triple(1125f, 405f, 1),
        "TOR" to Triple(1200f, 250f, 1), "BOS" to Triple(1493f, 320f, 1), "NYK" to Triple(1440f, 395f, 1), "BKN" to Triple(1440f, 457f, 1),
        "PHI" to Triple(1363f, 470f, -1), "WAS" to Triple(1320f, 545f, 1), "CHA" to Triple(1223f, 612f, 1), "ATL" to Triple(1138f, 660f, -1),
        "ORL" to Triple(1257f, 784f, 1), "MIA" to Triple(1311f, 860f, 1),
    )
    private val centres = places.mapValues { (c, p) -> FormePastille.centre(PlacePastilleNba(c, p.first, p.second, p.third, null)) }

    private fun aller(depuis: String, sens: SensNba) = NavCarteNba.voisine(centres.getValue(depuis), sens, centres, sauf = depuis)

    @Test
    fun capsulesEtCentres() {
        // Capsule vers la gauche : le logo à son bout droit ; vers la droite : à son bout gauche
        assertEquals(Offset(732f, 606f), centres["OKC"])
        assertEquals(Offset(953f, 850f), centres["HOU"])
        val ny = listOf(PlacePastilleNba("NYK", 1440f, 395f, 1, "NY"), PlacePastilleNba("BKN", 1440f, 457f, 1, "NY"))
        val r = FormePastille.capsule(ny)
        assertEquals(1411f, r.left, 0f)
        assertEquals(366f, r.top, 0f)
        assertEquals(224f, r.width, 0f)
        assertEquals(120f, r.height, 0f)
        assertEquals(1482f, FormePastille.texteX(ny[0]), 0f)
        assertEquals(1188f, FormePastille.texteX(PlacePastilleNba("PHI", 1363f, 470f, -1, null)), 0f)
    }

    /** ◀ ▲ ▶ ▼ : la pastille la plus proche dans la direction, dans l'axe de préférence. */
    @Test
    fun navigationEntrePastilles() {
        assertEquals("DAL", aller("SAS", SensNba.HAUT))
        assertEquals("OKC", aller("DAL", SensNba.HAUT))
        assertEquals("DAL", aller("OKC", SensNba.BAS))
        assertEquals("MEM", aller("OKC", SensNba.DROITE))
        assertEquals("BKN", aller("NYK", SensNba.BAS))
        assertEquals("NYK", aller("BKN", SensNba.HAUT))
        assertEquals("GSW", aller("SAC", SensNba.BAS))
        assertEquals("LAL", aller("GSW", SensNba.BAS))
        assertEquals("LAC", aller("LAL", SensNba.BAS))
        assertEquals("BOS", aller("NYK", SensNba.HAUT))
        assertEquals("PHI", aller("WAS", SensNba.HAUT))
    }

    /** Bords : rien au-delà (▼ sous les pastilles du bas : retour au dock). */
    @Test
    fun navigationAuxBords() {
        for (c in listOf("SAS", "HOU", "MIA")) assertNull("▼ depuis $c", aller(c, SensNba.BAS))
        assertNull(aller("POR", SensNba.HAUT))
        assertNull(aller("SAC", SensNba.GAUCHE))
        assertNull(aller("BOS", SensNba.DROITE))
        // Hors du cône de ±60° : DAL est presque à côté de OKC, pas « à droite »
        assertTrue(aller("OKC", SensNba.DROITE) != "DAL")
    }

    /** ▲ depuis le dock : la pastille la plus proche au-dessus de la tuile. */
    @Test
    fun entreeDepuisLeDock() {
        val dock = 934f
        assertEquals("SAS", NavCarteNba.voisine(Offset(600f, dock), SensNba.HAUT, centres))
        assertEquals("HOU", NavCarteNba.voisine(Offset(960f, dock), SensNba.HAUT, centres))
        assertEquals("MIA", NavCarteNba.voisine(Offset(1380f, dock), SensNba.HAUT, centres))
        assertNull(NavCarteNba.voisine(Offset(600f, dock), SensNba.HAUT, emptyMap()))
    }

    // ------------------------------------------------------------------ textes

    @Test
    fun rangs() {
        assertEquals("1" to "er", TextesNba.rang(1))
        assertEquals("2" to "e", TextesNba.rang(2))
        assertEquals("15" to "e", TextesNba.rang(15))
        assertEquals("1er", TextesNba.rangTexte(1))
        assertEquals("11e", TextesNba.rangTexte(11))
        val okc = BilanNba("OKC", 12, 1, 1, 1, 3, "8-2")
        assertEquals("1er OUEST · 1er DIV.", TextesNba.ligneRangs(okc))
        assertEquals("6e OUEST · 2e NORD-OUEST", TextesNba.rangsFiche(okc.copy(rangConference = 6, rangDivision = 2)))
        assertEquals("13e EST · 4e DIV.", TextesNba.ligneRangs(BilanNba("TOR", 4, 9, 13, 4, -2, null)))
        // En exposant : « er » et « e » ; « 1er DIV. » en or pour le leader
        val texte = rangsPastille(okc, androidx.compose.ui.unit.TextUnit.Unspecified)
        assertEquals("1er OUEST · 1er DIV.", texte.text)
        assertTrue(texte.spanStyles.any { it.item.color == OrNba })
        assertTrue(rangsPastille(okc.copy(rangDivision = 2), androidx.compose.ui.unit.TextUnit.Unspecified).spanStyles.none { it.item.color == OrNba })
    }

    /** Saison : d'octobre à décembre, celle qui commence ; de janvier à septembre, celle d'avant. */
    @Test
    fun saison() {
        fun t(an: Int, mois: Int, jour: Int) = Calendar.getInstance(paris).apply { clear(); set(an, mois, jour, 12, 0) }.timeInMillis
        assertEquals("2026-27", TextesNba.saison(t(2026, Calendar.NOVEMBER, 12), paris))
        assertEquals("2026-27", TextesNba.saison(t(2026, Calendar.OCTOBER, 1), paris))
        assertEquals("2025-26", TextesNba.saison(t(2026, Calendar.SEPTEMBER, 30), paris))
        assertEquals("2026-27", TextesNba.saison(t(2027, Calendar.JANUARY, 2), paris))
        assertEquals("2026-27", TextesNba.saison(t(2027, Calendar.JUNE, 15), paris))
        assertEquals("1999-00", TextesNba.saison(t(1999, Calendar.DECEMBER, 1), paris))
    }

    @Test
    fun bilansSeriesEtHeures() {
        assertEquals("12–1", TextesNba.bilan(BilanNba("OKC", 12, 1, 1, 1, 3, null)))
        assertEquals("––", TextesNba.bilan(null))
        assertEquals("3 victoires de suite", TextesNba.serie(3))
        assertEquals("2 défaites de suite", TextesNba.serie(-2))
        assertEquals("Victoire au dernier match", TextesNba.serie(1))
        assertNull(TextesNba.serie(0))
        assertEquals("7–3 sur les 10 derniers", TextesNba.dixDerniers("7-3"))
        assertNull(TextesNba.dixDerniers(null))
        assertNull(TextesNba.dixDerniers("?"))
        val t = NbaFactice.heureMaquette(paris)
        assertEquals("21:47", TextesNba.horloge(t, paris))
        assertEquals("JEUDI 12 NOVEMBRE", TextesNba.dateEnTete(t, paris))
        assertEquals("il y a 4 min", TextesNba.depuis(t - 4 * 60_000L, t))
        assertEquals("à l'instant", TextesNba.depuis(t - 20_000L, t))
        assertEquals("il y a 2 h", TextesNba.depuis(t - 125 * 60_000L, t))
        assertEquals("de 21:12", TextesNba.heureBilans(t - 35 * 60_000L, t, paris))
        assertEquals("du 11 nov.", TextesNba.heureBilans(t - 30 * 3_600_000L, t, paris))
    }

    /** Fiche : dates en français, heure locale (« ven. 14 nov. · 4:30 »), à l'extérieur / à domicile. */
    @Test
    fun textesDesMatchs() {
        fun utc(jour: Int, h: Int, m: Int) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2025, Calendar.NOVEMBER, jour, h, m) }.timeInMillis
        val prochain = MatchEquipe("LAC", domicile = false, debut = utc(14, 3, 30), score = null, scoreAdversaire = null)
        assertEquals("ven. 14 nov. · 4:30 · à LA Clippers", TextesNba.prochain(prochain, paris))
        val dernier = MatchEquipe("POR", domicile = false, debut = utc(11, 3, 0), score = 108, scoreAdversaire = 118)
        assertEquals("Défaite" to "108–118 à Portland · mar. 11 nov.", TextesNba.dernier(dernier, paris))
        val gagne = dernier.copy(adversaire = "MEM", domicile = true, score = 121, scoreAdversaire = 99)
        assertEquals("Victoire" to "121–99 contre Memphis · mar. 11 nov.", TextesNba.dernier(gagne, paris))
        assertEquals("32,1", TextesNba.moyenne(32.06))
        assertEquals("–", TextesNba.moyenne(null))
        assertEquals("Oklahoma City Thunder", TextesNba.nomComplet("OKC"))
        assertEquals("Golden State", TextesNba.ville("GSW"))
    }

    /** Classement factice : les rangs de la maquette (OKC 1er Ouest, CLE 1er Est, NYK 3e Est…). */
    @Test
    fun classementDeLaMaquette() {
        val c = NbaFactice.classement(0L).equipes
        assertEquals(30, c.size)
        assertEquals(1, c.getValue("OKC").rangConference)
        assertEquals(1, c.getValue("CLE").rangConference)
        assertEquals(2, c.getValue("BOS").rangConference)
        assertEquals(3, c.getValue("NYK").rangConference)
        assertEquals(15, c.getValue("UTA").rangConference)
        assertEquals(listOf("BOS", "CLE", "ORL", "OKC", "GSW", "HOU"), DivisionNba.entries.map { d -> c.values.first { it.rangDivision == 1 && DivisionNba.de(it.code) == d }.code })
        assertEquals("13e EST · 4e DIV.", TextesNba.ligneRangs(c.getValue("TOR")))
    }
}
