package fr.projo.launcher.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.projo.launcher.data.GenreTmdb
import org.json.JSONArray
import org.json.JSONObject

/**
 * « Ma liste » du design Galerie : les titres ajoutés par « Ajouter à ma liste », gardés sur
 * l'appareil (préférences, en JSON), du plus récent au plus ancien. État Compose : l'accueil
 * se met à jour dès qu'un titre est ajouté ou retiré.
 */
class MaListe(private val prefs: SharedPreferences) {

    var titres: List<StudioProgramme> by mutableStateOf(decoder(prefs.getString(CLE, null)))
        private set

    fun contient(id: String): Boolean = titres.any { it.id == id }

    /** Ajoute [p] en tête (sans doublon ; sans son rang dans les tendances, qui change chaque semaine). */
    fun ajouter(p: StudioProgramme) {
        if (contient(p.id)) return
        ecrire((listOf(p.copy(rang = null)) + titres).take(MAX))
    }

    fun retirer(id: String) {
        if (!contient(id)) return
        ecrire(titres.filterNot { it.id == id })
    }

    /** Ajoute ou retire [p] ; renvoie true s'il est maintenant dans la liste. */
    fun basculer(p: StudioProgramme): Boolean {
        if (contient(p.id)) retirer(p.id) else ajouter(p)
        return contient(p.id)
    }

    private fun ecrire(liste: List<StudioProgramme>) {
        titres = liste
        prefs.edit().putString(CLE, encoder(liste)).apply()
    }

    companion object {
        const val FICHIER = "galerie"
        const val CLE = "ma_liste"
        /** Au plus 100 titres gardés. */
        const val MAX = 100

        fun de(context: Context): MaListe = MaListe(context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE))

        fun encoder(liste: List<StudioProgramme>): String = JSONArray().apply {
            for (p in liste) {
                put(
                    JSONObject()
                        .put("id", p.id)
                        .put("titre", p.titre)
                        .put("genre", p.genre.name)
                        .put("genres", JSONArray(p.genres))
                        .put("paquet", p.paquet)
                        .put("service", p.service)
                        .put("image", p.image ?: "")
                        .put("affiche", p.affiche ?: "")
                        .put("resume", p.resume)
                        .put("sortie", p.sortie ?: "")
                        .put("note", p.note ?: -1.0)
                )
            }
        }.toString()

        /** Liste lue (vide si absente ou illisible : jamais d'erreur). */
        fun decoder(texte: String?): List<StudioProgramme> {
            if (texte.isNullOrBlank()) return emptyList()
            val a = runCatching { JSONArray(texte) }.getOrNull() ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val titre = o.optString("titre")
                val paquet = o.optString("paquet")
                if (id.isEmpty() || titre.isEmpty() || paquet.isEmpty()) return@mapNotNull null
                val g = o.optJSONArray("genres")
                StudioProgramme(
                    id = id,
                    titre = titre,
                    genre = if (o.optString("genre") == GenreTmdb.SERIE.name) GenreTmdb.SERIE else GenreTmdb.FILM,
                    genres = (0 until (g?.length() ?: 0)).mapNotNull { j -> g?.optString(j)?.takeIf { it.isNotEmpty() } },
                    paquet = paquet,
                    service = o.optString("service"),
                    image = o.optString("image").ifEmpty { null },
                    affiche = o.optString("affiche").ifEmpty { null },
                    resume = o.optString("resume"),
                    sortie = o.optString("sortie").ifEmpty { null },
                    note = o.optDouble("note", -1.0).takeIf { it > 0.0 },
                )
            }.distinctBy { it.id }
        }
    }
}
