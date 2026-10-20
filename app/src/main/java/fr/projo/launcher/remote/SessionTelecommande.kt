package fr.projo.launcher.remote

import org.json.JSONObject

/** Clé d'identité de la TV (ECDSA P-256). */
interface IdentiteTv {
    /** Clé publique, SPKI DER. */
    val spki: ByteArray

    /** Signature ECDSA P-256 / SHA-256 de [message] (UTF-8), format brut r‖s de 64 octets. */
    fun signer(message: String): ByteArray
}

/** Exécute les commandes d'un téléphone authentifié (touches, texte). */
interface Injecteur {
    fun touche(k: String)
    fun texte(s: String, curseur: Int?)
    fun valider()
}

/** Ce dont une session a besoin autour d'elle (la télécommande, ou un banc de test). */
interface HoteSession {
    val identite: IdentiteTv
    val registre: RegistreTelecommande
    val appairage: GestionAppairage
    val injecteur: Injecteur
    val nomTv: String
    val versionApp: String
    fun maintenant(): Long = System.currentTimeMillis()

    /**
     * Affiche « Autoriser le téléphone de Marie ? ». [reponse] est appelée une fois (sur le
     * fil de la télécommande). Rend false si une autre demande attend déjà une réponse.
     */
    fun demanderConfirmation(session: SessionTelecommande, nom: String, reponse: (Boolean) -> Unit): Boolean

    /** La session se ferme pendant que sa demande est affichée : la retirer. */
    fun annulerConfirmation(session: SessionTelecommande) {}

    /** Les deux `hello` sont vérifiés : le téléphone peut envoyer des commandes. */
    fun surAuthentifiee(session: SessionTelecommande) {}

    fun surAppaire(telephone: TelephoneAppaire) {}

    /** Journal des types d'événements seulement (jamais de texte, secret ni SDP). */
    fun journal(evenement: String) {}
}

/**
 * Une connexion avec un téléphone (un DataChannel), indépendante de WebRTC pour être testée.
 *
 * - [dtlsTel] : empreinte DTLS du SDP du téléphone (l'offre reçue) ;
 * - [dtlsTv] : empreinte DTLS du SDP de la TV (notre réponse) ;
 * - [envoyer] : envoie un message sur le canal ;
 * - [fermer] : ferme le canal (après avoir laissé partir le dernier message).
 *
 * Tant que les deux `hello` ne sont pas vérifiés, toute autre commande est ignorée.
 * Un téléphone inconnu dont la signature est bonne peut seulement envoyer `pair_request`.
 */
class SessionTelecommande(
    private val hote: HoteSession,
    private val dtlsTel: String?,
    private val dtlsTv: String?,
    private val envoyer: (String) -> Unit,
    private val fermer: () -> Unit,
) {
    enum class Etat { ATTENTE_HELLO, CLE_VERIFIEE, AUTHENTIFIEE, FERMEE }

    var etat = Etat.ATTENTE_HELLO
        private set

    /** Identifiant d'appareil du téléphone (après un `hello` valide). */
    var appareil: String? = null
        private set

    /** Clé publique du téléphone (SPKI) vérifiée par son `hello`. */
    private var cleTelephone: ByteArray? = null
    private var nomPropose = ""
    private var confirmationEnCours = false

    val authentifiee: Boolean get() = etat == Etat.AUTHENTIFIEE

    /** Message reçu du téléphone. */
    fun recevoir(texte: String) {
        if (etat == Etat.FERMEE || texte.length > Protocole.TAILLE_MAX) return
        val m = try {
            JSONObject(texte)
        } catch (e: Exception) {
            return
        }
        val t = m.optString("t")
        if (t == "hello") return hello(m)
        when (etat) {
            Etat.CLE_VERIFIEE -> if (t == "pair_request" && versionOk(m)) demandeAppairage(m)
            Etat.AUTHENTIFIEE -> if (versionOk(m)) commande(t, m)
            else -> Unit // avant les hello : ignoré
        }
    }

    /** Message de la TV vers le téléphone, seulement une fois authentifié (focus_text…). */
    fun envoyerSiAuthentifiee(message: String) {
        if (etat == Etat.AUTHENTIFIEE) envoyer(message)
    }

    /** Ce téléphone vient d'être révoqué sur la TV. */
    fun revoquer() {
        if (etat == Etat.FERMEE) return
        envoyer(Protocole.revoked())
        terminer()
    }

    /** Le canal s'est fermé (ou la TV le ferme). */
    fun terminer() {
        if (etat == Etat.FERMEE) return
        etat = Etat.FERMEE
        if (confirmationEnCours) {
            confirmationEnCours = false
            hote.annulerConfirmation(this)
        }
        fermer()
    }

    // ------------------------------------------------------------------ hello

    private fun versionOk(m: JSONObject): Boolean =
        m.opt("v").let { it is Number && it.toDouble() == RemoteConfig.VERSION_PROTOCOLE.toDouble() }

    private fun hello(m: JSONObject) {
        if (etat != Etat.ATTENTE_HELLO) return
        if (!versionOk(m)) {
            hote.journal("version incompatible")
            envoyer(Protocole.erreurVersion())
            terminer()
            return
        }
        val tel = dtlsTel
        val tv = dtlsTv
        val id = m.optString("appareil")
        val nonce = m.optString("nonce")
        val sig = Base64Url.decoder(m.optString("sig"))
        val octetsNonce = Base64Url.decoder(nonce)
        if (tel == null || tv == null || id.isEmpty() || id.length > 100 || sig == null ||
            octetsNonce == null || octetsNonce.size < 16 || nonce.length > 100
        ) return refuser()

        val connu = hote.registre.trouver(id)
        val cleRecue = m.optString("cle").takeIf { it.isNotEmpty() }?.let { Base64Url.decoder(it) ?: return refuser() }
        val spki: ByteArray = when {
            connu != null -> {
                val enregistree = Base64Url.decoder(connu.cle) ?: return refuser()
                if (cleRecue != null && !cleRecue.contentEquals(enregistree)) return refuser()
                enregistree
            }
            cleRecue != null -> cleRecue
            else -> {
                // Téléphone qui se croit appairé mais inconnu de la TV (révoqué) : il oublie la TV
                hote.journal("téléphone inconnu (révoqué)")
                envoyer(Protocole.revoked())
                terminer()
                return
            }
        }
        val cle = CryptoTelecommande.clePublique(spki) ?: return refuser()
        if (!CryptoTelecommande.verifier(cle, CryptoTelecommande.chaineHelloTelephone(tel, tv, nonce), sig)) return refuser()

        appareil = id
        cleTelephone = spki
        nomPropose = RegistreTelecommande.nettoyerNom(m.optString("nom"))
        val nonceTv = CryptoTelecommande.nonce()
        val sigTv = hote.identite.signer(CryptoTelecommande.chaineHelloTv(tv, tel, nonce, nonceTv))
        envoyer(
            Protocole.helloTv(
                nonce = nonceTv,
                sig = Base64Url.encoder(sigTv),
                nomTv = hote.nomTv,
                versionApp = hote.versionApp,
                idTv = hote.registre.idTv(),
                cle = Base64Url.encoder(hote.identite.spki),
            ),
        )
        if (connu != null) {
            etat = Etat.AUTHENTIFIEE
            hote.journal("téléphone connecté")
            hote.surAuthentifiee(this)
        } else {
            etat = Etat.CLE_VERIFIEE
            hote.journal("téléphone non appairé")
        }
    }

    /** Vérification ratée : canal fermé, sans détail. */
    private fun refuser() {
        hote.journal("hello refusé")
        terminer()
    }

    // ------------------------------------------------------------------ appairage

    private fun demandeAppairage(m: JSONObject) {
        if (confirmationEnCours) return
        val id = appareil ?: return
        val spki = cleTelephone ?: return
        val cle = Base64Url.decoder(m.optString("cle"))
        // La clé de la demande doit être celle qui a signé le hello
        if (cle == null || !cle.contentEquals(spki)) {
            envoyer(Protocole.pairRefused(RaisonRefus.INVALIDE))
            return
        }
        val empreinteTv = CryptoTelecommande.empreinteCle(hote.identite.spki)
        val raison = hote.appairage.verifier(m.optString("secret"), m.optString("empreinteTV").ifEmpty { null }, empreinteTv)
        if (raison != null) {
            hote.journal("appairage refusé (${raison.valeur})")
            envoyer(Protocole.pairRefused(raison))
            return
        }
        val nom = RegistreTelecommande.nettoyerNom(m.optString("nom")).ifEmpty { nomPropose }.ifEmpty { "Téléphone" }
        confirmationEnCours = true
        val affichee = hote.demanderConfirmation(this, nom) { autorise ->
            if (!confirmationEnCours || etat != Etat.CLE_VERIFIEE) return@demanderConfirmation
            confirmationEnCours = false
            if (!autorise) {
                hote.journal("appairage refusé (refuse)")
                envoyer(Protocole.pairRefused(RaisonRefus.REFUSE))
                return@demanderConfirmation
            }
            val telephone = TelephoneAppaire(id, nom, Base64Url.encoder(spki), hote.maintenant())
            hote.registre.ajouter(telephone)
            hote.appairage.consommer()
            envoyer(Protocole.paired(hote.nomTv, hote.registre.idTv(), Base64Url.encoder(hote.identite.spki)))
            etat = Etat.AUTHENTIFIEE
            hote.journal("appairage accepté")
            hote.surAppaire(telephone)
            hote.surAuthentifiee(this)
        }
        if (!affichee) {
            // Une autre demande attend déjà la réponse de l'utilisateur
            confirmationEnCours = false
            envoyer(Protocole.pairRefused(RaisonRefus.REFUSE))
        }
    }

    // ------------------------------------------------------------------ commandes

    private fun commande(t: String, m: JSONObject) {
        when (t) {
            "key" -> m.optString("k").takeIf { it in Protocole.TOUCHES }?.let { hote.injecteur.touche(it) }
            "text" -> {
                val s = m.optString("s").take(Protocole.TEXTE_MAX)
                val curseur = if (m.has("curseur") && !m.isNull("curseur")) m.optInt("curseur", -1).takeIf { it >= 0 } else null
                hote.injecteur.texte(s, curseur)
            }
            "submit" -> hote.injecteur.valider()
            "ping" -> envoyer(Protocole.pong(m.opt("n")))
            // Déjà appairé (la PWA a pu oublier la TV) : on confirme sans redemander
            "pair_request" -> envoyer(Protocole.paired(hote.nomTv, hote.registre.idTv(), Base64Url.encoder(hote.identite.spki)))
        }
    }
}
