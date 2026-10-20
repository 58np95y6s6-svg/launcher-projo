package fr.projo.launcher.remote

import java.security.MessageDigest
import java.security.SecureRandom

/** Secret d'appairage affiché par la TV (QR code + code de secours). */
class SecretAppairage(
    /** 16 octets aléatoires, en base64url : dans le QR code. */
    val secret: String,
    /** Code de secours de 8 caractères (sans tiret) : 4 pour l'identifiant temporaire, 4 pour le secret court. */
    val code: String,
    /** Fin de validité (horloge de [GestionAppairage]). */
    val expire: Long,
) {
    /** Code affiché : « ABCD-EFGH ». */
    val codeAffiche: String get() = code.substring(0, 4) + "-" + code.substring(4)

    /** Identifiant PeerJS temporaire écouté pendant l'appairage : « ibiprojo-c-ABCD ». */
    val idTemporaire: String get() = RemoteConfig.PREFIXE_CODE + code.substring(0, 4)

    /** Secret court (4 derniers caractères du code de secours). */
    val secretCourt: String get() = code.substring(4)

    override fun toString() = "SecretAppairage(…)" // jamais le secret dans les journaux
}

/** Raisons de refus, valeurs du champ `raison` de `pair_refused`. */
enum class RaisonRefus(val valeur: String) {
    REFUSE("refuse"), EXPIRE("expire"), INVALIDE("invalide"), TROP_D_ESSAIS("trop_d_essais"),
}

/**
 * Secret d'appairage : 5 minutes, usage unique, 5 échecs par minute puis blocage d'1 minute.
 *
 * Ordre des vérifications d'une demande (voir docs/telecommande.md, « Appairage ») :
 * 1. blocage en cours → `trop_d_essais` (sans examiner le secret) ;
 * 2. aucun secret actif (jamais créé, expiré ou déjà utilisé) → `expire` ;
 * 3. secret faux → `invalide` (compte comme un échec ; le 5e échec en 1 minute répond
 *    `trop_d_essais` et bloque 1 minute) ;
 * 4. empreinte de clé TV attendue par le téléphone différente → `invalide` (compte aussi).
 *
 * Pas de fil d'exécution propre : appelé depuis le fil de la télécommande (ou des tests).
 */
class GestionAppairage(
    private val horloge: () -> Long = { System.currentTimeMillis() },
    private val aleatoire: SecureRandom = SecureRandom(),
) {
    private var actuel: SecretAppairage? = null
    private val echecs = ArrayDeque<Long>()
    private var bloqueJusqua = 0L

    /** Crée un nouveau secret (remplace le précédent). */
    fun demarrer(): SecretAppairage {
        val octets = ByteArray(16).also { aleatoire.nextBytes(it) }
        val alphabet = RemoteConfig.ALPHABET_CODE
        val code = String(CharArray(8) { alphabet[aleatoire.nextInt(alphabet.length)] })
        return SecretAppairage(Base64Url.encoder(octets), code, horloge() + RemoteConfig.DUREE_SECRET_MS)
            .also { actuel = it }
    }

    /** Fin de l'appairage (dialogue fermé) : le secret ne sert plus. */
    fun arreter() {
        actuel = null
    }

    /** Secret encore valable, ou null (aucun, expiré ou consommé). */
    fun actif(): SecretAppairage? = actuel?.takeIf { horloge() < it.expire }

    /** L'appairage a réussi : le secret est consommé (usage unique). */
    fun consommer() {
        actuel = null
    }

    fun estBloque(): Boolean = horloge() < bloqueJusqua

    /**
     * Vérifie une demande `pair_request`. [empreinteAttendue] : champ `empreinteTV` (absent
     * avec le code de secours) ; [empreinteTv] : empreinte de la clé de la TV.
     * Rend null si tout est bon, sinon la raison du refus.
     */
    fun verifier(secretRecu: String, empreinteAttendue: String?, empreinteTv: String): RaisonRefus? {
        val maintenant = horloge()
        if (maintenant < bloqueJusqua) return RaisonRefus.TROP_D_ESSAIS
        val s = actif() ?: return RaisonRefus.EXPIRE
        if (!secretValide(s, secretRecu)) return echec(maintenant)
        if (!empreinteAttendue.isNullOrEmpty() && !egal(empreinteAttendue, empreinteTv)) return echec(maintenant)
        return null
    }

    private fun echec(maintenant: Long): RaisonRefus {
        while (echecs.isNotEmpty() && maintenant - echecs.first() >= RemoteConfig.FENETRE_ESSAIS_MS) echecs.removeFirst()
        echecs.addLast(maintenant)
        if (echecs.size >= RemoteConfig.ESSAIS_MAX) {
            echecs.clear()
            bloqueJusqua = maintenant + RemoteConfig.BLOCAGE_MS
            return RaisonRefus.TROP_D_ESSAIS
        }
        return RaisonRefus.INVALIDE
    }

    /**
     * Secret du QR code (base64url, 22 caractères), ou secret court du code de secours :
     * les 4 derniers caractères, ou le code entier (« ABCD-EFGH », « abcdefgh »…).
     */
    private fun secretValide(s: SecretAppairage, recu: String): Boolean {
        if (recu.length >= 16) return egal(recu.trimEnd('='), s.secret)
        val normalise = recu.uppercase().filter { it.isLetterOrDigit() }
        return when (normalise.length) {
            4 -> egal(normalise, s.secretCourt)
            8 -> egal(normalise, s.code)
            else -> false
        }
    }

    /** Comparaison en temps constant. */
    private fun egal(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
