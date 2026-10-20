package fr.projo.launcher.remote

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Téléphone appairé, tel qu'enregistré par la TV. */
data class TelephoneAppaire(
    /** Identifiant d'appareil du téléphone (champ `appareil`, « tel-… »). */
    val id: String,
    /** Nom affiché (donné par le téléphone, modifiable sur la TV). */
    val nom: String,
    /** Clé publique ECDSA P-256 (SPKI DER) en base64url. */
    val cle: String,
    /** Date d'appairage (ms depuis 1970). */
    val date: Long,
)

/**
 * Données de la télécommande, dans un fichier dédié (pas dans le DataStore des réglages :
 * lecture synchrone depuis le fil de la télécommande, et aucun conflit avec les autres branches).
 *
 * - identifiant PeerJS stable de la TV (« ibiprojo-<32 hex> ») et jeton de connexion au
 *   serveur (gardé pour retrouver le même identifiant après une coupure) ;
 * - liste des téléphones appairés.
 *
 * Écriture atomique : fichier temporaire puis renommage.
 */
class RegistreTelecommande(private val fichier: File) {

    private var idTv: String = ""
    private var jeton: String = ""
    private val telephones = LinkedHashMap<String, TelephoneAppaire>()

    init {
        charger()
        if (idTv.isEmpty() || jeton.isEmpty()) {
            if (idTv.isEmpty()) idTv = RemoteConfig.PREFIXE_TV + hex(CryptoTelecommande.octetsAleatoires(16))
            if (jeton.isEmpty()) jeton = hex(CryptoTelecommande.octetsAleatoires(8))
            enregistrer()
        }
    }

    @Synchronized fun idTv(): String = idTv
    @Synchronized fun jetonPeerJs(): String = jeton
    @Synchronized fun telephones(): List<TelephoneAppaire> = telephones.values.toList()
    @Synchronized fun trouver(id: String): TelephoneAppaire? = telephones[id]

    @Synchronized fun ajouter(t: TelephoneAppaire) {
        telephones[t.id] = t
        enregistrer()
    }

    @Synchronized fun renommer(id: String, nom: String): Boolean {
        val t = telephones[id] ?: return false
        telephones[id] = t.copy(nom = nettoyerNom(nom).ifEmpty { t.nom })
        enregistrer()
        return true
    }

    @Synchronized fun revoquer(id: String): Boolean {
        val retire = telephones.remove(id) != null
        if (retire) enregistrer()
        return retire
    }

    private fun charger() {
        try {
            if (!fichier.exists()) return
            val o = JSONObject(fichier.readText(Charsets.UTF_8))
            idTv = o.optString("idTv").takeIf { it.matches(Regex("${RemoteConfig.PREFIXE_TV}[0-9a-f]{32}")) } ?: ""
            jeton = o.optString("jeton").takeIf { it.matches(Regex("[0-9a-zA-Z]{8,64}")) } ?: ""
            val liste = o.optJSONArray("telephones") ?: JSONArray()
            for (i in 0 until liste.length()) {
                val t = liste.optJSONObject(i) ?: continue
                val id = t.optString("id")
                val cle = t.optString("cle")
                if (id.isEmpty() || cle.isEmpty()) continue
                telephones[id] = TelephoneAppaire(id, t.optString("nom", "Téléphone"), cle, t.optLong("date"))
            }
        } catch (e: Exception) {
            // Fichier abîmé : on repart de zéro (les téléphones devront être ré-appairés)
            telephones.clear()
        }
    }

    private fun enregistrer() {
        val o = JSONObject()
            .put("version", 1)
            .put("idTv", idTv)
            .put("jeton", jeton)
            .put("telephones", JSONArray().apply {
                for (t in telephones.values) {
                    put(JSONObject().put("id", t.id).put("nom", t.nom).put("cle", t.cle).put("date", t.date))
                }
            })
        fichier.parentFile?.mkdirs()
        val tmp = File(fichier.path + ".tmp")
        tmp.writeText(o.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(fichier)) {
            fichier.delete()
            tmp.renameTo(fichier)
        }
    }

    companion object {
        fun hex(octets: ByteArray): String = octets.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

        /** Nom de téléphone : une ligne, 40 caractères au plus, sans caractères de contrôle. */
        fun nettoyerNom(nom: String): String =
            nom.filter { !it.isISOControl() }.trim().take(40)
    }
}
