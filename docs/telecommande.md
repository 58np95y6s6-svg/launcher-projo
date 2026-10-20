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

## Hébergement

- **PWA** : la GitHub Action `remote-pwa.yml` copie `remote-pwa/` sur la branche `gh-pages`, quand ce dossier change sur `claude/launcher-projo` (et plus tard `main`). Réglage unique dans le dépôt : Settings → Pages → Source « Deploy from a branch », branche `gh-pages`, dossier `/`.
- **Service worker** : version dans `config.js`. Une nouvelle version détectée affiche une bannière « Nouvelle version — Recharger ».
