package fr.projo.launcher.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clé d'identité de la TV (ECDSA P-256).
 *
 * 1. Android Keystore (Android 6+) : la clé privée ne quitte jamais le Keystore.
 * 2. Repli (Keystore absent ou défaillant, Android 5.1 du Fire TV en minSdk 22, tests
 *    Robolectric) : clé logicielle dans le stockage privé de l'appli, chiffrée en AES-GCM
 *    avec une clé dérivée de l'identifiant de l'appareil et d'un sel aléatoire. Ce n'est
 *    qu'un obscurcissement : la vraie protection est le bac à sable de l'appli.
 *
 * Une signature d'essai valide la clé avant usage (certains Keystore de TV bas de gamme
 * créent la clé puis refusent de signer).
 */
object IdentiteCles {

    private const val TAG = "Projo.Telecommande"
    private const val ALIAS = "ibiprojo_identite_v1"

    fun charger(context: Context, dossier: File): IdentiteTv {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                keystore()?.let { return it }
            } catch (e: Exception) {
                Log.w(TAG, "Keystore indisponible, clé logicielle")
            }
        }
        return logicielle(context, File(dossier, "identite.bin"))
    }

    private fun valide(identite: IdentiteTv): Boolean {
        val cle = CryptoTelecommande.clePublique(identite.spki) ?: return false
        val essai = "ibiprojo-v1|essai"
        return CryptoTelecommande.verifier(cle, essai, identite.signer(essai))
    }

    @SuppressLint("NewApi")
    private fun keystore(): IdentiteTv? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existante = ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
        val (privee, publique) = if (existante != null) {
            existante.privateKey to existante.certificate.publicKey
        } else {
            val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            gen.initialize(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            val paire = gen.generateKeyPair()
            paire.private to paire.public
        }
        // SPKI normalisée (certaines versions rendent une clé publique sans encodage X.509 direct)
        val spki = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publique.encoded)).encoded
        val identite = IdentiteJava(privee, spki)
        if (valide(identite)) return identite
        try { ks.deleteEntry(ALIAS) } catch (e: Exception) { /* rien */ }
        return null
    }

    // ------------------------------------------------------------------ repli logiciel

    private fun logicielle(context: Context, fichier: File): IdentiteTv {
        lire(context, fichier)?.let { return it }
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        val paire = gen.generateKeyPair()
        val identite = IdentiteJava(paire.private, paire.public.encoded)
        try {
            ecrire(context, fichier, paire.private, paire.public.encoded)
        } catch (e: Exception) {
            Log.w(TAG, "Clé logicielle non enregistrée")
        }
        return identite
    }

    private fun cleDeChiffrement(context: Context, sel: ByteArray): SecretKeySpec {
        @SuppressLint("HardwareIds")
        val appareil = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        val graine = sel + (appareil + "|" + context.packageName).toByteArray(Charsets.UTF_8)
        return SecretKeySpec(CryptoTelecommande.sha256(graine), "AES")
    }

    /** Format : sel (16) ‖ iv (12) ‖ AES-GCM(spkiLen(2) ‖ spki ‖ pkcs8). */
    private fun ecrire(context: Context, fichier: File, privee: PrivateKey, spki: ByteArray) {
        val sel = CryptoTelecommande.octetsAleatoires(16)
        val iv = CryptoTelecommande.octetsAleatoires(12)
        val clair = byteArrayOf((spki.size shr 8).toByte(), spki.size.toByte()) + spki + privee.encoded
        val chiffre = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, cleDeChiffrement(context, sel), GCMParameterSpec(128, iv))
            doFinal(clair)
        }
        fichier.parentFile?.mkdirs()
        val tmp = File(fichier.path + ".tmp")
        tmp.writeBytes(sel + iv + chiffre)
        if (!tmp.renameTo(fichier)) { fichier.delete(); tmp.renameTo(fichier) }
    }

    private fun lire(context: Context, fichier: File): IdentiteTv? = try {
        if (!fichier.exists()) {
            null
        } else {
            val tout = fichier.readBytes()
            val sel = tout.copyOfRange(0, 16)
            val iv = tout.copyOfRange(16, 28)
            val clair = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, cleDeChiffrement(context, sel), GCMParameterSpec(128, iv))
                doFinal(tout.copyOfRange(28, tout.size))
            }
            val n = (clair[0].toInt() and 0xFF shl 8) or (clair[1].toInt() and 0xFF)
            val spki = clair.copyOfRange(2, 2 + n)
            val privee = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(clair.copyOfRange(2 + n, clair.size)))
            IdentiteJava(privee, spki).takeIf { valide(it) }
        }
    } catch (e: Exception) {
        null
    }
}
