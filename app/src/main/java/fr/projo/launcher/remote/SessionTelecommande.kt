package fr.projo.launcher.remote

import fr.projo.launcher.boxscore.Affichage
import fr.projo.launcher.boxscore.EtatNba
import fr.projo.launcher.liens.Liens
import fr.projo.launcher.liens.ResultatLien
import org.json.JSONArray
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
     * Applis et réglages pilotés depuis le téléphone (voir Pilotage). null : non proposé, le
     * `hello` de la TV n'annonce alors pas `fonctions` et ces messages sont ignorés.
     */
    val pilotage: Pilotage? get() = null

    /**
     * « Envoyer sur la TV » (liens). null : non proposé, `fonctions` n'annonce pas `liens` et
     * `liens_etat` / `lien_ouvrir` sont ignorés.
     */
    val liens: LiensTv? get() = null

    /**
     * Box score NBA par-dessus la vidéo. null : non proposé, `fonctions` n'annonce pas `nba` et
     * `nba_etat` / `nba_afficher` / `nba_choisir` sont ignorés.
     */
    val nba: NbaTv? get() = null

    /**
     * Identifiant du design de l'accueil (« galerie », « verre »…), annoncé dans `hello` pour que
     * la télécommande prenne le même style ; null : pas annoncé.
     */
    val design: String? get() = null

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
    /** Secret présenté par la demande en attente de confirmation (voir GestionAppairage.reserver). */
    private var secretReserve: SecretAppairage? = null

    val authentifiee: Boolean get() = etat == Etat.AUTHENTIFIEE

    /** Le téléphone a envoyé `reglages_liste` pendant cette connexion : il reçoit les changements. */
    var abonneReglages = false
        private set

    /** Le téléphone a envoyé `liens_etat` pendant cette connexion : il reçoit les changements de `liens`. */
    var abonneLiens = false
        private set

    /** Le téléphone a envoyé `nba_etat` pendant cette connexion : il reçoit les changements de `nba`. */
    var abonneNba = false
        private set

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
            Etat.CLE_VERIFIEE -> if (versionOk(m)) {
                // Pendant l'appairage : seulement pair_request et ping
                if (t == "pair_request") demandeAppairage(m) else if (t == "ping") envoyer(Protocole.pong(m.opt("n")))
            }
            Etat.AUTHENTIFIEE -> if (versionOk(m)) commande(t, m)
            else -> Unit // avant les hello : ignoré
        }
    }

    /** Message de la TV vers le téléphone, seulement une fois authentifié (focus_text…). */
    fun envoyerSiAuthentifiee(message: String) {
        if (etat == Etat.AUTHENTIFIEE) envoyer(message)
    }

    /** `reglages` envoyé de lui-même par la TV (valeur changée) : seulement aux téléphones abonnés. */
    fun envoyerReglagesSiAbonne(message: String) {
        if (abonneReglages) envoyerSiAuthentifiee(message)
    }

    /** `liens` envoyé de lui-même par la TV (VPN, réglage, adresse) : seulement aux téléphones abonnés. */
    fun envoyerLiensSiAbonne(message: String) {
        if (abonneLiens) envoyerSiAuthentifiee(message)
    }

    /** `nba` envoyé de lui-même par la TV (scores, liste, choix, format, erreur, réglage) : seulement aux abonnés. */
    fun envoyerNbaSiAbonne(message: String) {
        if (abonneNba) envoyerSiAuthentifiee(message)
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
            libererSecret(accepte = false)
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
        val spki: ByteArray = when {
            // Appareil connu : clé enregistrée (le champ `cle` éventuel est ignoré)
            connu != null -> Base64Url.decoder(connu.cle) ?: return refuser()
            m.optString("cle").isNotEmpty() -> Base64Url.decoder(m.optString("cle")) ?: return refuser()
            else -> {
                // Téléphone qui se croit appairé mais inconnu de la TV (révoqué hors ligne) :
                // hello signé, puis revoked, puis fermeture. La PWA oublie alors la TV.
                hote.journal("téléphone inconnu (révoqué)")
                envoyerHelloTv(tel, tv, nonce)
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
        envoyerHelloTv(tel, tv, nonce)
        if (connu != null) {
            etat = Etat.AUTHENTIFIEE
            hote.journal("téléphone connecté")
            hote.surAuthentifiee(this)
        } else {
            etat = Etat.CLE_VERIFIEE
            hote.journal("téléphone non appairé")
        }
    }

    private fun envoyerHelloTv(tel: String, tv: String, nonceTel: String) {
        val nonceTv = CryptoTelecommande.nonce()
        val sigTv = hote.identite.signer(CryptoTelecommande.chaineHelloTv(tv, tel, nonceTel, nonceTv))
        envoyer(
            Protocole.helloTv(
                nonce = nonceTv,
                sig = Base64Url.encoder(sigTv),
                nomTv = hote.nomTv,
                versionApp = hote.versionApp,
                idTv = hote.registre.idTv(),
                cle = Base64Url.encoder(hote.identite.spki),
                fonctions = fonctions(),
                design = hote.design,
            ),
        )
    }

    /**
     * Fonctions annoncées : `apps` et `reglages` avec le pilotage, `liens` avec l'envoi de liens,
     * `nba` avec le box score ; null si aucune.
     */
    private fun fonctions(): List<String>? {
        val f = buildList {
            if (hote.pilotage != null) addAll(Protocole.FONCTIONS)
            if (hote.liens != null) add(Protocole.FONCTION_LIENS)
            if (hote.nba != null) add(Protocole.FONCTION_NBA)
        }
        return f.ifEmpty { null }
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
        val empreinteTv = CryptoTelecommande.empreinteCle(hote.identite.spki)
        val raison = hote.appairage.verifier(m.optString("secret"), m.optString("empreinteTV").ifEmpty { null }, empreinteTv)
        if (raison != null) {
            hote.journal("appairage refusé (${raison.valeur})")
            envoyer(Protocole.pairRefused(raison))
            return
        }
        // La clé de la demande doit être celle qui a signé le hello
        val cle = Base64Url.decoder(m.optString("cle"))
        if (cle == null || !cle.contentEquals(spki)) {
            envoyer(Protocole.pairRefused(RaisonRefus.INVALIDE))
            return
        }
        val nom = RegistreTelecommande.nettoyerNom(m.optString("nom")).ifEmpty { nomPropose }.ifEmpty { "Téléphone" }
        confirmationEnCours = true
        // Pas de renouvellement du secret (QR code permanent) tant que l'utilisateur n'a pas répondu
        secretReserve = hote.appairage.reserver()
        val affichee = hote.demanderConfirmation(this, nom) { autorise ->
            if (!confirmationEnCours || etat != Etat.CLE_VERIFIEE) return@demanderConfirmation
            confirmationEnCours = false
            if (!autorise) {
                libererSecret(accepte = false)
                hote.journal("appairage refusé (refuse)")
                envoyer(Protocole.pairRefused(RaisonRefus.REFUSE))
                return@demanderConfirmation
            }
            val telephone = TelephoneAppaire(id, nom, Base64Url.encoder(spki), hote.maintenant())
            hote.registre.ajouter(telephone)
            // Usage unique : consomme le secret de CETTE demande (pas un secret renouvelé entre-temps)
            libererSecret(accepte = true)
            envoyer(Protocole.paired(hote.nomTv, hote.registre.idTv(), Base64Url.encoder(hote.identite.spki)))
            etat = Etat.AUTHENTIFIEE
            hote.journal("appairage accepté")
            hote.surAppaire(telephone)
            hote.surAuthentifiee(this)
        }
        if (!affichee) {
            // Une autre demande attend déjà la réponse de l'utilisateur
            confirmationEnCours = false
            libererSecret(accepte = false)
            envoyer(Protocole.pairRefused(RaisonRefus.REFUSE))
        }
    }

    private fun libererSecret(accepte: Boolean) {
        val s = secretReserve ?: return
        secretReserve = null
        hote.appairage.liberer(s, accepte)
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
            "apps_liste", "apps_images", "app_ouvrir", "reglages_liste", "reglage" -> hote.pilotage?.let { pilotage(it, t, m) }
            "liens_etat", "lien_ouvrir" -> hote.liens?.let { liens(it, t, m) }
            "nba_etat", "nba_afficher", "nba_choisir" -> hote.nba?.let { nba(it, t, m) }
        }
    }

    // ------------------------------------------------------------------ box score NBA

    /**
     * `nba_etat` (état, et abonnement de cette connexion), `nba_afficher` et `nba_choisir`
     * (docs/telecommande.md, « Box score NBA »). Format ou identifiant inconnu : ignoré ; le
     * téléphone reçoit quand même l'état actuel pour se recaler. Journal : types d'événements
     * seulement, jamais de score.
     */
    private fun nba(n: NbaTv, t: String, m: JSONObject) {
        val repondre: (EtatNba) -> Unit = { envoyerSiAuthentifiee(Protocole.nba(it)) }
        when (t) {
            "nba_etat" -> {
                abonneNba = true
                hote.journal("nba : état demandé")
                n.abonner(repondre)
            }
            "nba_afficher" -> {
                val format = Affichage.de(m.opt("affichage"))
                if (format == null) {
                    hote.journal("nba : affichage inconnu ignoré")
                    n.etat(repondre)
                    return
                }
                hote.journal("nba : affichage demandé")
                n.afficher(format, idMatch(m.opt("id")), repondre)
            }
            "nba_choisir" -> {
                val id = idMatch(m.opt("id"))
                if (id == null) {
                    n.etat(repondre)
                    return
                }
                hote.journal("nba : match demandé")
                n.choisir(id, repondre)
            }
        }
    }

    /** `id` d'un match (« LAL-BOS ») : chaîne non vide de 20 caractères au plus, sinon null. */
    private fun idMatch(v: Any?): String? = (v as? String)?.takeIf { it.isNotEmpty() && it.length <= Protocole.ID_MATCH_MAX }

    // ------------------------------------------------------------------ envoyer sur la TV (liens)

    /**
     * `liens_etat` (état, et abonnement de cette connexion) et `lien_ouvrir` (docs/telecommande.md,
     * « Envoyer sur la TV »). L'URL est vérifiée ici ; `lien_ouvert.url` reprend celle de la
     * demande. Journal : types d'événements seulement, jamais l'URL ni le titre.
     */
    private fun liens(l: LiensTv, t: String, m: JSONObject) {
        when (t) {
            "liens_etat" -> {
                abonneLiens = true
                hote.journal("état des liens demandé")
                l.etat { envoyerSiAuthentifiee(Protocole.liens(it)) }
            }
            "lien_ouvrir" -> {
                val brut = m.opt("url")
                val demandee = brut as? String ?: ""
                hote.journal("lien envoyé (téléphone)")
                val lien = Liens.lien(brut, m.opt("titre"))
                if (lien == null) {
                    hote.journal("lien refusé ${ResultatLien.INVALIDE}")
                    envoyerSiAuthentifiee(Protocole.lienOuvert(demandee, ResultatLien.Refuse(ResultatLien.INVALIDE)))
                    return
                }
                // Nom donné au téléphone à l'appairage (bandeau « envoyé par … »)
                val nom = appareil?.let { hote.registre.trouver(it)?.nom } ?: ""
                l.ouvrir(
                    lien,
                    sansVpn = m.opt("sansVpn") == true,
                    vpnDabord = m.opt("vpnDabord") == true,
                    expediteur = Liens.expediteur(nom),
                ) { r -> envoyerSiAuthentifiee(Protocole.lienOuvert(demandee, r)) }
            }
        }
    }

    // ------------------------------------------------------------------ pilotage (applis et réglages)

    /**
     * Applis et réglages (docs/telecommande.md, « Pilotage depuis le téléphone »). Tout est
     * vérifié ici : une appli doit être dans la liste actuelle de la TV, un réglage dans sa
     * description actuelle. Les réponses partent par [envoyerSiAuthentifiee] : rien si le
     * téléphone est parti entre-temps. Journal : types d'événements seulement.
     */
    private fun pilotage(p: Pilotage, t: String, m: JSONObject) {
        when (t) {
            "apps_liste" -> {
                hote.journal("liste des applis demandée")
                p.applis { envoyerSiAuthentifiee(Protocole.apps(it)) }
            }
            "apps_images" -> {
                val ids = idsDemandes(m.opt("ids"))
                if (ids.isEmpty()) return
                hote.journal("images d'applis demandées")
                p.applis { liste ->
                    if (!authentifiee) return@applis
                    val parId = liste.associateBy { it.id }
                    // Ordre demandé ; identifiants inconnus ignorés
                    val connues = ids.mapNotNull { parId[it] }
                    if (connues.isNotEmpty()) p.images(connues, { authentifiee }) { envoyerSiAuthentifiee(Protocole.appImage(it)) }
                }
            }
            "app_ouvrir" -> {
                val id = m.opt("id") as? String
                if (id.isNullOrEmpty() || id.length > Protocole.ID_APPLI_MAX) return
                p.applis { liste ->
                    if (!authentifiee) return@applis
                    if (liste.none { it.id == id }) {
                        // Seulement les applis de la liste actuelle (ni masquée, ni iBiTV, ni inconnue)
                        hote.journal("appli inconnue refusée")
                        envoyerSiAuthentifiee(Protocole.appOuverte(id, false))
                        return@applis
                    }
                    p.ouvrir(id) { ok ->
                        hote.journal(if (ok) "appli ouverte" else "appli non ouverte")
                        envoyerSiAuthentifiee(Protocole.appOuverte(id, ok))
                    }
                }
            }
            "reglages_liste" -> {
                abonneReglages = true
                hote.journal("réglages demandés")
                p.reglages { envoyerSiAuthentifiee(Protocole.reglages(it)) }
            }
            "reglage" -> p.reglages { items ->
                if (!authentifiee) return@reglages
                val changement = ReglagesTv.valider(items, m.opt("cle"), m.opt("valeur"), m.opt("bouton"))
                if (changement == null) {
                    // Refusé : l'état actuel quand même, pour que le téléphone se recale
                    hote.journal("réglage ignoré")
                    envoyerSiAuthentifiee(Protocole.reglages(items))
                    return@reglages
                }
                hote.journal("réglage ${changement.cle}")
                p.appliquer(changement) { p.reglages { envoyerSiAuthentifiee(Protocole.reglages(it)) } }
            }
        }
    }

    /** `apps_images.ids` : les 100 premiers au plus, chaînes seulement, sans doublon ; vide si ce n'est pas une liste. */
    private fun idsDemandes(brut: Any?): List<String> {
        val tableau = brut as? JSONArray ?: return emptyList()
        val ids = LinkedHashSet<String>()
        for (i in 0 until minOf(tableau.length(), Protocole.IDS_IMAGES_MAX)) {
            val id = tableau.opt(i) as? String ?: continue
            if (id.isNotEmpty() && id.length <= Protocole.ID_APPLI_MAX) ids += id
        }
        return ids.toList()
    }
}
