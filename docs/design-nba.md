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
  (cône de ±60°, distance pondérée) ; ▼ depuis les pastilles du bas revient au dock ; Retour
  revient au dock. Pastille sélectionnée : légèrement agrandie (×1,08), contour blanc 3 px,
  le reste de la carte inchangé (pas de voile).
- OK ouvre la **fiche** : panneau à droite (680 de large, hauteur selon le contenu, mêmes verre
  sombre et polices), deux onglets (◀ ▶) **Résumé** et **Effectif** ; Retour la ferme.
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

## Données (ESPN, flux publics, sans clé)

Requêtes simples, en-tête `Accept: application/json` seulement ; délai 8 s ; rien n'est écrit
dans le journal à part le type d'événement (jamais d'URL).

| Donnée | Adresse | Rythme |
|---|---|---|
| Classement | `https://site.api.espn.com/apis/v2/sports/basketball/nba/standings` | au démarrage du design, puis toutes les 10 min tant qu'il est affiché ; cache disque |
| Équipes (id ESPN, logos) | `https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams` | une fois par jour ; cache disque |
| Calendrier d'une équipe | `…/site/v2/sports/basketball/nba/teams/<id>/schedule` | à l'ouverture de la fiche ; cache 30 min |
| Effectif (et blessures) | `…/site/v2/sports/basketball/nba/teams/<id>/roster` | à l'ouverture de la fiche ; cache 1 h |
| Titulaires du dernier match | `…/site/v2/sports/basketball/nba/summary?event=<id>` | à l'ouverture de la fiche ; cache 6 h |
| Moyennes d'un joueur | `https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba/athletes/<id>/overview` | 5 titulaires, à l'ouverture de la fiche ; cache 6 h |
| Logos | `team.logos[0].href` (sinon `https://a.espncdn.com/i/teamlogos/nba/500/<abréviation ESPN en minuscules>.png`) | chargeur d'images de l'app, cache disque |

- Classement : `children[]` (Eastern / Western Conference) → `standings.entries[]` :
  `team.abbreviation`, `stats[] {name, value, displayValue}` : `wins`, `losses`, `playoffSeed`
  (rang de conférence), `winPercent`, `streak` (« W3 », « L8 »), `Last Ten Games` (« 7-3 ») ;
  rang de division calculé : % de victoires, puis victoires, puis rang de conférence.
- Codes ESPN → NBA : GS → GSW, NY → NYK, SA → SAS, NO → NOP, UTAH → UTA, WSH → WAS (déjà dans
  `boxscore/EquipesNba`, qui donne aussi les couleurs).
- Calendrier : `events[]` ; `competitions[0].status.type.completed` ; `competitors[]`
  (`homeAway`, `team.abbreviation`, `score.value` ou `score` texte, `winner`) ; `date` (UTC).
  Dernier = dernier terminé ; prochain = premier non terminé.
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
data class FicheEquipe(val code: String, val nomComplet: String, val dernier: MatchEquipe?, val prochain: MatchEquipe?,
                       val titulaires: List<JoueurNba>, val effectif: List<JoueurNba>, val blessures: List<BlessureNba>)
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

Analyseurs testés sur de vrais extraits ESPN raccourcis (`app/src/test/resources/nba-carte/`) ;
captures Robolectric : accueil NBA dans les 3 ambiances, pastille sélectionnée, fiche (Résumé
et Effectif), sans classement (premier démarrage hors ligne).
