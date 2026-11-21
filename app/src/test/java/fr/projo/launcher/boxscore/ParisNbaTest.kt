package fr.projo.launcher.boxscore

import fr.projo.launcher.boxscore.LignePari.Joueur
import fr.projo.launcher.boxscore.LignePari.NonSuivie
import fr.projo.launcher.boxscore.LignePari.TotalEquipe
import fr.projo.launcher.boxscore.LignePari.TotalMatch
import fr.projo.launcher.boxscore.LignePari.Vainqueur
import fr.projo.launcher.boxscore.Sens.MOINS
import fr.projo.launcher.boxscore.Sens.PLUS
import fr.projo.launcher.boxscore.StatPari.CONTRES
import fr.projo.launcher.boxscore.StatPari.INTERCEPTIONS
import fr.projo.launcher.boxscore.StatPari.INTERCEPTIONS_CONTRES
import fr.projo.launcher.boxscore.StatPari.PASSES
import fr.projo.launcher.boxscore.StatPari.POINTS
import fr.projo.launcher.boxscore.StatPari.POINTS_PASSES
import fr.projo.launcher.boxscore.StatPari.POINTS_REBONDS
import fr.projo.launcher.boxscore.StatPari.POINTS_REBONDS_PASSES
import fr.projo.launcher.boxscore.StatPari.REBONDS
import fr.projo.launcher.boxscore.StatPari.REBONDS_PASSES
import fr.projo.launcher.boxscore.StatPari.TROIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lecture du texte d'un pari (ParisNba) : le ticket Betclic de l'utilisateur, les variantes des
 * autres applis (sélection et type de pari sur une ou deux lignes, dans un sens ou dans l'autre),
 * les statistiques, les équipes, le bruit des tickets (cotes, mises, scores, autres sports), les
 * paris non suivis ; puis les noms des joueurs (« Tyrese Maxey » ↔ « T. Maxey »).
 */
class ParisNbaTest {

    private val phiInd = setOf("PHI", "IND")

    private fun lire(vararg lignes: String) = ParisNba.lire(lignes.joinToString("\n"))

    // ------------------------------------------------------------------ ticket de l'utilisateur

    @Test
    fun ticketBetclicDeLUtilisateur() {
        assertEquals(
            listOf(
                Vainqueur("PHI"),
                Joueur("Tyrese Maxey", POINTS, PLUS, 19.5, phiInd),
                Joueur("Kentavious Caldwell-Pope", POINTS, PLUS, 9.5, phiInd),
                Joueur("Ivica Zubac", PASSES, PLUS, 2.5, phiInd),
            ),
            ParisNba.lire(TICKET_BETCLIC),
        )
    }

    @Test
    fun ticketBetclicAvecRetoursWindowsEtEspacesInsecables() {
        val texte = TICKET_BETCLIC.replace("\n", "\r\n").replace("+ de", "+ de").replace("Combiné", "Combiné")
        assertEquals(4, ParisNba.lire(texte).size)
        assertEquals(Joueur("Tyrese Maxey", POINTS, PLUS, 19.5, phiInd), ParisNba.lire(texte)[1])
    }

    @Test
    fun ticketBetclicSansLeFootball() {
        // Le pari de football seul : rien
        assertTrue(lire("France", "1,15", "Résultat du match (t. rég)", "France 20:45 Turquie").isEmpty())
    }

    // ------------------------------------------------------------------ sens et seuils

    @Test
    fun plusDeMoinsDe() {
        assertEquals(listOf(Joueur("Tyrese Maxey", POINTS, PLUS, 19.5)), lire("Tyrese Maxey Plus de 19,5 points"))
        assertEquals(listOf(Joueur("Tyrese Maxey", POINTS, MOINS, 24.5)), lire("Tyrese Maxey Moins de 24,5 points"))
        assertEquals(listOf(Joueur("Tyrese Maxey", POINTS, PLUS, 19.5)), lire("Tyrese Maxey + de 19,5 pts"))
        assertEquals(listOf(Joueur("Tyrese Maxey", POINTS, MOINS, 24.5)), lire("Tyrese Maxey - de 24,5 pts"))
        // Point décimal, tiret long, signe moins typographique
        assertEquals(listOf(Joueur("Tyrese Maxey", POINTS, MOINS, 24.5)), lire("Tyrese Maxey − de 24.5 points"))
        assertEquals(listOf(Joueur("Tyrese Maxey", POINTS, PLUS, 20.0)), lire("Tyrese Maxey plus de 20 points"))
    }

    @Test
    fun paliers() {
        val maxey20 = listOf(Joueur("Tyrese Maxey", POINTS, PLUS, 19.5))
        assertEquals(maxey20, lire("Tyrese Maxey 20+", "Nombre de points du joueur (paliers)"))
        assertEquals(maxey20, lire("Tyrese Maxey 20+ points"))
        assertEquals(maxey20, lire("Tyrese Maxey : 20 ou plus", "Nombre de points du joueur (paliers)"))
        assertEquals(maxey20, lire("Tyrese Maxey marque 20 points ou plus"))
        assertEquals(maxey20, lire("Tyrese Maxey au moins 20 points"))
        assertEquals(listOf(Joueur("Joel Embiid", REBONDS, PLUS, 9.5)), lire("Joel Embiid 10 rebonds et plus"))
        assertEquals(listOf(Joueur("Joel Embiid", REBONDS, MOINS, 10.5)), lire("Joel Embiid 10 rebonds ou moins"))
    }

    @Test
    fun typeDePariSurLaMemeLigne() {
        val maxey = listOf(Joueur("Tyrese Maxey", POINTS, PLUS, 19.5))
        assertEquals(maxey, lire("Tyrese Maxey - Points : Plus de 19,5"))
        assertEquals(maxey, lire("Nombre de points de Tyrese Maxey : + de 19,5"))
        assertEquals(maxey, lire("Points de Tyrese Maxey (prolongations incluses) : Plus de 19,5"))
        assertEquals(maxey, lire("Tyrese Maxey + de 19,5 Nombre de points du joueur (paliers)"))
    }

    @Test
    fun typeDePariSurLaLigneSuivanteOuPrecedente() {
        val maxey = listOf(Joueur("Tyrese Maxey", POINTS, PLUS, 19.5))
        // Betclic : sélection puis type de pari
        assertEquals(maxey, lire("Tyrese Maxey + de 19,5", "Nombre de points du joueur (paliers)"))
        // Winamax, Unibet, PMU : type de pari (avec le joueur) puis sélection
        assertEquals(maxey, lire("Tyrese Maxey - Nombre de points", "Plus de 19,5"))
        assertEquals(maxey, lire("Points du joueur (prolongations incluses) - Tyrese Maxey", "Plus de 19.5"))
        assertEquals(maxey, lire("Tyrese Maxey : Nombre de points (prolongations incluses)", "1,85", "Plus de 19,5"))
        // Joueur seul, sélection avec l'unité
        assertEquals(maxey, lire("Tyrese Maxey", "Plus de 19,5 points"))
        // Joueur, type, sélection sur trois lignes
        assertEquals(maxey, lire("Tyrese Maxey", "Nombre de points (prolongations incluses)", "Plus de 19,5"))
    }

    @Test
    fun plusieursJambesDansLeSensWinamax() {
        assertEquals(
            listOf(
                Joueur("Tyrese Maxey", POINTS, PLUS, 19.5),
                Joueur("Joel Embiid", REBONDS, MOINS, 11.5),
                Joueur("Paul George", PASSES, PLUS, 3.5),
            ),
            lire(
                "Tyrese Maxey - Nombre de points", "Plus de 19,5", "1,80",
                "Joel Embiid - Nombre de rebonds", "Moins de 11,5", "1,90",
                "Paul George - Passes décisives", "Plus de 3,5", "2,05",
            ),
        )
    }

    @Test
    fun texteEnDirectCoupeApresPlusDe() {
        assertEquals(
            listOf(Joueur("Tyrese Maxey", POINTS, PLUS, 19.5, phiInd)),
            lire("Tyrese Maxey + de", "19,5", "Nombre de points du joueur (paliers)", "Sixers 29 8 37", "Pacers 35 4 39"),
        )
    }

    @Test
    fun majuscules() {
        assertEquals(listOf(Joueur("TYRESE MAXEY", POINTS, PLUS, 19.5)), lire("TYRESE MAXEY + DE 19,5", "NOMBRE DE POINTS DU JOUEUR (PALIERS)"))
    }

    // ------------------------------------------------------------------ statistiques

    @Test
    fun statistiques() {
        fun stat(type: String) = lire("Tyrese Maxey + de 4,5", type).single().let { (it as Joueur).stat }
        assertEquals(POINTS, stat("Nombre de points du joueur (paliers)"))
        assertEquals(REBONDS, stat("Nombre de rebonds du joueur"))
        assertEquals(PASSES, stat("Nombre de passes décisives du joueur (paliers)"))
        assertEquals(PASSES, stat("Passes"))
        assertEquals(TROIS, stat("Nombre de paniers à 3 points réussis du joueur"))
        assertEquals(TROIS, stat("Paniers à trois points"))
        assertEquals(TROIS, stat("Tirs à 3 points réussis"))
        assertEquals(TROIS, stat("3 pts réussis"))
        assertEquals(POINTS_REBONDS_PASSES, stat("Points + rebonds + passes du joueur"))
        assertEquals(POINTS_REBONDS_PASSES, stat("Points, rebonds et passes décisives"))
        assertEquals(POINTS_REBONDS_PASSES, stat("Pts + Reb + Pd"))
        assertEquals(POINTS_REBONDS, stat("Nombre de points + rebonds du joueur"))
        assertEquals(POINTS_PASSES, stat("Points + passes décisives"))
        assertEquals(REBONDS_PASSES, stat("Rebonds + passes décisives"))
    }

    @Test
    fun totauxDuMatchEtDesEquipes() {
        assertEquals(
            listOf(
                TotalMatch(PLUS, 221.5, phiInd),
                TotalEquipe("PHI", PLUS, 115.5),
                TotalEquipe("IND", MOINS, 110.5),
            ),
            lire(
                "Plus de 221,5", "Total de points du match",
                "Philadelphie 76ers + de 115,5", "Total de points de l'équipe",
                "Indiana Pacers - de 110,5", "Nombre de points de l'équipe (prolongations incluses)",
                "Sixers - Pacers",
            ),
        )
        assertEquals(listOf(TotalMatch(MOINS, 230.5)), lire("Nombre total de points", "Moins de 230,5"))
        assertEquals(listOf(TotalMatch(PLUS, 221.5)), lire("Total de points du match : Plus de 221,5"))
        assertEquals(listOf(TotalEquipe("PHI", PLUS, 115.5)), lire("Philadelphie 76ers - Total de points", "Plus de 115,5"))
        // Match nommé sur la ligne du pari
        assertEquals(listOf(TotalMatch(PLUS, 221.5, phiInd)), lire("Total de points - Philadelphia 76ers @ Indiana Pacers", "Plus de 221,5"))
    }

    @Test
    fun vainqueurs() {
        assertEquals(listOf(Vainqueur("PHI")), lire("Philadelphie 76ers", "Vainqueur du match"))
        assertEquals(listOf(Vainqueur("PHI")), lire("Vainqueur (prolongations incluses)", "Philadelphia 76ers"))
        assertEquals(listOf(Vainqueur("PHI")), lire("Vainqueur du match : Philadelphie 76ers"))
        assertEquals(listOf(Vainqueur("PHI")), lire("Sixers", "Face à face"))
        assertEquals(listOf(Vainqueur("PHI", reglementaire = true)), lire("Philadelphie 76ers", "Résultat du match (t. rég)"))
        assertEquals(listOf(Vainqueur("PHI", reglementaire = true)), lire("Philadelphie 76ers", "Résultat (temps réglementaire)"))
        assertEquals(listOf(Vainqueur("PHI")), lire("Résultat du match (prolongations incluses)", "Philadelphie 76ers"))
        // Ligne du match avant le type de pari (Winamax)
        assertEquals(
            listOf(Vainqueur("LAL"), Joueur("Luka Dončić", POINTS, PLUS, 32.5, setOf("LAL", "BOS")), TotalMatch(MOINS, 228.5, setOf("LAL", "BOS"))),
            lire(
                "Basketball · NBA", "Los Angeles Lakers - Boston Celtics", "Vainqueur (prolongations incluses)", "LA Lakers", "@1.65",
                "Luka Dončić : Nombre de points (prolongations incluses)", "Plus de 32,5",
                "Total de points (prolongations incluses)", "Moins de 228,5",
            ),
        )
    }

    @Test
    fun equipesReconnues() {
        fun equipe(nom: String) = (lire(nom, "Vainqueur du match").single() as Vainqueur).equipe
        assertEquals("PHI", equipe("Philadelphie 76ers"))
        assertEquals("PHI", equipe("Philadelphia 76ers"))
        assertEquals("PHI", equipe("Sixers"))
        assertEquals("PHI", equipe("Philadelphie"))
        assertEquals("POR", equipe("Blazers"))
        assertEquals("POR", equipe("Portland Trail Blazers"))
        assertEquals("CLE", equipe("Cavs"))
        assertEquals("NOP", equipe("La Nouvelle-Orléans Pelicans"))
        assertEquals("NOP", equipe("Nouvelle-Orléans"))
        assertEquals("NOP", equipe("New Orleans Pelicans"))
        assertEquals("GSW", equipe("Golden State Warriors"))
        assertEquals("LAL", equipe("LA Lakers"))
        assertEquals("LAC", equipe("Los Angeles Clippers"))
        assertEquals("OKC", equipe("Oklahoma City Thunder"))
        assertEquals("NYK", equipe("New York Knicks"))
        assertEquals("BKN", equipe("Brooklyn Nets"))
        assertEquals("SAS", equipe("San Antonio Spurs"))
        assertEquals("MIN", equipe("Minnesota Timberwolves"))
        assertEquals("UTA", equipe("Utah Jazz"))
        assertEquals("WAS", equipe("Washington Wizards"))
        assertEquals("MIA", equipe("Miami Heat"))
        assertEquals("DEN", equipe("Denver"))
    }

    @Test
    fun nomDeJoueurQuiRessembleAUneVille() {
        // « Orlando Robinson », « P.J. Washington » : des joueurs, pas Orlando ni Washington
        assertEquals(
            listOf(
                Joueur("Orlando Robinson", POINTS, PLUS, 5.5, setOf("DAL", "ORL")),
                Joueur("P.J. Washington", REBONDS, PLUS, 6.5, setOf("DAL", "ORL")),
                Vainqueur("ORL"),
            ),
            lire(
                "Orlando Robinson + de 5,5", "Nombre de points du joueur (paliers)",
                "P.J. Washington + de 6,5", "Nombre de rebonds du joueur",
                "Orlando Magic", "Vainqueur du match",
                "Dallas Mavericks - Orlando Magic",
            ),
        )
    }

    @Test
    fun matchDesLignesDeJoueurs() {
        // Betclic : la ligne du match suit les jambes de ce match
        assertEquals(
            listOf(
                Joueur("Tyrese Maxey", POINTS, PLUS, 19.5, phiInd),
                Joueur("Jayson Tatum", POINTS, PLUS, 27.5, setOf("BOS", "MIA")),
            ),
            lire(
                "Tyrese Maxey + de 19,5", "Nombre de points du joueur (paliers)", "Philadelphie 76ers - Indiana Pacers",
                "Jayson Tatum + de 27,5", "Nombre de points du joueur (paliers)", "Boston Celtics 01:30 Miami Heat",
            ),
        )
        // Équipe entre parenthèses
        assertEquals(listOf(Joueur("Tyrese Maxey", POINTS, PLUS, 19.5, setOf("PHI"))), lire("Tyrese Maxey (76ers) + de 19,5 points"))
    }

    // ------------------------------------------------------------------ bruit et autres sports

    @Test
    fun bruitDesTicketsIgnore() {
        assertTrue(
            lire(
                "Combiné (3)", "MYCOMBI", "8,75", "Cote totale 10,06", "Mise 1,50 €", "Gains possibles 13,59 €", "09:00 • Q2",
                "Sixers 29 8 37", "Pacers 35 4 39", "Pari placé le 15/11 à 00:12", "Réf. 123456", "Basketball - NBA", "Aujourd'hui 01:00",
                "Gagné", "Cash out", "Mi-temps",
            ).isEmpty(),
        )
        assertTrue(ParisNba.lire("").isEmpty())
        assertTrue(ParisNba.lire("   \n\n  ").isEmpty())
        assertTrue(ParisNba.lire("Bonjour, voici mon pari pour ce soir !").isEmpty())
    }

    @Test
    fun autresSportsIgnores() {
        assertTrue(
            lire(
                "Combiné (3)",
                "Paris SG", "Résultat du match (t. rég)", "Paris SG 21:00 Marseille", "1,45",
                "Kylian Mbappé + de 0,5", "Nombre de buts du joueur", "Real Madrid - FC Barcelone",
                "Plus de 2,5", "Nombre de buts du match", "Lens - Lille",
                "Mise 10,00 €",
            ).isEmpty(),
        )
        // Passes décisives au football : écartées par la ligne du match (pas NBA)
        assertTrue(lire("Ousmane Dembélé + de 0,5", "Nombre de passes décisives du joueur", "Paris SG 21:00 Marseille").isEmpty())
        // Combiné : les jambes NBA restent
        assertEquals(
            listOf(Vainqueur("BOS"), Joueur("Jayson Tatum", POINTS, PLUS, 27.5, setOf("BOS", "MIA"))),
            lire(
                "Paris SG", "Résultat du match (t. rég)", "Paris SG 21:00 Marseille",
                "Boston Celtics", "Vainqueur du match", "Jayson Tatum + de 27,5", "Nombre de points du joueur (paliers)",
                "Boston Celtics 01:30 Miami Heat",
            ),
        )
    }

    // ------------------------------------------------------------------ interceptions et contres

    /** Ticket de l'utilisateur (type de pari et joueur, puis « Plus de » à la ligne suivante). */
    @Test
    fun interceptionsEtContres() {
        assertEquals(
            listOf(
                Joueur("Tyrese Maxey", POINTS, PLUS, 24.5),
                Joueur("Tyrese Maxey", REBONDS, PLUS, 2.5),
                Joueur("Tyrese Maxey", INTERCEPTIONS, PLUS, 0.5),
            ),
            lire(
                "Nombre de points du joueur - Tyrese Maxey :", "Plus de 24,5",
                "Nombre de rebonds du joueur - Tyrese Maxey :", "Plus de 2,5",
                "Nombre d'interceptions du joueur - Tyrese Maxey :", "Plus de 0,5",
            ),
        )
        assertEquals(listOf(Joueur("Joel Embiid", CONTRES, PLUS, 1.5)), lire("Joel Embiid + de 1,5", "Nombre de contres du joueur"))
        assertEquals(listOf(Joueur("Joel Embiid", INTERCEPTIONS_CONTRES, MOINS, 2.5)), lire("Joel Embiid - Interceptions + contres : Moins de 2,5"))
    }

    /** Ligne non suivie lue sur deux lignes : pas de « : · » dans le libellé. */
    @Test
    fun libelleNonSuiviSansPonctuationEnTrop() {
        assertEquals(
            listOf(NonSuivie("Nombre de fautes du joueur - Tyrese Maxey · Plus de 0,5")),
            lire("Nombre de fautes du joueur - Tyrese Maxey :", "Plus de 0,5"),
        )
    }

    // ------------------------------------------------------------------ non suivis

    @Test
    fun parisNbaNonSuivis() {
        assertEquals(
            listOf(
                NonSuivie("Philadelphie 76ers (-4,5) · Handicap (prolongations incluses)"),
                NonSuivie("Joel Embiid Oui · Double-double"),
                NonSuivie("Tyrese Maxey + de 1,5 · Nombre de fautes du joueur"),
                NonSuivie("Philadelphie 76ers · Vainqueur de la 1re mi-temps"),
            ),
            lire(
                "Philadelphie 76ers (-4,5)", "Handicap (prolongations incluses)",
                "Joel Embiid Oui", "Double-double",
                "Tyrese Maxey + de 1,5", "Nombre de fautes du joueur",
                "Philadelphie 76ers", "Vainqueur de la 1re mi-temps",
                "Philadelphie 76ers - Indiana Pacers",
            ),
        )
        // Sélection d'un joueur sans type de pari : non suivie
        assertEquals(listOf(NonSuivie("Tyrese Maxey + de 19,5")), lire("Tyrese Maxey + de 19,5"))
        // Libellé coupé
        val long = lire("Tyrese Maxey + de 1,5 et encore beaucoup de texte qui ne veut rien dire du tout", "Nombre de fautes du joueur (prolongations incluses)")
        assertTrue((long.single() as NonSuivie).texte.length <= ParisNba.NON_SUIVIE_MAX)
    }

    // ------------------------------------------------------------------ limites

    @Test
    fun doublonsEtLimites() {
        // La même ligne écrite de trois façons : une seule
        assertEquals(1, lire("Tyrese Maxey + de 19,5 points", "Tyrese Maxey - Points : Plus de 19,5", "Tyrese Maxey 20+ points").size)
        // 20 lignes au plus
        val beaucoup = (1..30).joinToString("\n") { "Tyrese Maxey Plus de ${it},5 points" }
        assertEquals(ParisNba.LIGNES_MAX, ParisNba.lire(beaucoup).size)
        // Texte trop long : 4000 caractères lus, sans la ligne entamée
        val texte = "Tyrese Maxey + de 19,5 points\n" + "x".repeat(3960) + "\nJoel Embiid + de 9,5 rebonds"
        assertTrue(texte.length > ParisNba.TEXTE_MAX)
        assertEquals(listOf(Joueur("Tyrese Maxey", POINTS, PLUS, 19.5)), ParisNba.lire(texte))
        assertTrue(ParisNba.limiter(texte).endsWith("x"))
        assertEquals("court", ParisNba.limiter("court"))
    }

    // ------------------------------------------------------------------ noms des joueurs

    private val joueurs = listOf(
        joueurPari("T. Maxey", "Tyrese Maxey"),
        joueurPari("K. Caldwell-Pope", "Kentavious Caldwell-Pope"),
        joueurPari("L. Dončić", ""),
        joueurPari("J. Jackson Jr.", ""),
        joueurPari("R. O'Neale", "Royce O'Neale"),
        joueurPari("K. Towns", ""),
        joueurPari("H. Jones", ""),
        joueurPari("J. Williams", "Jalen Williams"),
        joueurPari("J. Williams", "Jaylin Williams"),
        joueurPari("S. Gilgeous-Alexander", "Shai Gilgeous-Alexander"),
        joueurPari("P. Washington", ""),
        joueurPari("A. Şengün", ""),
    )

    private fun trouve(nom: String): String? = NomsJoueurs.trouver(nom, joueurs)?.let { it.nomComplet.ifEmpty { it.nom } }

    @Test
    fun nomsDesJoueurs() {
        assertEquals("Tyrese Maxey", trouve("Tyrese Maxey"))
        assertEquals("Tyrese Maxey", trouve("T. Maxey"))
        assertEquals("Tyrese Maxey", trouve("TYRESE MAXEY"))
        assertEquals("Kentavious Caldwell-Pope", trouve("Kentavious Caldwell-Pope"))
        assertEquals("Kentavious Caldwell-Pope", trouve("Kentavious Caldwell Pope"))
        assertEquals("L. Dončić", trouve("Luka Doncic"))
        assertEquals("L. Dončić", trouve("Luka Dončić"))
        assertEquals("J. Jackson Jr.", trouve("Jaren Jackson Jr"))
        assertEquals("J. Jackson Jr.", trouve("Jaren Jackson"))
        assertEquals("Royce O'Neale", trouve("Royce O’Neale"))
        assertEquals("Royce O'Neale", trouve("Royce ONeale"))
        assertEquals("K. Towns", trouve("Karl-Anthony Towns"))
        assertEquals("H. Jones", trouve("Herb Jones"))
        assertEquals("Shai Gilgeous-Alexander", trouve("Shai Gilgeous-Alexander"))
        assertEquals("P. Washington", trouve("P.J. Washington"))
        assertEquals("A. Şengün", trouve("Alperen Sengun"))
        // Deux « J. Williams » : le nom complet départage
        assertEquals("Jalen Williams", trouve("Jalen Williams"))
        assertEquals("Jaylin Williams", trouve("Jaylin Williams"))
        assertNull(trouve("J. Williams"))
        // Absent du box score
        assertNull(trouve("LeBron James"))
        // Nom de famille seul, s'il est unique
        assertEquals("Tyrese Maxey", trouve("Maxey"))
        assertNull(trouve("Williams"))
    }

    @Test
    fun nomCourt() {
        assertEquals("T. Maxey", NomsJoueurs.court("Tyrese Maxey"))
        assertEquals("K. Caldwell-Pope", NomsJoueurs.court("Kentavious Caldwell-Pope"))
        assertEquals("P.J. Washington", NomsJoueurs.court("P.J. Washington"))
        assertEquals("T. Maxey", NomsJoueurs.court("T. Maxey"))
        assertEquals("J. Jackson Jr.", NomsJoueurs.court("Jaren Jackson Jr."))
        assertEquals("19,5", texteNombre(19.5))
        assertEquals("20", texteNombre(20.0))
        assertEquals("221,5", texteNombre(221.5))
    }

    @Test
    fun cleDesLignes() {
        // Même joueur écrit autrement : même ligne
        assertEquals(Joueur("Luka Dončić", POINTS, PLUS, 30.5).cle, Joueur("luka doncic", POINTS, PLUS, 30.5, setOf("LAL")).cle)
        assertTrue(Joueur("Luka Dončić", POINTS, PLUS, 30.5).cle != Joueur("Luka Dončić", POINTS, PLUS, 31.5).cle)
        assertTrue(TotalMatch(PLUS, 221.5, phiInd).cle != TotalMatch(PLUS, 221.5, setOf("LAL", "BOS")).cle)
    }
}
