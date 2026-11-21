package fr.projo.launcher.boxscore

/** Ticket Betclic réel (combiné NBA + football), tel que copié depuis l'appli. */
const val TICKET_BETCLIC = """Combiné (2)
MYCOMBI
8,75
Philadelphie 76ers
Vainqueur du match
Tyrese Maxey + de 19,5
Nombre de points du joueur (paliers)
Kentavious Caldwell-Pope + de 9,5
Nombre de points du joueur (paliers)
Ivica Zubac + de 2,5
Nombre de passes décisives du joueur (paliers)
09:00 • Q2
Sixers 29 8 37
Pacers 35 4 39
France
1,15
Résultat du match (t. rég)
France 20:45 Turquie
Cote totale 10,06
Mise 1,50 €
Gains possibles 13,59 €"""

/** Ticket Winamax réel de l'utilisateur (une ligne par pari), tel que copié depuis l'appli. */
const val TICKET_WINAMAX = """Triple-double LeBron James
Écart de points (handicap) Philadelphia 76ers -15.5
Nombre de points de Philadelphia 76ers Plus de 120,5
Nombre de points du joueur (paliers) - Joel Embiid Plus de 19,5
Nombre de points du joueur (paliers) - Tyrese Maxey Plus de 29,5
Nombre de points du joueur (paliers) - Tyler Herro Plus de 14,5
Nombre de points du joueur (paliers) - VJ Edgecombe Plus de 14,5
Écart de points (handicap) Philadelphia 76ers -12.5
Nombre de points Plus de 229,5
Écart de points (handicap) Philadelphia 76ers -6.5
Nombre de points Plus de 224,5
Résultat Philadelphia 76ers
Nombre de points Moins de 243,5"""

/** Joueur de test (stats données, le reste à zéro). */
fun joueurPari(
    nom: String,
    complet: String,
    pts: Int = 0,
    reb: Int = 0,
    pd: Int = 0,
    trois: Int = 0,
    int: Int = 0,
    ctr: Int = 0,
    titulaire: Boolean = true,
    aJoue: Boolean = true,
    surTerrain: Boolean? = false,
) = JoueurNba(
    nom = nom,
    titulaire = titulaire,
    surTerrain = surTerrain,
    aJoue = aJoue,
    minutes = if (aJoue) 24 else 0,
    points = pts,
    rebonds = reb,
    passes = pd,
    tirsReussis = pts / 2,
    tirsTentes = pts,
    troisReussis = trois,
    troisTentes = trois * 2,
    plusMoins = 0,
    nomComplet = complet,
    interceptions = int,
    contres = ctr,
)

/** Philadelphie (extérieur) à Indiana : 3e QT · 5:48, 71 à 74 par défaut. */
fun matchPhiInd(
    scorePhi: Int = 71,
    scoreInd: Int = 74,
    etat: EtatMatch = EtatMatch.EN_COURS,
    periode: Int = 3,
    horloge: Double? = 348.0,
) = MatchNba(
    id = "PHI-IND",
    idSource = "0022600123",
    debut = 1_000,
    ext = EquipeMatch("PHI", "76ers", scorePhi, listOf(29, 22, 20)),
    dom = EquipeMatch("IND", "Pacers", scoreInd, listOf(35, 21, 18)),
    etat = etat,
    periode = periode,
    horloge = horloge,
    finDePeriode = false,
)

/** Box score PHI-IND : Maxey, Caldwell-Pope, Zubac et Embiid selon les paramètres. */
fun boxPhiInd(
    match: MatchNba = matchPhiInd(),
    maxey: Int = 23,
    kcp: Int = 6,
    zubacPd: Int = 2,
    embiidReb: Int = 10,
    embiidJoue: Boolean = true,
) = BoxScore(
    match,
    EquipeBox(
        listOf(
            joueurPari("T. Maxey", "Tyrese Maxey", pts = maxey, reb = 3, pd = 5, trois = 3, surTerrain = true),
            joueurPari("J. Embiid", "Joel Embiid", pts = 16, reb = embiidReb, pd = 3, aJoue = embiidJoue),
            joueurPari("P. George", "Paul George", pts = 11, reb = 4, pd = 2, trois = 2, surTerrain = true),
            joueurPari("K. Oubre Jr.", "Kelly Oubre Jr.", pts = 9, reb = 5, pd = 1, surTerrain = true),
            joueurPari("V. Edgecombe", "VJ Edgecombe", pts = 8, reb = 4, pd = 2, surTerrain = true),
            joueurPari("Q. Grimes", "Quentin Grimes", pts = 4, reb = 1, pd = 1, titulaire = false, surTerrain = true),
        ),
        null,
    ),
    EquipeBox(
        listOf(
            joueurPari("P. Siakam", "Pascal Siakam", pts = 18, reb = 6, pd = 3, surTerrain = true),
            joueurPari("A. Nembhard", "Andrew Nembhard", pts = 12, reb = 2, pd = 6, trois = 2, surTerrain = true),
            joueurPari("K. Caldwell-Pope", "Kentavious Caldwell-Pope", pts = kcp, reb = 2, pd = 1, trois = 2, surTerrain = true),
            joueurPari("I. Zubac", "Ivica Zubac", pts = 14, reb = 9, pd = zubacPd, surTerrain = true),
            joueurPari("A. Nesmith", "Aaron Nesmith", pts = 10, reb = 3, pd = 1, trois = 2),
            joueurPari("T. McConnell", "T.J. McConnell", pts = 6, reb = 1, pd = 4, titulaire = false, surTerrain = true),
        ),
        null,
    ),
    surTerrainConnu = true,
)

/** Philadelphie (extérieur) à Milwaukee : 4e QT · 7:12, 88 à 81 par défaut. */
fun matchPhiMil(
    scorePhi: Int = 88,
    scoreMil: Int = 81,
    etat: EtatMatch = EtatMatch.EN_COURS,
    periode: Int = 4,
    horloge: Double? = 432.0,
) = MatchNba(
    id = "PHI-MIL",
    idSource = "0022600456",
    debut = 3_000,
    ext = EquipeMatch("PHI", "76ers", scorePhi, listOf(24, 22, 25, scorePhi - 71)),
    dom = EquipeMatch("MIL", "Bucks", scoreMil, listOf(20, 23, 21, scoreMil - 64)),
    etat = etat,
    periode = periode,
    horloge = horloge,
    finDePeriode = false,
)

/** Box score PHI-MIL : Maxey 27 pts, Embiid 21 pts et 9 rebonds, Antetokounmpo 24 pts et 8 rebonds par défaut. */
fun boxPhiMil(
    match: MatchNba = matchPhiMil(),
    maxey: Int = 27,
    embiidReb: Int = 9,
    giannisReb: Int = 8,
) = BoxScore(
    match,
    EquipeBox(
        listOf(
            joueurPari("T. Maxey", "Tyrese Maxey", pts = maxey, reb = 4, pd = 6, trois = 4, surTerrain = true),
            joueurPari("J. Embiid", "Joel Embiid", pts = 21, reb = embiidReb, pd = 4, surTerrain = true),
            joueurPari("P. George", "Paul George", pts = 14, reb = 5, pd = 3, trois = 2, surTerrain = true),
            joueurPari("K. Oubre Jr.", "Kelly Oubre Jr.", pts = 10, reb = 6, pd = 1, surTerrain = true),
            joueurPari("V. Edgecombe", "VJ Edgecombe", pts = 9, reb = 3, pd = 2, surTerrain = true),
            joueurPari("Q. Grimes", "Quentin Grimes", pts = 7, reb = 2, pd = 2, titulaire = false),
        ),
        null,
    ),
    EquipeBox(
        listOf(
            joueurPari("G. Antetokounmpo", "Giannis Antetokounmpo", pts = 24, reb = giannisReb, pd = 7, surTerrain = true),
            joueurPari("K. Porter Jr.", "Kevin Porter Jr.", pts = 15, reb = 3, pd = 5, trois = 2, surTerrain = true),
            joueurPari("M. Turner", "Myles Turner", pts = 12, reb = 7, pd = 1, trois = 2, surTerrain = true),
            joueurPari("K. Kuzma", "Kyle Kuzma", pts = 11, reb = 5, pd = 2, surTerrain = true),
            joueurPari("G. Trent Jr.", "Gary Trent Jr.", pts = 9, reb = 2, pd = 1, trois = 3, surTerrain = true),
            joueurPari("B. Portis", "Bobby Portis", pts = 10, reb = 6, pd = 1, titulaire = false),
        ),
        null,
    ),
    surTerrainConnu = true,
)

/** Combiné Winamax (une ligne par pari, « COTE » puis la cote à la ligne suivante) : 5 lignes de PHI-MIL. */
const val COMBINE_WINAMAX_PHI_MIL = """Écart de points (handicap) Philadelphia 76ers -4.5
Nombre de points du joueur (paliers) - Tyrese Maxey Plus de 19,5
Nombre de points du joueur (paliers) - Joel Embiid Plus de 24,5
Nombre de rebonds du joueur (paliers) - Giannis Antetokounmpo Plus de 9,5
Nombre de points Plus de 210,5
MISE
10,00 €
COTE
5,10
GAINS POTENTIELS
51,00 €"""

/** Combiné Betclic de PHI-MIL (sélection puis type de pari) avec sa cote totale. */
const val COMBINE_BETCLIC_PHI_MIL = """Combiné (2)
Tyrese Maxey + de 24,5
Nombre de points du joueur (paliers)
Philadelphie 76ers + de 80,5
Total de points de l'équipe
Cote totale 10,06
Mise 2,00 €
Gains possibles 20,12 €"""

/** Combiné sans cote : Embiid moins de 8,5 rebonds (perdu dès 9) et 76ers vainqueurs. */
const val COMBINE_SANS_COTE_PHI_MIL = """Joel Embiid Moins de 8,5 rebonds
Philadelphie 76ers
Vainqueur du match"""
