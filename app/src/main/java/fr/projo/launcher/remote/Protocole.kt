package fr.projo.launcher.remote

import fr.projo.launcher.boxscore.EquipeEtat
import fr.projo.launcher.boxscore.EtatNba
import fr.projo.launcher.liens.ResultatLien
import org.json.JSONArray
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

    // ------------------------------------------------------------------ pilotage (applis et réglages)

    /** Fonctions annoncées dans le `hello` de la TV quand le pilotage est disponible. */
    val FONCTIONS = listOf("apps", "reglages")

    /** Fonction annoncée quand « Envoyer sur la TV » est disponible (onglet « Envoyer » de la PWA). */
    const val FONCTION_LIENS = "liens"

    /** Fonction annoncée quand le box score NBA est disponible (bandeau de la PWA). */
    const val FONCTION_NBA = "nba"

    /** `nba_afficher.id`, `nba_choisir.id` (« LAL-BOS ») : longueur maximale acceptée. */
    const val ID_MATCH_MAX = 20

    /** `lien_ouvert.url` : l'URL de la demande, coupée à cette longueur (message sous la limite de la PWA). */
    const val URL_REPONSE_MAX = 8_192

    /** `apps_images` : identifiants examinés au plus (les suivants sont ignorés). */
    const val IDS_IMAGES_MAX = 100

    /** Identifiant d'appli (nom de paquet) : longueur maximale acceptée. */
    const val ID_APPLI_MAX = 255

    /** Nom d'appli envoyé dans `apps` : longueur maximale. */
    const val NOM_APPLI_MAX = 80

    /** `app_image.img` (URL data:) : 48 Ko au plus. */
    const val IMAGE_MAX = 48 * 1024

    /**
     * Taille maximale d'un message envoyé au téléphone (la PWA refuse au-delà de 65 536
     * caractères) : `apps` s'arrête avant, marge comprise.
     */
    const val ENVOI_MAX = 60_000

    private fun message(t: String) = JSONObject().put("v", RemoteConfig.VERSION_PROTOCOLE).put("t", t)

    /** [fonctions] : null (pas de pilotage) → champ absent. */
    fun helloTv(
        nonce: String,
        sig: String,
        nomTv: String,
        versionApp: String,
        idTv: String,
        cle: String,
        fonctions: List<String>? = null,
        /** Design de l'accueil (thème de la télécommande) ; null : pas annoncé. */
        design: String? = null,
    ): String {
        val m = message("hello").put("nonce", nonce).put("sig", sig).put("nomTV", nomTv).put("versionApp", versionApp)
            .put("idTV", idTv).put("cle", cle)
        if (fonctions != null) {
            val tableau = JSONArray()
            for (f in fonctions) tableau.put(f)
            m.put("fonctions", tableau)
        }
        if (design != null) m.put("design", design)
        return m.toString()
    }

    /** Le design de l'accueil a changé : la télécommande change de thème. */
    fun design(id: String): String = message("design").put("id", id).toString()

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

    // ------------------------------------------------------------------ pilotage

    /** `apps` : la liste s'arrête avant [ENVOI_MAX] (des centaines d'applis passent). */
    fun apps(liste: List<AppliTv>): String {
        val tableau = JSONArray()
        var taille = 64
        for (a in liste) {
            val o = JSONObject().put("id", a.id).put("nom", a.nom.take(NOM_APPLI_MAX)).put("favori", a.favori).put("maj", a.maj)
            val t = o.toString().length + 1
            if (taille + t > ENVOI_MAX) break
            taille += t
            tableau.put(o)
        }
        return message("apps").put("liste", tableau).toString()
    }

    fun appImage(image: ImageAppli): String =
        message("app_image").put("id", image.id).put("maj", image.maj).put("forme", image.forme).put("img", image.img).toString()

    fun appOuverte(id: String, ok: Boolean): String = message("app_ouverte").put("id", id).put("ok", ok).toString()

    // ------------------------------------------------------------------ envoyer sur la TV (liens)

    fun liens(e: EtatLiens): String {
        val m = message("liens").put("vpn", e.vpn).put("seulementVpn", e.seulementVpn).put("appliPages", e.appliPages)
        e.appliVpn?.let { m.put("appliVpn", it) }
        e.raccourci?.let { m.put("raccourci", JSONObject().put("adresse", it)) }
        return m.toString()
    }

    /** `lien_ouvert` : [url] = celle de la demande. */
    fun lienOuvert(url: String, r: ResultatLien): String {
        val m = message("lien_ouvert").put("url", url.take(URL_REPONSE_MAX))
        when (r) {
            is ResultatLien.Ouvert -> m.put("ok", true).put("ouvertDans", r.appli)
            is ResultatLien.Refuse -> m.put("ok", false).put("raison", r.raison)
            // Le téléphone ne reçoit jamais de garde-fou (réservé au raccourci) : même sens que vpn_coupe
            ResultatLien.Question -> m.put("ok", false).put("raison", ResultatLien.VPN_COUPE)
        }
        return m.toString()
    }

    // ------------------------------------------------------------------ box score NBA

    /** `nba` : matchs (15 au plus), match choisi (null), format, décalage, erreur facultative. */
    fun nba(e: EtatNba): String {
        val matchs = JSONArray()
        for (match in e.matchs) {
            matchs.put(
                JSONObject().put("id", match.id).put("ext", equipeNba(match.ext)).put("dom", equipeNba(match.dom))
                    .put("statut", match.statut).put("direct", match.direct),
            )
        }
        val m = message("nba").put("matchs", matchs).put("choisi", e.choisi ?: JSONObject.NULL)
            .put("affichage", e.affichage.id).put("decalage", e.decalage)
        e.erreur?.let { m.put("erreur", it) }
        return m.toString()
    }

    private fun equipeNba(e: EquipeEtat): JSONObject =
        JSONObject().put("code", e.code).put("nom", e.nom).put("score", e.score).put("couleur", e.couleur).put("texte", e.texte)

    fun reglages(items: List<ReglageTv>): String {
        val tableau = JSONArray()
        for (r in items) tableau.put(reglage(r))
        return message("reglages").put("items", tableau).toString()
    }

    private fun reglage(r: ReglageTv): JSONObject {
        val o = JSONObject().put("cle", r.cle).put("type", r.type).put("groupe", r.groupe).put("titre", r.titre)
        r.detail?.let { o.put("detail", it) }
        if (!r.actif) o.put("actif", false)
        when (r) {
            is ReglageTv.Curseur -> o.put("valeur", r.valeur).put("min", r.min).put("max", r.max)
            is ReglageTv.Interrupteur -> o.put("valeur", r.valeur)
            is ReglageTv.Choix -> {
                val options = JSONArray()
                for (op in r.options) {
                    val j = JSONObject().put("id", op.id).put("nom", op.nom)
                    op.detail?.let { j.put("detail", it) }
                    options.put(j)
                }
                o.put("valeur", r.valeur).put("options", options)
            }
            is ReglageTv.Boutons -> {
                val boutons = JSONArray()
                for (b in r.boutons) {
                    val j = JSONObject().put("id", b.id).put("nom", b.nom)
                    if (b.principal) j.put("principal", true)
                    boutons.put(j)
                }
                o.put("boutons", boutons)
                // Deux décimales suffisent (0.42 plutôt que 0.41999998688697815)
                r.progression?.let { o.put("progression", Math.round(it * 100.0) / 100.0) }
            }
        }
        return o
    }
}
