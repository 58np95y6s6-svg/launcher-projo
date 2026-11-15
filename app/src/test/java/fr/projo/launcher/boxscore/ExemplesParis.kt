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

/** Joueur de test (stats données, le reste à zéro). */
fun joueurPari(
    nom: String,
    complet: String,
    pts: Int = 0,
    reb: Int = 0,
    pd: Int = 0,
    trois: Int = 0,
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
