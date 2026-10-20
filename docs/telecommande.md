# Télécommande smartphone (PWA) — architecture et protocole

Version du protocole : **1**

## Vue d'ensemble

```
Téléphone (PWA, GitHub Pages, HTTPS)          TV (iBiProjo, Android)
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

1. Sur la TV, Réglages → « Appairer un téléphone ».
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
| `hello` | `nonce`, `sig`, `nomTV`, `versionApp` | réponse authentifiée |
| `paired` | `nomTV` | appairage accepté |
| `pair_refused` | `raison` : `refuse` `expire` `invalide` `trop_d_essais` | appairage refusé |
| `revoked` | — | ce téléphone a été révoqué (la PWA oublie la TV) |
| `focus_text` | `type` : `texte` `email` `motdepasse` `nombre` `url` `recherche`, `s` (contenu actuel, vide si mot de passe), `action` : `go` `search` `send` `next` `done` | un champ texte a le focus |
| `focus_lost` | — | plus de champ texte : mode navigation |
| `pong` | `n` | réponse au ping |
| `erreur` | `code` | par ex. `version` |

Compatibilité : à `hello`, chacun annonce `v`. Si la TV reçoit une version qu'elle ne gère pas, elle répond `erreur` avec `code: "version"` et `vMax`. La PWA affiche alors :
- « Mettez à jour iBiProjo sur la TV » si sa propre version est la plus récente ;
- sinon « Rechargez la télécommande ».

## Côté TV : injection

- **Texte** : clavier iBiProjo (IME, `remote/ClavierTelecommande`). Il remplace le contenu du champ actif via `InputConnection` (`setComposingText` / `commitText`, `deleteSurroundingText`) et envoie l'action de l'éditeur pour `submit`. Il détecte le focus : `onStartInput` donne le type du champ via `EditorInfo.inputType` / `imeOptions`, et `onFinishInput` envoie `focus_lost`.
  - Activation unique : Paramètres → Clavier (projecteur) ; sur Fire TV : `adb shell ime enable fr.projo.launcher/.remote.ClavierTelecommande` puis `ime set …`.
- **Navigation** :
  - quand le clavier iBiProjo est lié à un champ : touches directionnelles envoyées par `InputConnection.sendKeyEvent` ;
  - sinon, par le service d'accessibilité : `GLOBAL_ACTION_BACK` / `GLOBAL_ACTION_HOME`, et déplacement du focus vers l'élément focusable voisin dans la direction demandée, puis `ACTION_CLICK` pour OK ;
  - repli silencieux si rien n'est possible.
- **Volume** : `AudioManager.adjustStreamVolume`.
- Une appli à grille de lettres maison n'ouvre pas de clavier : le téléphone reste en mode navigation, sans erreur.

## Journaux

Jamais de texte saisi, de secret, de clé privée ni de SDP complet dans les journaux, des deux côtés. On journalise seulement les types d'événements (connexion, appairage accepté/refusé, révocation).

## Précisions (côté TV, version 1)

Ces points complètent la spécification ; la TV les applique telles quelles.

### Signalisation PeerJS

- URL : `wss://0.peerjs.com:443/peerjs?key=peerjs&id=<id>&token=<jeton>` (hôte, port, chemin, clé dans `RemoteConfig.kt`).
  - La TV garde son jeton avec son identifiant (même jeton après une coupure : pas d'`ID-TAKEN`).
  - `HEARTBEAT` toutes les 10 s ; reconnexion avec délai croissant 2 s → 2 min, remis à zéro à chaque `OPEN` et au retour du réseau.
- C'est **le téléphone qui appelle** : il crée le DataChannel `telecommande` (non négocié, fiable, ordonné), fait l'offre et l'envoie en `OFFER`. La TV ne fait jamais d'offre.
- Format des `payload` (le même que la bibliothèque PeerJS, pour rester compatibles) :
  - `OFFER` : `{ "sdp": { "type": "offer", "sdp": "…" }, "type": "data", "connectionId": "<id libre>" }` ;
  - `ANSWER` (TV) : `{ "sdp": { "type": "answer", "sdp": "…" }, "type": "data", "connectionId": "<le même>", "browser": "ibiprojo" }` ;
  - `CANDIDATE` (dans les deux sens, trickle ICE) : `{ "candidate": { "candidate": "…", "sdpMid": "0", "sdpMLineIndex": 0 }, "type": "data", "connectionId": "<le même>" }`. Un candidat vide (fin des candidats) est ignoré.
  - `LEAVE` / `EXPIRE` venant du téléphone : la TV ferme la connexion correspondante.
- Une nouvelle `OFFER` d'un même téléphone remplace sa connexion précédente. Au plus 3 connexions à la fois.
- La TV n'est joignable que si l'écran est allumé et qu'au moins un téléphone est appairé (ou qu'un appairage est en cours). Écran éteint : WebSocket et connexions fermées.
- Le DataChannel doit envoyer `hello` dans les 20 s, sinon la TV ferme.

### Encodages

- **base64url** : alphabet RFC 4648 §5, **sans** `=`. (La TV accepte aussi `=` et `+ /` en lecture.)
- **Clés** : SPKI DER (`exportKey("spki")`), en base64url.
- **Signatures** : ECDSA P-256 + SHA-256 au format **brut r‖s de 64 octets** (format natif de WebCrypto), en base64url (86 caractères). Le format DER est refusé.
- **Chaînes signées** : encodées en UTF-8 avant signature.
- **Nonces** : 16 octets aléatoires en base64url (22 caractères). La TV refuse un nonce de moins de 16 octets.
- **Empreinte DTLS** (`empreinteDtlsTel`, `empreinteDtlsTV`) : valeur de la première ligne `a=fingerprint:sha-256 …` du SDP (de session ou de média), **en hexadécimal majuscule avec les « : »**, sans le nom de l'algorithme. Ex. `8A:3F:…:C2` (95 caractères). Le téléphone prend la sienne dans son offre (`localDescription`) et celle de la TV dans la réponse reçue ; la TV fait l'inverse. Une empreinte autre que sha-256 seule : connexion refusée.
- **Empreinte de clé** : `base64url(SHA-256(SPKI)[0..16])`, 22 caractères.

### `hello`

- Téléphone → TV : `appareil` = identifiant d'appareil du téléphone (son identifiant PeerJS `tel-…` conseillé, ≤ 100 caractères). `cle` est **obligatoire** tant que le téléphone n'est pas appairé ; ensuite il peut l'omettre (s'il l'envoie, elle doit être identique à la clé enregistrée).
- TV → téléphone : en plus de `nonce`, `sig`, `nomTV`, `versionApp`, la TV ajoute toujours **`idTV`** (identifiant stable `ibiprojo-…`) et **`cle`** (SPKI de la TV). Avec le code de secours, c'est ainsi que le téléphone reçoit la clé à accepter. Avec le QR code ou une TV déjà connue, le téléphone vérifie que `empreinte(cle)` est bien celle attendue (ou la clé enregistrée) avant de vérifier `sig`.
- `appareil` inconnu **et** pas de `cle` (téléphone révoqué qui se croit encore appairé) : la TV répond `revoked` (sans `hello`) et ferme. La PWA oublie alors la TV.
- `appareil` inconnu avec `cle` et signature valable : la TV répond `hello` ; seul `pair_request` est ensuite accepté (et toujours rien d'autre, pas même `ping`).
- Version : `v` absent ou différent de 1 dans `hello` → `{"v":1,"t":"erreur","code":"version","vMax":1}` puis fermeture. Après le `hello`, un message dont `v` n'est pas 1 est ignoré.

### Appairage

- `pair_request.secret` :
  - QR code : le secret base64url (22 caractères) ;
  - code de secours : les **4 derniers caractères** du code (la TV accepte aussi les 8, avec ou sans tiret, en minuscules ou majuscules).
  - La TV accepte les deux formes sur ses deux identifiants (principal et `ibiprojo-c-ABCD`).
- `pair_request.cle` doit être la clé qui a signé le `hello` (sinon `invalide`). `empreinteTV` est absent ou vide avec le code de secours.
- Ordre exact des vérifications :
  1. blocage en cours → `trop_d_essais` (le secret n'est pas examiné) ;
  2. aucun secret valable (jamais créé, dialogue fermé, expiré ou déjà utilisé) → `expire` ;
  3. secret faux → `invalide` ; empreinte de la TV différente → `invalide`. Chacun compte comme un échec ; le **5e échec en 60 s** répond `trop_d_essais` et bloque 60 s.
  4. confirmation à l'écran ; si une autre demande attend déjà une réponse → `refuse`.
- Refus par l'utilisateur → `pair_refused` `refuse` ; le secret reste valable (le dialogue reste ouvert).
- `paired` contient `nomTV`, plus **`idTV`** et **`cle`** (les mêmes que dans `hello`). Le canal est alors authentifié : les commandes passent tout de suite, sans nouveau `hello`.
- `pair_request` d'un téléphone déjà appairé (authentifié) : la TV répond `paired` directement (cas d'une PWA qui a oublié la TV).
- Le nom (`pair_request.nom`, sinon `hello.nom`) est réduit à 40 caractères sans caractères de contrôle.

### Commandes

- `text.s` : au plus 10 000 caractères ; `curseur` (facultatif) en caractères depuis le début, ramené dans le texte ; absent = fin du texte.
- `focus_text` est envoyé à l'arrivée sur un champ, et aussi dès qu'un téléphone s'authentifie si un champ a déjà le focus. `focus_lost` arrive 200 ms après la sortie du champ (pas d'aller-retour en passant d'un champ à l'autre).
- `focus_text.type` : `recherche` pour un champ texte dont l'action est « Rechercher » ; `nombre` aussi pour téléphone et date ; `action` vaut `done` quand l'éditeur n'en déclare pas.
- `pong.n` reprend `ping.n` tel quel.

### Vecteurs de test

Clés de **test** (publiques, à ne jamais utiliser ailleurs), produites avec WebCrypto (Node 22) ; `sigTV` produite par le code de la TV et vérifiée par WebCrypto. Voir `app/src/test/java/fr/projo/launcher/remote/VecteursTest.kt`.

```
empreinteDtlsTel = 00:01:02:03:04:05:06:07:08:09:0A:0B:0C:0D:0E:0F:10:11:12:13:14:15:16:17:18:19:1A:1B:1C:1D:1E:1F
empreinteDtlsTV  = A0:A1:A2:A3:A4:A5:A6:A7:A8:A9:AA:AB:AC:AD:AE:AF:B0:B1:B2:B3:B4:B5:B6:B7:B8:B9:BA:BB:BC:BD:BE:BF
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
  sigTel = OT12fBc_mwIQHw9M_P4v9Qd-d43CCoYeKP9qkMSZ6bqG82ixZlGbzYduUHxMt0Up_lflxPsXLFS5KBghhMk4pw
hello TV : "ibiprojo-v1|hello-tv|" + empreinteDtlsTV + "|" + empreinteDtlsTel + "|" + nonceTel + "|" + nonceTV
  sigTV  = TEfTatTvX4x1iH8BP-wStkUYiLhyVMsd12agaFfAAOgUqFe4nA_decE9SpvrQiBkSUX4SZOEkVKcvkkJOK2TmA
```

Les signatures ECDSA sont aléatoires : on vérifie ces signatures, on ne cherche pas à les reproduire.

### Côté TV : composants et cycle de vie

- `remote/Telecommande.kt` : un objet pour toute l'appli, sur un fil dédié. **Pas de service au premier plan** : iBiProjo est l'écran d'accueil (processus prioritaire), et le système garde liés le clavier iBiProjo et le service d'accessibilité ; le premier composant qui (re)démarre le processus relance la télécommande. WebRTC (bibliothèque native) n'est chargé qu'à la première connexion et libéré 30 s après la dernière.
- Clavier sans téléphone connecté : quand l'utilisateur ouvre un champ à la télécommande de la TV, le clavier iBiProjo rend la main au clavier précédent. Il se re-sélectionne à la connexion d'un téléphone si l'appli a reçu `WRITE_SECURE_SETTINGS` (`adb shell pm grant fr.projo.launcher android.permission.WRITE_SECURE_SETTINGS`), sinon il faut le re-choisir dans les réglages.
- Navigation : 1. clavier lié à un champ → `sendKeyEvent` ; 2. iBiProjo au premier plan → injection dans sa propre fenêtre (permise sans droit particulier pour la fenêtre de l'appli) ; 3. service d'accessibilité → actions globales, `focusSearch` de l'appli puis repli géométrique (éléments focusables visibles, comme FocusFinder), `ACTION_CLICK` sur l'élément focalisé ou son parent cliquable ; 4. sinon rien. `home` passe par `GLOBAL_ACTION_HOME`, sinon par l'intent Accueil ; `playpause` par `AudioManager.dispatchMediaKeyEvent`.
- Données : `files/telecommande/telecommande.json` (identifiant TV, jeton PeerJS, téléphones appairés) et `files/telecommande/identite.bin` (seulement en repli logiciel, clé chiffrée AES-GCM avec une clé dérivée d'ANDROID_ID et d'un sel : simple obscurcissement, la protection réelle est le bac à sable de l'appli).

### Fusion avec la branche Fire TV

Sur Fire TV, le service d'accessibilité `system/RedirectionAccueil` existe déjà (ouverture automatique). Pour n'avoir **qu'un service à activer**, il porte aussi le rôle « navigation » :

1. `RedirectionAccueil` hérite de `remote.NavigationTelecommande` au lieu d'`AccessibilityService` (ses `onServiceConnected` / `onDestroy` / `onInterrupt` / `onAccessibilityEvent` appellent déjà ou peuvent appeler `super`) : le rôle navigation s'attache tout seul (`HoteNavigation.attacher`).
2. `res/xml/redirection_accueil.xml` : `android:canRetrieveWindowContent="true"` et `android:accessibilityFlags="flagRequestFilterKeyEvents|flagIncludeNotImportantViews"` ; adapter la description (« lit l'écran seulement pour la navigation depuis le téléphone »). Après la mise à jour, redémarrer le Fire TV (même remarque que pour la touche Maison).
3. `res/values/telecommande.xml` : `telecommande_service_navigation` = `false` (le service séparé `NavigationTelecommande` disparaît des réglages du Fire TV).

Aucun fichier n'entre en conflit textuel (fusion d'essai faite) : seules ces trois modifications sont à faire à la main dans le commit de fusion. Alternative sans modification : laisser les deux services et activer les deux (`enabled_accessibility_services` séparés par `:`).

## Hébergement

- **PWA** : la GitHub Action `remote-pwa.yml` copie `remote-pwa/` sur la branche `gh-pages`, quand ce dossier change sur `claude/launcher-projo` (et plus tard `main`). Réglage unique dans le dépôt : Settings → Pages → Source « Deploy from a branch », branche `gh-pages`, dossier `/`.
- **Service worker** : version dans `config.js`. Une nouvelle version détectée affiche une bannière « Nouvelle version — Recharger ».
