package fr.projo.launcher.data

import android.util.Log
import java.text.Normalizer

/**
 * Favoris proposés au premier lancement, dans cet ordre, s'ils sont installés.
 * Les apps sont retrouvées par leur NOM (les noms de paquets varient selon les versions TV).
 */
object DefaultFavorites {

    /** Nom affiché → variantes de nom acceptées (comparées sans accents, espaces ni ponctuation). */
    private val souhaites = listOf(
        "Netflix" to listOf("netflix"),
        "Prime Video" to listOf("primevideo", "amazonprimevideo"),
        "Canal+" to listOf("canalplus", "mycanal"),
        "SmartTube" to listOf("smarttube"),
        "Twitch" to listOf("twitch"),
    )

    fun find(apps: List<AppInfo>): List<String> {
        val trouves = mutableListOf<String>()
        for ((nom, variantes) in souhaites) {
            // Correspondance exacte d'abord, puis « commence par » (ex. « SmartTube Beta »)
            val app = apps.firstOrNull { normaliser(it.label) in variantes }
                ?: apps.firstOrNull { a -> variantes.any { normaliser(a.label).startsWith(it) } }
            if (app != null) {
                Log.i(AppRepository.TAG, "Favori par défaut « $nom » → ${app.packageName} (${app.label})")
                trouves += app.packageName
            } else {
                Log.i(AppRepository.TAG, "Favori par défaut « $nom » : non installé")
            }
        }
        return trouves
    }

    /** « CANAL+ » → « canalplus », « Prime Vidéo » → « primevideo ». */
    fun normaliser(s: String): String {
        val sansAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return sansAccents.lowercase().replace("+", "plus").replace(Regex("[^a-z0-9]"), "")
    }
}
