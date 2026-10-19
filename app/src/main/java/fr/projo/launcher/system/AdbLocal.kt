package fr.projo.launcher.system

import android.content.ComponentName
import android.content.Context
import android.util.Base64
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.math.BigInteger
import java.net.ConnectException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * « ADB local » : iBiProjo se connecte au débogage ADB du Fire TV lui-même (127.0.0.1:5555),
 * comme le ferait un ordinateur, pour activer son ouverture automatique. Le Fire TV affiche
 * une fenêtre « Autoriser le débogage ? » qu'il faut accepter avec la télécommande.
 *
 * Prérequis : Paramètres → Ma Fire TV → Options pour les développeurs → Débogage ADB : activé.
 */
object AdbLocal {

    /**
     * Active le service d'ouverture automatique (en gardant les autres services d'accessibilité
     * déjà actifs). À appeler hors du thread principal.
     * @return null si tout s'est bien passé, sinon un message d'erreur pour l'utilisateur.
     */
    fun activerOuverture(context: Context): String? {
        val notre = ComponentName(context, RedirectionAccueil::class.java)
        return try {
            Dadb.create("127.0.0.1", 5555, cles(context), 8_000, 120_000).use { adb ->
                fun shell(cmd: String): String = adb.open("shell:$cmd").use { it.source.readUtf8() }
                val actuels = shell("settings get secure enabled_accessibility_services").trim()
                val liste = actuels.split(':').map { it.trim() }
                    .filter { it.isNotEmpty() && it != "null" }.toMutableList()
                if (liste.none { ComponentName.unflattenFromString(it) == notre }) liste += notre.flattenToString()
                shell("settings put secure enabled_accessibility_services ${liste.joinToString(":")}")
                shell("settings put secure accessibility_enabled 1")
            }
            if (FireTv.redirectionActive(context)) null
            else "Le Fire TV a refusé l'activation. Réessaie et accepte la fenêtre « Autoriser le débogage »."
        } catch (e: ConnectException) {
            "Active d'abord « Débogage ADB » : Paramètres → Ma Fire TV → Options pour les développeurs."
        } catch (e: Exception) {
            "Activation impossible (${e.javaClass.simpleName}). Vérifie que « Débogage ADB » est activé, " +
                "puis accepte la fenêtre « Autoriser le débogage »."
        }
    }

    /** Clés RSA de « l'ordinateur » iBiProjo, créées une fois puis gardées dans l'app. */
    private fun cles(context: Context): AdbKeyPair {
        val dossier = File(context.filesDir, "adb").apply { mkdirs() }
        val fPrivee = File(dossier, "cle.pk8")
        val fPublique = File(dossier, "cle.pub")
        if (!fPrivee.exists() || !fPublique.exists()) {
            val paire = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            fPrivee.writeBytes(paire.private.encoded)
            fPublique.writeBytes(clePubliqueAdb(paire.public as RSAPublicKey))
        }
        val privee = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(fPrivee.readBytes()))
        // dadb attend le contenu du fichier .pub suivi d'un octet nul
        return AdbKeyPair(privee, fPublique.readBytes() + 0)
    }

    /**
     * Clé publique au format d'ADB (structure RSAPublicKey de « mincrypt », en base64),
     * calculée ici pour rester compatible avec Android 5 (pas de java.util.Base64).
     */
    private fun clePubliqueAdb(cle: RSAPublicKey): ByteArray {
        val mots = 2048 / 32
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n = cle.modulus
        val n0inv = r32.subtract(n.mod(r32).modInverse(r32)).toInt()
        val rr = BigInteger.ONE.shiftLeft(4096).mod(n)
        val tampon = ByteBuffer.allocate(4 + 4 + mots * 4 + mots * 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
        tampon.putInt(mots)
        tampon.putInt(n0inv)
        var reste = n
        repeat(mots) { tampon.putInt(reste.mod(r32).toInt()); reste = reste.shiftRight(32) }
        reste = rr
        repeat(mots) { tampon.putInt(reste.mod(r32).toInt()); reste = reste.shiftRight(32) }
        tampon.putInt(cle.publicExponent.toInt())
        val b64 = Base64.encodeToString(tampon.array(), Base64.NO_WRAP)
        return "$b64 iBiProjo@firetv".toByteArray()
    }
}
