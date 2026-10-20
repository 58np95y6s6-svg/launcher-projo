package fr.projo.launcher.remote

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec

/**
 * Base64url (RFC 4648 §5) SANS remplissage « = », comme dans la spécification.
 * Écrit à la main : java.util.Base64 n'existe pas avant Android 8 (Fire TV : Android 9 mais
 * minSdk 22) et android.util.Base64 empêcherait de tester ce code hors d'Android.
 * Le décodage accepte aussi le remplissage et l'alphabet base64 classique (+ /).
 */
object Base64Url {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encoder(octets: ByteArray): String {
        val sb = StringBuilder((octets.size * 4 + 2) / 3)
        var i = 0
        while (i + 3 <= octets.size) {
            val n = (octets[i].toInt() and 0xFF shl 16) or (octets[i + 1].toInt() and 0xFF shl 8) or (octets[i + 2].toInt() and 0xFF)
            sb.append(ALPHABET[n ushr 18 and 63]).append(ALPHABET[n ushr 12 and 63]).append(ALPHABET[n ushr 6 and 63]).append(ALPHABET[n and 63])
            i += 3
        }
        when (octets.size - i) {
            1 -> {
                val n = octets[i].toInt() and 0xFF shl 16
                sb.append(ALPHABET[n ushr 18 and 63]).append(ALPHABET[n ushr 12 and 63])
            }
            2 -> {
                val n = (octets[i].toInt() and 0xFF shl 16) or (octets[i + 1].toInt() and 0xFF shl 8)
                sb.append(ALPHABET[n ushr 18 and 63]).append(ALPHABET[n ushr 12 and 63]).append(ALPHABET[n ushr 6 and 63])
            }
        }
        return sb.toString()
    }

    /** null si la chaîne n'est pas du base64url valide. */
    fun decoder(texte: String): ByteArray? {
        val s = texte.trimEnd('=')
        if (s.length % 4 == 1) return null
        val sortie = ByteArrayOutputStream(s.length * 3 / 4)
        var tampon = 0
        var bits = 0
        for (c in s) {
            val v = when (c) {
                in 'A'..'Z' -> c - 'A'
                in 'a'..'z' -> c - 'a' + 26
                in '0'..'9' -> c - '0' + 52
                '-', '+' -> 62
                '_', '/' -> 63
                else -> return null
            }
            tampon = (tampon shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                sortie.write(tampon ushr bits and 0xFF)
            }
        }
        return sortie.toByteArray()
    }
}

/**
 * Cryptographie du protocole (voir docs/telecommande.md) :
 * - clés ECDSA P-256 échangées en SPKI (DER) encodée en base64url ;
 * - empreinte d'une clé : SHA-256 de la SPKI, 16 premiers octets, base64url ;
 * - signatures ECDSA P-256 / SHA-256 au format « brut » r‖s de 64 octets (IEEE P1363,
 *   celui de WebCrypto), en base64url. Java signe et vérifie en DER : on convertit.
 */
object CryptoTelecommande {

    private val aleatoire = SecureRandom()

    fun octetsAleatoires(n: Int): ByteArray = ByteArray(n).also { aleatoire.nextBytes(it) }

    /** Nonce de `hello` : 16 octets aléatoires en base64url. */
    fun nonce(): String = Base64Url.encoder(octetsAleatoires(16))

    fun sha256(octets: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(octets)

    /** Empreinte d'une clé publique (SPKI DER) : SHA-256 tronqué à 16 octets, base64url (22 caractères). */
    fun empreinteCle(spki: ByteArray): String = Base64Url.encoder(sha256(spki).copyOf(16))

    /** Clé publique P-256 depuis sa SPKI ; null si invalide ou d'une autre courbe. */
    fun clePublique(spki: ByteArray): PublicKey? = try {
        val cle = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spki))
        cle.takeIf { (it as? ECPublicKey)?.params?.curve?.field?.fieldSize == 256 }
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------------ chaînes signées

    fun chaineHelloTelephone(dtlsTel: String, dtlsTv: String, nonceTel: String): String =
        "${RemoteConfig.CONTEXTE_SIGNATURE}|hello|$dtlsTel|$dtlsTv|$nonceTel"

    fun chaineHelloTv(dtlsTv: String, dtlsTel: String, nonceTel: String, nonceTv: String): String =
        "${RemoteConfig.CONTEXTE_SIGNATURE}|hello-tv|$dtlsTv|$dtlsTel|$nonceTel|$nonceTv"

    // ------------------------------------------------------------------ signatures

    /** Vérifie une signature brute r‖s (64 octets) de [message] (UTF-8). */
    fun verifier(cle: PublicKey, message: String, signatureBrute: ByteArray): Boolean = try {
        val der = brutVersDer(signatureBrute) ?: return false
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(cle)
            update(message.toByteArray(Charsets.UTF_8))
            verify(der)
        }
    } catch (e: Exception) {
        false
    }

    /** Signe avec une clé privée Java (logicielle ou Keystore) et rend le format brut r‖s. */
    fun signer(cle: PrivateKey, message: String): ByteArray {
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(cle)
            update(message.toByteArray(Charsets.UTF_8))
            sign()
        }
        return derVersBrut(der) ?: error("signature illisible")
    }

    /** r‖s (2 × 32 octets) → DER SEQUENCE { INTEGER r, INTEGER s }. */
    fun brutVersDer(brut: ByteArray): ByteArray? {
        if (brut.size != 64) return null
        val r = BigInteger(1, brut.copyOfRange(0, 32))
        val s = BigInteger(1, brut.copyOfRange(32, 64))
        if (r.signum() == 0 || s.signum() == 0) return null
        val rb = r.toByteArray()
        val sb = s.toByteArray()
        val corps = ByteArrayOutputStream()
        corps.write(0x02); corps.write(rb.size); corps.write(rb)
        corps.write(0x02); corps.write(sb.size); corps.write(sb)
        val c = corps.toByteArray()
        return byteArrayOf(0x30, c.size.toByte()) + c // corps ≤ 70 octets : longueur sur 1 octet
    }

    /** DER → r‖s (2 × 32 octets). */
    fun derVersBrut(der: ByteArray): ByteArray? {
        try {
            if (der.size < 8 || der[0].toInt() != 0x30) return null
            var i = 2
            if (der[1].toInt() and 0x80 != 0) i += der[1].toInt() and 0x7F
            fun entier(): ByteArray {
                require(der[i].toInt() == 0x02)
                val n = der[i + 1].toInt() and 0xFF
                val v = der.copyOfRange(i + 2, i + 2 + n)
                i += 2 + n
                val sansZeros = v.dropWhile { it.toInt() == 0 }.toByteArray()
                require(sansZeros.size <= 32)
                return ByteArray(32 - sansZeros.size) + sansZeros
            }
            return entier() + entier()
        } catch (e: Exception) {
            return null
        }
    }

    // ------------------------------------------------------------------ SDP

    /**
     * Empreinte DTLS d'un SDP, normalisée « <algorithme en minuscules> <HEX MAJUSCULES:…> »,
     * ex. « sha-256 4F:1A:…:DD » : première ligne `a=fingerprint:` en sha-256, sinon la
     * première ligne `a=fingerprint:` (session ou média). null si absente.
     */
    fun empreinteDtls(sdp: String): String? {
        val lignes = sdp.lineSequence().mapNotNull { ligne ->
            val l = ligne.trim()
            if (!l.startsWith("a=fingerprint:", ignoreCase = true)) return@mapNotNull null
            val parties = l.substring("a=fingerprint:".length).trim().split(Regex("\\s+"))
            if (parties.size != 2 || !parties[1].matches(Regex("[0-9A-Fa-f:]+"))) return@mapNotNull null
            parties[0].lowercase() to parties[1].uppercase()
        }.toList()
        val choisie = lignes.firstOrNull { it.first == "sha-256" } ?: lignes.firstOrNull() ?: return null
        return choisie.first + " " + choisie.second
    }
}

/** Identité dont la clé privée est un objet Java (Keystore ou clé logicielle). */
class IdentiteJava(private val privee: PrivateKey, override val spki: ByteArray) : IdentiteTv {
    override fun signer(message: String): ByteArray = CryptoTelecommande.signer(privee, message)
}
