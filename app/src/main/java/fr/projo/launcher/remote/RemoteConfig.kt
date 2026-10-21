package fr.projo.launcher.remote

/**
 * Réglages de la télécommande smartphone, définis ici une seule fois (voir docs/telecommande.md).
 * Côté téléphone, l'équivalent est remote-pwa/config.js : les deux doivent rester d'accord.
 */
object RemoteConfig {

    /** Version du protocole parlée par la TV (champ `v` de chaque message). */
    const val VERSION_PROTOCOLE = 1

    /** Adresse de la télécommande (PWA publiée sur GitHub Pages). */
    const val URL_PWA = "https://58np95y6s6-svg.github.io/launcher-projo/"

    /** Version courte de l'adresse, affichée sous le QR code (à taper à la main). */
    const val URL_PWA_COURTE = "58np95y6s6-svg.github.io/launcher-projo"

    // ------------------------------------------------------------------ serveur de mise en relation

    /** Serveur PeerJS : changer ces 4 valeurs suffit pour passer à un serveur à nous. */
    const val PEERJS_HOTE = "0.peerjs.com"
    const val PEERJS_PORT = 443
    const val PEERJS_CHEMIN = "/"
    const val PEERJS_CLE = "peerjs"
    const val PEERJS_SECURISE = true

    /** Battement envoyé au serveur (HEARTBEAT) pour garder la WebSocket ouverte. */
    const val HEARTBEAT_MS = 5_000L

    /**
     * Reconnexion avec le même jeton : le serveur peut reprendre l'identifiant sans renvoyer
     * OPEN. Connexion considérée ouverte à OPEN, ou ce délai après l'ouverture de la socket
     * sans ID-TAKEN ni ERROR.
     */
    const val OPEN_IMPLICITE_MS = 2_500L

    /** Reconnexion au serveur : délai croissant (×2) entre ces deux bornes. */
    const val RECONNEXION_MIN_MS = 2_000L
    const val RECONNEXION_MAX_MS = 120_000L

    /** Adresse WebSocket du serveur PeerJS pour un identifiant et un jeton donnés. */
    fun urlPeerJs(id: String, jeton: String): String {
        val schema = if (PEERJS_SECURISE) "wss" else "ws"
        val chemin = if (PEERJS_CHEMIN.endsWith("/")) PEERJS_CHEMIN else "$PEERJS_CHEMIN/"
        return "$schema://$PEERJS_HOTE:$PEERJS_PORT${chemin}peerjs?key=$PEERJS_CLE&id=$id&token=$jeton"
    }

    // ------------------------------------------------------------------ WebRTC

    /** Serveurs STUN (pas de TURN : téléphone et TV sont sur le même réseau). */
    val STUN = listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")

    /** Nom du DataChannel ouvert par le téléphone. */
    const val CANAL = "telecommande"

    /** Nombre maximal de téléphones connectés en même temps (mémoire : 1 Go de RAM). */
    const val CONNEXIONS_MAX = 3

    /** Connexion WebRTC qui n'aboutit pas (canal non ouvert ou `hello` absent) : abandon. */
    const val DELAI_AUTHENTIFICATION_MS = 20_000L

    // ------------------------------------------------------------------ appairage

    /** Durée de validité du secret d'appairage (QR code et code de secours). */
    const val DUREE_SECRET_MS = 5 * 60_000L

    /** Adresse encodée dans le QR code d'appairage (fragment `#` : jamais envoyé au serveur web). */
    fun urlAppairage(idTv: String, secret: String, empreinteTv: String): String =
        "$URL_PWA#pair=$idTv.$secret.$empreinteTv"

    /**
     * Module Télécommande (QR code permanent sur l'accueil) : après un appairage, « ✓ Marie est
     * appairé » reste affiché ce temps (et tant que le dialogue en grand est ouvert) avant le
     * nouveau QR code.
     */
    const val PAUSE_APRES_APPAIRAGE_MS = 6_000L

    /** Limite de tentatives : N échecs dans la fenêtre, puis blocage. */
    const val ESSAIS_MAX = 5
    const val FENETRE_ESSAIS_MS = 60_000L
    const val BLOCAGE_MS = 60_000L

    /** Alphabet du code de secours (sans 0/O, 1/I/L). */
    const val ALPHABET_CODE = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    /** Préfixes des identifiants PeerJS. */
    const val PREFIXE_TV = "ibiprojo-"
    const val PREFIXE_CODE = "ibiprojo-c-"

    /** Contexte des chaînes signées (`ibiprojo-v1|hello|…`). */
    const val CONTEXTE_SIGNATURE = "ibiprojo-v1"
}
