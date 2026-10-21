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
- **Lisibilité à 3 m** : noir sur blanc, dans un cadre blanc avec la zone de silence de 4 modules, quel que soit le design ; environ 95 u de côté en PETIT ; bords des modules alignés sur les pixels. Correction d'erreur **L** pour le module (un écran n'a rien à reconstituer ; l'URL de 141 caractères tient en version 7, 45 × 45 modules, au lieu de 49 × 49 en M), **M** pour le grand dialogue.

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
| `hello` | `nonce`, `sig`, `nomTV`, `versionApp`, `cle` | réponse authentifiée (`cle` : SPKI de la TV, voir « Précisions ») |
| `paired` | `nomTV`, `idTV` | appairage accepté (`idTV` : identifiant PeerJS stable) |
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

## Hébergement

- **PWA** : la GitHub Action `.github/workflows/remote-pwa.yml` copie `remote-pwa/` sur la branche `gh-pages` (commit orphelin poussé en force, avec un fichier `.nojekyll`), quand ce dossier change sur `claude/launcher-projo` ou `main`, ou à la demande (workflow_dispatch).
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
   La PWA compare aussi `v` : `v` > sa version → « Rechargez la télécommande » ; `v` < sa version minimale → « Mettez à jour iBiProjo sur la TV ».
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

