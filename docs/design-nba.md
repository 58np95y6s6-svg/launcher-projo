# Design « NBA » : la carte des conférences et divisions

Design d'accueil d'iBiTV (au même titre que Galerie, Verre…), identifiant `nba`, nom « NBA »,
résumé « Carte des divisions, bilans en direct et dock ». Projet personnel, jamais distribué.

Maquettes de référence (rendus 1920 × 1080 et leur source HTML, qui fait foi pour les tailles et
couleurs) : `maquette2-leaders.png` (ambiance par défaut), `maquette2-parquet.png`,
`maquette2-nuit.png`, `maquette2.html` + `monuments.js` + `scripts/dessin-monuments.js` +
`scripts/assembler2.py` (dossier de travail fourni à part, hors dépôt).

## Écran (référence 1920 × 1080, mis à l'échelle comme les autres designs)

- **Fond** : dégradé radial sombre propre à l'ambiance (voir « Ambiances »).
- **En haut à gauche** : barre verticale bicolore 10 × 64, « CLASSEMENT NBA » (Oswald 700, 40,
  capitales), dessous « SAISON 2026-27 · … » (Barlow Condensed 700, 18, espacement 0,22 em,
  60 %). La saison se déduit de la date (octobre → saison qui commence ; janvier–septembre →
  saison commencée l'année d'avant). Sous-titre : « chaque division aux couleurs de son leader »
  (ambiance Leaders) ou « conférences et divisions » (autres).
- **En haut à droite** : heure (Oswald 600, 60) et date (« MERCREDI 12 NOVEMBRE », Barlow
  Condensed 700, 18, 60 %).
- **La carte** (presque tout l'écran) : `res/raw/carte_nba.json` (format plus bas) : 6 îles (une
  par division) séparées par un vide, ombre portée sous chaque île (décalage 10, flou 9, noir
  60 %), remplissage de l'ambiance, reflet en dégradé (haut-gauche clair → bas-droite sombre),
  frontières des États en traits fins **seulement à l'intérieur des îles**, liseré de chaque île.
- **Monuments** : 17 silhouettes (parties pleines `f`, traits `s` avec épaisseur `e` et bouts
  `cap`, texte `t`), opacité 26–34 %, couleur de l'ambiance ; ceux `enMer` (hors des îles) en
  clair (#e8e2d4, 28 %).
- **Noms des divisions** : Barlow Condensed 800, 22, espacement 5, capitales, 55–62 % ;
  `horsIle` (Centrale) en clair.
- **Ronds centraux** (ambiance Leaders) : sur chaque île, cercle de rayon `r` et cercle intérieur
  (36 %), traits blancs 13 % / 10 %, et le **logo du leader** de la division en silhouette blanche
  (teinte blanche, 13–16 %) au centre, largeur ≈ 1,1 r. Découpés par l'île.
- **Pastilles des 30 équipes** (`pastilles`) : capsule 224 × 58 (rayon 29), fond #0a0e1c à 93 %,
  liseré blanc 14 %, ombre ; logo dans un disque blanc de rayon 29 bordé de la couleur de
  l'équipe (4 px), au point (`x`, `y`) ; `sens` 1 : capsule vers la droite, −1 : vers la gauche.
  Texte à côté du logo : bilan « 12–1 » (Oswald 700, 27) ; dessous « 1er OUEST · 1er DIV. »
  (Barlow Condensed 700, 16, 72 %, « er »/« e » en exposant ; « 1er DIV. » en or #f6c445 pour le
  leader de division). Point blanc lumineux sur la ville (`villes`) et trait blanc 60 % de la
  ville au logo si la pastille est décalée. `groupe` (« NY », « LA ») : équipes de la même ville
  dans **une seule** capsule à deux lignes (séparateur 12 %), un seul point et un seul trait.
- **Tuile « TOP 3 DES CONFÉRENCES »** (bas gauche, 452 de large, coins 26, verre sombre) : deux
  colonnes OUEST | EST (pastille de couleur de conférence), 3 lignes : rang, logo (disque 34),
  code, bilan.
- **Dock** (bas, centré) : les applis favorites puis séparateur et « Toutes les apps » (grille
  2 × 2), tuiles 80 × 80 coins 22, capsule de verre sombre 116 de haut ; la tuile sélectionnée
  monte et grandit (×1,16) avec un contour blanc et son nom au-dessus. Mêmes favoris et même
  « Toutes les apps » que les autres designs (réutiliser l'existant).
- **« BILANS À JOUR · IL Y A 4 MIN »** (bas droite, 16, 45 %) ; « HORS LIGNE · bilans de
  21:12 » si le dernier rafraîchissement a échoué ; rien tant qu'il n'y a aucun bilan.

## Ambiances (réglage du design « Ambiance » : Leaders par défaut, Parquet, Nuit d'arène)

Valeurs exactes dans `maquette2.html` (objet `PAL`) :

- **Leaders** : chaque île prend la couleur de l'équipe en tête de sa division (rang de division
  1) : couleur de l'équipe ; si trop claire (luminance > 0,45), mélangée à #0b1226 (55 %). Si
  une île voisine (liste `VOISINES`) a déjà une teinte trop proche (écart < 28° et écart de
  luminance ≤ 0,12), on prend l'accent de l'équipe assombri (mélange 45 % noir). Puis mélange
  22 % avec #0b1226. Planches discrètes (5 %), liseré blanc 55 %, textes et monuments blancs.
  Sans classement : îles gris ardoise.
- **Parquet** : bois clair à l'Est, foncé à l'Ouest (6 teintes), planches (7 %), noms et
  monuments « peints » en #1b2238.
- **Nuit d'arène** : sarcelle à l'Est, violet à l'Ouest, liseré de conférence cyan / lilas.

Rendu de la carte : coûteux une seule fois (chemins, ombres floues) → dessiné dans une image en
mémoire (une par ambiance et par jeu de couleurs) puis réaffiché ; redessiné seulement quand
l'ambiance ou les leaders changent. Pas d'animation permanente.

## `res/raw/carte_nba.json`

Tout en pixels de référence 1920 × 1080 :

```
{
  "divisions": [{"id": "atlantique", "conference": "est", "d": "M…L…Z…"}, …],   // îles (pair-impair)
  "frontieresEtats": "M…L…",                                                       // à découper par les îles
  "villes": {"BOS": [x, y], …},
  "pastilles": {"NYK": {"x": 1440, "y": 395, "sens": 1, "groupe": "NY"}, …},
  "nomsDivisions": {"centrale": {"x": 1095, "y": 207, "horsIle": true}, …},
  "rondsCentraux": {"atlantique": {"x": 1330, "y": 300, "r": 96}, …},
  "monuments": [{"id": "goldengate", "nom": "Golden Gate", "x": 210, "y": 300, "echelle": 0.9,
                 "enMer": true, "largeur": 160, "hauteur": 62, "pairImpair": false,
                 "parties": [{"m": "s", "e": 3, "d": "M0,44 L160,44"}, …]}, …]
}
```

Un monument est centré en (`x`, `y`) et mis à l'échelle `echelle` depuis son repère
`largeur` × `hauteur`. Parties : `m` = `f` (forme pleine, opacité `o` facultative, pair-impair si
`pairImpair`), `s` (trait, épaisseur `e`, bouts `cap` « round » ou plats), `t` (texte `texte`,
centré en `x`, `y`, taille `taille`, espacement `espace`, couleur du fond de l'île : il paraît
découpé). Chemins : commandes SVG M, L, Q, C, A, Z, m, a (`PathParser` de Compose).

Divisions des équipes : Atlantique BOS BKN NYK PHI TOR ; Centrale CHI CLE DET IND MIL ;
Sud-Est ATL CHA MIA ORL WAS ; Nord-Ouest DEN MIN OKC POR UTA ; Pacifique GSW LAC LAL PHX SAC ;
Sud-Ouest DAL HOU MEM NOP SAS.

## Sélection d'une équipe et fiche

- Depuis le dock, ▲ entre sur la carte (équipe la plus proche au-dessus de la tuile
  sélectionnée). Sur la carte, ◀ ▲ ▶ ▼ vont à la pastille la plus proche dans la direction
  (cône de ±60°, distance pondérée, en px de référence : la caméra du survol n'y change rien) ;
  ▼ depuis les pastilles du bas revient au dock ; Retour revient au dock. Pastille sélectionnée :
  légèrement agrandie (×1,08), contour blanc 3 px. Tant qu'une pastille est sélectionnée, la
  carte est en **survol** de sa division (voir « Survol d'une division »).
- OK ouvre la **fiche** : panneau à droite (680 de large, hauteur selon le contenu, mêmes verre
  sombre et polices), deux onglets (◀ ▶) **Résumé** et **Effectif** ; Retour la ferme (retour au
  survol).
  - En-tête : grand logo, nom (« Oklahoma City Thunder »), bilan, « 6e OUEST · 2e NORD-OUEST »,
    série (« 3 victoires de suite » / « 2 défaites de suite ») et 10 derniers (« 7–3 sur les 10
    derniers »).
  - Résumé : **Dernier match** (« Défaite 108–118 à Portland · mar. 11 nov. », logo de
    l'adversaire, V/D colorée) ; **Prochain match** (« ven. 14 nov. · 4:30 · à LA Clippers »,
    heure locale) ; **Cinq de départ** (les 5 titulaires du dernier match, avec moyennes de la
    saison : MIN, PTS, REB, PD) ; **Blessures** (nom, statut traduit, zone : « Absent · orteil »).
  - Effectif : tous les joueurs (numéro, nom, poste, « blessé » le cas échéant), deux colonnes.
  - Postes en abréviations françaises : MJ, AR, AI, AF, P (ESPN : PG, SG, SF, PF, C ; G → AR,
    F → AI ; « G-F » → « AR-AI »).
  - Chargement : squelette discret ; erreur : « Fiche indisponible pour l'instant ».

## Survol d'une division

Quand on se promène d'équipe en équipe, la caméra cadre la division de l'équipe sélectionnée, qui
se soulève de la carte ; un panneau résume l'équipe (maquette `maquette-survol.png`, script
`scripts/survol.py`, dossier de travail hors dépôt). Code : `ui/SurvolNba.kt`.

- **État** : survol = une pastille sélectionnée (`choisie`) et la fiche fermée. Avec la fiche
  ouverte, la caméra et la division soulevée restent (seul le panneau laisse la place à la fiche).
- **Caméra** (calque de la carte et de ses pastilles, `graphicsLayer`, pas de nouveau dessin) :
  cadre de la division = son île, ses 5 pastilles et ses villes (+ 8 px) ; elle est agrandie pour
  que ce cadre, soulevé, tienne dans la zone à gauche du panneau (x 40…1230, y 140…900), au plus
  ×1,75, son milieu soulevé au milieu de la zone (Atlantique, Centrale, Pacifique ×1,75 ;
  Sud-Est ×1,61 ; Sud-Ouest ×1,59 ; Nord-Ouest ×1,33). 450 ms, lent – rapide – lent
  (cubic-bezier 0,42 0 0,58 1) ; l'échelle varie de façon géométrique et le milieu de la division
  d'arrivée glisse en ligne droite. Pastille de la même division : la caméra ne bouge pas ; d'une
  autre division (les flèches peuvent sortir de la division) : la caméra glisse vers elle. Sortie
  du survol : retour à la carte entière en 400 ms.
- **Division soulevée** : agrandie ×1,05 autour du milieu de son cadre et montée de 26 px ;
  ombre portée laissée à sa place (décalée de 26 px vers le bas, flou 26 px, noir 75 %) et ombre
  ambiante large (flou 70 px, noir 35 %) ; bord lumineux le long de l'île (blanc 50 %, 2 px, lueur
  #c8d2eb 55 % floutée). La levée suit la caméra ; la division précédente redescend en s'effaçant.
  Nette à l'échelle de la caméra : l'île (remplissage, planches, reflet, logo du leader en
  filigrane, frontières, liserés, monuments posés dessus, nom, villes, capsules des pastilles) est
  redessinée seule dans une image à l'échelle finale (×1,05 compris), hors du fil principal, dès
  qu'on entre dans la division (≈ 60–90 ms sur un PC) ; son ombre dans une image au quart. Les
  logos, bilans et rangs de ses 5 pastilles (et la pastille choisie, agrandie et cerclée) sont
  redessinés par-dessus, en vectoriel. Images gardées : 3 divisions au plus (≈ 4 Mo chacune en
  1080p, 1,3 Mpx au plus : moins de 16 Mo en tout).
- **Le reste en retrait** : la carte (image ×1, simplement agrandie : un peu floue, c'est voulu)
  désaturée de moitié (matrice de couleurs), voile noir 62 % (38 % de luminosité), bords assombris
  (vignette) ; logos et textes des autres pastilles à 55 % en plus ; fondu de la couleur du fond
  sous l'en-tête (titre et heure restent lisibles, non agrandis). Le top 3 s'efface ; le dock et
  l'état des bilans ne changent pas.
- **Panneau de survol** (x 1262, y 140, 600 de large, jusqu'à y 986 ; au-dessus du dock, à
  20 px, quand le dock passe dessous ; verre sombre, coins 30) :
  - en-tête : logo dans un disque blanc cerclé de la couleur de l'équipe (92), nom (Oswald 38),
    bilan (Oswald 32) et « 11e OUEST · 4e SUD-OUEST » (exposants) ; puis « 10 victoires de suite ·
    meilleur marqueur : V. Wembanyama, 30,3 pts · 11,7 reb » (série du classement ; meilleur
    marqueur = le titulaire qui marque le plus, d'après la fiche ; sur deux lignes si trop long) ;
  - « 3 DERNIERS MATCHS » : 3 lignes au plus, la plus récente d'abord : pastille V / D (vert
    #50dc82 / rouge #ff5c5c), logo de l'adversaire, score (l'équipe d'abord, « 124–119 »),
    « à Chicago » / « contre Golden State », date « jeu. 12 nov. » (heure locale) ;
  - « PROCHAIN MATCH » : logo, « sam. 14 nov. · 3:30 », lieu ;
  - « DIVISION SUD-OUEST » : les 5 équipes par rang de division (rang, logo, code, bilan,
    « ÉCART » = ((V du leader − V) + (D − D du leader)) / 2, virgule française, « — » pour le
    leader), l'équipe survolée surlignée ;
  - aide : touches « OK » fiche complète · « FLÈCHES » équipe voisine · « RETOUR » carte entière.
  En-tête et classement de la division s'affichent tout de suite (classement) ; matchs et
  meilleur marqueur attendent la fiche (squelette discret ; « Matchs indisponibles pour
  l'instant » si elle manque). La fiche est demandée 300 ms après la sélection (les flèches
  enchaînées ne demandent rien), par `DonneesNba.fiche` (caches habituels, fil `IO`) ; OK ouvre
  la fiche complète avec cette fiche déjà lue.
- **Touches** : OK : fiche complète (fondu enchaîné panneau → fiche) ; Retour dans la fiche :
  le survol ; Retour en survol, ▼ sous les pastilles du bas, Maison, focus qui quitte le dock :
  plus de sélection, retour à la carte entière, le top 3 revient.
- **Coût** (projecteur 1 Go, Fire TV) : rien n'est créé pendant l'animation (filtres de couleur
  préparés, transformations de calques) ; la carte n'est pas redessinée ; une image par division
  survolée (≤ 3 gardées).

## Données (ESPN, flux publics, sans clé)

Requêtes simples, en-tête `Accept: application/json` seulement ; délai 8 s ; rien n'est écrit
dans le journal à part le type d'événement (jamais d'URL).

| Donnée | Adresse | Rythme |
|---|---|---|
| Classement | `https://site.api.espn.com/apis/v2/sports/basketball/nba/standings` | au démarrage du design, puis toutes les 10 min tant qu'il est affiché ; cache disque |
| Équipes (id ESPN, logos) | `https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams` | une fois par jour ; cache disque |
| Calendrier d'une équipe | `…/site/v2/sports/basketball/nba/teams/<id>/schedule` | fiche demandée (survol ou fiche) ; cache 30 min |
| Effectif (et blessures) | `…/site/v2/sports/basketball/nba/teams/<id>/roster` | fiche demandée ; cache 1 h |
| Titulaires du dernier match | `…/site/v2/sports/basketball/nba/summary?event=<id>` | fiche demandée ; cache 6 h |
| Moyennes d'un joueur | `https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba/athletes/<id>/overview` | 5 titulaires, fiche demandée ; cache 6 h |

« Fiche demandée » : 300 ms après la sélection d'une équipe sur la carte (survol), ou à
l'ouverture de la fiche si elle n'est pas encore là.
| Logos | `team.logos[0].href` (sinon `https://a.espncdn.com/i/teamlogos/nba/500/<abréviation ESPN en minuscules>.png`) | chargeur d'images de l'app, cache disque |

- Classement : `children[]` (Eastern / Western Conference) → `standings.entries[]` :
  `team.abbreviation`, `stats[] {name, value, displayValue}` : `wins`, `losses`, `playoffSeed`
  (rang de conférence), `winPercent`, `streak` (« W3 », « L8 »), `Last Ten Games` (« 7-3 ») ;
  rang de division calculé : % de victoires, puis victoires, puis rang de conférence.
- Codes ESPN → NBA : GS → GSW, NY → NYK, SA → SAS, NO → NOP, UTAH → UTA, WSH → WAS (déjà dans
  `boxscore/EquipesNba`, qui donne aussi les couleurs).
- Calendrier : `events[]` ; `competitions[0].status.type.completed` ; `competitors[]`
  (`homeAway`, `team.abbreviation`, `score.value` ou `score` texte, `winner`) ; `date` (UTC).
  Derniers = les 3 derniers terminés, le plus récent d'abord (`derniers`, `dernier` = le
  premier) ; prochain = premier non terminé (ni annulé ni reporté).
- Effectif : `athletes[]` : `id`, `displayName`, `shortName`, `jersey`, `position.abbreviation`,
  `injuries[] {status, date, details?{type, location, side}}`.
- Résumé du match : `boxscore.players[]` (une entrée par équipe, `team.abbreviation`) →
  `statistics[0].athletes[] {athlete{id, shortName, position}, starter, stats[]}`.
- Moyennes : `statistics.names` / `labels` et `statistics.splits[]` : `displayName` « Regular
  Season » → `stats[]` (`avgMinutes`, `avgPoints`, `avgRebounds`, `avgAssists`, `gamesPlayed`).
- Statuts de blessure : Out → « Absent », Day-To-Day → « Au jour le jour », Questionable →
  « Incertain », Doubtful → « Peu probable », Probable → « Probable », Suspension → « Suspendu » ;
  zones courantes traduites (knee → genou, ankle → cheville, toe → orteil, hamstring → ischios,
  foot → pied, back → dos, hip → hanche, calf → mollet, groin → aine, shoulder → épaule,
  wrist → poignet, hand → main, illness → maladie, rest → repos, concussion → commotion…),
  sinon le terme d'origine.

## Contrat Kotlin (paquet `fr.projo.launcher.nba`)

L'affichage n'utilise que ceci ; les données le fournissent.

```kotlin
enum class ConferenceNba { EST, OUEST }
enum class DivisionNba(val id: String, val conference: ConferenceNba, val nom: String) {
    ATLANTIQUE("atlantique", EST, "Atlantique"), CENTRALE("centrale", EST, "Centrale"), SUD_EST("sudest", EST, "Sud-Est"),
    NORD_OUEST("nordouest", OUEST, "Nord-Ouest"), PACIFIQUE("pacifique", OUEST, "Pacifique"), SUD_OUEST("sudouest", OUEST, "Sud-Ouest");
    companion object { fun de(code: String): DivisionNba } // division d'une équipe (code NBA)
}
data class BilanNba(val code: String, val victoires: Int, val defaites: Int, val rangConference: Int,
                    val rangDivision: Int, val serie: Int /* +3 : 3 victoires de suite ; −2 */, val dixDerniers: String?)
data class ClassementNba(val equipes: Map<String, BilanNba>, val misAJour: Long /* ms epoch */, val horsLigne: Boolean)
data class MatchEquipe(val adversaire: String, val domicile: Boolean, val debut: Long, val score: Int?, val scoreAdversaire: Int?) {
    val victoire: Boolean? get() = if (score == null || scoreAdversaire == null) null else score > scoreAdversaire
}
data class JoueurNba(val nom: String, val numero: String?, val poste: String?, val blesse: Boolean,
                     val minutes: Double?, val points: Double?, val rebonds: Double?, val passes: Double?)
data class BlessureNba(val nom: String, val statut: String, val zone: String?)
data class FicheEquipe(val code: String, val nomComplet: String,
                       val derniers: List<MatchEquipe> /* 3 au plus, le plus récent d'abord */, val prochain: MatchEquipe?,
                       val titulaires: List<JoueurNba>, val effectif: List<JoueurNba>, val blessures: List<BlessureNba>) {
    val dernier: MatchEquipe? get() = derniers.firstOrNull()
}
object DonneesNba {
    val classement: StateFlow<ClassementNba?>   // cache disque au démarrage, puis rafraîchi
    fun suivre(actif: Boolean)                   // design NBA affiché ? (10 min tant qu'actif ; rien sinon)
    suspend fun fiche(code: String): FicheEquipe?
    fun logo(code: String): String               // URL du logo
}
```

## Télécommande (PWA)

Thème `nba` (le design de la TV choisit le thème, comme les autres) : bleu nuit #0a1534 →
#050a1c, textes blancs, accent or #f6c445, titres en Oswald, libellés en Barlow Condensed.

## Tests et captures

Analyseurs testés sur de vrais extraits ESPN raccourcis (`app/src/test/resources/nba-carte/`),
dont les 3 derniers matchs (triés, limités à 3) ; logique pure (`NbaLogiqueTest`) : écart avec le
leader, meilleur marqueur, textes des matchs, caméra (cadrage, levée, trajet) ; carte
(`CarteNbaTest`) : chaque division soulevée tient dans la zone, image nette de la division
soulevée. Écran (`NbaTest`) : entrée en survol depuis le dock, flèches dans la division (caméra
immobile) et vers une autre (caméra qui suit), OK → fiche, Retour → survol, Retour / ▼ / Maison →
carte entière sans sélection, fiche demandée une seule fois après des flèches enchaînées.
Captures Robolectric : accueil NBA dans les 3 ambiances, pastille sélectionnée (survol), survol
`nba_survol` (Spurs, Leaders), `nba_survol_est` (Celtics), `nba_survol_parquet` (Thunder,
Parquet), fiche (Résumé et Effectif, ouverte depuis le survol), sans classement (premier
démarrage hors ligne).
