package fr.projo.launcher.remote

import org.json.JSONObject

/**
 * Messages du DataChannel (voir docs/telecommande.md, « Messages »).
 * Chaque message : objet JSON avec `v` (version du protocole) et `t` (type).
 */
object Protocole {

    /** Touches acceptées dans `key`. */
    val TOUCHES = setOf("up", "down", "left", "right", "ok", "back", "home", "vol+", "vol-", "mute", "menu", "playpause")

    /** Types de champ de `focus_text`. */
    object TypeChamp {
        const val TEXTE = "texte"
        const val EMAIL = "email"
        const val MOT_DE_PASSE = "motdepasse"
        const val NOMBRE = "nombre"
        const val URL = "url"
        const val RECHERCHE = "recherche"
    }

    /** Actions de l'éditeur de `focus_text`. */
    object ActionChamp {
        const val GO = "go"
        const val SEARCH = "search"
        const val SEND = "send"
        const val NEXT = "next"
        const val DONE = "done"
    }

    /** Taille maximale d'un message reçu (texte d'un champ compris). */
    const val TAILLE_MAX = 64 * 1024

    /** Longueur maximale du texte d'un champ (`text.s`, `focus_text.s`). */
    const val TEXTE_MAX = 10_000

    private fun message(t: String) = JSONObject().put("v", RemoteConfig.VERSION_PROTOCOLE).put("t", t)

    fun helloTv(nonce: String, sig: String, nomTv: String, versionApp: String, idTv: String, cle: String): String =
        message("hello").put("nonce", nonce).put("sig", sig).put("nomTV", nomTv).put("versionApp", versionApp)
            .put("idTV", idTv).put("cle", cle).toString()

    fun paired(nomTv: String, idTv: String, cle: String): String =
        message("paired").put("nomTV", nomTv).put("idTV", idTv).put("cle", cle).toString()

    fun pairRefused(raison: RaisonRefus): String = message("pair_refused").put("raison", raison.valeur).toString()

    fun revoked(): String = message("revoked").toString()

    fun focusText(type: String, texte: String, action: String): String =
        message("focus_text").put("type", type).put("s", texte.take(TEXTE_MAX)).put("action", action).toString()

    fun focusLost(): String = message("focus_lost").toString()

    fun pong(n: Any?): String = message("pong").put("n", n ?: JSONObject.NULL).toString()

    fun erreurVersion(): String =
        message("erreur").put("code", "version").put("vMax", RemoteConfig.VERSION_PROTOCOLE).toString()
}
