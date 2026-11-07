# Télécommande smartphone (PWA) — architecture et protocole

Version du protocole : **1**

## Vue d'ensemble

```
Téléphone (PWA, GitHub Pages, HTTPS)          TV (iBiTV, Android)
        │                                              │
        │   1. mise en relation (WebSocket sécurisé)   │
        └──────────►  serveur PeerJS public  ◄─────────┘
                      (0.peerjs.com) : ne relaie que
                      OFFER / ANSWER / CANDIDATE
        │                                              │
        └──── 2. WebRTC DataChannel direct (DTLS) ─────┘
             sur le réseau local : toutes les commandes
```

- **PWA** : `https://58np95y6s6-svg.github.io/launcher-projo/` (dossier `remote-pwa/` du dépôt, publié sur la branche `gh-pages`).
  - L'URL est définie à un seul endroit de chaque côté : `remote-pwa/config.js` (PWA) et `remote/RemoteConfig.kt` (TV).
- **Mise en relation** : serveur public PeerJS (`wss://0.peerjs.com/peerjs?key=peerjs&id=<id>&token=<aléatoire>`), sans compte ni clé.
  - On n'utilise PAS la bibliothèque cliente PeerJS : les deux côtés parlent directement le protocole du serveur (messages JSON `OPEN`, `HEARTBEAT`, `OFFER`, `ANSWER`, `CANDIDATE`, `LEAVE`, `EXPIRE`, `ID-TAKEN`, `ERROR` ; champs `type`, `src`, `dst`, `payload`).
  - Le serveur ne voit que des SDP et des candidats ICE, jamais une commande ni un texte.
  - Le serveur est configurable (hôte, port, chemin, clé) à un seul endroit, pour pouvoir passer plus tard à un serveur à nous (par ex. Cloudflare).
- **STUN** : `stun:stun.l.google.com:19302` et `stun:stun1.l.google.com:19302`. Pas de TURN pour l'instant : le téléphone et la TV sont sur le même réseau.
- **DataChannel** : nommé `telecommande`, fiable et ordonné. Messages JSON UTF-8.

## Identités et clés

- **TV**
  - Identifiant PeerJS stable : `ibiprojo-<32 hex aléatoires>`, créé au premier lancement et gardé.
  - Paire de clés ECDSA P-256 dans l'Android Keystore (repli : clé logicielle chiffrée si le Keystore n'est pas disponible).
- **Téléphone**
  - Identifiant PeerJS : `tel-<32 hex aléatoires>`, gardé dans IndexedDB.
  - Paire de clés ECDSA P-256 WebCrypto, non exportable, dans IndexedDB.
- Les clés publiques sont échangées en SPKI encodé en base64url.
- Empreinte d'une clé = SHA-256 de la SPKI, en base64url, tronquée à 16 octets.

## Appairage

1. Sur la TV, Réglages → « Appairer un téléphone », ou le module « Télécommande » de l'accueil (voir plus bas).
2. La TV crée un **secret d'appairage** de 16 octets aléatoires, valable 5 minutes et à usage unique. Elle affiche :
   - le QR code `https://58np95y6s6-svg.github.io/launcher-projo/#pair=<idTV>.<secret base64url>.<empreinte clé TV>` (fragment `#` : jamais envoyé au serveur web) ;
   - un **code de secours** de 8 caractères : alphabet sans ambiguïté `23456789ABCDEFGHJKMNPQRSTUVWXYZ`, affiché `XXXX-XXXX`.
3. Code de secours `ABCD-EFGH`, saisi dans la PWA si le QR code ne passe pas :
   - les 4 premiers caractères forment un identifiant temporaire. Pendant l'appairage, la TV écoute aussi l'identifiant PeerJS `ibiprojo-c-ABCD` ;
   - les 4 suivants forment le secret court, valable 5 minutes, à usage unique, avec la limite de tentatives ;
   - la PWA n'a alors pas l'empreinte de la TV : elle accepte la clé que la TV présente. La sécurité repose sur la confirmation à l'écran de la TV, qui affiche le nom saisi sur le téléphone ;
   - après l'appairage, la TV donne son identifiant stable et sa clé, et tout continue comme avec le QR code.
4. Le téléphone demande un nom (« Téléphone de Marie »), se connecte à la TV en WebRTC, puis envoie `pair_request`.
5. La TV vérifie, dans l'ordre :
   - le secret : bon, non expiré, non utilisé ;
   - la limite de tentatives : 5 essais ratés par minute, puis blocage d'1 minute ;
   - que l'empreinte attendue par le téléphone correspond bien à sa clé.

   Puis elle affiche « Autoriser le téléphone de Marie ? » avec Autoriser / Refuser.
6. Si l'utilisateur autorise : la TV enregistre l'appareil (identifiant, nom, clé publique, date) et répond `paired`. Le secret est consommé.
7. Le téléphone enregistre la TV : identifiant, nom, clé publique, empreinte.

### Module « Télécommande » : QR code permanent sur l'accueil

Le module (Réglages → Modules → « Télécommande », `modules/telecommande/ModuleTelecommande.kt`) affiche le même QR code que le dialogue (même URL), le code de secours `XXXX-XXXX`, « Scannez avec votre téléphone » et, en MOYEN / GRAND, les téléphones appairés (pastille verte : connecté). OK sur le module ouvre le dialogue d'appairage en grand.

- **Renouvellement** (`remote/RenouvellementQr.kt`) : le secret dure toujours 5 minutes. Tant que le module est affiché (accueil au premier plan **et** écran allumé), la TV en crée un nouveau à l'échéance (ou dès qu'il est consommé) et le QR code se met à jour. Accueil caché, autre module, écran éteint : aucun secret n'est créé, et celui en cours est retiré 1 s après la disparition du module.
- **Jamais pendant une confirmation** : une demande vérifiée qui attend « Autoriser / Refuser » réserve son secret (`GestionAppairage.reserver` / `liberer`). Le renouvellement attend la réponse (ou la fermeture du canal) ; accepter ne consomme que le secret de cette demande, jamais un secret créé entre-temps. Après un appairage, « ✓ » reste affiché 6 s (et tant que le dialogue en grand est ouvert), puis nouveau QR code.
- **QR code scanné juste avant un renouvellement** : le secret remplacé répond `expire` (et non `invalide`) et ne compte pas dans la limite de tentatives. Renouveler ne remet pas la limite à zéro.
- **Sécurité** : un QR code exposé en permanence ne suffit pas à piloter la TV : tout appairage est confirmé à l'écran de la TV, avec la limite de tentatives. Contrepartie : tant que le module est affiché, la TV reste joignable (WebSocket PeerJS principale et identifiant temporaire `ibiprojo-c-XXXX`, qui change à chaque renouvellement).
- **Lisibilité à 3 m** : noir sur blanc, dans un cadre blanc avec la zone de silence de 4 modules, quel que soit le design ; cadre d'environ 100 u en PETIT (QR code de 97 u) ; bords des modules alignés sur les pixels. Correction d'erreur **L** pour le module (un écran n'a rien à reconstituer ; l'URL de 141 caractères tient en version 7, 45 × 45 modules, au lieu de 49 × 49 en M), **M** pour le grand dialogue.

## Authentification mutuelle à chaque connexion

L'empreinte DTLS (`a=fingerprint` du SDP) de chacun est liée aux clés d'identité.

- Le téléphone envoie `hello` avec :
  - son identifiant d'appareil et sa clé publique (ou son empreinte s'il est déjà appairé) ;
  - un nonce aléatoire `nonceTel` ;
  - la signature ECDSA de `"ibiprojo-v1|hello|" + empreinteDtlsTel + "|" + empreinteDtlsTV + "|" + nonceTel`.
- La TV vérifie la signature avec la clé enregistrée, ou avec la clé fournie pendant un appairage. Elle répond `hello` avec :
  - son nonce `nonceTV` ;
  - la signature de `"ibiprojo-v1|hello-tv|" + empreinteDtlsTV + "|" + empreinteDtlsTel + "|" + nonceTel + "|" + nonceTV`.
- Le téléphone vérifie avec la clé de la TV (connue par le QR code ou déjà enregistrée).
- Tant que les deux `hello` ne sont pas vérifiés, toute autre commande est ignorée.
- Si la vérification échoue, le canal est fermé, sans détail dans les journaux.
- Un serveur de mise en relation malveillant ne peut donc ni lire (DTLS) ni s'interposer : il ne peut pas falsifier les empreintes signées.

## Messages

Chaque message est un objet JSON avec `v` (version du protocole) et `t` (type).

Téléphone → TV :

| `t` | Champs | Rôle |
|---|---|---|
| `hello` | `appareil`, `cle`?, `nonce`, `sig`, `nom`? | ouverture authentifiée |
| `pair_request` | `secret`, `nom`, `cle`, `empreinteTV` | demande d'appairage |
| `key` | `k` : `up` `down` `left` `right` `ok` `back` `home` `vol+` `vol-` `mute` `menu` `playpause` | touche |
| `text` | `s` (texte complet du champ), `curseur`? | remplace le contenu du champ actif |
| `submit` | — | Entrée / valider |
| `ping` | `n` | maintien de la connexion (toutes les 15 s) |

TV → téléphone :

| `t` | Champs | Rôle |
|---|---|---|
| `hello` | `nonce`, `sig`, `nomTV`, `versionApp`, `cle`, `fonctions`? | réponse authentifiée (`cle` : SPKI de la TV, voir « Précisions » ; `fonctions` : voir « Pilotage ») |
| `paired` | `nomTV`, `idTV` | appairage accepté (`idTV` : identifiant PeerJS stable) |
| `pair_refused` | `raison` : `refuse` `expire` `invalide` `trop_d_essais` | appairage refusé |
| `revoked` | — | ce téléphone a été révoqué (la PWA oublie la TV) |
| `focus_text` | `type` : `texte` `email` `motdepasse` `nombre` `url` `recherche`, `s` (contenu actuel, vide si mot de passe), `action` : `go` `search` `send` `next` `done` | un champ texte a le focus |
| `focus_lost` | — | plus de champ texte : mode navigation |
| `pong` | `n` | réponse au ping |
| `erreur` | `code` | par ex. `version` |

Compatibilité : à `hello`, chacun annonce `v`. Si la TV reçoit une version qu'elle ne gère pas, elle répond `erreur` avec `code: "version"` et `vMax`. La PWA affiche alors :
- « Mettez à jour iBiTV sur la TV » si sa propre version est la plus récente ;
- sinon « Rechargez la télécommande ».

## Pilotage depuis le téléphone : applis et réglages (version 1, ajout compatible)

Le téléphone peut ouvrir n'importe quelle appli de la TV et régler iBiTV (son, design, mode Ambiance,
mises à jour…). Ce sont de **nouveaux types de messages** dans la version 1 du protocole : une TV qui
ne les connaît pas les ignore, et une PWA qui ne les voit pas annoncés n'affiche pas les onglets.
Comme les touches, ils ne sont acceptés que d'un téléphone **authentifié** (après les deux `hello`).

### Annonce

`hello` de la TV contient `fonctions` : liste de chaînes, ici `["apps", "reglages"]`. La PWA montre
les onglets « Applis » et « Réglages » seulement pour les fonctions annoncées (absent = aucune).

### Applis

Téléphone → TV :

| `t` | Champs | Rôle |
|---|---|---|
| `apps_liste` | — | demande la liste des applis |
| `apps_images` | `ids` : identifiants (au plus 100) | demande les images manquantes |
| `app_ouvrir` | `id` | ouvre l'appli sur la TV |

TV → téléphone :

| `t` | Champs | Rôle |
|---|---|---|
| `apps` | `liste` : `[{id, nom, favori, maj}]` | applis qu'on peut ouvrir, dans l'ordre de l'accueil |
| `app_image` | `id`, `maj`, `forme` : `banniere` \| `icone`, `img` | image d'une appli |
| `app_ouverte` | `id`, `ok` | résultat d'`app_ouvrir` |

- `id` = nom du paquet Android ; `nom` = nom affiché sur la TV ; `favori` = l'appli est dans les
  favoris de l'accueil ; `maj` = date de dernière mise à jour de l'appli (ms), qui sert de version de
  l'image.
- Ordre : les favoris dans l'ordre de l'accueil, puis les autres par ordre alphabétique. Ni les applis
  masquées, ni iBiTV, ni la tuile « Source HDMI ».
- `img` : URL `data:` (`image/webp` ou `image/png`), **48 Ko au plus** (message entier < 64 Ko, limite
  du codec de la PWA). `banniere` = image 16:9 de l'appli TV (240 × 135), sinon `icone` carrée (96 × 96).
- La PWA garde les images (clé `id` + `maj`) et ne demande que celles qui lui manquent. La TV répond
  à `apps_images` par un `app_image` par identifiant connu, dans l'ordre demandé ; elle ignore les
  identifiants inconnus.
- `app_ouvrir` : la TV n'ouvre que les identifiants de sa liste actuelle. Sinon `ok: false`, et rien
  ne s'ouvre.

### Réglages

Téléphone → TV :

| `t` | Champs | Rôle |
|---|---|---|
| `reglages_liste` | — | demande les réglages, et abonne ce téléphone à leurs changements |
| `reglage` | `cle`, et `valeur` **ou** `bouton` | change un réglage, ou appuie sur un de ses boutons |

TV → téléphone :

| `t` | Champs | Rôle |
|---|---|---|
| `reglages` | `items` : liste de réglages | état complet, dans l'ordre d'affichage |

La TV renvoie `reglages` après chaque `reglage` (même refusé, pour que le téléphone se recale), et de
lui-même quand une valeur change (volume réglé à la télécommande de la TV, progression d'une mise à
jour…), au plus toutes les 300 ms, aux seuls téléphones qui ont envoyé `reglages_liste` pendant cette
connexion.

Un réglage :

| Champ | Rôle |
|---|---|
| `cle` | identifiant stable (ex. `volume`) |
| `type` | `curseur`, `interrupteur`, `choix` ou `boutons` |
| `groupe` | titre de la section (« Son », « Affichage »…) ; les réglages d'un même groupe se suivent |
| `titre` | libellé |
| `detail` | texte secondaire facultatif (état, conseil) |
| `valeur` | `curseur` : entier ; `interrupteur` : booléen ; `choix` : `id` de l'option choisie |
| `min`, `max` | `curseur` seulement (entiers) |
| `options` | `choix` seulement : `[{id, nom, detail?}]` |
| `boutons` | `boutons` seulement : `[{id, nom, principal?}]` |
| `progression` | `boutons` seulement, facultatif : 0..1 pendant une opération longue |
| `actif` | facultatif, `false` = affiché grisé, non modifiable |

`reglage {cle, valeur}` pour `curseur` (entier ramené dans `min..max`), `interrupteur` (booléen),
`choix` (`id` d'une des options) ; `reglage {cle, bouton}` pour `boutons`. Clé, type ou valeur
inconnus : ignoré.

Réglages de la TV (version 1) :

| Groupe | `cle` | Type | Effet |
|---|---|---|---|
| Son | `volume` | curseur 0..max du flux musique | volume de la TV |
| Son | `muet` | interrupteur | couper / remettre le son |
| Affichage | `design` | choix (les designs : Verre, Pastel…) | design de l'accueil |
| Affichage | `nouveautes` | interrupteur | affiches des nouveautés en fond |
| Affichage | `galerie_defilement` | choix (`0`, `5`, `8`, `15` s) | design Galerie seulement : titre suivant quand on ne touche à rien (`0` : jamais) |
| Mode Ambiance | `ambiance_auto` | interrupteur | néon automatique avec les applis de musique |
| Mode Ambiance | `ambiance_intensite` | choix (Douce, Normale, Forte) | intensité du néon |
| Mode Ambiance | `ambiance_couleurs` | choix (Pochette, Arc-en-ciel) | couleurs du néon |
| Mode Ambiance | `ambiance_decalage` | choix (0, 100… 400 ms) | synchro avec le son |
| Envoyer sur la TV | `liens_seulement_vpn` | interrupteur | n'ouvrir les liens que si un VPN est actif (voir « Envoyer sur la TV ») |
| Envoyer sur la TV | `liens_pages` | choix (`auto`, puis les applis qui ouvrent les pages web) | appli des pages web |
| Envoyer sur la TV | `liens_raccourci` | interrupteur | accepter les liens du bouton Partager de l'iPhone |
| Envoyer sur la TV | `liens_code` | boutons `nouveau` | nouveau code du raccourci iOS |
| Mises à jour | `maj` | boutons `verifier`, `installer` | rechercher / télécharger et installer la nouvelle version d'iBiTV |
| Ouvrir sur la TV | `systeme` | boutons `wifi`, `bluetooth`, `reglages` | ouvre la page système sur la TV |

- `maj` : `detail` donne la version installée et l'état (« à jour », « nouvelle version 1.5 »,
  « vérification… », message d'erreur) ; `installer` n'est présent que si une version plus récente
  existe ; `progression` pendant le téléchargement. Android demande toujours de confirmer
  l'installation **sur l'écran de la TV** : le téléphone le rappelle, et la touche OK de la
  télécommande suffit. La TV redémarre iBiTV après l'installation : la PWA se reconnecte seule.
- Si l'écran d'accueil d'iBiTV n'est pas encore créé (rare : iBiTV est l'écran d'accueil), `maj`
  est `actif: false` avec « Ouvre iBiTV sur la TV ».

### Journaux

Comme pour le reste : types d'événements seulement (« appli ouverte », « réglage design »), jamais de
liste d'applis ni de valeurs.

### Côté TV : implémentation et précisions

- **Fichiers** : `remote/Pilotage.kt` (sans Android, testé sur la JVM : interface `Pilotage`,
  description des réglages `ReglagesTv.decrire`, validation `ReglagesTv.valider`, ordre des applis
  `ListeApplis`, regroupement `Regroupement`), `remote/PilotageLauncher.kt` (Android),
  `remote/ImagesApplis.kt` (images). `SessionTelecommande` reçoit les messages et vérifie tout ;
  `HoteSession.pilotage` (null dans un banc de test sans pilotage : pas de `fonctions`).
- **Fils** : les messages arrivent sur le fil de la télécommande ; liste des applis et images sur un
  fil à part (« pilotage », une image à la fois pour qu'une autre demande passe entre deux) ; lecture
  des réglages et toutes les écritures (états Compose, ViewModel, ouverture d'une appli ou d'une page
  système) sur le fil principal.
- **Écran d'accueil** : MainActivity prête son ViewModel (`Telecommande.attacherAccueil` à la
  création, `detacherAccueil` à la destruction). Sans lui : `maj` grisé, et la liste des applis est
  relue directement (sans l'écrire dans les journaux).
- **Renvoi de `reglages`** : récepteur `android.media.VOLUME_CHANGED_ACTION` /
  `STREAM_MUTE_CHANGED_ACTION`, préférence du design (DataStore), états Compose des nouveautés et du
  Mode Ambiance (`snapshotFlow`), flux `maj` du ViewModel, et chaque réglage appliqué. Le tout est
  regroupé : au plus un envoi toutes les 300 ms (le premier après un calme part tout de suite), et
  rien n'est lu si aucun téléphone n'est abonné. L'écoute démarre à la première demande d'un
  téléphone. Le téléphone qui envoie `reglage` reçoit en plus sa réponse directe.
- **Précisions** (choix les plus sûrs, la PWA n'a rien à changer) :
  - `id` des options : toujours des chaînes, y compris pour `ambiance_decalage` (`"0"`, `"100"`…
    `"400"`). La TV accepte aussi un entier dans `reglage.valeur` pour un `id` numérique. Un retard
    enregistré hors de la liste (ancienne version) est ajouté aux options pour rester sélectionné.
  - `curseur` : un nombre non entier est arrondi, puis ramené dans `min..max` ; une chaîne ou un
    booléen est refusé.
  - Un réglage `actif: false` refuse `reglage` (et renvoie `reglages`).
  - `apps_images` : seuls les 100 premiers éléments de `ids` sont examinés ; les éléments qui ne sont
    pas des chaînes et les doublons sont ignorés ; `ids` absent ou qui n'est pas une liste : aucune
    réponse.
  - `app_ouvrir` sans `id` chaîne non vide (255 caractères au plus) : ignoré, sans réponse. `id`
    hors de la liste actuelle : `app_ouverte {ok: false}`.
  - `apps` : la liste s'arrête avant 60 000 caractères (limite de 65 536 du codec de la PWA), soit
    plusieurs centaines d'applis ; `nom` coupé à 80 caractères.
  - Images : WebP (qualité 85, puis 70, 50, 30 si l'URL dépasse 48 Ko), PNG si le WebP échoue ;
    appli sans image possible sous 48 Ko : pas d'`app_image`. Cache mémoire (2 Mo) par paquet et `maj`.
  - `maj` : `detail` = « Version <version affichée sur la TV> · <état> » ; `verifier` (« Rechercher »)
    toujours présent, `installer` (« Installer 1.5 », `principal`) dès qu'une version plus récente
    est connue, y compris pendant le téléchargement (un second appui ne fait rien).
  - `systeme` : `wifi`, `bluetooth`, `reglages` ouvrent la page sur la TV (comme les tuiles de
    Réglages) ; si elle n'existe pas, la TV affiche « Réglage indisponible sur cet appareil ».

### Côté téléphone (PWA)

Précisions de comportement (le protocole ci-dessus ne change pas) :

- **Onglets** « Télécommande », « Applis », « Réglages » dans une barre fixe en bas de l'écran,
  seulement pour les fonctions annoncées (gardées avec la TV pour l'ouverture suivante, mises à jour à
  chaque `hello`). Sans `fonctions`, la télécommande reste seule et inchangée. Sur Applis et Réglages,
  l'en-tête est une pastille « ● Salon · iBiTV » (point : état de la connexion ; la toucher ouvre
  « Mes TV »). Un `focus_text` ramène sur la télécommande.
- Rien n'est demandé tant qu'aucun onglet n'est ouvert. `apps_liste` part à chaque ouverture de
  l'onglet Applis, `reglages_liste` à chaque ouverture de l'onglet Réglages, et l'un ou l'autre à
  chaque reconnexion si l'onglet est ouvert (l'abonnement aux réglages vaut pour une connexion).
- **Applis** : la dernière liste est gardée par TV (affichage immédiat à la réouverture). Images dans
  IndexedDB (magasin `images`, clé `id|maj` ; les versions plus anciennes de la même appli sont
  retirées) ; seules les manquantes sont demandées, par paquets de 100, une fois par connexion. Une
  image jamais reçue (8 s) est remplacée par l'initiale du nom. Après `app_ouvrir`, confirmation
  « Netflix s'ouvre sur la TV » à `app_ouverte` ; `ok: false` ou pas de réponse en 6 s : message d'erreur.
- **Réglages**, rendu générique : sections par `groupe` dans l'ordre reçu ; `curseur` avec sa valeur
  en % de `min..max` ; `choix` en pastilles (en cartes nom + détail si une option a un `detail`) ;
  `boutons` côte à côte dans l'ordre reçu, le principal en dégradé ; `progression` en barre ;
  `actif: false` grisé et désactivé ; `titre` vide : pas de ligne de titre. Un type inconnu n'est pas
  affiché. Seule exception liée à une clé : `design`, en cartes avec un aperçu coloré par `id`
  (`verre`, `pastel`, `console`, `une`, `chaines`, `studio`, `galerie` ; aperçu neutre pour un autre `id`).
- **Curseur** : au plus un `reglage` toutes les 150 ms pendant le glissé, puis la valeur finale au
  relâcher (renvoyée même identique si la TV a changé la valeur entre-temps). Les `reglages` reçus
  pendant le glissé ne bougent pas le curseur.
- **Valeurs envoyées** : affichées tout de suite, et gardées jusqu'à ce que la TV renvoie la même
  valeur ou 1,5 s (les réponses en retard aux envois intermédiaires sont ignorées) ; ensuite l'écran
  se recale sur la dernière valeur de la TV.
- `reglages` reçus : mise à jour sur place (défilement, focus et glissé préservés) ; l'affichage n'est
  reconstruit que si la structure change (clés, types, groupes, bornes, options, boutons), et jamais
  pendant un glissé.
- Bouton `installer` touché : note « La TV va demander de confirmer l'installation : appuyez sur OK
  avec la télécommande », jusqu'à la fin de la connexion (iBiTV redémarre après l'installation).
- Journaux de la PWA : `appli_ouverte`, `appli_refusee`, `reglage <cle>`.

## Envoyer sur la TV : liens (version 1, ajout compatible)

Le téléphone envoie un lien (page web, vidéo, YouTube…) que la TV ouvre dans la bonne appli. Avec
« Seulement avec le VPN », la TV n'ouvre un lien que si un VPN est actif sur elle (VYPN, Proton VPN,
WireGuard… n'importe lequel). Mêmes règles que le pilotage : nouveaux types de messages de la
version 1, acceptés seulement d'un téléphone **authentifié** ; une TV qui ne les connaît pas les
ignore, une PWA qui ne voit pas `liens` annoncé n'affiche pas l'onglet.

### Annonce

`hello` de la TV : `fonctions` contient `"liens"` → onglet « Envoyer » dans la PWA.

### Messages

Téléphone → TV :

| `t` | Champs | Rôle |
|---|---|---|
| `liens_etat` | — | demande l'état, et abonne ce téléphone à ses changements (pour cette connexion) |
| `lien_ouvrir` | `url`, `titre`?, `sansVpn`?, `vpnDabord`? | ouvre le lien sur la TV |

TV → téléphone :

| `t` | Champs | Rôle |
|---|---|---|
| `liens` | `vpn`, `seulementVpn`, `appliPages`, `appliVpn`?, `raccourci`? | état |
| `lien_ouvert` | `url`, `ok`, `ouvertDans`?, `raison`? | résultat d'un `lien_ouvrir` |

`liens` :

- `vpn` (booléen) : un VPN est actif sur la TV (réseau Android de type VPN, quelle que soit l'appli).
- `seulementVpn` (booléen) : réglage « Seulement avec le VPN ».
- `appliPages` (chaîne) : nom de l'appli qui ouvrira les pages web (« VYPN », « Silk »…) ; `""` si aucune.
- `appliVpn` (chaîne, facultatif) : nom de l'appli VPN que la TV sait ouvrir (la première installée
  de la liste de « Côté TV ») ; absent s'il n'y en a pas.
- `raccourci` (objet, facultatif) : `{adresse}`, l'URL complète à mettre dans le raccourci iOS, par
  ex. `http://192.168.1.42:8642/envoyer?c=K7Q2M9XD`. Présent seulement si le réglage
  `liens_raccourci` est activé **et** que la TV a une adresse IPv4 sur le réseau local.

La TV envoie `liens` après chaque `liens_etat`, puis d'elle-même quand le VPN s'allume ou se coupe,
quand un réglage du groupe « Envoyer sur la TV » change ou quand son adresse change : au plus un envoi
toutes les 300 ms (comme `reglages`), aux seuls téléphones abonnés.

`lien_ouvrir` :

- `url` : chaîne `http://` ou `https://` avec un hôte, 2 048 caractères au plus ; sinon
  `lien_ouvert {ok: false, raison: "invalide"}`. La TV n'ouvre jamais d'autre schéma (`file:`,
  `intent:`, `javascript:`, `content:`…).
- `titre` (facultatif, 200 caractères au plus, caractères de contrôle retirés) : montré sur la TV au
  lieu de l'hôte.
- `sansVpn: true` : ouvre même si le VPN est coupé (choix explicite « Ouvrir sans VPN »).
- `vpnDabord: true` : si le VPN est coupé, la TV ouvre l'appli VPN (`appliVpn`) et attend au plus
  60 s qu'un VPN s'allume, puis ouvre le lien.

`lien_ouvert` (`url` : celle de la demande) :

- `ok: true`, `ouvertDans` : nom de l'appli qui s'ouvre.
- `ok: false`, `raison` :
  - `vpn_coupe` : « Seulement avec le VPN » est activé, le VPN est coupé, sans `sansVpn` ni
    `vpnDabord` : rien ne s'ouvre ;
  - `attente_vpn` : (`vpnDabord`) l'appli VPN s'ouvre sur la TV ; un second `lien_ouvert` suivra,
    `ok: true` dès que le VPN s'allume, ou `raison: "delai_vpn"` après 60 s ;
  - `pas_d_appli_vpn` : `vpnDabord`, mais aucune appli VPN connue n'est installée ;
  - `remplace` : un autre lien attendait le VPN et le remplace (une seule attente à la fois) ;
  - `invalide` : URL refusée ;
  - `aucune_appli` : aucune appli de la TV ne sait ouvrir ce lien.

### Ouverture sur la TV

Mêmes règles pour un lien du téléphone et un lien du raccourci iOS :

1. **YouTube** (`youtube.com`, `www.` / `m.youtube.com`, `youtu.be`) → iBiTube (`com.ibi.ibitube`),
   sinon SmartTube (`com.teamsmart.videomanager.tv`), sinon YouTube pour TV
   (`com.google.android.youtube.tv`, `com.amazon.firetv.youtube`).
2. **Netflix** (`netflix.com`) → `com.netflix.ninja` ; **Prime Video** (`primevideo.com`,
   `amazon.<pays>/gp/video/…`) → `com.amazon.firebat`, `com.amazon.avod.thirdpartyclient`,
   `com.amazon.avod` ; **Disney+** (`disneyplus.com`) → `com.disney.disneyplus`.
3. **Vidéo directe** (chemin en `.mp4`, `.m4v`, `.mkv`, `.webm`, `.mov`, `.avi`, `.ts`, `.m3u8`,
   `.mpd`) → VLC (`org.videolan.vlc`), Kodi (`org.xbmc.kodi`), MX Player (`com.mxtech.videoplayer.ad`),
   sinon toute appli qui ouvre `video/*`.
4. **Page web** (tout le reste, et les cas 1 à 3 quand aucune appli ne prend le lien) → appli du
   réglage `liens_pages`. `auto` (par défaut) : VYPN (`net.vypn.app`, ou une appli nommée « VYPN »),
   Silk (`com.amazon.cloud9`), TV Bro (`com.phlox.tvwebbrowser`), Firefox (`org.mozilla.tv.firefox`,
   `org.mozilla.firefox`), Chrome (`com.android.chrome`), puis toute appli qui ouvre une page `https`,
   iBiTV exclue.

Chaque appli reçoit `ACTION_VIEW` avec l'URL (et le type `video/*` au cas 3), limité à son paquet ;
une appli qui ne déclare pas cette ouverture est sautée. Avant d'ouvrir, si iBiTV est à l'écran, un
bandeau s'affiche environ 1 s : « <titre ou hôte> · envoyé par <nom du téléphone, ou « l'iPhone »
pour le raccourci> — Ouverture dans <appli>… ». La musique en arrière-plan se met en pause, comme à
l'ouverture d'une appli vidéo.

### Raccourci iOS (bouton Partager de Safari)

Si `liens_raccourci` est activé, le petit serveur HTTP de la TV (port 8642, celui des puces NFC,
démarré dès qu'une des deux fonctions est activée) accepte :

- `POST /envoyer?c=<code>`, corps JSON `{"url": "…", "titre"?: "…"}` ou corps texte contenant
  seulement l'URL ;
- `GET /envoyer?c=<code>&url=<URL encodée>`.

Code : 8 caractères (lettres A–Z sans I ni O, chiffres 2–9), propre au raccourci (différent de celui
des puces NFC), comparé en temps constant ; au-delà de 10 codes faux en une minute, tout est refusé
jusqu'à la fin de la minute. Réponse `text/plain; charset=utf-8`, que le raccourci montre en
notification :

| Statut | Texte |
|---|---|
| 200 | « Envoyé sur <nom de la TV> · ouverture dans <appli> » |
| 202 | « Le VPN de la TV est coupé : réponds sur la TV » (garde-fou ci-dessous) |
| 400 | « Lien invalide » |
| 403 | « Code incorrect » |
| 404 | « Envoi depuis l'iPhone désactivé sur la TV » (réglage coupé ; même réponse pour toute autre adresse) |
| 422 | « Aucune appli de la TV ne sait ouvrir ce lien » |

**Garde-fou à l'écran** (lien du raccourci, « Seulement avec le VPN », VPN coupé) : iBiTV passe au
premier plan et demande « Le VPN de la TV est coupé » : « Allumer le VPN et ouvrir » (seulement si
`appliVpn` existe ; même attente de 60 s que `vpnDabord`), « Ouvrir sans VPN », « Annuler ».

### Réglages (groupe « Envoyer sur la TV »)

Dans `reglages` (télécommande) et dans Réglages de la TV :

| `cle` | Type | Effet |
|---|---|---|
| `liens_seulement_vpn` | interrupteur | n'ouvrir les liens que si un VPN est actif (**activé** par défaut) |
| `liens_pages` | choix : `auto` puis les applis installées qui ouvrent une page `https` (`id` = paquet) | appli des pages web |
| `liens_raccourci` | interrupteur | accepter les liens du bouton Partager de l'iPhone (**désactivé** par défaut) |
| `liens_code` | boutons `nouveau` (« Nouveau code ») | nouveau code du raccourci : l'ancien ne marche plus |

### Journaux

Types d'événements seulement : « lien envoyé (téléphone | raccourci) », « lien ouvert », « lien refusé
<raison> ». Jamais l'URL, le titre ni le code.

### Côté TV : précisions

- **Fichiers** : `liens/Liens.kt` (sans Android, testé sur la JVM : vérification de l'URL, titre,
  genre du lien, ordre des applis, appli VPN), `liens/EnvoiLiens.kt` (sans Android : déroulé,
  garde-fou, attente du VPN), `liens/OuvertureLiens.kt` (résolution `ACTION_VIEW` par paquet,
  ouverture), `liens/EnvoiTv.kt` (réglages, suivi du VPN, bandeau, garde-fou, code du raccourci),
  `remote/LiensTelecommande.kt` + `remote/LiensLauncher.kt` (`HoteSession.liens` ; null : pas de
  `liens` dans `fonctions`), `puces/ServeurPuces.kt` (route `/envoyer`), `system/AdresseLocale.kt`
  (adresse sur le réseau local), `ui/EnvoyerTvDialogs.kt` (réglages, bandeau, garde-fou).
- **Annonce** : `fonctions` = `["apps", "reglages", "liens"]` (`liens` est ajouté à la fin).
- **Fils** : comme le pilotage, les messages arrivent sur le fil de la télécommande ; tout le reste
  (état du VPN, réglages, ouverture, bandeau, garde-fou) sur le fil principal ; la route HTTP attend
  la réponse (10 s au plus) sur le fil du serveur.
- **`lien_ouvrir`** : l'URL est rognée de ses espaces ; schéma et hôte passent en minuscules (filtres
  d'intention des applis) ; refusée si elle contient un espace ou un caractère de contrôle, ou si le
  port est invalide. `sansVpn` / `vpnDabord` : seulement le booléen `true`. Les deux à la fois :
  `vpnDabord` l'emporte. `vpnDabord` avec un VPN déjà actif : ouverture tout de suite.
  `lien_ouvert.url` : l'URL de la demande telle quelle (`""` si ce n'était pas une chaîne).
- **« envoyé par »** : nom du téléphone appairé, avec l'article quand il commence par un nom commun
  (« Téléphone de Marie » → « le téléphone de Marie », « iPhone de Paul » → « l'iPhone de Paul ») ;
  « l'iPhone » pour le raccourci.
- **Ouverture** : réponse `ok: true` une fois l'appli lancée (après le bandeau d'1 s si iBiTV est à
  l'écran). Une appli qui refuse le lancement est sautée au profit de la suivante. Avant d'ouvrir :
  `PauseMusique.pauser` (toujours, quel que soit le genre du lien). Rien n'est écrit dans le journal
  à propos de l'intention.
- **`liens_pages`** : options = `auto` puis les applis qui ouvrent `https://example.com/`
  (`MATCH_DEFAULT_ONLY | MATCH_ALL`), dans l'ordre « auto » ; une appli choisie qui n'est plus
  installée revient à `auto`. Le choix est essayé en premier, puis l'ordre « auto ».
- **Adresse du raccourci** : IPv4 d'une interface Wi-Fi ou Ethernet, jamais celle d'un VPN (tun0,
  wg0… ou interface point à point), ni 127.x / 169.254.x ; adresse privée d'abord. Changement de
  réseau (`registerDefaultNetworkCallback`) : `liens` renvoyé.
- **Raccourci iOS, compléments** (le tableau des statuts ci-dessus ne change pas) : `/envoyer/`
  accepté aussi ; corps du POST : JSON, texte (l'URL seule) ou formulaire `url=…&titre=…`, 16 Ko au
  plus, `Content-Length` ou envoi par morceaux ; GET : `c` et `url` seulement. Pendant le blocage
  (plus de 10 codes faux dans la minute) : **429** « Trop d'essais : réessaie dans une minute » (au
  lieu de 403, pour ne pas faire croire que le bon code est faux) ; autre méthode que GET / POST :
  405 ; pas de réponse de la TV en 10 s : 500 « La TV n'a pas répondu ». Le code des puces NFC et
  celui du raccourci ont chacun leur compte d'échecs. Serveur démarré pour le raccourci seulement :
  une puce NFC reçoit la page 404 « Puces NFC désactivées ». La TV n'est pas réveillée (contrairement
  aux puces NFC).
- **Journal** : « lien envoyé (téléphone | raccourci) », « lien ouvert », « lien refusé <raison> »
  (`attente_vpn` compris), « garde-fou affiché », « lien refusé annule », « raccourci : code refusé »,
  « VPN allumé / coupé ». Jamais l'URL, le titre ni le code.
- **VPN actif** : un réseau de `ConnectivityManager` a le transport `TRANSPORT_VPN` ; suivi par
  `registerNetworkCallback` (renvoi de `liens`, pastille de l'accueil).
- **Appli VPN** (`appliVpn`, « Allumer le VPN ») : première installée de VYPN (`net.vypn.app`),
  Proton VPN (`ch.protonvpn.android`), WireGuard (`com.wireguard.android`), Cloudflare WARP
  (`com.cloudflare.onedotonedotonedotone`), NordVPN (`com.nordvpn.android`), Surfshark
  (`com.surfshark.vpnclient.android`), ExpressVPN (`com.expressvpn.vpn`), Windscribe
  (`com.windscribe.vpn`), Mullvad (`net.mullvad.mullvadvpn`) ; ouverte comme une appli.
- **Pastille VPN** (design Galerie, en-tête, à gauche de Réglages) : verte « VPN » quand un VPN est
  actif ; rouge « VPN coupé » si « Seulement avec le VPN » est activé et le VPN coupé ; cachée sinon.
  OK dessus ouvre l'appli VPN.
  - Verre de l'en-tête, point de couleur, « VPN » en gras et, en petit gris, le nom de l'appli VPN
    (verte) ou « coupé » (rouge). Dans l'étage du haut de Galerie : ◀ ▶ entre les onglets, elle et
    Réglages. Sans appli VPN connue, OK l'annonce (« Aucune appli VPN connue sur la TV »).
- **Bandeau et garde-fou** : Galerie a son dessin (pilule d'encre sous les onglets ; carte de verre
  claire, bouclier rouge barré, titre à empattements, boutons en pilules) ; les autres designs
  prennent l'habillage de leurs dialogues. Focus du garde-fou sur « Allumer le VPN et ouvrir »
  (sinon « Ouvrir sans VPN ») ; Retour = « Annuler ».
- **Réglages de la TV** : tuile « Envoyer sur la TV » (section Télécommande smartphone, tous les
  designs) → dialogue : « Seulement avec le VPN », état du VPN (OK : son appli), pages web
  (« Automatique » puis les applis), « Envoyer depuis l'iPhone » (avec l'adresse du raccourci) et
  « Nouveau code » (confirmé par un second OK).

### Côté téléphone (PWA)

- **Onglet « Envoyer »** (icône écran + flèche), entre « Applis » et « Réglages », si `liens` est
  annoncé. `liens_etat` part à chaque ouverture de l'onglet, et à chaque reconnexion s'il est ouvert.
- **Champ** + « Coller » (presse-papiers ; si l'accès est refusé, invitation à coller à la main).
  « Envoyer sur la TV » n'est actif que pour une URL valable ; un domaine sans schéma est complété
  (`arte.tv/x` → `https://arte.tv/x`).
- **Ligne d'état** sous le bouton : « VPN de la TV actif · les pages s'ouvrent dans X », ou « VPN de
  la TV coupé » (avec « · la TV demandera de l'allumer » si `seulementVpn`).
- **Résultat** : `ok` → « Envoyé sur <TV> · ouverture dans X », champ vidé, lien en tête des récents ;
  `vpn_coupe` → feuille « Le VPN de la TV est coupé » : « Allumer le VPN (<appliVpn>) et ouvrir »
  (renvoi avec `vpnDabord`, seulement si `appliVpn`), « Ouvrir sans VPN » (renvoi avec `sansVpn`),
  « Annuler » ; `attente_vpn` → « Allume le VPN sur la TV : le lien s'ouvrira ensuite » ; `delai_vpn`,
  `pas_d_appli_vpn`, `remplace`, `invalide`, `aucune_appli` → message clair ; sans réponse en 6 s :
  erreur.
- **Récents** : les 20 derniers liens de chaque TV (titre ou hôte ; « hôte · ouvert dans X · date » ;
  bouton « Renvoyer ») ; un lien renvoyé remonte en tête, sans doublon. Gardés sur le téléphone.
- **« Depuis Safari »** → écran « Bouton Partager » : les 4 étapes du raccourci et `raccourci.adresse`
  avec « Copier » ; sans adresse, bouton « Activer sur la TV » (`reglage {cle: "liens_raccourci",
  valeur: true}`). Si l'adresse a changé depuis la dernière copie (gardée par TV) : bandeau
  « L'adresse de la TV a changé : mets à jour ton raccourci ».
- Journaux de la PWA : `lien_envoye`, `lien_refuse <raison>` (jamais l'URL).

## Côté TV : injection

- **Texte** : clavier iBiTV (IME, `remote/ClavierTelecommande`). Il remplace le contenu du champ actif via `InputConnection` (`setComposingText` / `commitText`, `deleteSurroundingText`) et envoie l'action de l'éditeur pour `submit`. Il détecte le focus : `onStartInput` donne le type du champ via `EditorInfo.inputType` / `imeOptions`, et `onFinishInput` envoie `focus_lost`.
  - Activation unique : Paramètres → Clavier (projecteur) ; sur Fire TV : `adb shell ime enable fr.projo.launcher/.remote.ClavierTelecommande` puis `ime set …`.
- **Navigation** :
  - quand le clavier iBiTV est lié à un champ : touches directionnelles envoyées par `InputConnection.sendKeyEvent` ;
  - sinon, par le service d'accessibilité : `GLOBAL_ACTION_BACK` / `GLOBAL_ACTION_HOME`, et déplacement du focus vers l'élément focusable voisin dans la direction demandée, puis `ACTION_CLICK` pour OK ;
  - repli silencieux si rien n'est possible.
- **Volume** : `AudioManager.adjustStreamVolume`.
- Une appli à grille de lettres maison n'ouvre pas de clavier : le téléphone reste en mode navigation, sans erreur.

## Journaux

Jamais de texte saisi, de secret, de clé privée ni de SDP complet dans les journaux, des deux côtés. On journalise seulement les types d'événements (connexion, appairage accepté/refusé, révocation).

## Hébergement

- **PWA** : la GitHub Action `.github/workflows/remote-pwa.yml` copie `remote-pwa/` sur la branche `gh-pages` (commit orphelin poussé en force, avec un fichier `.nojekyll`), quand ce dossier change sur `claude/launcher-projo`, `claude/launcher-firetv` ou `main`, ou à la demande (workflow_dispatch). Une seule PWA sert les deux appareils : la dernière publiée gagne, et elle reste compatible avec les TV plus anciennes (onglets Applis et Réglages cachés sans `fonctions`).
  - **Réglage unique dans le dépôt** : Settings → Pages → Source « Deploy from a branch », branche `gh-pages`, dossier `/` → Save. Le site est ensuite servi sur `https://58np95y6s6-svg.github.io/launcher-projo/`.
  - `build.yml` ignore la branche `gh-pages` (pas de compilation d'APK dessus).
- **Service worker** : version dans `config.js` (`versionApp`). À la publication, l'Action y ajoute le SHA du commit (`1.0.0+abc1234`) : chaque publication est donc détectée. Une nouvelle version affiche la bannière « Nouvelle version disponible — Recharger ».
- **Tests** : `.github/workflows/remote-pwa-tests.yml` (Node 22 + Playwright/Chromium, sans Android) lance les tests unitaires, les tests de bout en bout et produit les captures (artefact `captures-telecommande`). En local :

  ```bash
  cd remote-pwa-dev && npm install && npx playwright install chromium
  npm test            # unitaires (node:test)
  npm run test:e2e    # PWA ↔ fausse TV, via un faux serveur PeerJS local
  npm run captures    # captures iPhone 390×844 et Android 412×915
  ```

  `remote-pwa-dev/` (jamais publié) contient aussi la fausse TV en JavaScript (`e2e/fausse-tv.js`, utile comme implémentation de référence du côté TV), le faux serveur PeerJS (`e2e/faux-peerjs.mjs`) et la génération des icônes (`outils/icones.py`).

## Précisions d'implémentation (v1) — à respecter à l'identique côté TV

Ces points complètent la spécification ; la PWA les applique et la fausse TV des tests aussi.

### Encodages

- **base64url** : RFC 4648 §5, **sans** remplissage `=`.
- **Nonce** et **secret d'appairage** : 16 octets aléatoires → 22 caractères base64url.
- **Empreinte de clé** : `base64url(SHA-256(SPKI DER)[0..16])` → 22 caractères.
- **Empreinte DTLS** (dans les textes signés) : lue dans la ligne `a=fingerprint:` du SDP, première ligne en `sha-256` (sinon la première), **normalisée** en `"<algorithme en minuscules> <octets HEX MAJUSCULES séparés par :>"`, par ex. `sha-256 4F:1A:…:DD`. Chacun lit la sienne dans SA description locale et celle de l'autre dans la description distante.
- **Textes signés** (UTF-8, séparateur `|`, rien d'autre) :
  - téléphone : `ibiprojo-v1|hello|<fpTel>|<fpTV>|<nonceTel>` ;
  - TV : `ibiprojo-v1|hello-tv|<fpTV>|<fpTel>|<nonceTel>|<nonceTV>`.
- **Signatures** : ECDSA P-256 / SHA-256, au format **IEEE P1363** (`r ‖ s`, 64 octets, chacun sur 32 octets), en base64url (86 caractères). C'est le format natif de WebCrypto ; une signature DER est refusée. Côté Android (`Signature("SHA256withECDSA")` produit du DER) : convertir DER → P1363 avant l'envoi et P1363 → DER avant la vérification.
- **Vecteurs de test** : `remote-pwa-dev/tests/vecteurs.json` (SDP, empreintes, textes signés, signatures P1363 **et** DER des deux côtés, fragment, code). Données publiques uniquement ; à reprendre dans les tests Kotlin.

### Serveur PeerJS (protocole WebSocket brut)

- URL : `wss://0.peerjs.com:443/peerjs?key=peerjs&id=<id>&token=<jeton>` (`/` + `peerjs` ; hôte, port, chemin, clé dans `config.js`).
- Le téléphone garde son **jeton** dans IndexedDB avec son identifiant : à la reconnexion avec le même jeton, le serveur reprend l'identifiant même si l'ancienne socket n'est pas encore expirée, **mais n'envoie alors pas `OPEN`**. Règle des deux côtés : connexion considérée ouverte à `OPEN`, ou 2,5 s après l'ouverture de la socket sans `ID-TAKEN` ni `ERROR`.
- `{"type":"HEARTBEAT"}` toutes les 5 s.
- Envoi : `{"type":"OFFER"|"ANSWER"|"CANDIDATE"|"LEAVE","dst":"<pair>","payload":{…}}` ; le serveur remet le message avec `"src"` ajouté.
- `{"type":"EXPIRE","src":"<pair>","dst":"<nous>"}` : le pair n'était pas connecté (message gardé ~5 s puis abandonné) → la PWA affiche « box injoignable » et réessaie.
- **C'est le téléphone qui appelle** : il crée l'offre et le DataChannel `telecommande` (`ordered: true`, fiable, non négocié) ; la TV répond et le reçoit par `ondatachannel` (refuser toute autre étiquette).
- Charges utiles (compatibles avec celles de la bibliothèque PeerJS) :

  ```json
  OFFER     {"sdp":{"type":"offer","sdp":"v=0…"},"type":"data","connectionId":"tc_<16 hex>","label":"telecommande","reliable":true,"serialization":"json"}
  ANSWER    {"sdp":{"type":"answer","sdp":"v=0…"},"type":"data","connectionId":"tc_<16 hex>"}
  CANDIDATE {"candidate":{"candidate":"candidate:…","sdpMid":"0","sdpMLineIndex":0},"type":"data","connectionId":"tc_<16 hex>"}
  LEAVE     {"type":"data","connectionId":"tc_<16 hex>"}
  ```

  - ICE « trickle » : les candidats partent dès qu'ils sont trouvés ; pas de message de fin de candidats. Chaque côté met en attente les candidats reçus avant d'avoir appliqué la description distante.
  - La TV reprend le `connectionId` de l'offre dans ANSWER et CANDIDATE ; le téléphone ignore tout message d'un autre `src` ou d'un autre `connectionId`.
  - `LEAVE` : envoyé par le téléphone quand il ferme volontairement la session (facultatif à traiter côté TV).
- Les navigateurs masquent souvent leur IP locale derrière un nom `.local` (mDNS) : la TV n'a pas à le résoudre, la connexion s'établit par les candidats de la TV (candidat « peer-reflexive »).

### Déroulé sur le DataChannel

1. Téléphone → `hello` : `{v, t:"hello", appareil, nonce, sig}` ; **`appareil` = identifiant PeerJS du téléphone** (`tel-<32 hex>`). En appairage seulement, il ajoute `cle` (SPKI) et `nom`.
2. TV : version non gérée → `erreur {code:"version", vMax}` puis fermeture. Sinon :
   - appareil connu → vérifie avec la clé enregistrée (le champ `cle` éventuel est ignoré) ;
   - appareil inconnu **avec** `cle` → session d'appairage, vérifie avec cette clé ;
   - appareil inconnu **sans** `cle` (téléphone révoqué pendant qu'il était hors ligne) → envoie son `hello` signé, puis `revoked`, puis ferme ;
   - signature fausse → fermeture, sans détail.
3. TV → `hello` : `{v, t:"hello", nonce, sig, nomTV, versionApp, cle}`. **Ajout : `cle` (SPKI de la TV) est obligatoire** — le téléphone n'a que l'empreinte (QR code) ou rien (code de secours). Le téléphone vérifie : `cle` identique à celle enregistrée (TV appairée), ou `empreinte(cle)` = empreinte du QR code, ou rien (code de secours) ; puis la signature.
   La PWA compare aussi `v` : `v` > sa version → « Rechargez la télécommande » ; `v` < sa version minimale → « Mettez à jour iBiTV sur la TV ».
4. Après les deux `hello` : `ping`/`pong` autorisés (y compris pendant l'appairage) ; la PWA envoie un `ping` toutes les 15 s et considère la connexion perdue après 2 `pong` manqués (puis se reconnecte). Elle envoie aussi un `ping` immédiat au retour au premier plan (4 s pour répondre).
5. Appairage : téléphone → `pair_request {secret, nom, cle, empreinteTV}`.
   - QR : `secret` = secret base64url du QR ; `empreinteTV` = empreinte du QR.
   - Code de secours : `secret` = les 4 derniers caractères, en MAJUSCULES, sans tiret (`"PQ9X"`) ; **`empreinteTV` absent**.
   - La TV vérifie en plus que `pair_request.cle` = la `cle` du `hello`.
   - Pendant l'appairage, la TV n'accepte que `pair_request` et `ping` ; le téléphone, lui, ignore `focus_text`/`focus_lost`.
6. TV → `paired {nomTV, idTV}`. **Ajout : `idTV` (identifiant PeerJS stable `ibiprojo-<32 hex>`) est obligatoire**, indispensable après un code de secours (le téléphone était connecté à `ibiprojo-c-XXXX`). Le **même DataChannel** devient alors une session de pilotage : la TV accepte les commandes sans reconnexion. Les reconnexions suivantes visent `idTV`.
7. `revoked`, `focus_text`, `focus_lost`, `paired`, `pair_refused` reçus avant la vérification du `hello` de la TV sont ignorés par la PWA. Les messages sont traités strictement dans l'ordre de réception.
8. `text` : `{s, curseur?}` ; `curseur` = position de fin de sélection dans `s` (absent si le navigateur ne la donne pas, par ex. champs `email`). Un message par modification (frappe, collage, correction automatique).

### Saisie et champs côté téléphone

| `focus_text.type` | champ du téléphone |
|---|---|
| `texte` | `type=text` |
| `email` | `type=email` |
| `motdepasse` | `type=password`, masqué par défaut, bouton « Afficher » |
| `nombre` | `type=text`, `inputmode=decimal` |
| `url` | `type=url` |
| `recherche` | `type=search` |

`action` devient `enterkeyhint` (touche Entrée du clavier). Entrée → `submit`. `focus_lost` → le champ est vidé, le clavier fermé (`blur`).

### Limites iOS (et choix retenus)

- **Clavier automatique** : iOS n'ouvre pas le clavier sur un `focus()` déclenché hors d'un geste de l'utilisateur (un message réseau n'en est pas un). La PWA tente `focus()` (suffisant sur Android/Chrome, surtout juste après un appui), puis, si le champ n'a pas le focus ou si iOS n'a pas réduit la zone visible, met le champ en surbrillance pulsée avec « Touchez le champ pour ouvrir le clavier ». Le champ texte reste toujours visible en bas de l'écran ; une fois touché, il remonte sous l'en-tête pour rester au-dessus du clavier.
- **Installation** : pas de `beforeinstallprompt` sur iOS → instructions « Partager → Sur l'écran d'accueil ».
- **Stockage séparé** : sur iPhone, l'app installée sur l'écran d'accueil ne partage pas l'IndexedDB de Safari. Un appairage fait dans Safari reste dans Safari. Après un scan du QR code dans Safari, la PWA le signale et propose « Copier le lien d'appairage » ; dans l'app installée, le champ « Code de secours ou lien d'appairage » accepte ce lien (sécurité du QR code) ou le code de secours.
- **Vibreur** : `navigator.vibrate` n'existe pas sur iOS ; la PWA utilise l'interrupteur natif (`<input type=checkbox switch>`, iOS 18+) qui produit un léger retour haptique au toucher. Rien sur les versions plus anciennes.
- **Arrière-plan** : iOS suspend la page ; au retour, la PWA vérifie la connexion (ping) et se reconnecte si besoin.
- **Clé privée** : non exportable, conservée telle quelle (CryptoKey) dans IndexedDB. Si un navigateur refusait d'y stocker une CryptoKey, repli : clé PKCS#8 conservée localement (jamais envoyée).

## Côté TV : précisions supplémentaires (version 1)

Compatibles avec « Précisions d'implémentation (v1) » ci-dessus, qui font référence. La TV ajoute :

- **Champs en plus** (ignorés sans risque par la PWA) : `hello` de la TV contient aussi `idTV` ; `paired` contient aussi `cle` (la même SPKI que dans `hello`).
- **Version** : `v` absent ou différent de 1 dans `hello` → `erreur {code:"version", vMax:1}` puis fermeture. Après les `hello`, un message dont `v` n'est pas 1 est ignoré.
- **Appairage, ordre des vérifications** :
  1. blocage en cours → `trop_d_essais` (le secret n'est pas examiné) ;
  2. aucun secret valable (jamais créé, dialogue fermé, expiré ou déjà utilisé) → `expire` ; de même pour le dernier secret remplacé par un nouveau (QR code permanent renouvelé), sans compter d'échec ;
  3. secret faux, ou `empreinteTV` différente de l'empreinte de la clé de la TV, ou `cle` différente de celle du `hello` → `invalide`. Les deux premiers comptent comme un échec ; le **5e échec en 60 s** répond `trop_d_essais` et bloque 60 s ;
  4. une autre demande attend déjà la réponse de l'utilisateur → `refuse` ; sinon confirmation à l'écran. Refus → `refuse`, le secret reste valable.
- **Secret** : la TV accepte le secret du QR code et le secret court (4 derniers caractères ; aussi les 8, avec ou sans tiret, sans distinction de casse) sur ses deux identifiants (principal et `ibiprojo-c-XXXX`).
- `pair_request` d'un téléphone déjà authentifié (PWA qui a oublié la TV) → `paired` directement.
- **Nom** : `pair_request.nom` (sinon `hello.nom`), réduit à 40 caractères sans caractères de contrôle.
- **Connexions** : la TV n'est joignable que si l'écran est allumé **et** qu'au moins un téléphone est appairé (ou qu'un appairage est en cours, dont le module « Télécommande » affiché sur l'accueil). Au plus 3 connexions ; une nouvelle `OFFER` d'un même téléphone remplace l'ancienne ; `hello` attendu dans les 20 s. `LEAVE`/`EXPIRE` du téléphone ferment sa connexion.
- **Commandes** : `text.s` ≤ 10 000 caractères ; `curseur` ramené dans le texte, absent = fin. `focus_text` est aussi envoyé dès qu'un téléphone s'authentifie si un champ a déjà le focus ; `focus_lost` part 200 ms après la sortie du champ. `focus_text.type` : `recherche` pour un champ texte dont l'action est « Rechercher », `nombre` aussi pour téléphone et date ; `action` = `done` quand l'éditeur n'en déclare pas.

### Vecteurs de test de la TV

En plus de `remote-pwa-dev/tests/vecteurs.json` (vérifiés par `VecteursPwaTest.kt`), la TV a ses propres vecteurs (`VecteursTest.kt`) : clés de **test** (publiques), `sigTel` produite par WebCrypto (Node 22) avec la clé privée du téléphone ci-dessous, `sigTV` produite par le code Kotlin de la TV puis vérifiée par WebCrypto.

```
empreinteDtlsTel = sha-256 00:01:02:03:04:05:06:07:08:09:0A:0B:0C:0D:0E:0F:10:11:12:13:14:15:16:17:18:19:1A:1B:1C:1D:1E:1F
empreinteDtlsTV  = sha-256 A0:A1:A2:A3:A4:A5:A6:A7:A8:A9:AA:AB:AC:AD:AE:AF:B0:B1:B2:B3:B4:B5:B6:B7:B8:B9:BA:BB:BC:BD:BE:BF
nonceTel = AAECAwQFBgcICQoLDA0ODw      (octets 0..15)
nonceTV  = EBESExQVFhcYGRobHB0eHw      (octets 16..31)

Téléphone
  SPKI      = MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfXhOCAc8ijZlkV1xxYqDAvSUM3TX6o-m-9oZiXMShPiGgUY0Y_JAZ2scmZtD3MMs8QEUcMJe9A2lNf6lYoQWRQ
  PKCS8     = MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgZVEnuUk3XJpN13xv7gNZqYEwJ_VDS28nLiULD82hbvuhRANCAAR9eE4IBzyKNmWRXXHFioMC9JQzdNfqj6b72hmJcxKE-IaBRjRj8kBnaxyZm0PcwyzxARRwwl70DaU1_qVihBZF
  empreinte = yGw8iBaC6qZywzkIGurx7w
TV
  SPKI      = MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECGDuYL_37I0zG84bk87LU49uh8u9m8XC06NjyB16vlf19zvpIot7-oQ5LgZ2ZD5QJtQTzx2UiTYXsK1Oano10Q
  PKCS8     = MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgoDtJK7SVh1q8DDIZ7xnZztzr86DEFPHU7et2O-MygXihRANCAAQIYO5gv_fsjTMbzhuTzstTj26Hy72bxcLTo2PIHXq-V_X3O-kii3v6hDkuBnZkPlAm1BPPHZSJNhewrU5qejXR
  empreinte = YPXzIOhZAT92PqkIdIKfBw

hello téléphone : "ibiprojo-v1|hello|" + empreinteDtlsTel + "|" + empreinteDtlsTV + "|" + nonceTel
  sigTel = f3TG45JmH2GRnufVp1fo39Bv6DNHW_kg_qoTBC_3LBBcIx_FCtsR2AUgz5GPLcaa0yz1F6DSdssycYPjwQP7GQ
hello TV : "ibiprojo-v1|hello-tv|" + empreinteDtlsTV + "|" + empreinteDtlsTel + "|" + nonceTel + "|" + nonceTV
  sigTV  = 3Y3_K7efigm7PyFWSNjImjCq1UYNyjGeqr2tObQ0-eK74k9_EYJB0x9BBIv3M0jPNJDEH1WZdZcHREhizKC2Fw
```

### Composants et cycle de vie

- `remote/Telecommande.kt` : un objet pour toute l'appli, sur un fil dédié. **Pas de service au premier plan** : iBiTV est l'écran d'accueil (processus prioritaire), et le système garde liés le clavier iBiTV et le service d'accessibilité ; le premier composant qui (re)démarre le processus relance la télécommande. WebRTC (bibliothèque native) n'est chargé qu'à la première connexion et libéré 30 s après la dernière.
- Clavier sans téléphone connecté : quand l'utilisateur ouvre un champ à la télécommande de la TV, le clavier iBiTV rend la main au clavier précédent. Il se re-sélectionne à la connexion d'un téléphone si l'appli a reçu `WRITE_SECURE_SETTINGS` (`adb shell pm grant fr.projo.launcher android.permission.WRITE_SECURE_SETTINGS`), sinon il faut le re-choisir dans les réglages.
- Navigation : 1. clavier lié à un champ → `sendKeyEvent` ; 2. iBiTV au premier plan → injection dans sa propre fenêtre (permise sans droit particulier pour la fenêtre de l'appli) ; 3. service d'accessibilité → actions globales, `focusSearch` de l'appli puis repli géométrique (éléments focusables visibles, comme FocusFinder), `ACTION_CLICK` sur l'élément focalisé ou son parent cliquable ; 4. sinon rien. `home` passe par `GLOBAL_ACTION_HOME`, sinon par l'intent Accueil ; `playpause` par `AudioManager.dispatchMediaKeyEvent`.
- Données : `files/telecommande/telecommande.json` (identifiant TV, jeton PeerJS, téléphones appairés) et `files/telecommande/identite.bin` (seulement en repli logiciel, clé chiffrée AES-GCM avec une clé dérivée d'ANDROID_ID et d'un sel : simple obscurcissement, la protection réelle est le bac à sable de l'appli).

### Fusion avec la branche Fire TV

Sur Fire TV, le service d'accessibilité `system/RedirectionAccueil` existe déjà (ouverture automatique). Pour n'avoir **qu'un service à activer**, il porte aussi le rôle « navigation » :

1. `RedirectionAccueil` hérite de `remote.NavigationTelecommande` au lieu d'`AccessibilityService` (ses `onServiceConnected` / `onDestroy` / `onInterrupt` / `onAccessibilityEvent` appellent déjà ou peuvent appeler `super`) : le rôle navigation s'attache tout seul (`HoteNavigation.attacher`).
2. `res/xml/redirection_accueil.xml` : `android:canRetrieveWindowContent="true"` et `android:accessibilityFlags="flagRequestFilterKeyEvents|flagIncludeNotImportantViews"` ; adapter la description (« lit l'écran seulement pour la navigation depuis le téléphone »). Après la mise à jour, redémarrer le Fire TV (même remarque que pour la touche Maison).
3. `res/values/telecommande.xml` : `telecommande_service_navigation` = `false` (le service séparé `NavigationTelecommande` disparaît des réglages du Fire TV).

Fait sur `claude/wf-remote-firetv` (avec en plus : `FireTv.majAFinir` signale aussi un service relié sans la lecture des fenêtres, et l'aide de la télécommande donne les commandes adb de `RedirectionAccueil`). Aucun fichier n'entre en conflit textuel (fusion d'essai faite) : seules ces trois modifications sont à faire à la main dans le commit de fusion. Alternative sans modification : laisser les deux services et activer les deux (`enabled_accessibility_services` séparés par `:`).

