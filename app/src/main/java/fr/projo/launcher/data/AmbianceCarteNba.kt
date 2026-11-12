package fr.projo.launcher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ambiance de la carte (réglage du design NBA). */
enum class AmbianceNba(val id: String, val nom: String, val detail: String) {
    LEADERS("leaders", "Leaders", "Chaque division aux couleurs de son leader"),
    PARQUET("parquet", "Parquet", "Bois clair à l'Est, foncé à l'Ouest"),
    NUIT("nuit", "Nuit d'arène", "Sarcelle à l'Est, violet à l'Ouest");

    companion object {
        val PAR_DEFAUT = LEADERS

        /** Ambiance enregistrée ; identifiant inconnu ou absent : [PAR_DEFAUT]. */
        fun de(id: String?): AmbianceNba = entries.firstOrNull { it.id == id } ?: PAR_DEFAUT
    }
}

/**
 * Réglage « Ambiance de la carte » du design NBA : Leaders (par défaut), Parquet ou Nuit d'arène.
 * Enregistré sur l'appareil ; changé depuis Réglages ou depuis le téléphone.
 */
object AmbianceCarteNba {
    private const val FICHIER = "nba_carte"
    private const val CLE = "ambiance"
    private var lu = false

    /** Valeur courante (lue au premier appel de [charger]). */
    var ambiance by mutableStateOf(AmbianceNba.PAR_DEFAUT)
        private set

    fun charger(context: Context) {
        if (lu) return
        lu = true
        ambiance = AmbianceNba.de(context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).getString(CLE, null))
    }

    fun ecrire(context: Context, valeur: AmbianceNba) {
        context.getSharedPreferences(FICHIER, Context.MODE_PRIVATE).edit().putString(CLE, valeur.id).apply()
        lu = true
        ambiance = valeur
    }

    /** Choix suivant (tuile des Réglages : chaque OK passe au suivant, en boucle). */
    fun suivante(valeur: AmbianceNba): AmbianceNba = AmbianceNba.entries[(valeur.ordinal + 1) % AmbianceNba.entries.size]
}
